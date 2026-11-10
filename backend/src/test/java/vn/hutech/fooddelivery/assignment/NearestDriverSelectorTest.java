package vn.hutech.fooddelivery.assignment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NearestDriverSelectorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0, 0);
    private final NearestDriverSelector selector = new NearestDriverSelector();

    @Test
    void ranksAllEligibleDriversAndSelectsNearestRegardlessOfInputOrder() {
        var candidates = List.of(available(1, 0.0, 3.0), available(3, 0.0, 1.0), available(2, 0.0, 2.0));

        var ranked = selector.rank(0, 0, candidates, NOW);

        assertEquals(List.of(3L, 2L, 1L), ids(ranked));
        assertTrue(ranked.get(0).distanceKm() < ranked.get(1).distanceKm());
        assertTrue(ranked.get(1).distanceKm() < ranked.get(2).distanceKm());
        assertEquals(ranked.getFirst(), selector.selectNearest(0, 0, candidates, NOW).orElseThrow());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"PENDING", "REJECTED", "SUSPENDED", "UNKNOWN", "approved"})
    void excludesNearestDriverUnlessStatusIsApproved(String status) {
        assertExcluded(new DriverCandidate(1, status, true, 0.0, 0.0, NOW, false, false));
    }

    @Test
    void excludesOfflineDriver() {
        assertExcluded(new DriverCandidate(1, "APPROVED", false, 0.0, 0.0, NOW, false, false));
    }

    @ParameterizedTest
    @MethodSource("missingGps")
    void excludesMissingGps(Double lat, Double lng, LocalDateTime at) {
        assertExcluded(new DriverCandidate(1, "APPROVED", true, lat, lng, at, false, false));
    }

    static Stream<Arguments> missingGps() {
        return Stream.of(
                Arguments.of(null, 0.0, NOW),
                Arguments.of(0.0, null, NOW),
                Arguments.of(0.0, 0.0, null),
                Arguments.of(null, null, null)
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 59, 60})
    void acceptsGpsUpToExactly60SecondsOld(long ageSeconds) {
        var candidate = locatedAt(NOW.minusSeconds(ageSeconds));

        assertEquals(1L, selector.selectNearest(0, 0, List.of(candidate), NOW).orElseThrow().driverId());
    }

    @ParameterizedTest
    @ValueSource(longs = {61, 120})
    void excludesGpsOlderThan60Seconds(long ageSeconds) {
        assertExcluded(locatedAt(NOW.minusSeconds(ageSeconds)));
    }

    @Test
    void excludesGpsJustBeyond60SecondsWithoutTruncatingAge() {
        assertExcluded(locatedAt(NOW.minusSeconds(60).minusNanos(1)));
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 60})
    void excludesFutureGps(long futureSeconds) {
        assertExcluded(locatedAt(NOW.plusSeconds(futureSeconds)));
    }

    @Test
    void excludesGpsEvenOneNanosecondInFuture() {
        assertExcluded(locatedAt(NOW.plusNanos(1)));
    }

    @Test
    void usesSuppliedTimeIncludingFractionalSeconds() {
        var now = NOW.plusNanos(500_000_000);
        var candidate = locatedAt(now.minusSeconds(60));

        assertTrue(selector.selectNearest(0, 0, List.of(candidate), now).isPresent());
        assertTrue(selector.selectNearest(0, 0, List.of(candidate), now.plusNanos(1)).isEmpty());
    }

    @Test
    void excludesDriverWithActiveOrderFlag() {
        assertExcluded(new DriverCandidate(1, "APPROVED", true, 0.0, 0.0, NOW, true, false));
    }

    @Test
    void excludesDriverWithPendingOfferFlag() {
        assertExcluded(new DriverCandidate(1, "APPROVED", true, 0.0, 0.0, NOW, false, true));
    }

    @ParameterizedTest
    @MethodSource("invalidCoordinates")
    void excludesInvalidDriverCoordinates(double lat, double lng) {
        assertExcluded(available(1, lat, lng));
    }

    @ParameterizedTest
    @MethodSource("invalidCoordinates")
    void rejectsInvalidRestaurantCoordinates(double lat, double lng) {
        var candidates = List.of(available(1, 0.0, 0.0));

        assertThrows(IllegalArgumentException.class, () -> selector.rank(lat, lng, candidates, NOW));
        assertThrows(IllegalArgumentException.class, () -> selector.selectNearest(lat, lng, candidates, NOW));
    }

    static Stream<Arguments> invalidCoordinates() {
        return Stream.of(
                Arguments.of(-90.0000001, 0.0), Arguments.of(90.0000001, 0.0),
                Arguments.of(0.0, -180.0000001), Arguments.of(0.0, 180.0000001),
                Arguments.of(Double.NaN, 0.0), Arguments.of(0.0, Double.NaN),
                Arguments.of(Double.POSITIVE_INFINITY, 0.0), Arguments.of(Double.NEGATIVE_INFINITY, 0.0),
                Arguments.of(0.0, Double.POSITIVE_INFINITY), Arguments.of(0.0, Double.NEGATIVE_INFINITY)
        );
    }

    @ParameterizedTest
    @MethodSource("coordinateBoundaries")
    void acceptsCoordinateBoundariesForRestaurantAndDriver(double lat, double lng) {
        var nearest = selector.selectNearest(lat, lng, List.of(available(1, lat, lng)), NOW).orElseThrow();

        assertEquals(0.0, nearest.distanceKm(), 1e-9);
    }

    static Stream<Arguments> coordinateBoundaries() {
        return Stream.of(Arguments.of(-90.0, -180.0), Arguments.of(90.0, 180.0),
                Arguments.of(0.0, -180.0), Arguments.of(0.0, 180.0));
    }

    @Test
    void breaksEqualDistanceTiesBySmallerDriverIdForEitherInputOrder() {
        var first = available(9, 0.0, 1.0);
        var second = available(2, 0.0, -1.0);

        assertEquals(List.of(2L, 9L), ids(selector.rank(0, 0, List.of(first, second), NOW)));
        assertEquals(List.of(2L, 9L), ids(selector.rank(0, 0, List.of(second, first), NOW)));
        assertEquals(2L, selector.selectNearest(0, 0, List.of(first, second), NOW).orElseThrow().driverId());
    }

    @Test
    void comparesFullDistanceBeforeDriverIdWithoutRounding() {
        var candidates = List.of(available(1, 0.0, 0.000002), available(9, 0.0, 0.000001));

        var ranked = selector.rank(0, 0, candidates, NOW);

        assertEquals(List.of(9L, 1L), ids(ranked));
        assertTrue(ranked.get(0).distanceKm() > 0);
        assertTrue(ranked.get(0).distanceKm() < ranked.get(1).distanceKm());
        assertTrue(ranked.get(1).distanceKm() < 0.005);
    }

    @Test
    void emptyCandidatesReturnEmptyRankingAndSelection() {
        assertTrue(selector.rank(0, 0, List.of(), NOW).isEmpty());
        assertTrue(selector.selectNearest(0, 0, List.of(), NOW).isEmpty());
    }

    @Test
    void allIneligibleCandidatesReturnEmptyRankingAndSelection() {
        var candidates = List.of(locatedAt(NOW.minusSeconds(61)),
                new DriverCandidate(2, "PENDING", true, 0.0, 0.0, NOW, false, false));

        assertTrue(selector.rank(0, 0, candidates, NOW).isEmpty());
        assertTrue(selector.selectNearest(0, 0, candidates, NOW).isEmpty());
    }

    @Test
    void skipsNullCandidateWithoutLosingValidDriver() {
        assertEquals(List.of(2L), ids(selector.rank(0, 0,
                Arrays.asList(null, available(2, 0.0, 1.0)), NOW)));
    }

    @Test
    void rejectsMissingCandidateListOrBackendTime() {
        assertThrows(NullPointerException.class, () -> selector.rank(0, 0, null, NOW));
        assertThrows(NullPointerException.class, () -> selector.rank(0, 0, List.of(), null));
        assertThrows(NullPointerException.class, () -> selector.selectNearest(0, 0, null, NOW));
        assertThrows(NullPointerException.class, () -> selector.selectNearest(0, 0, List.of(), null));
    }

    @Test
    void returnsReasonableHaversineDistanceInKilometers() {
        var nearest = selector.selectNearest(0, 0, List.of(available(1, 0.0, 1.0)), NOW).orElseThrow();

        assertEquals(111.195, nearest.distanceKm(), 0.001);
    }

    @Test
    void driverAtRestaurantHasZeroDistance() {
        var nearest = selector.selectNearest(10.7769, 106.7009,
                List.of(available(1, 10.7769, 106.7009)), NOW).orElseThrow();

        assertEquals(0.0, nearest.distanceKm(), 1e-9);
    }

    @Test
    void rankingDoesNotReorderOrModifyInputList() {
        var candidates = new ArrayList<>(List.of(available(1, 0.0, 2.0), available(2, 0.0, 1.0)));
        var original = List.copyOf(candidates);

        selector.rank(0, 0, candidates, NOW);

        assertEquals(original, candidates);
    }

    private void assertExcluded(DriverCandidate candidate) {
        var candidates = List.of(candidate, available(2, 0.0, 1.0));

        assertEquals(List.of(2L), ids(selector.rank(0, 0, candidates, NOW)));
        assertEquals(2L, selector.selectNearest(0, 0, candidates, NOW).orElseThrow().driverId());
    }

    private static DriverCandidate available(long id, Double lat, Double lng) {
        return new DriverCandidate(id, "APPROVED", true, lat, lng, NOW, false, false);
    }

    private static DriverCandidate locatedAt(LocalDateTime at) {
        return new DriverCandidate(1, "APPROVED", true, 0.0, 0.0, at, false, false);
    }

    private static List<Long> ids(List<RankedDriver> ranked) {
        return ranked.stream().map(RankedDriver::driverId).toList();
    }
}
