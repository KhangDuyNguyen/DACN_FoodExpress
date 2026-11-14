package vn.hutech.fooddelivery.assignment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static vn.hutech.fooddelivery.assignment.AssignmentOfferRepository.InvitationHistory;
import static vn.hutech.fooddelivery.assignment.AssignmentOfferRepository.OrderSnapshot;
import static vn.hutech.fooddelivery.assignment.AssignmentOfferRepository.RestaurantLocation;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0);
    private static final RestaurantLocation RESTAURANT = new RestaurantLocation(1, BigDecimal.TEN, BigDecimal.valueOf(106));
    private static final DriverCandidate NEAR = driver(7, 10);
    private static final DriverCandidate FAR = driver(8, 11);
    private static final AssignmentOffer OFFER = new AssignmentOffer(1, 301, 7, BigDecimal.ZERO,
            NOW, NOW.plusSeconds(30));

    @Mock AssignmentRepository candidates;
    @Mock AssignmentOfferRepository offers;
    @Mock OfferCreationService creation;

    private AssignmentService service() {
        var zone = ZoneId.of("Asia/Ho_Chi_Minh");
        return new AssignmentService(candidates, offers, creation, Clock.fixed(NOW.atZone(zone).toInstant(), zone));
    }

    private void givenCandidates(List<DriverCandidate> drivers, Map<Long, InvitationHistory> history) {
        when(offers.findOrder(301)).thenReturn(Optional.of(new OrderSnapshot(301, 1, null, "PLACED")));
        when(offers.findRestaurant(1)).thenReturn(Optional.of(RESTAURANT));
        when(offers.findInvitationHistory(301)).thenReturn(history);
        when(candidates.findEligibleCandidates(NOW)).thenReturn(drivers);
    }

    @Test
    void choosesNearestUsingExistingSelectorRatherThanInputOrder() {
        givenCandidates(List.of(FAR, NEAR), Map.of());
        when(creation.tryCreateOffer(301, NEAR, RESTAURANT)).thenReturn(Optional.of(OFFER));

        assertEquals(Optional.of(OFFER), service().createOffer(301));
        verify(creation, never()).tryCreateOffer(301, FAR, RESTAURANT);
    }

    @Test
    void triesNextCandidateWhenNearestFailsLockedRevalidation() {
        givenCandidates(List.of(NEAR, FAR), Map.of());
        when(creation.tryCreateOffer(301, NEAR, RESTAURANT)).thenReturn(Optional.empty());
        when(creation.tryCreateOffer(301, FAR, RESTAURANT)).thenReturn(Optional.of(OFFER));

        assertTrue(service().createOffer(301).isPresent());
        var order = inOrder(creation);
        order.verify(creation).tryCreateOffer(301, NEAR, RESTAURANT);
        order.verify(creation).tryCreateOffer(301, FAR, RESTAURANT);
    }

    @Test
    void neverInvitedDriverTakesPriorityOverCloserExpiredDriverAfterCooldown() {
        givenCandidates(List.of(NEAR, FAR), Map.of(7L, new InvitationHistory(true, false, false, NOW.minusSeconds(60))));
        when(creation.tryCreateOffer(301, FAR, RESTAURANT)).thenReturn(Optional.of(OFFER));

        assertTrue(service().createOffer(301).isPresent());
        verify(creation, never()).tryCreateOffer(301, NEAR, RESTAURANT);
    }

    @Test
    void rejectedDriverIsNeverRetriedForSameOrder() {
        givenCandidates(List.of(NEAR, FAR), Map.of(7L, new InvitationHistory(true, true, false, null)));
        when(creation.tryCreateOffer(301, FAR, RESTAURANT)).thenReturn(Optional.of(OFFER));

        assertTrue(service().createOffer(301).isPresent());
        verify(creation, never()).tryCreateOffer(301, NEAR, RESTAURANT);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 59})
    void expiredDriverCannotBeRetriedBefore60Seconds(long secondsSinceExpiry) {
        givenCandidates(List.of(NEAR), Map.of(7L,
                new InvitationHistory(true, false, false, NOW.minusSeconds(secondsSinceExpiry))));

        assertTrue(service().createOffer(301).isEmpty());
        verifyNoInteractions(creation);
    }

    @ParameterizedTest
    @ValueSource(longs = {60, 61, 120})
    void expiredDriverCanBeRetriedAtOrAfter60Seconds(long secondsSinceExpiry) {
        givenCandidates(List.of(NEAR), Map.of(7L,
                new InvitationHistory(true, false, false, NOW.minusSeconds(secondsSinceExpiry))));
        when(creation.tryCreateOffer(301, NEAR, RESTAURANT)).thenReturn(Optional.of(OFFER));

        assertTrue(service().createOffer(301).isPresent());
    }

    @Test
    void missingExpiryFailsClosedAndRejectionWinsOverExpiredHistory() {
        assertFalse(new InvitationHistory(true, false, true, null).canInvite(NOW));
        assertFalse(new InvitationHistory(true, true, false, NOW.minusDays(1)).canInvite(NOW));
    }

    @Test
    void emptyCandidatesDoNotCreateAnything() {
        givenCandidates(List.of(), Map.of());

        assertTrue(service().createOffer(301).isEmpty());
        verifyNoInteractions(creation);
    }

    @Test
    void lockFailureIsPropagatedAndDoesNotTryAnotherDriver() {
        givenCandidates(List.of(NEAR, FAR), Map.of());
        var failure = new CannotAcquireLockException("Simulated deadlock");
        when(creation.tryCreateOffer(301, NEAR, RESTAURANT)).thenThrow(failure);

        assertSame(failure, assertThrows(CannotAcquireLockException.class, () -> service().createOffer(301)));
        verify(creation, never()).tryCreateOffer(301, FAR, RESTAURANT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_PAYMENT", "ACCEPTED", "PICKED_UP",
            "DELIVERING", "DELIVERED", "CANCELLED", "REJECTED"})
    void orderOutsideAssignmentStagesIsIgnored(String status) {
        when(offers.findOrder(301)).thenReturn(Optional.of(new OrderSnapshot(301, 1, null, status)));

        assertTrue(service().createOffer(301).isEmpty());
        verifyNoInteractions(candidates, creation);
    }

    @Test
    void missingOrAssignedOrderIsIgnored() {
        when(offers.findOrder(301)).thenReturn(Optional.empty())
                .thenReturn(Optional.of(new OrderSnapshot(301, 1, 7L, "PLACED")));

        assertTrue(service().createOffer(301).isEmpty());
        assertTrue(service().createOffer(301).isEmpty());
        verifyNoInteractions(candidates, creation);
    }

    @Test
    void existingOfferedOrderIsIgnored() {
        when(offers.findOrder(301)).thenReturn(Optional.of(new OrderSnapshot(301, 1, null, "PLACED")));
        when(offers.hasOfferedForOrder(301)).thenReturn(true);

        assertTrue(service().createOffer(301).isEmpty());
        verifyNoInteractions(candidates, creation);
    }

    @Test
    void invalidOrderIdFailsBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class, () -> service().createOffer(0));
        assertThrows(IllegalArgumentException.class, () -> service().createOffer(-1));
        verifyNoInteractions(offers, candidates, creation);
    }

    private static DriverCandidate driver(long id, double lat) {
        return new DriverCandidate(id, "APPROVED", true, lat, 106.0, NOW, false, false);
    }
}
