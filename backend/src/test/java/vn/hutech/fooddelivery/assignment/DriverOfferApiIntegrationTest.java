package vn.hutech.fooddelivery.assignment;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.security.JwtService;
import vn.hutech.fooddelivery.order.OrderRepository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

/** HTTP + JWT + MySQL that. Moi ghi/xoa fixture chi nam trong container rieng do Testcontainers tao. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "debug=false", "app.admin.phone=", "app.admin.password=",
        "app.jwt.secret=driver_offer_http_test_secret_at_least_32_chars",
        "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout=5",
        "logging.level.org.springframework.jdbc=INFO",
        "logging.level.org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration=ERROR"
})
@Import(DriverOfferApiIntegrationTest.Services.class)
@Testcontainers(disabledWithoutDocker = true)
class DriverOfferApiIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);
    @Container @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired TestRestTemplate rest;
    @Autowired JwtService jwt;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired TestClock clock;
    @MockitoSpyBean DriverOfferRepository offers;
    @MockitoSpyBean AssignmentOfferRepository assignmentOffers;
    @MockitoSpyBean(name = "testAssignmentService") AssignmentService assignments;

    @TestConfiguration(proxyBeanMethods = false)
    static class Services {
        @Bean TestClock driverOfferTestClock() { return new TestClock(); }

        @Bean @Primary
        OfferCreationService testOfferCreationService(AssignmentOfferRepository offers, TestClock clock) {
            return new OfferCreationService(offers, clock);
        }

        @Bean @Primary
        AssignmentService testAssignmentService(AssignmentRepository candidates, AssignmentOfferRepository offers,
                                                 OfferCreationService creation, TestClock clock) {
            return new AssignmentService(candidates, offers, creation, clock);
        }

        @Bean @Primary
        DriverOfferDecisionService testDriverOfferDecisionService(DriverOfferRepository offers,
                AssignmentOfferRepository assignments, OrderRepository orders, TestClock clock) {
            return new DriverOfferDecisionService(offers, assignments, orders, clock);
        }

        @Bean @Primary
        DriverOfferService testDriverOfferService(DriverOfferRepository offers, DriverOfferDecisionService decisions,
                                                  AssignmentService assignments, TestClock clock) {
            return new DriverOfferService(offers, decisions, assignments, clock);
        }
    }

    static class TestClock extends Clock {
        private final ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        private volatile Instant current = NOW.atZone(zone).toInstant();
        void set(LocalDateTime at) { current = at.atZone(zone).toInstant(); }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId other) { return Clock.fixed(current, other); }
        @Override public Instant instant() { return current; }
    }

    @BeforeEach
    void resetDisposableContainerFixtures() {
        clock.set(NOW);
        jdbc.update("DELETE FROM order_assignments", Map.of());
        jdbc.update("DELETE FROM orders", Map.of());
        jdbc.update("DELETE FROM drivers", Map.of());
        jdbc.update("DELETE FROM restaurants WHERE id = 900001", Map.of());
        jdbc.update("DELETE FROM merchants WHERE id = 900001", Map.of());
        jdbc.update("DELETE FROM users WHERE id >= 900000 OR id BETWEEN 42 AND 47", Map.of());
        user(900001, "CUSTOMER");
        user(900002, "MERCHANT");
        user(42, "DRIVER"); user(43, "DRIVER"); user(44, "CUSTOMER");
        user(45, "MERCHANT"); user(46, "ADMIN"); user(47, "DRIVER");
        jdbc.update("""
                INSERT INTO merchants (id, owner_user_id, business_name)
                VALUES (900001, 900002, 'Driver Offer Test Merchant')
                """, Map.of());
        jdbc.update("""
                INSERT INTO restaurants (id, merchant_id, name, address, lat, lng)
                VALUES (900001, 900001, 'Test Restaurant', 'Pickup Test Address', 10, 106)
                """, Map.of());
        jdbc.update("""
                INSERT INTO drivers (id, user_id, status, is_online, current_lat, current_lng, last_location_at)
                VALUES (7, 42, 'APPROVED', TRUE, 10, 106, :now),
                       (8, 43, 'APPROVED', TRUE, 10.1, 106, :now)
                """, Map.of("now", Timestamp.valueOf(NOW)));
        order(301, null, "PLACED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"get", "accept", "reject"})
    void unauthenticatedAndInvalidJwtRequestsAreUnauthorized(String action) {
        offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        assertError(request(action, 401, new HttpHeaders()), 401);
        var invalid = new HttpHeaders();
        invalid.setBearerAuth("invalid.jwt.token");
        assertError(request(action, 401, invalid), 401);
        assertEquals("OFFERED", offerStatus(401));
    }

    @ParameterizedTest
    @ValueSource(longs = {44, 45, 46})
    void rolesAreReadFromDatabaseRatherThanForgedDriverClaim(long userId) {
        offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        for (String action : List.of("get", "accept", "reject")) {
            assertError(request(action, 401, headers(userId)), 403);
        }
        assertEquals("OFFERED", offerStatus(401));
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept", "reject"})
    void anotherDriversOfferIsForbiddenEvenWithForgedBodyAndQuery(String action) {
        offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        var response = rest.exchange("/api/driver/offers/401/" + action + "?driverId=7&userId=42", HttpMethod.POST,
                new HttpEntity<>("{\"driverId\":7,\"userId\":42}", headers(43)), JsonNode.class);
        assertError(response, 403);
        assertEquals("OFFERED", offerStatus(401));
        assertEquals(null, orderRow(301).get("driver_id"));
    }

    @Test
    void getOnlyOwnActiveOffersReturnsCountdownAndNoCustomerContactWithoutWriting() {
        offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        order(302, null, "PLACED");
        offer(402, 302, 8, "OFFERED", NOW.plusSeconds(30));
        var before = jdbc.queryForList("SELECT * FROM order_assignments ORDER BY id", Map.of());

        var response = rest.exchange("/api/driver/offers?driverId=8&userId=43", HttpMethod.GET,
                new HttpEntity<>(headers(42)), JsonNode.class);

        assertEquals(200, response.getStatusCode().value());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(NOW, LocalDateTime.parse(body.path("serverTime").asText()));
        assertEquals(1, body.path("offers").size());
        var first = body.path("offers").get(0);
        assertEquals(401, first.path("offerId").asLong());
        assertEquals(301, first.path("orderId").asLong());
        assertEquals("DRIVER-OFFER-301", first.path("orderCode").asText());
        assertEquals("Test Restaurant", first.path("restaurantName").asText());
        assertEquals("Pickup Test Address", first.path("restaurantAddress").asText());
        assertEquals(10.0, first.path("pickupLat").asDouble());
        assertEquals(106.0, first.path("pickupLng").asDouble());
        assertEquals("Delivery Test Area", first.path("deliveryAddress").asText());
        assertEquals(1.23, first.path("distanceKm").asDouble());
        assertEquals(NOW.plusSeconds(30), LocalDateTime.parse(first.path("expiresAt").asText()));
        assertFalse(body.toString().contains("Private Receiver"));
        assertFalse(body.toString().contains("0900000999"));
        assertFalse(body.toString().contains("Private delivery note"));
        assertFalse(first.has("receiverPhone"));
        assertEquals(before, jdbc.queryForList("SELECT * FROM order_assignments ORDER BY id", Map.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "nullExpiry", "futureOffered", "cancelledOrder", "assignedOrder", "resolved"})
    void getHidesInvalidOffersWithoutChangingTheirStoredStatus(String invalid) {
        offer(401, 301, 7, "OFFERED", "nullExpiry".equals(invalid) ? null : NOW.plusSeconds(30));
        switch (invalid) {
            case "expired" -> clock.set(NOW.plusSeconds(30));
            case "futureOffered" -> jdbc.update("UPDATE order_assignments SET offered_at = :at WHERE id = 401",
                    Map.of("at", Timestamp.valueOf(NOW.plusSeconds(1))));
            case "cancelledOrder" -> jdbc.update("UPDATE orders SET status = 'CANCELLED' WHERE id = 301", Map.of());
            case "assignedOrder" -> jdbc.update("UPDATE orders SET driver_id = 8 WHERE id = 301", Map.of());
            case "resolved" -> jdbc.update("UPDATE order_assignments SET status = 'REJECTED' WHERE id = 401", Map.of());
            default -> { }
        }
        var before = offerRow(401);
        assertEquals(0, get(42).getBody().path("offers").size());
        assertEquals(before, offerRow(401));
    }

    @Test
    void getAndAcceptAgreeAtLastNanosecondBeforeExpiry() {
        offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        clock.set(NOW.plusSeconds(30).minusNanos(1));
        assertEquals(1, get(42).getBody().path("offers").size());
        assertEquals(200, post(42, 401, "accept").getStatusCode().value());
    }

    @Test
    void validAcceptAttachesOnlyJwtDriverAndReplayIsReadOnly() {
        offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        var before = orderRow(301);
        var first = rest.exchange("/api/driver/offers/401/accept?driverId=8&userId=43", HttpMethod.POST,
                new HttpEntity<>("{\"driverId\":8}", headers(42)), JsonNode.class);

        assertEquals(200, first.getStatusCode().value());
        assertEquals("ACCEPTED", first.getBody().path("status").asText());
        assertEquals(7L, ((Number) orderRow(301).get("driver_id")).longValue());
        assertEquals("PLACED", before.get("status"));
        assertEquals("PREPARING", orderRow(301).get("status"));
        assertEquals(1, count("SELECT COUNT(*) FROM order_status_history WHERE order_id = 301 AND status = 'PREPARING' AND changed_by = 42"));
        assertEquals("ACCEPTED", offerStatus(401));
        var accepted = offerRow(401);
        var assigned = orderRow(301);
        clock.set(NOW.plusMinutes(2));
        jdbc.update("UPDATE drivers SET is_online = FALSE WHERE id = 7", Map.of());
        var replay = post(42, 401, "accept");
        assertEquals(200, replay.getStatusCode().value());
        assertEquals(first.getBody(), replay.getBody());
        assertEquals(accepted, offerRow(401));
        assertEquals(assigned, orderRow(301));
        assertEquals(1, count("SELECT COUNT(*) FROM order_status_history WHERE order_id = 301 AND status = 'PREPARING'"));
        assertEquals(1, count("SELECT COUNT(*) FROM order_assignments"));
    }

    @Test
    void validRejectCommitsBeforeImmediateReassignmentAndReplayDoesNotDuplicate() {
        offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        var before = orderRow(301);
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            try (var connection = dataSource.getConnection();
                 var statement = connection.prepareStatement("SELECT status FROM order_assignments WHERE id = ?")) {
                statement.setLong(1, 401);
                try (var row = statement.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals("REJECTED", row.getString(1)); // Connection khac thay commit truoc khi retry.
                }
            }
            return invocation.callRealMethod();
        }).when(assignments).createOffer(301);

        var first = post(42, 401, "reject");

        assertEquals(200, first.getStatusCode().value());
        assertEquals("REJECTED", offerStatus(401));
        assertEquals(1, count("SELECT COUNT(*) FROM order_assignments WHERE driver_id = 8 AND status = 'OFFERED'"));
        assertEquals(before, orderRow(301));
        assertTrue(jdbc.queryForObject("SELECT is_online FROM drivers WHERE id = 7", Map.of(), Boolean.class));
        clock.set(NOW.plusSeconds(5));
        assertEquals(first.getBody(), post(42, 401, "reject").getBody());
        assertEquals(2, count("SELECT COUNT(*) FROM order_assignments"));
    }

    @Test
    void postCommitReassignmentErrorIsExplicitAndReplayRecovers() {
        offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        doThrow(new CannotAcquireLockException("Private SQL failure")).when(assignments).createOffer(301);

        var failed = post(42, 401, "reject");

        assertError(failed, 503);
        assertTrue(failed.getBody().path("message").asText().startsWith("Đã từ chối"));
        assertFalse(failed.getBody().toString().contains("Private SQL"));
        assertEquals("REJECTED", offerStatus(401));
        var committed = offerRow(401);
        assertEquals(1, count("SELECT COUNT(*) FROM order_assignments"));
        doCallRealMethod().when(assignments).createOffer(301);
        clock.set(NOW.plusSeconds(5));
        assertEquals(200, post(42, 401, "reject").getStatusCode().value());
        assertEquals(committed, offerRow(401));
        assertEquals(1, count("SELECT COUNT(*) FROM order_assignments WHERE driver_id = 8 AND status = 'OFFERED'"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept", "reject"})
    void expiredOfferCannotBeRespondedToEvenIfDatabaseStillSaysOffered(String action) {
        offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        clock.set(NOW.plusSeconds(30));
        var before = offerRow(401);
        assertError(post(42, 401, action), 409);
        assertEquals(before, offerRow(401));
        assertEquals(null, orderRow(301).get("driver_id"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXPIRED", "CANCELLED", "REJECTED"})
    void resolvedOfferDoesNotBecomeAccepted(String status) {
        offer(401, 301, 7, status, NOW.plusSeconds(30));
        assertError(post(42, 401, "accept"), 409);
        assertEquals(status, offerStatus(401));
    }

    @Test
    void acceptedOfferCannotBeRejectedAndCancelledOrderCannotReplayAccept() {
        offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        assertEquals(200, post(42, 401, "accept").getStatusCode().value());
        assertError(post(42, 401, "reject"), 409);
        jdbc.update("UPDATE orders SET status = 'CANCELLED' WHERE id = 301", Map.of());
        assertError(post(42, 401, "accept"), 409);
        assertEquals("ACCEPTED", offerStatus(401));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "SUSPENDED", "offline", "oldGps", "futureGps", "missingGps", "invalidLat",
            "activeOrder", "otherOffer"})
    void driverEligibilityIsRecheckedInAcceptTransaction(String change) {
        offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        switch (change) {
            case "PENDING", "SUSPENDED" -> jdbc.update("UPDATE drivers SET status = :status WHERE id = 7", Map.of("status", change));
            case "offline" -> jdbc.update("UPDATE drivers SET is_online = FALSE WHERE id = 7", Map.of());
            case "oldGps" -> jdbc.update("UPDATE drivers SET last_location_at = :at WHERE id = 7",
                    Map.of("at", Timestamp.valueOf(NOW.minusSeconds(61))));
            case "futureGps" -> jdbc.update("UPDATE drivers SET last_location_at = :at WHERE id = 7",
                    Map.of("at", Timestamp.valueOf(NOW.plusSeconds(1))));
            case "missingGps" -> jdbc.update("UPDATE drivers SET current_lat = NULL WHERE id = 7", Map.of());
            case "invalidLat" -> jdbc.update("UPDATE drivers SET current_lat = 91 WHERE id = 7", Map.of());
            case "activeOrder" -> order(302, 7L, "DELIVERING");
            case "otherOffer" -> { order(302, null, "PLACED"); offer(402, 302, 7, "OFFERED", NOW.plusSeconds(30)); }
            default -> throw new IllegalArgumentException(change);
        }
        assertError(post(42, 401, "accept"), 409);
        assertEquals("OFFERED", offerStatus(401));
        assertEquals(null, orderRow(301).get("driver_id"));
    }

    @Test
    void rejectWithoutNextDriverDoesNotCancelOrder() {
        offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        jdbc.update("UPDATE drivers SET is_online = FALSE WHERE id = 8", Map.of());
        var before = orderRow(301);
        assertEquals(200, post(42, 401, "reject").getStatusCode().value());
        assertEquals(before, orderRow(301));
        assertEquals(1, count("SELECT COUNT(*) FROM order_assignments"));
    }

    @Test
    void acceptFailureAfterBothSqlUpdatesRollsBackOfferAndOrder() {
        offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        var orderBefore = orderRow(301);
        var offerBefore = offerRow(401);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new DataIntegrityViolationException("Private SQL details");
        }).when(offers).assignOrder(301, 7);

        assertError(post(42, 401, "accept"), 500);
        assertEquals(orderBefore, orderRow(301));
        assertEquals(offerBefore, offerRow(401));
        doCallRealMethod().when(offers).assignOrder(301, 7);
        assertEquals(200, post(42, 401, "accept").getStatusCode().value());
    }

    @Test
    void rejectSqlFailureRollsBackAndDoesNotReassign() {
        offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        var before = offerRow(401);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new DataIntegrityViolationException("Private SQL details");
        }).when(offers).respond(any(), eq("REJECTED"), any());

        assertError(post(42, 401, "reject"), 500);
        assertEquals(before, offerRow(401));
        assertEquals(1, count("SELECT COUNT(*) FROM order_assignments"));
    }

    @Test
    void lockErrorIsReportedAsUnavailableInsteadOfSuccess() {
        offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        doThrow(new CannotAcquireLockException("Private SQL deadlock")).when(offers).lockOffer(401);
        assertError(post(42, 401, "accept"), 503);
        assertEquals("OFFERED", offerStatus(401));
    }

    @ParameterizedTest
    @ValueSource(strings = {"acceptTwice", "acceptReject", "rejectTwice", "twoDrivers", "twoOrders"})
    void mysqlConcurrencyThroughRealHttpRequests(String race) throws Exception {
        offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        if ("twoDrivers".equals(race)) offer(402, 301, 8, "OFFERED", NOW.plusSeconds(30));
        if ("twoOrders".equals(race)) {
            order(302, null, "PLACED"); offer(402, 302, 7, "OFFERED", NOW.plusSeconds(30));
        }
        var reached = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        Set<Long> connections = ConcurrentHashMap.newKeySet();
        Set<String> isolations = ConcurrentHashMap.newKeySet();
        Answer<Object> barrier = invocation -> {
            if (calls.incrementAndGet() <= 2) {
                connections.add(jdbc.queryForObject("SELECT CONNECTION_ID()", Map.of(), Long.class));
                isolations.add(jdbc.queryForObject("SELECT @@transaction_isolation", Map.of(), String.class));
                reached.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
            }
            return invocation.callRealMethod();
        };
        doAnswer(barrier).when(offers).findMetadata(anyLong());
        String firstAction = "rejectTwice".equals(race) ? "reject" : "accept";
        String secondAction = Set.of("acceptReject", "rejectTwice").contains(race) ? "reject" : "accept";
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> post(42, 401, firstAction));
            var second = executor.submit(() -> post("twoDrivers".equals(race) ? 43 : 42,
                    Set.of("twoDrivers", "twoOrders").contains(race) ? 402 : 401, secondAction));
            try {
                assertTrue(reached.await(5, TimeUnit.SECONDS));
                release.countDown();
                var a = first.get(15, TimeUnit.SECONDS);
                var b = second.get(15, TimeUnit.SECONDS);
                var statuses = List.of(a.getStatusCode().value(), b.getStatusCode().value()).stream().sorted().toList();
                assertEquals(2, connections.size());
                assertEquals(Set.of("READ-COMMITTED"), isolations);
                if (Set.of("acceptTwice", "rejectTwice").contains(race)) {
                    assertEquals(List.of(200, 200), statuses);
                    assertEquals(a.getBody(), b.getBody());
                } else if ("twoOrders".equals(race)) {
                    // Hai OFFERED cung driver la du lieu bat thuong: ca hai bi tu choi an toan.
                    assertEquals(List.of(409, 409), statuses);
                    assertEquals(0, count("SELECT COUNT(*) FROM orders WHERE driver_id IS NOT NULL"));
                } else {
                    assertEquals(List.of(200, 409), statuses);
                }
                assertTrue(count("SELECT COUNT(*) FROM orders WHERE driver_id = 7") <= 1);
                assertTrue(count("SELECT COUNT(*) FROM order_assignments WHERE order_id = 301 AND status = 'ACCEPTED'") <= 1);
                if ("rejectTwice".equals(race)) {
                    assertEquals(1, count("SELECT COUNT(*) FROM order_assignments WHERE status = 'OFFERED'"));
                    assertEquals(2, count("SELECT COUNT(*) FROM order_assignments"));
                }
                if (Set.of("acceptTwice", "twoDrivers").contains(race)) {
                    assertEquals(1, count("SELECT COUNT(*) FROM order_assignments WHERE status = 'ACCEPTED'"));
                }
                System.out.printf("MYSQL_DRIVER_OFFER_RACE %s connections=%s isolation=%s http=%s%n",
                        race, connections, isolations, statuses);
            } finally {
                release.countDown();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acceptWaitingForOrderLockSeesCommittedCancellationOrExpiry(boolean cancel) throws Exception {
        offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        var reached = new CountDownLatch(1);
        doAnswer(invocation -> { reached.countDown(); return invocation.callRealMethod(); })
                .when(assignmentOffers).lockOrder(301);
        try (var connection = dataSource.getConnection(); var executor = Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            try {
                String sql = cancel ? "UPDATE orders SET status = 'CANCELLED' WHERE id = ?"
                        : "SELECT id FROM orders WHERE id = ? FOR UPDATE";
                try (var statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, 301);
                    if (cancel) assertEquals(1, statement.executeUpdate());
                    else try (var row = statement.executeQuery()) { assertTrue(row.next()); }
                }
                var response = executor.submit(() -> post(42, 401, "accept"));
                assertTrue(reached.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> response.get(200, TimeUnit.MILLISECONDS));
                if (!cancel) clock.set(NOW.plusSeconds(30));
                connection.commit();
                assertError(response.get(10, TimeUnit.SECONDS), 409);
                assertEquals("OFFERED", offerStatus(401));
                assertEquals(null, orderRow(301).get("driver_id"));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void missingProfileOfferAndInvalidIdUseApiError() {
        assertError(get(47), 404);
        assertError(post(42, 99999, "accept"), 404);
        assertError(post(42, 0, "accept"), 400);
        var badId = rest.exchange("/api/driver/offers/not-a-number/reject", HttpMethod.POST,
                new HttpEntity<>(headers(42)), JsonNode.class);
        assertError(badId, 400);
    }

    @Test
    void swaggerDocumentsExactlyThreeOfferOperationsAndNoClientDriverIdentity() {
        var response = rest.getForEntity("/v3/api-docs", JsonNode.class);
        assertEquals(200, response.getStatusCode().value());
        var paths = response.getBody().path("paths");
        assertTrue(paths.path("/api/driver/offers").has("get"));
        for (String action : List.of("accept", "reject")) {
            var operation = paths.path("/api/driver/offers/{id}/" + action).path("post");
            assertFalse(operation.isMissingNode());
            assertFalse(operation.has("requestBody"));
            assertEquals(1, operation.path("parameters").size());
            assertEquals("id", operation.path("parameters").get(0).path("name").asText());
            for (String status : List.of("200", "400", "401", "403", "404", "409", "500", "503")) {
                assertTrue(operation.path("responses").has(status), action + " response " + status);
            }
        }
        int offerPaths = 0;
        var names = paths.fieldNames();
        while (names.hasNext()) {
            if (names.next().startsWith("/api/driver/offers")) offerPaths++;
        }
        assertEquals(3, offerPaths);
    }

    private HttpHeaders headers(long userId) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(jwt.generateToken(userId, "DRIVER"));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<JsonNode> request(String action, long id, HttpHeaders headers) {
        String url = "get".equals(action) ? "/api/driver/offers" : "/api/driver/offers/" + id + "/" + action;
        return rest.exchange(url, "get".equals(action) ? HttpMethod.GET : HttpMethod.POST,
                new HttpEntity<>(headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(long userId) { return request("get", 0, headers(userId)); }
    private ResponseEntity<JsonNode> post(long userId, long offerId, String action) {
        return request(action, offerId, headers(userId));
    }

    private void assertError(ResponseEntity<JsonNode> response, int status) {
        assertEquals(status, response.getStatusCode().value(), () -> String.valueOf(response.getBody()));
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(status, body.path("status").asInt());
        assertTrue(body.path("fieldErrors").isObject());
        assertTrue(body.has("timestamp"));
        assertFalse(body.has("trace"));
        assertFalse(body.toString().contains("Private SQL"));
    }

    private void user(long id, String role) {
        jdbc.update("""
                INSERT INTO users (id, full_name, phone, password_hash, role)
                VALUES (:id, 'Driver Offer Test User', :phone, 'test-only-hash', :role)
                """, Map.of("id", id, "phone", "09" + id, "role", role));
    }

    private void order(long id, Long driverId, String status) {
        jdbc.update("""
                INSERT INTO orders (id, code, customer_id, restaurant_id, driver_id, status,
                                    receiver_name, receiver_phone, delivery_address, delivery_lat, delivery_lng,
                                    subtotal, total_amount, note)
                VALUES (:id, :code, 900001, 900001, :driverId, :status,
                        'Private Receiver', '0900000999', 'Delivery Test Area', 10, 106, 50000, 50000, 'Private delivery note')
                """, new MapSqlParameterSource().addValue("id", id).addValue("code", "DRIVER-OFFER-" + id)
                .addValue("driverId", driverId).addValue("status", status));
    }

    private void offer(long id, long orderId, long driverId, String status, LocalDateTime expiresAt) {
        jdbc.update("""
                INSERT INTO order_assignments (id, order_id, driver_id, status, distance_km, offered_at, expires_at, responded_at)
                VALUES (:id, :orderId, :driverId, :status, 1.23, :offeredAt, :expiresAt, :respondedAt)
                """, new MapSqlParameterSource().addValue("id", id).addValue("orderId", orderId).addValue("driverId", driverId)
                .addValue("status", status).addValue("offeredAt", Timestamp.valueOf(NOW))
                .addValue("expiresAt", expiresAt == null ? null : Timestamp.valueOf(expiresAt))
                .addValue("respondedAt", "OFFERED".equals(status) ? null : Timestamp.valueOf(NOW)));
    }

    private String offerStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM order_assignments WHERE id = :id", Map.of("id", id), String.class);
    }
    private Map<String, Object> offerRow(long id) {
        return jdbc.queryForMap("SELECT * FROM order_assignments WHERE id = :id", Map.of("id", id));
    }
    private Map<String, Object> orderRow(long id) {
        return jdbc.queryForMap("SELECT * FROM orders WHERE id = :id", Map.of("id", id));
    }
    private int count(String sql) { return jdbc.queryForObject(sql, Map.of(), Integer.class); }
}
