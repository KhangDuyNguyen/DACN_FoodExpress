package vn.hutech.fooddelivery.assignment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MySQL container rieng + Flyway that; fixture trong transaction rollback cua @JdbcTest. */
@JdbcTest(properties = "spring.config.import=")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AssignmentRepository.class)
@Testcontainers(disabledWithoutDocker = true)
class AssignmentRepositoryIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0, 0);
    private static final BigDecimal LAT = new BigDecimal("10.7769123");
    private static final BigDecimal LNG = new BigDecimal("106.7009123");

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    AssignmentRepository repository;
    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void createIndependentOrderFixtures() {
        insertUser(900001, "CUSTOMER");
        insertUser(900002, "MERCHANT");
        jdbc.update("""
                INSERT INTO merchants (id, owner_user_id, business_name)
                VALUES (900001, 900002, 'Assignment Test Merchant')
                """, Map.of());
        jdbc.update("""
                INSERT INTO restaurants (id, merchant_id, name, address, lat, lng)
                VALUES (900001, 900001, 'Assignment Test Restaurant', 'Test address', 10, 106)
                """, Map.of());
    }

    @Test
    void mapsEligibleDriverAndCanPassResultDirectlyToSelector() {
        insertDriver(7, "APPROVED", true, LAT, LNG, NOW);
        insertDriver(2, "APPROVED", true, new BigDecimal("11"), LNG, NOW.minusSeconds(30));

        var candidates = repository.findEligibleCandidates(NOW);

        assertEquals(List.of(2L, 7L), ids(candidates));
        assertEquals(new DriverCandidate(7, "APPROVED", true,
                LAT.doubleValue(), LNG.doubleValue(), NOW, false, false), candidates.get(1));
        assertEquals(7L, new NearestDriverSelector().selectNearest(
                LAT.doubleValue(), LNG.doubleValue(), candidates, NOW).orElseThrow().driverId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "REJECTED", "SUSPENDED"})
    void excludesNonApprovedDriver(String status) {
        insertDriver(7, status, true, LAT, LNG, NOW);
        assertTrue(repository.findEligibleCandidates(NOW).isEmpty());
    }

    @Test
    void excludesOfflineDriver() {
        insertDriver(7, "APPROVED", false, LAT, LNG, NOW);
        assertTrue(repository.findEligibleCandidates(NOW).isEmpty());
    }

    @ParameterizedTest
    @MethodSource("missingOrInvalidGps")
    void excludesMissingOrInvalidGps(BigDecimal lat, BigDecimal lng, LocalDateTime at) {
        insertDriver(7, "APPROVED", true, lat, lng, at);
        assertTrue(repository.findEligibleCandidates(NOW).isEmpty());
    }

    static Stream<Arguments> missingOrInvalidGps() {
        // DECIMAL cua MySQL khong luu duoc NaN/Infinity; selector NV2.1 da test dau vao Double nay.
        return Stream.of(
                Arguments.of(null, LNG, NOW), Arguments.of(LAT, null, NOW),
                Arguments.of(LAT, LNG, null),
                Arguments.of(new BigDecimal("90.0000001"), LNG, NOW),
                Arguments.of(new BigDecimal("-90.0000001"), LNG, NOW),
                Arguments.of(LAT, new BigDecimal("180.0000001"), NOW),
                Arguments.of(LAT, new BigDecimal("-180.0000001"), NOW)
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 59, 60})
    void acceptsGpsUpToExactly60Seconds(long ageSeconds) {
        insertDriver(7, "APPROVED", true, LAT, LNG, NOW.minusSeconds(ageSeconds));
        assertEquals(List.of(7L), ids(repository.findEligibleCandidates(NOW)));
    }

    @ParameterizedTest
    @ValueSource(longs = {-120, -61, 1, 60})
    void excludesStaleOrFutureGps(long offsetSeconds) {
        insertDriver(7, "APPROVED", true, LAT, LNG, NOW.plusSeconds(offsetSeconds));
        assertTrue(repository.findEligibleCandidates(NOW).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 500_000_000, 999_999_999})
    void fractionalBackendNowDoesNotRoundGpsAgeOrAdmitFutureGps(int nanos) {
        insertDriver(1, "APPROVED", true, LAT, LNG, NOW.minusSeconds(60));
        insertDriver(2, "APPROVED", true, LAT, LNG, NOW.minusSeconds(59));
        insertDriver(3, "APPROVED", true, LAT, LNG, NOW);
        insertDriver(4, "APPROVED", true, LAT, LNG, NOW.plusSeconds(1));
        var now = NOW.plusNanos(nanos);

        var candidates = repository.findEligibleCandidates(now);

        assertEquals(List.of(2L, 3L), ids(candidates));
        assertEquals(2, new NearestDriverSelector().rank(10, 106, candidates, now).size());
    }

    @ParameterizedTest
    @MethodSource("coordinateBoundaries")
    void acceptsCoordinateBoundaries(BigDecimal lat, BigDecimal lng) {
        insertDriver(7, "APPROVED", true, lat, lng, NOW);
        assertEquals(List.of(7L), ids(repository.findEligibleCandidates(NOW)));
    }

    static Stream<Arguments> coordinateBoundaries() {
        return Stream.of(
                Arguments.of(new BigDecimal("-90"), new BigDecimal("-180")),
                Arguments.of(new BigDecimal("90"), new BigDecimal("180"))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_PAYMENT", "PLACED", "ACCEPTED", "PREPARING", "READY_FOR_PICKUP",
            "PICKED_UP", "DELIVERING"})
    void excludesDriverAssignedToAnyNonTerminalOrder(String status) {
        insertAvailableDriver(7);
        insertAvailableDriver(8);
        insertOrder(301, 7L, status);

        assertEquals(List.of(8L), ids(repository.findEligibleCandidates(NOW)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DELIVERED", "CANCELLED", "REJECTED"})
    void terminalOrderDoesNotMakeDriverBusy(String status) {
        insertAvailableDriver(7);
        insertOrder(301, 7L, status);
        insertOrder(302, 7L, status);

        assertEquals(List.of(7L), ids(repository.findEligibleCandidates(NOW)));
    }

    @Test
    void terminalHistoryCannotHideAnotherActiveOrder() {
        insertAvailableDriver(7);
        insertOrder(301, 7L, "DELIVERED");
        insertOrder(302, 7L, "DELIVERING");

        assertTrue(repository.findEligibleCandidates(NOW).isEmpty());
    }

    @Test
    void unassignedActiveOrderDoesNotExcludeUnrelatedDrivers() {
        insertAvailableDriver(7);
        insertOrder(301, null, "PLACED");

        assertEquals(List.of(7L), ids(repository.findEligibleCandidates(NOW)));
    }

    @Test
    void pendingOfferExcludesOnlyItsDriverEvenForUnassignedOrder() {
        insertAvailableDriver(7);
        insertAvailableDriver(8);
        insertOrder(301, null, "PLACED");
        insertOffer(401, 301, 7, "OFFERED", NOW.plusSeconds(30));
        insertOffer(402, 301, 7, "OFFERED", NOW.plusSeconds(30));

        assertEquals(List.of(8L), ids(repository.findEligibleCandidates(NOW)));
    }

    @ParameterizedTest
    @MethodSource("offeredExpirationTimes")
    void offeredStatusStillBlocksRegardlessOfExpiryTimestamp(LocalDateTime expiresAt) {
        insertAvailableDriver(7);
        insertOrder(301, null, "PLACED");
        insertOffer(401, 301, 7, "OFFERED", expiresAt);

        assertTrue(repository.findEligibleCandidates(NOW).isEmpty());
    }

    static Stream<Arguments> offeredExpirationTimes() {
        return Stream.of(Arguments.of((Object) null), Arguments.of(NOW.minusSeconds(1)), Arguments.of(NOW));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACCEPTED", "REJECTED", "EXPIRED", "CANCELLED"})
    void resolvedOfferDoesNotBlockWithoutAnActiveOrder(String status) {
        insertAvailableDriver(7);
        insertOrder(301, null, "PLACED");
        insertOffer(401, 301, 7, status, NOW.plusSeconds(30));

        assertEquals(List.of(7L), ids(repository.findEligibleCandidates(NOW)));
    }

    @Test
    void resolvedOfferHistoryCannotHideAnotherPendingOffer() {
        insertAvailableDriver(7);
        insertOrder(301, null, "PLACED");
        insertOffer(401, 301, 7, "EXPIRED", NOW.minusSeconds(1));
        insertOffer(402, 301, 7, "OFFERED", NOW.plusSeconds(30));

        assertTrue(repository.findEligibleCandidates(NOW).isEmpty());
    }

    @Test
    void historicalOrdersAndOffersDoNotDuplicateEligibleDriver() {
        insertAvailableDriver(7);
        insertOrder(301, 7L, "DELIVERED");
        insertOrder(302, 7L, "CANCELLED");
        insertOffer(401, 301, 7, "ACCEPTED", NOW.minusSeconds(1));
        insertOffer(402, 302, 7, "CANCELLED", null);

        assertEquals(List.of(7L), ids(repository.findEligibleCandidates(NOW)));
    }

    @Test
    void readingCandidatesLeavesDriversOrdersAndOffersUnchanged() {
        insertAvailableDriver(7);
        insertAvailableDriver(8);
        insertOrder(301, null, "PLACED");
        insertOffer(401, 301, 8, "OFFERED", NOW.minusSeconds(1));
        var driversBefore = jdbc.queryForList("SELECT * FROM drivers ORDER BY id", Map.of());
        var ordersBefore = jdbc.queryForList("SELECT * FROM orders ORDER BY id", Map.of());
        var offersBefore = jdbc.queryForList("SELECT * FROM order_assignments ORDER BY id", Map.of());

        assertEquals(List.of(7L), ids(repository.findEligibleCandidates(NOW)));

        assertEquals(driversBefore, jdbc.queryForList("SELECT * FROM drivers ORDER BY id", Map.of()));
        assertEquals(ordersBefore, jdbc.queryForList("SELECT * FROM orders ORDER BY id", Map.of()));
        assertEquals(offersBefore, jdbc.queryForList("SELECT * FROM order_assignments ORDER BY id", Map.of()));
    }

    @Test
    void noEligibleDriversReturnsEmptyList() {
        assertTrue(repository.findEligibleCandidates(NOW).isEmpty());
        insertDriver(7, "PENDING", false, null, null, null);
        assertTrue(repository.findEligibleCandidates(NOW).isEmpty());
    }

    private void insertAvailableDriver(long id) {
        insertDriver(id, "APPROVED", true, LAT, LNG, NOW);
    }

    private void insertDriver(long id, String status, boolean online,
                              BigDecimal lat, BigDecimal lng, LocalDateTime at) {
        long userId = 910000 + id;
        insertUser(userId, "DRIVER");
        jdbc.update("""
                INSERT INTO drivers (id, user_id, status, is_online, current_lat, current_lng, last_location_at)
                VALUES (:id, :userId, :status, :online, :lat, :lng, :at)
                """, new MapSqlParameterSource().addValue("id", id).addValue("userId", userId)
                .addValue("status", status).addValue("online", online).addValue("lat", lat).addValue("lng", lng)
                .addValue("at", at == null ? null : Timestamp.valueOf(at)));
    }

    private void insertUser(long id, String role) {
        jdbc.update("""
                INSERT INTO users (id, full_name, phone, password_hash, role)
                VALUES (:id, 'Assignment Test User', :phone, 'test-only-hash', :role)
                """, Map.of("id", id, "phone", "09" + id, "role", role));
    }

    private void insertOrder(long id, Long driverId, String status) {
        jdbc.update("""
                INSERT INTO orders (id, code, customer_id, restaurant_id, driver_id, status,
                                    receiver_name, receiver_phone, delivery_address, delivery_lat, delivery_lng,
                                    subtotal, total_amount)
                VALUES (:id, :code, 900001, 900001, :driverId, :status,
                        'Test Receiver', '0999999999', 'Test address', 10, 106, 50000, 50000)
                """, new MapSqlParameterSource().addValue("id", id).addValue("code", "ASSIGN-TEST-" + id)
                .addValue("driverId", driverId).addValue("status", status));
    }

    private void insertOffer(long id, long orderId, long driverId, String status, LocalDateTime expiresAt) {
        jdbc.update("""
                INSERT INTO order_assignments (id, order_id, driver_id, status, offered_at, expires_at)
                VALUES (:id, :orderId, :driverId, :status, :offeredAt, :expiresAt)
                """, new MapSqlParameterSource().addValue("id", id).addValue("orderId", orderId)
                .addValue("driverId", driverId).addValue("status", status)
                .addValue("offeredAt", Timestamp.valueOf(NOW.minusSeconds(30)))
                .addValue("expiresAt", expiresAt == null ? null : Timestamp.valueOf(expiresAt)));
    }

    private static List<Long> ids(List<DriverCandidate> candidates) {
        return candidates.stream().map(DriverCandidate::driverId).toList();
    }
}
