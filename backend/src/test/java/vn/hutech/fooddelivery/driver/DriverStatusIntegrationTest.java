package vn.hutech.fooddelivery.driver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.driver.dto.DriverStatusRequest;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.security.JwtService;
import vn.hutech.fooddelivery.user.UserRepository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.any;

/** Chi su dung fixture/container rieng; khong thay doi tai khoan PENDING tren DB dang dung. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "debug=false", "app.admin.phone=", "app.admin.password=",
        "app.jwt.secret=driver_status_http_test_secret_at_least_32_chars",
        "app.assignment.scheduling.enabled=false",
        "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout=2",
        "logging.level.org.springframework.jdbc=INFO",
        "logging.level.org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration=ERROR"
})
@Testcontainers(disabledWithoutDocker = true)
class DriverStatusIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("driver_status_guard_test").withUsername("driver_status_guard");

    private static final AtomicInteger sequence = new AtomicInteger();

    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;
    @Autowired
    ObjectMapper mapper;
    @MockitoSpyBean
    NamedParameterJdbcTemplate jdbc;
    @MockitoSpyBean DriverRepository drivers;
    @Autowired DriverService service;
    @Autowired PlatformTransactionManager transactions;
    @Autowired
    DataSource dataSource;
    @Autowired
    UserRepository users;
    @Autowired
    JwtService jwt;

    @BeforeEach
    void refuseFixtureWritesOutsideDedicatedContainer() throws Exception {
        assertTrue(mysql.isRunning());
        try (var connection = dataSource.getConnection()) {
            assertEquals("driver_status_guard_test", connection.getCatalog());
            assertTrue(connection.getMetaData().getURL().contains(":" + mysql.getMappedPort(3306) + "/driver_status_guard_test"));
            assertEquals("driver_status_guard", jdbc.queryForObject(
                    "SELECT SUBSTRING_INDEX(CURRENT_USER(), '@', 1)", Map.of(), String.class));
            assertTrue(jdbc.queryForObject("SELECT VERSION()", Map.of(), String.class).startsWith("8.4."));
        }
    }

    @Test
    void approvedDriverCanUsePostLocationThenGoOnlineWithoutChangingOtherDataOrDriver() throws Exception {
        var driver = createDriver("APPROVED", false, null, null, null);
        var other = createDriver("APPROVED", false, BigDecimal.ZERO, BigDecimal.ZERO, now());
        var otherBefore = snapshot(other.driverId());
        assertEquals(HttpStatus.CREATED, rest.postForEntity("/api/driver/location",
                new HttpEntity<>("{\"lat\":10.7769,\"lng\":106.7009}", headers(driver.token())), JsonNode.class)
                .getStatusCode());
        var before = withoutOnline(snapshot(driver.driverId()));

        var response = patch(driver.token(), "{\"isOnline\":true}", "?driverId=" + other.driverId());

        assertEquals(200, response.statusCode());
        assertEquals(mapper.readTree("{\"isOnline\":true}"), mapper.readTree(response.body()));
        assertEquals(true, snapshot(driver.driverId()).get("is_online"));
        assertEquals(before, withoutOnline(snapshot(driver.driverId())));
        assertEquals(otherBefore, snapshot(other.driverId()));
        var profile = rest.exchange("/api/driver/profile", HttpMethod.GET,
                new HttpEntity<>(headers(driver.token())), JsonNode.class).getBody();
        assertNotNull(profile);
        assertTrue(profile.path("isOnline").asBoolean());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "REJECTED", "SUSPENDED"})
    void nonApprovedDriverWithFreshGpsCannotGoOnline(String status) throws Exception {
        var driver = createDriver(status, false, BigDecimal.ZERO, BigDecimal.ZERO, now());
        var before = snapshot(driver.driverId());

        var response = patch(driver.token(), "{\"isOnline\":true}", "");

        assertEquals(403, response.statusCode());
        assertEquals(403, mapper.readTree(response.body()).path("status").asInt());
        assertEquals(before, snapshot(driver.driverId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingLat", "missingLng", "missingTime", "old", "future", "invalidLat", "invalidLng"})
    void invalidGpsReturns409WithoutUpdatingStoredState(String kind) throws Exception {
        BigDecimal lat = "missingLat".equals(kind) ? null : BigDecimal.ZERO;
        BigDecimal lng = "missingLng".equals(kind) ? null : BigDecimal.ZERO;
        LocalDateTime at = "missingTime".equals(kind) ? null : now();
        if ("old".equals(kind)) at = now().minusSeconds(120);
        if ("future".equals(kind)) at = now().plusSeconds(120);
        if ("invalidLat".equals(kind)) lat = new BigDecimal("91");
        if ("invalidLng".equals(kind)) lng = new BigDecimal("181");
        var driver = createDriver("APPROVED", false, lat, lng, at);
        var before = snapshot(driver.driverId());

        var response = patch(driver.token(), "{\"isOnline\":true}", "");

        assertEquals(409, response.statusCode());
        assertEquals(409, mapper.readTree(response.body()).path("status").asInt());
        assertEquals(before, snapshot(driver.driverId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "APPROVED", "REJECTED", "SUSPENDED"})
    void allStatusesCanGoOfflineRepeatedlyWithoutGps(String status) throws Exception {
        var driver = createDriver(status, true, null, null, null);
        var before = withoutOnline(snapshot(driver.driverId()));

        for (int attempt = 0; attempt < 2; attempt++) {
            var response = patch(driver.token(), "{\"isOnline\":false}", "");
            assertEquals(200, response.statusCode());
            assertEquals(mapper.readTree("{\"isOnline\":false}"), mapper.readTree(response.body()));
            assertEquals(false, snapshot(driver.driverId()).get("is_online"));
            assertEquals(before, withoutOnline(snapshot(driver.driverId())));
        }
    }

    @Test
    void offlineWithOldGpsLeavesDeliveringOrderUnchanged() throws Exception {
        var driver = createDriver("APPROVED", true, BigDecimal.ZERO, BigDecimal.ZERO, now().minusDays(1));
        long customerId = users.insert("Customer Test", phone(), null, "test-only-hash", "CUSTOMER");
        jdbc.update("""
                INSERT INTO orders (code, customer_id, restaurant_id, driver_id, status,
                                    receiver_name, receiver_phone, delivery_address, delivery_lat, delivery_lng,
                                    subtotal, total_amount)
                VALUES ('STATUS-DELIVERING', :customerId, 1, :driverId, 'DELIVERING',
                        'Receiver Test', '0999999999', 'Test address', 10, 106, 50000, 50000)
                """, Map.of("customerId", customerId, "driverId", driver.driverId()));
        var orderBefore = jdbc.queryForMap("SELECT * FROM orders WHERE code = :code",
                Map.of("code", "STATUS-DELIVERING"));

        // Approved 4B-3A policy: protect an assigned order instead of permitting OFF.
        assertEquals(409, patch(driver.token(), "{\"isOnline\":false}", "").statusCode());
        assertEquals(true, snapshot(driver.driverId()).get("is_online"));

        assertEquals(orderBefore, jdbc.queryForMap("SELECT * FROM orders WHERE code = :code",
                Map.of("code", "STATUS-DELIVERING")));
    }

    @Test
    void driverWithoutProfileReceives404() throws Exception {
        long userId = users.insert("Status Test", phone(), null, "test-only-hash", "DRIVER");
        var response = patch(jwt.generateToken(userId, "DRIVER"), "{\"isOnline\":false}", "");
        assertEquals(404, response.statusCode());
        assertEquals(404, mapper.readTree(response.body()).path("status").asInt());
    }

    @Test
    void patchWaitingForConcurrentSuspensionCannotEnableOnline() throws Exception {
        var driver = createDriver("APPROVED", false, BigDecimal.ZERO, BigDecimal.ZERO, now());
        try (var connection = dataSource.getConnection(); var executor = Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.prepareStatement("UPDATE drivers SET status = 'SUSPENDED' WHERE id = ?")) {
                    statement.setLong(1, driver.driverId());
                    assertEquals(1, statement.executeUpdate());
                }
                var started = new CountDownLatch(1);
                var request = executor.submit(() -> {
                    started.countDown();
                    return patch(driver.token(), "{\"isOnline\":true}", "");
                });
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> request.get(200, TimeUnit.MILLISECONDS));
                connection.commit();

                assertEquals(403, request.get(10, TimeUnit.SECONDS).statusCode());
                assertEquals("SUSPENDED", snapshot(driver.driverId()).get("status"));
                assertEquals(false, snapshot(driver.driverId()).get("is_online"));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void swaggerDocumentsStrictBooleanRequestAndStoredOnlineResponse() {
        var response = rest.getForEntity("/v3/api-docs", JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode api = response.getBody();
        assertNotNull(api);
        var patch = api.path("paths").path("/api/driver/status").path("patch");
        assertFalse(patch.isMissingNode());
        assertFalse(patch.has("parameters"));
        for (String code : Set.of("200", "400", "401", "403", "404", "409", "500")) {
            assertTrue(patch.path("responses").has(code), code);
        }
        var requestSchema = api.path("components").path("schemas").path("DriverStatusRequest");
        assertEquals(1, requestSchema.path("properties").size());
        assertEquals("boolean", requestSchema.path("properties").path("isOnline").path("type").asText());
        assertFalse(requestSchema.path("additionalProperties").asBoolean(true));
        assertEquals(1, api.path("components").path("schemas").path("DriverStatusResponse").path("properties").size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_PAYMENT", "PLACED", "ACCEPTED", "PREPARING", "READY_FOR_PICKUP", "PICKED_UP", "DELIVERING"})
    void everyAssignedActiveStateBlocksOffAndKeepsOrderUnchanged(String state) throws Exception {
        var driver = createDriver("APPROVED", true, null, null, null);
        long orderId = createOrder(driver.driverId(), state);
        var before = jdbc.queryForMap("SELECT * FROM orders WHERE id=:id", Map.of("id", orderId));
        var response = patch(driver.token(), "{\"isOnline\":false}", "");
        assertEquals(409, response.statusCode());
        assertEquals(409, mapper.readTree(response.body()).path("status").asInt());
        assertFalse(mapper.readTree(response.body()).has("currentOrder"));
        assertEquals(true, snapshot(driver.driverId()).get("is_online"));
        assertEquals(before, jdbc.queryForMap("SELECT * FROM orders WHERE id=:id", Map.of("id", orderId)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DELIVERED", "CANCELLED", "REJECTED"})
    void terminalOrdersDoNotBlockOff(String state) throws Exception {
        var driver = createDriver("APPROVED", true, null, null, null);
        createOrder(driver.driverId(), state);
        assertEquals(200, patch(driver.token(), "{\"isOnline\":false}", "").statusCode());
        assertEquals(false, snapshot(driver.driverId()).get("is_online"));
    }

    @Test
    void activeOrderOnAnotherDriverDoesNotBlockIdleDriver() throws Exception {
        var busy = createDriver("APPROVED", true, null, null, null);
        createOrder(busy.driverId(), "PLACED");
        var idle = createDriver("APPROVED", true, null, null, null);
        assertEquals(200, patch(idle.token(), "{\"isOnline\":false}", "").statusCode());
        assertEquals(true, snapshot(busy.driverId()).get("is_online"));
    }

    @Test
    void assignedAlreadyOfflineDriverKeepsIdempotentFalse() throws Exception {
        var driver = createDriver("APPROVED", false, null, null, null);
        long orderId = createOrder(driver.driverId(), "PLACED");
        var before = snapshot(driver.driverId());
        for (int i = 0; i < 2; i++) assertEquals(200, patch(driver.token(), "{\"isOnline\":false}", "").statusCode());
        assertEquals(before, snapshot(driver.driverId()));
        assertEquals(driver.driverId(), jdbc.queryForObject("SELECT driver_id FROM orders WHERE id=:id", Map.of("id", orderId), Long.class));
    }

    @Test
    void c01AcceptHoldsDriverFirstThenOffSeesCommittedAssignment() throws Exception {
        var fixture = offerFixture();
        var acquired = new CountDownLatch(1); var release = new CountDownLatch(1); var offEntered = new CountDownLatch(1);
        holdAcceptDriver(fixture.driver().driverId(), acquired, release, false);
        doAnswer(invocation -> { offEntered.countDown(); return invocation.callRealMethod(); })
                .when(drivers).findStatusByUserIdForUpdate(fixture.driver().userId());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                var accept = executor.submit(() -> accept(fixture));
                assertTrue(acquired.await(5, TimeUnit.SECONDS));
                var off = executor.submit(() -> patch(fixture.driver().token(), "{\"isOnline\":false}", ""));
                assertTrue(offEntered.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> off.get(150, TimeUnit.MILLISECONDS));
                release.countDown();
                assertEquals(200, accept.get(10, TimeUnit.SECONDS).getStatusCode().value());
                assertEquals(409, off.get(10, TimeUnit.SECONDS).statusCode());
                assertAssigned(fixture);
                assertEquals(true, snapshot(fixture.driver().driverId()).get("is_online"));
                System.out.println("MYSQL_CONCURRENCY C01 Accept=200 Offline=409 committed assignment preserved");
            } finally { release.countDown(); }
        }
    }

    @Test
    void c02OffHoldsDriverFirstThenAcceptReadsOfflineAndDoesNotAssign() throws Exception {
        var fixture = offerFixture();
        var acquired = new CountDownLatch(1); var release = new CountDownLatch(1); var acceptEntered = new CountDownLatch(1);
        doAnswer(invocation -> {
            var row = invocation.callRealMethod();
            assertEquals("READ-COMMITTED", jdbc.queryForObject("SELECT @@transaction_isolation", Map.of(), String.class));
            acquired.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return row;
        }).when(drivers).findStatusByUserIdForUpdate(fixture.driver().userId());
        holdAcceptDriver(fixture.driver().driverId(), acceptEntered, new CountDownLatch(0), true);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                var off = executor.submit(() -> patch(fixture.driver().token(), "{\"isOnline\":false}", ""));
                assertTrue(acquired.await(5, TimeUnit.SECONDS));
                var accept = executor.submit(() -> accept(fixture));
                assertTrue(acceptEntered.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> accept.get(150, TimeUnit.MILLISECONDS));
                release.countDown();
                assertEquals(200, off.get(10, TimeUnit.SECONDS).statusCode());
                assertEquals(409, accept.get(10, TimeUnit.SECONDS).getStatusCode().value());
                assertEquals(false, snapshot(fixture.driver().driverId()).get("is_online"));
                assertEquals(null, jdbc.queryForMap("SELECT driver_id FROM orders WHERE id=:id", Map.of("id", fixture.orderId())).get("driver_id"));
                assertEquals("OFFERED", offerStatus(fixture));
                System.out.println("MYSQL_CONCURRENCY C02 Offline=200 Accept=409 no assignment");
            } finally { release.countDown(); }
        }
    }

    @Test
    void oldRepeatableReadCallerCannotHideCommittedActiveOrder() throws Exception {
        var driver = createDriver("APPROVED", true, null, null, null);
        var caller = new TransactionTemplate(transactions);
        caller.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        try (var executor = Executors.newSingleThreadExecutor()) {
            caller.executeWithoutResult(tx -> {
                assertEquals(0, activeCount(driver.driverId())); // Establish the old caller snapshot.
                try { executor.submit(() -> createOrder(driver.driverId(), "PLACED")).get(5, TimeUnit.SECONDS); }
                catch (Exception e) { throw new IllegalStateException(e); }
                assertEquals(0, activeCount(driver.driverId()));
                assertThrows(ConflictException.class, () -> service.updateStatus(driver.userId(), new DriverStatusRequest(false)));
            });
        }
        assertEquals(true, snapshot(driver.driverId()).get("is_online"));
        System.out.println("MYSQL_SNAPSHOT outer REPEATABLE-READ ignored; new status transaction sees committed active order");
    }

    @ParameterizedTest
    @ValueSource(strings = {"read", "afterWrite"})
    void sqlFailureReturns500AndRollsBackStatus(String stage) throws Exception {
        var driver = createDriver("APPROVED", true, null, null, null);
        var before = snapshot(driver.driverId());
        if ("read".equals(stage)) {
            doAnswer(invocation -> jdbc.queryForObject("SELECT guard_missing_column FROM drivers", Map.of(), Long.class))
                    .when(drivers).hasActiveOrder(driver.driverId());
        } else {
            doAnswer(invocation -> {
                invocation.callRealMethod();
                return jdbc.queryForObject("SELECT guard_missing_column FROM drivers", Map.of(), Long.class);
            }).when(drivers).updateOnline(driver.driverId(), false);
        }
        var response = patch(driver.token(), "{\"isOnline\":false}", "");
        assertEquals(500, response.statusCode());
        assertEquals(500, mapper.readTree(response.body()).path("status").asInt());
        assertEquals(before, snapshot(driver.driverId()));
    }

    @Test
    void lockTimeoutIsNotSuccessAndExplicitRetryAfterReleaseWorks() throws Exception {
        var driver = createDriver("APPROVED", true, null, null, null);
        try (var connection = dataSource.getConnection(); var executor = Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.prepareStatement("SELECT id FROM drivers WHERE id=? FOR UPDATE")) {
                    statement.setLong(1, driver.driverId()); statement.executeQuery().close();
                }
                var off = executor.submit(() -> patch(driver.token(), "{\"isOnline\":false}", ""));
                assertEquals(500, off.get(10, TimeUnit.SECONDS).statusCode());
                assertEquals(true, snapshot(driver.driverId()).get("is_online"));
            } finally { connection.rollback(); }
        }
        assertEquals(200, patch(driver.token(), "{\"isOnline\":false}", "").statusCode());
    }

    @Test
    void boundedAcceptOffStressNeverProducesAcceptedAndOffline() throws Exception {
        for (int iteration = 0; iteration < 8; iteration++) {
            var fixture = offerFixture(); var start = new CountDownLatch(1);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var accept = executor.submit(() -> { start.await(); return accept(fixture).getStatusCode().value(); });
                var off = executor.submit(() -> { start.await(); return patch(fixture.driver().token(), "{\"isOnline\":false}", "").statusCode(); });
                start.countDown();
                int accepted = accept.get(10, TimeUnit.SECONDS); int offline = off.get(10, TimeUnit.SECONDS);
                if (accepted == 200) { assertEquals(409, offline); assertAssigned(fixture); assertEquals(true, snapshot(fixture.driver().driverId()).get("is_online")); }
                else { assertEquals(409, accepted); assertEquals(200, offline); assertEquals("OFFERED", offerStatus(fixture));
                    assertEquals(null, jdbc.queryForMap("SELECT driver_id FROM orders WHERE id=:id", Map.of("id", fixture.orderId())).get("driver_id")); }
            }
        }
        System.out.println("MYSQL_STRESS 8 concurrent Accept/Offline pairs; no deadlock or inconsistent result");
    }

    private void holdAcceptDriver(long driverId, CountDownLatch acquired, CountDownLatch release, boolean beforeLock) {
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            Map<?, ?> params = invocation.getArgument(1);
            boolean target = sql.contains("FROM drivers WHERE id = :driverId AND user_id = :userId FOR UPDATE")
                    && Long.valueOf(driverId).equals(params.get("driverId"));
            if (target && beforeLock) acquired.countDown();
            Object result = invocation.callRealMethod();
            if (target && !beforeLock) { acquired.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); }
            return result;
        }).when(jdbc).query(anyString(), org.mockito.ArgumentMatchers.<String, Object>anyMap(),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
    }

    private int activeCount(long driverId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE driver_id=:id AND status NOT IN ('DELIVERED','CANCELLED','REJECTED')",
                Map.of("id", driverId), Integer.class);
    }

    private long createOrder(Long driverId, String status) {
        long customer = users.insert("Guard Fixture", phone(), null, "test-only-hash", "CUSTOMER");
        String code = "GUARD-" + sequence.incrementAndGet();
        jdbc.update("""
                INSERT INTO orders(code,customer_id,restaurant_id,driver_id,status,receiver_name,receiver_phone,
                                   delivery_address,delivery_lat,delivery_lng,subtotal,total_amount)
                VALUES(:code,:customer,1,:driver,:status,'Fixture Receiver','0999999999','Fixture Delivery',10,106,50000,50000)
                """, new MapSqlParameterSource().addValue("code", code).addValue("customer", customer)
                .addValue("driver", driverId).addValue("status", status));
        return jdbc.queryForObject("SELECT id FROM orders WHERE code=:code", Map.of("code", code), Long.class);
    }

    private OfferFixture offerFixture() {
        var driver = createDriver("APPROVED", true, BigDecimal.TEN, new BigDecimal("106"), now());
        long orderId = createOrder(null, "PLACED");
        var at = now();
        jdbc.update("INSERT INTO order_assignments(order_id,driver_id,status,offered_at,expires_at,distance_km) VALUES(:order,:driver,'OFFERED',:at,:expires,1.2)",
                Map.of("order", orderId, "driver", driver.driverId(), "at", Timestamp.valueOf(at), "expires", Timestamp.valueOf(at.plusSeconds(30))));
        long offerId = jdbc.queryForObject("SELECT id FROM order_assignments WHERE order_id=:id", Map.of("id", orderId), Long.class);
        return new OfferFixture(orderId, offerId, driver);
    }

    private org.springframework.http.ResponseEntity<JsonNode> accept(OfferFixture f) {
        return rest.exchange("/api/driver/offers/" + f.offerId() + "/accept", HttpMethod.POST,
                new HttpEntity<>(headers(f.driver().token())), JsonNode.class);
    }

    private String offerStatus(OfferFixture f) {
        return jdbc.queryForObject("SELECT status FROM order_assignments WHERE id=:id", Map.of("id", f.offerId()), String.class);
    }

    private void assertAssigned(OfferFixture f) {
        assertEquals(f.driver().driverId(), jdbc.queryForObject("SELECT driver_id FROM orders WHERE id=:id", Map.of("id", f.orderId()), Long.class));
        assertEquals("PLACED", jdbc.queryForObject("SELECT status FROM orders WHERE id=:id", Map.of("id", f.orderId()), String.class));
        assertEquals("ACCEPTED", offerStatus(f));
    }

    private record OfferFixture(long orderId, long offerId, DriverFixture driver) { }

    private HttpResponse<String> patch(String token, String body, String query) throws Exception {
        // HttpClient cua JDK 21 ho tro PATCH, khong can them dependency HTTP cho test.
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/driver/status" + query))
                    .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json").method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                    .build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private HttpHeaders headers(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private Map<String, Object> snapshot(long driverId) {
        return jdbc.queryForMap("SELECT * FROM drivers WHERE id = :id", Map.of("id", driverId));
    }

    private Map<String, Object> withoutOnline(Map<String, Object> snapshot) {
        var fields = new HashMap<>(snapshot);
        fields.remove("is_online");
        return fields;
    }

    private LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private String phone() {
        return String.format("096%07d", sequence.incrementAndGet());
    }

    private DriverFixture createDriver(String status, boolean online, BigDecimal lat, BigDecimal lng, LocalDateTime at) {
        long userId = users.insert("Status Test", phone(), null, "test-only-hash", "DRIVER");
        long driverId = 2000 + userId;
        var params = new MapSqlParameterSource().addValue("id", driverId).addValue("userId", userId)
                .addValue("status", status).addValue("online", online).addValue("lat", lat).addValue("lng", lng)
                .addValue("locationAt", at == null ? null : Timestamp.valueOf(at));
        jdbc.update("""
                INSERT INTO drivers (id, user_id, vehicle_type, status, is_online, current_lat, current_lng, last_location_at)
                VALUES (:id, :userId, 'BICYCLE', :status, :online, :lat, :lng, :locationAt)
                """, params);
        return new DriverFixture(driverId, userId, jwt.generateToken(userId, "DRIVER"));
    }

    private record DriverFixture(long driverId, long userId, String token) {
    }
}
