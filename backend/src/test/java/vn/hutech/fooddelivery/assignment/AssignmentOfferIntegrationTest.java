package vn.hutech.fooddelivery.assignment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;

/**
 * Chi ghi/xoa fixture trong MySQLContainer rieng. Khong co transaction test bao ngoai:
 * fixture can commit de nhieu connection va transaction REQUIRES_NEW cung nhin thay.
 */
@JdbcTest(properties = {"spring.config.import=", "debug=false",
        "logging.level.org.springframework.jdbc=INFO",
        "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout=5"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AssignmentRepository.class, AssignmentOfferRepository.class, AssignmentOfferIntegrationTest.Services.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class AssignmentOfferIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0);

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired AssignmentService service;
    @Autowired OfferCreationService creation;
    @Autowired AssignmentRepository candidates;
    @MockitoSpyBean AssignmentOfferRepository offers;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired TestClock clock;

    @TestConfiguration(proxyBeanMethods = false)
    static class Services {
        @Bean TestClock testClock() { return new TestClock(); }

        @Bean
        OfferCreationService offerCreationService(AssignmentOfferRepository offers, TestClock clock) {
            return new OfferCreationService(offers, clock);
        }

        @Bean
        AssignmentService assignmentService(AssignmentRepository candidates, AssignmentOfferRepository offers,
                                             OfferCreationService creation, TestClock clock) {
            return new AssignmentService(candidates, offers, creation, clock);
        }
    }

    static class TestClock extends Clock {
        private final ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        private volatile Instant current = NOW.atZone(zone).toInstant();

        void set(LocalDateTime time) { current = time.atZone(zone).toInstant(); }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId other) { return Clock.fixed(current, other); }
        @Override public Instant instant() { return current; }
    }

    @BeforeEach
    void resetOnlyDisposableContainerFixtures() {
        clock.set(NOW);
        jdbc.update("DELETE FROM order_assignments", Map.of());
        jdbc.update("DELETE FROM orders", Map.of());
        jdbc.update("DELETE FROM drivers", Map.of());
        jdbc.update("DELETE FROM restaurants WHERE id = 900001", Map.of());
        jdbc.update("DELETE FROM merchants WHERE id = 900001", Map.of());
        jdbc.update("DELETE FROM users WHERE id >= 900000", Map.of());
        user(900001, "CUSTOMER");
        user(900002, "MERCHANT");
        jdbc.update("""
                INSERT INTO merchants (id, owner_user_id, business_name)
                VALUES (900001, 900002, 'Offer Test Merchant')
                """, Map.of());
        jdbc.update("""
                INSERT INTO restaurants (id, merchant_id, name, address, lat, lng)
                VALUES (900001, 900001, 'Offer Test Restaurant', 'Test address', 10, 106)
                """, Map.of());
        order(301, null, "PLACED");
    }

    @Test
    void createsOneThirtySecondOfferAndLeavesOrderAndDriverUnchanged() {
        driver(7, 10, NOW);
        var orderBefore = orderRow(301);
        var driverBefore = jdbc.queryForMap("SELECT * FROM drivers WHERE id = 7", Map.of());
        clock.set(NOW.plusNanos(987_654_321));

        var result = service.createOffer(301).orElseThrow();

        assertEquals(7, result.driverId());
        assertEquals(NOW, result.offeredAt());
        assertEquals(NOW.plusSeconds(30), result.expiresAt());
        assertEquals(new BigDecimal("0.00"), result.distanceKm());
        var saved = jdbc.queryForMap("SELECT * FROM order_assignments WHERE id = :id", Map.of("id", result.id()));
        assertEquals("OFFERED", saved.get("status"));
        assertEquals(result.offeredAt(), asTime(saved.get("offered_at")));
        assertEquals(result.expiresAt(), asTime(saved.get("expires_at")));
        assertEquals(orderBefore, orderRow(301));
        assertEquals(driverBefore, jdbc.queryForMap("SELECT * FROM drivers WHERE id = 7", Map.of()));
        assertEquals(1, offeredCount());
    }

    @Test
    void nearestDriverWinsAndHaversineDistanceIsStoredAtSchemaScale() {
        driver(7, 10.1, NOW);
        driver(8, 10.01, NOW);

        var result = service.createOffer(301).orElseThrow();

        assertEquals(8, result.driverId());
        assertEquals(new BigDecimal("1.11"), result.distanceKm());
    }

    @ParameterizedTest
    @ValueSource(strings = {"sameOrder", "sameDriver"})
    void concurrentCallsUseDifferentMySqlConnectionsAndCreateOnlyOneOffer(String race) throws Exception {
        runRace(race);
    }

    @RepeatedTest(3)
    void sameOrderRaceRemainsSafeAcrossRepeatedRuns() throws Exception {
        runRace("sameOrder");
    }

    @RepeatedTest(3)
    void twoOrdersRacingForSameDriverRemainSafeAcrossRepeatedRuns() throws Exception {
        runRace("sameDriver");
    }

    private void runRace(String race) throws Exception {
        driver(7, 10, NOW);
        order(302, null, "PLACED");
        var reached = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        Set<Long> connectionIds = ConcurrentHashMap.newKeySet();
        Set<String> isolations = ConcurrentHashMap.newKeySet();
        Answer<Object> barrier = invocation -> {
            connectionIds.add(jdbc.queryForObject("SELECT CONNECTION_ID()", Map.of(), Long.class));
            isolations.add(jdbc.queryForObject("SELECT @@transaction_isolation", Map.of(), String.class));
            reached.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS), "Race barrier timed out");
            return invocation.callRealMethod();
        };
        if ("sameOrder".equals(race)) {
            doAnswer(barrier).when(offers).lockOrder(301);
        } else {
            // Ca hai transaction da khoa Order khac nhau, nhung chua ai duoc khoa Driver.
            doAnswer(barrier).when(offers).lockDriver(7);
        }
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.createOffer(301));
            var second = executor.submit(() -> service.createOffer("sameOrder".equals(race) ? 301 : 302));
            try {
                assertTrue(reached.await(5, TimeUnit.SECONDS), "Both transactions must reach the lock");
                release.countDown();
                var results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
                assertEquals(1, results.stream().filter(Optional::isPresent).count());
                assertEquals(1, offeredCount());
                assertEquals(2, connectionIds.size());
                assertEquals(Set.of("READ-COMMITTED"), isolations);
                assertEquals(null, orderRow(301).get("driver_id"));
                assertEquals(null, orderRow(302).get("driver_id"));
                System.out.printf("MYSQL_CONCURRENCY %s connections=%s isolation=%s offered=1%n",
                        race, connectionIds, isolations);
            } finally {
                release.countDown();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void committedOfflineWhileWaitingForDriverLockIsRejectedAndCanFallBack(boolean withFallback) throws Exception {
        driver(7, 10, NOW);
        if (withFallback) driver(8, 10.1, NOW);
        var reached = new CountDownLatch(1);
        doAnswer(invocation -> { reached.countDown(); return invocation.callRealMethod(); }).when(offers).lockDriver(7);
        try (var connection = dataSource.getConnection(); var executor = Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            try {
                try (var update = connection.prepareStatement("UPDATE drivers SET is_online = FALSE WHERE id = ?")) {
                    update.setLong(1, 7);
                    assertEquals(1, update.executeUpdate());
                }
                var result = executor.submit(() -> service.createOffer(301));
                assertTrue(reached.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> result.get(200, TimeUnit.MILLISECONDS));
                connection.commit();

                var created = result.get(10, TimeUnit.SECONDS);
                assertEquals(withFallback, created.isPresent());
                if (withFallback) assertEquals(8, created.orElseThrow().driverId());
                assertEquals(withFallback ? 1 : 0, offeredCount());
                assertEquals(0, count("SELECT COUNT(*) FROM order_assignments WHERE driver_id = 7"));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void gpsCanBecomeStaleWhileWaitingForDriverLock() throws Exception {
        driver(7, 10, NOW);
        var reached = new CountDownLatch(1);
        doAnswer(invocation -> { reached.countDown(); return invocation.callRealMethod(); }).when(offers).lockDriver(7);
        try (var connection = dataSource.getConnection(); var executor = Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            try {
                try (var lock = connection.prepareStatement("SELECT id FROM drivers WHERE id = ? FOR UPDATE")) {
                    lock.setLong(1, 7);
                    try (var row = lock.executeQuery()) { assertTrue(row.next()); }
                }
                var result = executor.submit(() -> service.createOffer(301));
                assertTrue(reached.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> result.get(200, TimeUnit.MILLISECONDS));
                clock.set(NOW.plusSeconds(61));
                connection.commit();

                assertTrue(result.get(10, TimeUnit.SECONDS).isEmpty());
                assertEquals(0, offeredCount());
            } finally {
                connection.rollback();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {-61, 1})
    void oldOrFutureGpsDoesNotCreateOffer(long offsetSeconds) {
        driver(7, 10, NOW.plusSeconds(offsetSeconds));
        assertTrue(service.createOffer(301).isEmpty());
        assertEquals(0, offeredCount());
    }

    @Test
    void exactlySixtySecondGpsIsAccepted() {
        driver(7, 10, NOW.minusSeconds(60));
        assertTrue(service.createOffer(301).isPresent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"activeOrder", "pendingOffer", "rejectedHistory", "expiredHistory",
            "orderStatus", "assignedOrder", "restaurantLocation", "driverLocation"})
    void revalidatesChangesCommittedAfterSelectionBeforeOrderLock(String change) throws Exception {
        driver(7, 10, NOW);
        order(302, null, "PLACED");
        doAnswer(invocation -> {
            try (var connection = dataSource.getConnection()) {
                applyCommittedChange(connection, change);
            }
            return invocation.callRealMethod();
        }).when(offers).lockOrder(301);

        assertTrue(service.createOffer(301).isEmpty());
        assertEquals("pendingOffer".equals(change) ? 1 : 0, offeredCount());
        assertEquals(0, count("SELECT COUNT(*) FROM order_assignments WHERE order_id = 301 AND status = 'OFFERED'"));
    }

    private void applyCommittedChange(Connection connection, String change) throws Exception {
        String sql = switch (change) {
            case "activeOrder" -> "UPDATE orders SET driver_id = 7, status = 'DELIVERING' WHERE id = 302";
            case "pendingOffer" -> """
                    INSERT INTO order_assignments (order_id, driver_id, status) VALUES (302, 7, 'OFFERED')
                    """;
            case "rejectedHistory" -> """
                    INSERT INTO order_assignments (order_id, driver_id, status) VALUES (301, 7, 'REJECTED')
                    """;
            case "expiredHistory" -> """
                    INSERT INTO order_assignments (order_id, driver_id, status, expires_at)
                    VALUES (301, 7, 'EXPIRED', '2026-11-04 11:59:59')
                    """;
            case "orderStatus" -> "UPDATE orders SET status = 'CANCELLED' WHERE id = 301";
            case "assignedOrder" -> "UPDATE orders SET driver_id = 7 WHERE id = 301";
            case "restaurantLocation" -> "UPDATE restaurants SET lat = 11 WHERE id = 900001";
            case "driverLocation" -> "UPDATE drivers SET current_lat = 11 WHERE id = 7";
            default -> throw new IllegalArgumentException(change);
        };
        try (var statement = connection.prepareStatement(sql)) { assertEquals(1, statement.executeUpdate()); }
    }

    @Test
    void requiresNewDoesNotInheritAnOuterRepeatableReadSnapshot() {
        driver(7, 10, NOW);
        order(302, null, "PLACED");
        DriverCandidate candidate = candidates.findEligibleCandidates(NOW).getFirst();
        var restaurant = offers.findRestaurant(900001).orElseThrow();
        var parent = new TransactionTemplate(transactionManager);
        parent.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        parent.executeWithoutResult(status -> {
            assertEquals(0, offeredCount()); // Thiet lap consistent snapshot cua transaction cha.
            assertTrue(creation.tryCreateOffer(301, candidate, restaurant).isPresent());
            assertEquals(0, offeredCount()); // Snapshot cha van cu.
            assertTrue(creation.tryCreateOffer(302, candidate, restaurant).isEmpty());
        });
        assertEquals(1, offeredCount());
    }

    @Test
    void restaurantCoordinatesCannotChangeBetweenFinalCheckAndOfferCommit() throws Exception {
        driver(7, 10, NOW);
        var started = new CountDownLatch(1);
        var update = new AtomicReference<Future<Integer>>();
        try (var executor = Executors.newSingleThreadExecutor()) {
            doAnswer(invocation -> {
                update.set(executor.submit(() -> {
                    try (var connection = dataSource.getConnection();
                         var statement = connection.prepareStatement("UPDATE restaurants SET lat = 11 WHERE id = ?")) {
                        statement.setLong(1, 900001);
                        started.countDown();
                        return statement.executeUpdate();
                    }
                }));
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> update.get().get(200, TimeUnit.MILLISECONDS));
                return invocation.callRealMethod();
            }).when(offers).insertOffer(eq(301L), eq(7L), anyDouble(), eq(NOW));

            var created = service.createOffer(301).orElseThrow();

            assertEquals(new BigDecimal("0.00"), created.distanceKm());
            assertEquals(1, update.get().get(10, TimeUnit.SECONDS));
            assertEquals(new BigDecimal("11.0000000"), offers.findRestaurant(900001).orElseThrow().lat());
            assertEquals(1, offeredCount());
        }
    }

    @Test
    void assignedOrderDoesNotGetOffer() {
        driver(7, 10, NOW);
        jdbc.update("UPDATE orders SET driver_id = :id WHERE id = 301", Map.of("id", 7));
        assertTrue(service.createOffer(301).isEmpty());
        assertEquals(0, offeredCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_PAYMENT", "ACCEPTED", "PREPARING", "READY_FOR_PICKUP", "PICKED_UP",
            "DELIVERING", "DELIVERED", "CANCELLED", "REJECTED"})
    void nonPlacedOrderDoesNotGetOffer(String status) {
        driver(7, 10, NOW);
        jdbc.update("UPDATE orders SET status = :status WHERE id = 301", Map.of("status", status));
        assertTrue(service.createOffer(301).isEmpty());
        assertEquals(0, offeredCount());
    }

    @Test
    void rejectionOfSameOrderPermanentlyBlocksReinvitation() {
        driver(7, 10, NOW);
        history(301, 7, "REJECTED", null);
        history(301, 7, "EXPIRED", NOW.minusSeconds(120));
        assertTrue(service.createOffer(301).isEmpty());
        assertEquals(0, offeredCount());
    }

    @Test
    void rejectionOfAnotherOrderDoesNotBlockCurrentOrder() {
        driver(7, 10, NOW);
        order(302, null, "PLACED");
        history(302, 7, "REJECTED", null);
        assertTrue(service.createOffer(301).isPresent());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 59, 60, 61})
    void expiredCooldownIncludesExactlySixtySecondsAndUsesLatestExpiry(long age) {
        driver(7, 10, NOW);
        history(301, 7, "EXPIRED", NOW.minusSeconds(200));
        history(301, 7, "EXPIRED", NOW.minusSeconds(age));

        assertEquals(age >= 60, service.createOffer(301).isPresent());
        assertEquals(age >= 60 ? 1 : 0, offeredCount());
    }

    @Test
    void expiredWithoutTimestampFailsClosed() {
        driver(7, 10, NOW);
        history(301, 7, "EXPIRED", null);
        assertTrue(service.createOffer(301).isEmpty());
    }

    @Test
    void newDriverHasPriorityOverNearerExpiredDriver() {
        driver(7, 10, NOW);
        driver(8, 10.1, NOW);
        history(301, 7, "EXPIRED", NOW.minusSeconds(60));
        assertEquals(8, service.createOffer(301).orElseThrow().driverId());
    }

    @Test
    void expiredGroupStillUsesNearestAndDriverIdTieBreak() {
        driver(8, 10, NOW);
        driver(7, 10, NOW);
        history(301, 8, "EXPIRED", NOW.minusSeconds(120));
        history(301, 7, "EXPIRED", NOW.minusSeconds(60));
        assertEquals(7, service.createOffer(301).orElseThrow().driverId());
    }

    @Test
    void offeredStillBlocksAfterTimestampPassesUntilExpiryFlowProcessesIt() {
        driver(7, 10, NOW);
        history(301, 7, "OFFERED", NOW.minusSeconds(1));
        assertTrue(service.createOffer(301).isEmpty());
        assertEquals(1, offeredCount());
    }

    @Test
    void noDriverDoesNotCancelOrderAndMissingOrderDoesNotCreateData() {
        var before = orderRow(301);
        assertTrue(service.createOffer(301).isEmpty());
        assertTrue(service.createOffer(999999).isEmpty());
        assertEquals(before, orderRow(301));
        assertEquals(0, offeredCount());
    }

    @Test
    void failureAfterActualInsertRollsBackAndReleasesLocksForRetry() {
        driver(7, 10, NOW);
        var orderBefore = orderRow(301);
        doAnswer(invocation -> {
            invocation.callRealMethod(); // SQL INSERT that da chay trong transaction.
            throw new DataIntegrityViolationException("Injected failure after INSERT");
        }).when(offers).insertOffer(eq(301L), eq(7L), anyDouble(), eq(NOW));

        assertThrows(DataIntegrityViolationException.class, () -> service.createOffer(301));
        assertEquals(0, count("SELECT COUNT(*) FROM order_assignments"));
        assertEquals(orderBefore, orderRow(301));

        doCallRealMethod().when(offers).insertOffer(eq(301L), eq(7L), anyDouble(), eq(NOW));
        assertTrue(service.createOffer(301).isPresent());
        assertEquals(1, offeredCount());
    }

    private void user(long id, String role) {
        jdbc.update("""
                INSERT INTO users (id, full_name, phone, password_hash, role)
                VALUES (:id, 'Offer Test User', :phone, 'test-only-hash', :role)
                """, Map.of("id", id, "phone", "09" + id, "role", role));
    }

    private void driver(long id, double lat, LocalDateTime gpsAt) {
        user(910000 + id, "DRIVER");
        jdbc.update("""
                INSERT INTO drivers (id, user_id, status, is_online, current_lat, current_lng, last_location_at)
                VALUES (:id, :userId, 'APPROVED', TRUE, :lat, 106, :at)
                """, Map.of("id", id, "userId", 910000 + id, "lat", lat, "at", Timestamp.valueOf(gpsAt)));
    }

    private void order(long id, Long driverId, String status) {
        jdbc.update("""
                INSERT INTO orders (id, code, customer_id, restaurant_id, driver_id, status,
                                    receiver_name, receiver_phone, delivery_address, delivery_lat, delivery_lng,
                                    subtotal, total_amount)
                VALUES (:id, :code, 900001, 900001, :driverId, :status,
                        'Test Receiver', '0999999999', 'Test address', 10, 106, 50000, 50000)
                """, new MapSqlParameterSource().addValue("id", id).addValue("code", "OFFER-TEST-" + id)
                .addValue("driverId", driverId).addValue("status", status));
    }

    private void history(long orderId, long driverId, String status, LocalDateTime expiresAt) {
        jdbc.update("""
                INSERT INTO order_assignments (order_id, driver_id, status, offered_at, expires_at)
                VALUES (:orderId, :driverId, :status, :offeredAt, :expiresAt)
                """, new MapSqlParameterSource().addValue("orderId", orderId).addValue("driverId", driverId)
                .addValue("status", status).addValue("offeredAt", Timestamp.valueOf(NOW.minusSeconds(230)))
                .addValue("expiresAt", expiresAt == null ? null : Timestamp.valueOf(expiresAt)));
    }

    private Map<String, Object> orderRow(long id) {
        return jdbc.queryForMap("SELECT * FROM orders WHERE id = :id", Map.of("id", id));
    }

    private int offeredCount() {
        return count("SELECT COUNT(*) FROM order_assignments WHERE status = 'OFFERED'");
    }

    private int count(String sql) { return jdbc.queryForObject(sql, Map.of(), Integer.class); }

    private static LocalDateTime asTime(Object value) {
        return value instanceof Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) value;
    }
}
