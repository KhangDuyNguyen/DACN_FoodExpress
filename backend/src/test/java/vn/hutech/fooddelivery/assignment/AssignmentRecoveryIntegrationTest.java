package vn.hutech.fooddelivery.assignment;

import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.order.OrderRepository;
import vn.hutech.fooddelivery.common.ConflictException;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

/** Moi fixture/DELETE chi nam trong container test rieng, khong ket noi database dang dung. */
@JdbcTest(properties = {"spring.config.import=", "debug=false", "logging.level.org.springframework.jdbc=INFO",
        "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout=5"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AssignmentRepository.class, AssignmentOfferRepository.class, DriverOfferRepository.class,
        AssignmentRecoveryRepository.class, OrderRepository.class, AssignmentRecoveryIntegrationTest.Services.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class AssignmentRecoveryIntegrationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);
    @Container @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired TestClock clock;
    @Autowired AssignmentOfferLifecycleService lifecycle;
    @Autowired AssignmentRecoveryService recovery;
    @Autowired OfferCreationService creation;
    @Autowired DriverOfferDecisionService decisions;
    @Autowired AssignmentRepository candidates;
    @MockitoSpyBean AssignmentOfferRepository parents;
    @MockitoSpyBean DriverOfferRepository offers;
    @MockitoSpyBean AssignmentRecoveryRepository repository;
    @MockitoSpyBean AssignmentService assignments;

    @TestConfiguration(proxyBeanMethods = false)
    static class Services {
        @Bean TestClock recoveryTestClock() { return new TestClock(); }
        @Bean OfferCreationService offerCreationService(AssignmentOfferRepository parents, TestClock clock) {
            return new OfferCreationService(parents, clock);
        }
        @Bean AssignmentService assignmentService(AssignmentRepository candidates, AssignmentOfferRepository parents,
                                                   OfferCreationService creation, TestClock clock) {
            return new AssignmentService(candidates, parents, creation, clock);
        }
        @Bean DriverOfferDecisionService driverOfferDecisionService(DriverOfferRepository offers,
                AssignmentOfferRepository parents, OrderRepository orders, TestClock clock) {
            return new DriverOfferDecisionService(offers, parents, orders, clock);
        }
        @Bean AssignmentOfferLifecycleService assignmentOfferLifecycleService(AssignmentOfferRepository parents,
                DriverOfferRepository offers, AssignmentRecoveryRepository repository, TestClock clock) {
            return new AssignmentOfferLifecycleService(parents, offers, repository, clock);
        }
        @Bean AssignmentRecoveryService assignmentRecoveryService(AssignmentRecoveryRepository repository,
                AssignmentOfferLifecycleService lifecycle, AssignmentService assignments, TestClock clock) {
            return new AssignmentRecoveryService(repository, lifecycle, assignments, clock, 50);
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
    void resetContainerFixtures() {
        clock.set(NOW);
        jdbc.update("DELETE FROM order_assignments", Map.of());
        jdbc.update("DELETE FROM orders", Map.of());
        jdbc.update("DELETE FROM drivers", Map.of());
        jdbc.update("DELETE FROM restaurants WHERE id = 900001", Map.of());
        jdbc.update("DELETE FROM merchants WHERE id = 900001", Map.of());
        jdbc.update("DELETE FROM users WHERE id >= 900000", Map.of());
        user(900001, "CUSTOMER"); user(900002, "MERCHANT");
        jdbc.update("INSERT INTO merchants (id, owner_user_id, business_name) VALUES (900001, 900002, 'Recovery Test')", Map.of());
        jdbc.update("""
                INSERT INTO restaurants (id, merchant_id, name, address, lat, lng)
                VALUES (900001, 900001, 'Recovery Test Restaurant', 'Test address', 10, 106)
                """, Map.of());
        order(301, null, "PLACED");
    }

    private AssignmentRecoveryService worker(int batch) {
        // Worker doc lap mo phong process restart/multi-instance; lifecycle va Assignment van la Spring proxy that.
        return new AssignmentRecoveryService(repository, lifecycle, assignments, clock, batch);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    void exactThirtySecondBoundaryKeepsOrExpiresWithoutChangingOrder(long nanos) {
        driver(7);
        offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        var before = orderRow(301);
        clock.set(NOW.plusSeconds(30).plusNanos(nanos));

        var result = lifecycle.reconcileOffer(401);

        assertEquals(nanos >= 0, result.isPresent());
        assertEquals(nanos >= 0 ? "EXPIRED" : "OFFERED", offerStatus(401));
        assertEquals(NOW.plusSeconds(30), time(offerRow(401).get("expires_at")));
        if (nanos >= 0) assertEquals(NOW.plusSeconds(30), time(offerRow(401).get("responded_at")));
        assertEquals(before, orderRow(301));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACCEPTED", "REJECTED", "CANCELLED", "EXPIRED"})
    void resolvedOffersStayUnchangedEvenWhenOrderIsCancelled(String status) {
        driver(7); offer(401, 301, 7, status, NOW.minusSeconds(1));
        jdbc.update("UPDATE orders SET status = 'CANCELLED' WHERE id = 301", Map.of());
        var before = offerRow(401);
        assertTrue(lifecycle.reconcileOffer(401).isEmpty());
        assertEquals(before, offerRow(401));
    }

    @Test
    void twoExpirySweepsAreIdempotentAndNeverCancelUnassignedOrder() {
        driver(7); offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        clock.set(NOW.plusSeconds(30));
        var first = recovery.expireAndReassign();
        var expired = offerRow(401);

        assertEquals(1, first.expired());
        assertEquals(0, recovery.expireAndReassign().expired());
        assertEquals(expired, offerRow(401));
        assertEquals("PLACED", orderRow(301).get("status"));
        assertEquals(null, orderRow(301).get("driver_id"));
        assertEquals(0, offeredCount());
    }

    @Test
    void nullExpiryGetsFiniteCooldownAndDriverCanBeRetriedAfterSixtySeconds() {
        driver(7); offer(401, 301, 7, "OFFERED", null);
        assertEquals(1, recovery.expireAndReassign().expired());
        assertEquals(NOW, time(offerRow(401).get("expires_at")));
        assertEquals(NOW, time(offerRow(401).get("responded_at")));
        assertEquals(0, offeredCount());
        clock.set(NOW.plusSeconds(59)); refreshGps(7, NOW.plusSeconds(59));
        assertEquals(0, recovery.recoverWaitingOrders().created());
        clock.set(NOW.plusSeconds(60)); refreshGps(7, NOW.plusSeconds(60));
        assertEquals(1, recovery.recoverWaitingOrders().created());
        assertEquals(1, count("SELECT COUNT(*) FROM order_assignments WHERE driver_id = 7 AND status = 'OFFERED'"));
        assertEquals("EXPIRED", offerStatus(401));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "REJECTED", "ACCEPTED", "DELIVERING", "DELIVERED", "assigned"})
    void offeredForInvalidOrderIsCancelledWithoutTouchingOrderOrCreatingNewOffer(String state) {
        driver(7); offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        if ("assigned".equals(state)) jdbc.update("UPDATE orders SET driver_id = 7 WHERE id = 301", Map.of());
        else jdbc.update("UPDATE orders SET status = :status WHERE id = 301", Map.of("status", state));
        var before = orderRow(301);

        var report = recovery.expireAndReassign();

        assertEquals(1, report.cancelled());
        assertEquals(0, report.created());
        assertEquals(0, report.attempted());
        assertEquals("CANCELLED", offerStatus(401));
        assertEquals(before, orderRow(301));
        assertEquals(0, recovery.recoverWaitingOrders().created());
    }

    @Test
    void aExpiresThenEligibleBReceivesFreshThirtySecondOfferAfterCommit() {
        driver(7); driver(8); offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        clock.set(NOW.plusSeconds(30));
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            try (var connection = dataSource.getConnection();
                 var statement = connection.prepareStatement("SELECT status FROM order_assignments WHERE id = ?")) {
                statement.setLong(1, 401);
                try (var row = statement.executeQuery()) { assertTrue(row.next()); assertEquals("EXPIRED", row.getString(1)); }
            }
            return invocation.callRealMethod();
        }).when(assignments).createOffer(301);

        var report = recovery.expireAndReassign();

        assertEquals(1, report.expired()); assertEquals(1, report.created());
        assertEquals("EXPIRED", offerStatus(401));
        var next = jdbc.queryForMap("SELECT * FROM order_assignments WHERE status = 'OFFERED'", Map.of());
        assertEquals(8L, ((Number) next.get("driver_id")).longValue());
        assertEquals(NOW.plusSeconds(30), time(next.get("offered_at")));
        assertEquals(NOW.plusSeconds(60), time(next.get("expires_at")));
        assertEquals(null, orderRow(301).get("driver_id"));
    }

    @Test
    void rejectedDriverIsNotInvitedAgainAndNeverInvitedDriverHasPriority() {
        driver(7); driver(8); offer(401, 301, 7, "REJECTED", NOW);
        assertEquals(1, recovery.recoverWaitingOrders().created());
        assertEquals(1, count("SELECT COUNT(*) FROM order_assignments WHERE driver_id = 8 AND status = 'OFFERED'"));
        assertEquals("REJECTED", offerStatus(401));
    }

    @Test
    void neverInvitedBBeatsNearerAWhoseExpiredCooldownAlreadyPassed() {
        driver(7); driver(8); offer(401, 301, 7, "EXPIRED", NOW.minusSeconds(120));
        assertEquals(1, recovery.recoverWaitingOrders().created());
        assertEquals(1, count("SELECT COUNT(*) FROM order_assignments WHERE driver_id = 8 AND status = 'OFFERED'"));
    }

    @Test
    void noCandidatesStayPlacedAndNewOnlineDriverIsPickedOnNextSweep() {
        var before = orderRow(301);
        assertEquals(1, recovery.recoverWaitingOrders().noCandidates());
        assertEquals(before, orderRow(301));
        assertEquals(0, offeredCount());
        driver(7);
        assertEquals(1, recovery.recoverWaitingOrders().created());
        assertEquals(0, recovery.recoverWaitingOrders().created());
        assertEquals(1, offeredCount());
    }

    @Test
    void busyDriverCannotReceiveAnotherOrderDuringRecovery() {
        driver(7); order(302, 7L, "DELIVERING");
        assertEquals(1, recovery.recoverWaitingOrders().noCandidates());
        assertEquals(0, offeredCount());
        assertEquals(7L, ((Number) orderRow(302).get("driver_id")).longValue());
    }

    @Test
    void validPendingOfferIsNotDuplicatedOrMutatedByEitherSweep() {
        driver(7); driver(8); offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        var before = offerRow(401);
        assertEquals(0, recovery.expireAndReassign().created());
        assertEquals(0, recovery.recoverWaitingOrders().created());
        assertEquals(before, offerRow(401));
        assertEquals(1, offeredCount());
    }

    @Test
    void recoveryAfterRestartFindsExpiredCommittedStateWithoutInMemoryFlags() {
        driver(7); driver(8); offer(401, 301, 7, "OFFERED", NOW.minusSeconds(1));
        assertTrue(lifecycle.reconcileOffer(401).isPresent()); // Mo phong commit truoc khi process dung.
        var restarted = worker(50);
        assertEquals(1, restarted.recoverWaitingOrders().created());
        assertEquals(0, worker(50).recoverWaitingOrders().created());
        assertEquals(1, offeredCount());
    }

    @Test
    void recoveryCanCleanExpiredOfferEvenIfExpiryTaskHasNotRunAfterRestart() {
        driver(7); driver(8); offer(401, 301, 7, "OFFERED", NOW.minusSeconds(1));
        var report = worker(50).recoverWaitingOrders();
        assertEquals(1, report.expired()); assertEquals(1, report.created());
        assertEquals("EXPIRED", offerStatus(401));
    }

    @Test
    void rejectCommitFollowedByReassignmentFailureIsRecoveredOnLaterSweep() {
        driver(7); driver(8); offer(401, 301, 7, "REJECTED", NOW.plusSeconds(30));
        doThrow(new CannotAcquireLockException("Injected retry failure")).when(assignments).createOffer(301);
        assertEquals(1, recovery.recoverWaitingOrders().errors());
        assertEquals(0, offeredCount());
        doCallRealMethod().when(assignments).createOffer(301);
        assertEquals(1, worker(50).recoverWaitingOrders().created());
        assertEquals("REJECTED", offerStatus(401));
        assertEquals(1, offeredCount());
    }

    @Test
    void defaultBatchIsAtMostFiftyAndMovesPastUnassignableOrders() {
        for (long id = 302; id <= 351; id++) order(id, null, "PLACED");
        var process = worker(50);
        assertEquals(50, process.recoverWaitingOrders().scanned());
        assertEquals(1, process.recoverWaitingOrders().scanned());
        assertEquals(0, offeredCount());
    }

    @Test
    void keysetFairnessLetsLaterOrderGetDriverDespiteRejectedEarlierOrders() {
        driver(7); order(302, null, "PLACED"); order(303, null, "PLACED");
        offer(401, 301, 7, "REJECTED", NOW); offer(402, 302, 7, "REJECTED", NOW);
        var process = worker(2);
        assertEquals(0, process.recoverWaitingOrders().created());
        assertEquals(1, process.recoverWaitingOrders().created());
        assertEquals(1, count("SELECT COUNT(*) FROM order_assignments WHERE order_id = 303 AND status = 'OFFERED'"));
    }

    @Test
    void sqlFailureForOneOrderDoesNotStopFollowingOrder() {
        driver(7); order(302, null, "PLACED");
        doThrow(new CannotAcquireLockException("Injected order failure")).when(assignments).createOffer(301);
        var report = worker(50).recoverWaitingOrders();
        assertEquals(1, report.errors()); assertEquals(1, report.created());
        assertEquals(1, count("SELECT COUNT(*) FROM order_assignments WHERE order_id = 302 AND status = 'OFFERED'"));
        assertEquals("PLACED", orderRow(301).get("status"));
    }

    @Test
    void expirySqlFailureAfterUpdateRollsBackAndNextSweepCanRecover() {
        driver(7); driver(8); offer(401, 301, 7, "OFFERED", NOW.minusSeconds(1));
        var before = offerRow(401);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new DataIntegrityViolationException("Injected failure after real expiry UPDATE");
        }).when(repository).completeOffer(any(), eq("EXPIRED"), any());

        var failed = recovery.expireAndReassign();
        assertEquals(1, failed.errors()); assertEquals(0, failed.expired()); assertEquals(0, failed.created());
        assertEquals(before, offerRow(401));
        doCallRealMethod().when(repository).completeOffer(any(), eq("EXPIRED"), any());
        assertEquals(1, recovery.expireAndReassign().created());
        assertEquals("EXPIRED", offerStatus(401));
        assertEquals(1, offeredCount());
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void acceptAgainstExpiryHasExactlyOneWinningStateAtBackendBoundary(long nanos) throws Exception {
        driver(7); offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        clock.set(NOW.plusSeconds(30).plusNanos(nanos));
        var gate = new RaceGate();
        doAnswer(gate.answer()).when(offers).findMetadata(401);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var accept = executor.submit(() -> {
                try { decisions.accept(910007, 401); return true; }
                catch (ConflictException expected) { return false; }
            });
            var expire = executor.submit(() -> lifecycle.reconcileOffer(401));
            try {
                gate.releaseBoth();
                assertEquals(nanos < 0, accept.get(10, TimeUnit.SECONDS));
                assertEquals(nanos >= 0, expire.get(10, TimeUnit.SECONDS).isPresent());
                assertEquals(nanos < 0 ? "ACCEPTED" : "EXPIRED", offerStatus(401));
                gate.verify("accept-expire-nanos-" + nanos);
            } finally { gate.release.countDown(); }
        }
    }

    @Test
    void acceptedBeforeDeadlineIsNotOverwrittenBySchedulerWaitingAtDeadline() throws Exception {
        driver(7); offer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        clock.set(NOW.plusSeconds(30).minusNanos(1));
        var checked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var expiryWaiting = new CountDownLatch(1);
        var lockCalls = new AtomicInteger();
        doAnswer(invocation -> {
            if (lockCalls.incrementAndGet() == 2) expiryWaiting.countDown();
            return invocation.callRealMethod();
        }).when(parents).lockOrder(301);
        doAnswer(invocation -> {
            checked.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS));
            return invocation.callRealMethod();
        }).when(offers).respond(any(), eq("ACCEPTED"), any());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var accept = executor.submit(() -> decisions.accept(910007, 401));
            try {
                assertTrue(checked.await(5, TimeUnit.SECONDS));
                clock.set(NOW.plusSeconds(30));
                var expire = executor.submit(() -> worker(50).expireAndReassign());
                assertTrue(expiryWaiting.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> expire.get(200, TimeUnit.MILLISECONDS));
                release.countDown();
                assertEquals("ACCEPTED", accept.get(10, TimeUnit.SECONDS).status());
                assertEquals(0, expire.get(10, TimeUnit.SECONDS).expired());
                assertEquals("ACCEPTED", offerStatus(401));
                assertEquals(7L, ((Number) orderRow(301).get("driver_id")).longValue());
            } finally { release.countDown(); }
        }
    }

    @Test
    void expiryAndConcurrentOfferCreationStillLeaveOnlyOneOffered() throws Exception {
        driver(7); driver(8); offer(401, 301, 7, "OFFERED", NOW);
        var expected = candidates.findEligibleCandidates(NOW).stream().filter(d -> d.driverId() == 8).findFirst().orElseThrow();
        var restaurant = parents.findRestaurant(900001).orElseThrow();
        var gate = new RaceGate();
        doAnswer(gate.answer()).when(parents).lockOrder(301);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var expire = executor.submit(() -> worker(50).expireAndReassign());
            var create = executor.submit(() -> creation.tryCreateOffer(301, expected, restaurant));
            try {
                gate.releaseBoth(); expire.get(15, TimeUnit.SECONDS); create.get(15, TimeUnit.SECONDS);
                assertEquals("EXPIRED", offerStatus(401)); assertEquals(1, offeredCount());
                assertEquals(1, count("SELECT COUNT(*) FROM order_assignments WHERE driver_id = 8 AND status = 'OFFERED'"));
                gate.verify("expire-create");
            } finally { gate.release.countDown(); }
        }
    }

    @Test
    void twoIndependentExpiryWorkersCommitExpirationAndNextOfferOnlyOnce() throws Exception {
        driver(7); driver(8); offer(401, 301, 7, "OFFERED", NOW);
        var gate = new RaceGate();
        doAnswer(gate.answer()).when(offers).findMetadata(401);
        var firstWorker = worker(50); var secondWorker = worker(50);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(firstWorker::expireAndReassign);
            var second = executor.submit(secondWorker::expireAndReassign);
            try {
                gate.releaseBoth();
                var a = first.get(15, TimeUnit.SECONDS); var b = second.get(15, TimeUnit.SECONDS);
                assertEquals(1, a.expired() + b.expired()); assertEquals(1, a.created() + b.created());
                assertEquals(0, a.errors() + b.errors()); assertEquals(1, offeredCount());
                gate.verify("two-expiry-workers");
            } finally { gate.release.countDown(); }
        }
    }

    @Test
    void twoRecoveryWorkersProposingDifferentOrdersCannotDoubleBookOneDriver() throws Exception {
        var firstWorker = worker(1);
        assertEquals(1, firstWorker.recoverWaitingOrders().noCandidates()); // Cursor 301, chua co driver.
        order(302, null, "PLACED"); driver(7);
        var secondWorker = worker(1);
        var gate = new RaceGate();
        doAnswer(gate.answer()).when(parents).lockDriver(7);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(firstWorker::recoverWaitingOrders); // Order 302.
            var second = executor.submit(secondWorker::recoverWaitingOrders); // Order 301.
            try {
                gate.releaseBoth();
                var a = first.get(15, TimeUnit.SECONDS); var b = second.get(15, TimeUnit.SECONDS);
                assertEquals(1, a.created() + b.created()); assertEquals(0, a.errors() + b.errors());
                assertEquals(1, count("SELECT COUNT(*) FROM order_assignments WHERE driver_id = 7 AND status = 'OFFERED'"));
                gate.verify("two-orders-one-driver");
            } finally { gate.release.countDown(); }
        }
    }

    @Test
    void orderCancellationCommittedWhileExpiryWaitsCancelsOfferWithoutRetry() throws Exception {
        driver(7); driver(8); offer(401, 301, 7, "OFFERED", NOW);
        var reached = new CountDownLatch(1);
        doAnswer(invocation -> { reached.countDown(); return invocation.callRealMethod(); }).when(parents).lockOrder(301);
        try (var connection = dataSource.getConnection(); var executor = Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.prepareStatement("UPDATE orders SET status = 'CANCELLED' WHERE id = ?")) {
                    statement.setLong(1, 301); assertEquals(1, statement.executeUpdate());
                }
                var result = executor.submit(() -> worker(50).expireAndReassign());
                assertTrue(reached.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> result.get(200, TimeUnit.MILLISECONDS));
                connection.commit();
                var report = result.get(10, TimeUnit.SECONDS);
                assertEquals(1, report.cancelled()); assertEquals(0, report.created());
                assertEquals("CANCELLED", offerStatus(401)); assertEquals(0, offeredCount());
            } finally { connection.rollback(); }
        }
    }

    private class RaceGate {
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger calls = new AtomicInteger();
        final Set<Long> connections = ConcurrentHashMap.newKeySet();
        final Set<String> isolations = ConcurrentHashMap.newKeySet();
        Answer<Object> answer() {
            return invocation -> {
                if (calls.incrementAndGet() <= 2) {
                    connections.add(jdbc.queryForObject("SELECT CONNECTION_ID()", Map.of(), Long.class));
                    isolations.add(jdbc.queryForObject("SELECT @@transaction_isolation", Map.of(), String.class));
                    ready.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS));
                }
                return invocation.callRealMethod();
            };
        }
        void releaseBoth() throws Exception { assertTrue(ready.await(5, TimeUnit.SECONDS)); release.countDown(); }
        void verify(String race) {
            assertEquals(2, connections.size()); assertEquals(Set.of("READ-COMMITTED"), isolations);
            System.out.printf("MYSQL_RECOVERY_RACE %s connections=%s isolation=%s offered=%d%n",
                    race, connections, isolations, offeredCount());
        }
    }

    private void user(long id, String role) {
        jdbc.update("""
                INSERT INTO users (id, full_name, phone, password_hash, role)
                VALUES (:id, 'Recovery Test User', :phone, 'test-only-hash', :role)
                """, Map.of("id", id, "phone", "09" + id, "role", role));
    }
    private void driver(long id) {
        user(910000 + id, "DRIVER");
        jdbc.update("""
                INSERT INTO drivers (id, user_id, status, is_online, current_lat, current_lng, last_location_at)
                VALUES (:id, :userId, 'APPROVED', TRUE, :lat, 106, :at)
                """, Map.of("id", id, "userId", 910000 + id, "lat", id == 7 ? 10 : 10.1, "at", Timestamp.valueOf(NOW)));
    }
    private void refreshGps(long driverId, LocalDateTime at) {
        jdbc.update("UPDATE drivers SET last_location_at = :at WHERE id = :id", Map.of("id", driverId, "at", Timestamp.valueOf(at)));
    }
    private void order(long id, Long driverId, String status) {
        jdbc.update("""
                INSERT INTO orders (id, code, customer_id, restaurant_id, driver_id, status, receiver_name,
                                    receiver_phone, delivery_address, delivery_lat, delivery_lng, subtotal, total_amount)
                VALUES (:id, :code, 900001, 900001, :driverId, :status, 'Test Receiver',
                        '0999999999', 'Test address', 10, 106, 50000, 50000)
                """, new MapSqlParameterSource().addValue("id", id).addValue("code", "RECOVERY-TEST-" + id)
                .addValue("driverId", driverId).addValue("status", status));
    }
    private void offer(long id, long orderId, long driverId, String status, LocalDateTime expiresAt) {
        LocalDateTime offeredAt = expiresAt == null ? NOW : expiresAt.minusSeconds(30);
        jdbc.update("""
                INSERT INTO order_assignments (id, order_id, driver_id, status, distance_km, offered_at, expires_at, responded_at)
                VALUES (:id, :orderId, :driverId, :status, 1.23, :offeredAt, :expiresAt, :respondedAt)
                """, new MapSqlParameterSource().addValue("id", id).addValue("orderId", orderId).addValue("driverId", driverId)
                .addValue("status", status).addValue("offeredAt", Timestamp.valueOf(offeredAt))
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
    private int offeredCount() { return count("SELECT COUNT(*) FROM order_assignments WHERE status = 'OFFERED'"); }
    private LocalDateTime time(Object value) {
        return value instanceof Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) value;
    }
}
