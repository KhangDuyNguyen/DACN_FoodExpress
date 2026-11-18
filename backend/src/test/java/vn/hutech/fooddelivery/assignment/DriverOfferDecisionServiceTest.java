package vn.hutech.fooddelivery.assignment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.common.NotFoundException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverOfferDecisionServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);
    private static final DriverCandidate DRIVER = new DriverCandidate(7, "APPROVED", true,
            10.0, 106.0, NOW, false, false);

    @Mock DriverOfferRepository offers;
    @Mock AssignmentOfferRepository assignments;

    private DriverOfferDecisionService service(LocalDateTime now) {
        var zone = ZoneId.of("Asia/Ho_Chi_Minh");
        return new DriverOfferDecisionService(offers, assignments, Clock.fixed(now.atZone(zone).toInstant(), zone));
    }

    private DriverOfferRepository.OfferSnapshot given(DriverCandidate driver, String offerStatus,
                                                      Long assignedDriver, String orderStatus) {
        var offer = new DriverOfferRepository.OfferSnapshot(401, 301, 7, offerStatus,
                NOW, NOW.plusSeconds(30), "OFFERED".equals(offerStatus) ? null : NOW.minusSeconds(1));
        when(offers.findDriverId(42)).thenReturn(Optional.of(7L));
        when(offers.findMetadata(401)).thenReturn(Optional.of(new DriverOfferRepository.OfferMetadata(401, 301, 7)));
        when(assignments.lockOrder(301)).thenReturn(Optional.of(
                new AssignmentOfferRepository.OrderSnapshot(301, 1, assignedDriver, orderStatus)));
        when(offers.lockDriverForUser(7, 42)).thenReturn(Optional.of(driver));
        when(offers.lockOffer(401)).thenReturn(Optional.of(offer));
        return offer;
    }

    @Test
    void acceptLocksParentsThenOfferAndUpdatesBothRecords() {
        var offer = given(DRIVER, "OFFERED", null, "PLACED");

        assertEquals(new DriverOfferActionResponse(401, 301, "ACCEPTED", NOW),
                service(NOW.plusNanos(999)).accept(42, 401));

        var order = inOrder(offers, assignments);
        order.verify(assignments).lockOrder(301);
        order.verify(offers).lockDriverForUser(7, 42);
        order.verify(offers).lockOffer(401);
        order.verify(offers).respond(offer, "ACCEPTED", NOW);
        order.verify(offers).assignOrder(301, 7);
    }

    @Test
    void acceptedReplayPreservesResultDespiteExpiredTimestampAndChangedGpsOnline() {
        var offline = new DriverCandidate(7, "APPROVED", false, null, null, null, false, false);
        var offer = given(offline, "ACCEPTED", 7L, "PLACED");

        assertEquals(offer.response(), service(NOW.plusMinutes(5)).accept(42, 401));
        verify(offers, never()).respond(any(), anyString(), any());
        verify(offers, never()).assignOrder(anyLong(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "REJECTED", "DELIVERED", "DELIVERING", "ACCEPTED"})
    void acceptedReplayRejectsInvalidOrderStatus(String status) {
        given(DRIVER, "ACCEPTED", 7L, status);
        assertThrows(ConflictException.class, () -> service(NOW).accept(42, 401));
    }

    @Test
    void acceptedReplayRejectsOrderAttachedToAnotherDriver() {
        given(DRIVER, "ACCEPTED", 8L, "PLACED");
        assertThrows(ConflictException.class, () -> service(NOW).accept(42, 401));
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXPIRED", "CANCELLED", "REJECTED"})
    void resolvedOfferCannotBeAccepted(String status) {
        given(DRIVER, status, null, "PLACED");
        assertThrows(ConflictException.class, () -> service(NOW).accept(42, 401));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    void exactExpiryBoundaryUsesBackendNanoseconds(long nanosFromExpiry) {
        given(DRIVER, "OFFERED", null, "PLACED");
        var now = NOW.plusSeconds(30).plusNanos(nanosFromExpiry);
        if (nanosFromExpiry < 0) {
            assertEquals("ACCEPTED", service(now).accept(42, 401).status());
        } else {
            assertThrows(ConflictException.class, () -> service(now).accept(42, 401));
        }
    }

    @ParameterizedTest
    @MethodSource("invalidDrivers")
    void rejectsIneligibleDriverSnapshot(DriverCandidate driver) {
        given(driver, "OFFERED", null, "PLACED");
        assertThrows(ConflictException.class, () -> service(NOW).accept(42, 401));
        verify(offers, never()).respond(any(), anyString(), any());
    }

    static Stream<Arguments> invalidDrivers() {
        return Stream.of(
                new DriverCandidate(7, "PENDING", true, 10.0, 106.0, NOW, false, false),
                new DriverCandidate(7, "APPROVED", false, 10.0, 106.0, NOW, false, false),
                new DriverCandidate(7, "APPROVED", true, null, 106.0, NOW, false, false),
                new DriverCandidate(7, "APPROVED", true, 10.0, null, NOW, false, false),
                new DriverCandidate(7, "APPROVED", true, Double.NaN, 106.0, NOW, false, false),
                new DriverCandidate(7, "APPROVED", true, 10.0, Double.POSITIVE_INFINITY, NOW, false, false),
                new DriverCandidate(7, "APPROVED", true, 90.1, 106.0, NOW, false, false),
                new DriverCandidate(7, "APPROVED", true, 10.0, -180.1, NOW, false, false),
                new DriverCandidate(7, "APPROVED", true, 10.0, 106.0, null, false, false),
                new DriverCandidate(7, "APPROVED", true, 10.0, 106.0, NOW.plusNanos(1), false, false),
                new DriverCandidate(7, "APPROVED", true, 10.0, 106.0, NOW.minusSeconds(60).minusNanos(1), false, false)
        ).map(Arguments::of);
    }

    @Test
    void exactlySixtySecondGpsIsAccepted() {
        given(new DriverCandidate(7, "APPROVED", true, 10.0, 106.0, NOW.minusSeconds(60), false, false),
                "OFFERED", null, "PLACED");
        assertEquals("ACCEPTED", service(NOW).accept(42, 401).status());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void busyOrOtherPendingOfferBlocksAccept(boolean activeOrder) {
        given(DRIVER, "OFFERED", null, "PLACED");
        if (activeOrder) when(assignments.hasActiveOrder(7)).thenReturn(true);
        else when(offers.hasOtherOffered(7, 401)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service(NOW).accept(42, 401));
    }

    @Test
    void invitationHistoryIsRecheckedBeforeAccept() {
        given(DRIVER, "OFFERED", null, "PLACED");
        when(assignments.findInvitationHistory(301)).thenReturn(Map.of(7L,
                new AssignmentOfferRepository.InvitationHistory(true, true, false, null)));
        assertThrows(ConflictException.class, () -> service(NOW).accept(42, 401));
    }

    @Test
    void rejectDoesNotRequireOnlineOrFreshGpsAndNeverAttachesOrder() {
        var offline = new DriverCandidate(7, "APPROVED", false, null, null, null, false, false);
        var offer = given(offline, "OFFERED", null, "PLACED");

        assertEquals(new DriverOfferActionResponse(401, 301, "REJECTED", NOW), service(NOW).reject(42, 401));
        verify(offers).respond(offer, "REJECTED", NOW);
        verify(offers, never()).assignOrder(anyLong(), anyLong());
    }

    @Test
    void rejectReplayPreservesOriginalTimestampEvenIfOrderLaterCancelled() {
        var offer = given(DRIVER, "REJECTED", null, "CANCELLED");
        assertEquals(offer.response(), service(NOW.plusMinutes(5)).reject(42, 401));
        verify(offers, never()).respond(any(), anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACCEPTED", "CANCELLED", "EXPIRED"})
    void rejectDoesNotChangeOtherResolvedStates(String status) {
        given(DRIVER, status, null, "PLACED");
        assertThrows(ConflictException.class, () -> service(NOW).reject(42, 401));
    }

    @Test
    void expiredOfferCannotBeRejected() {
        given(DRIVER, "OFFERED", null, "PLACED");
        assertThrows(ConflictException.class, () -> service(NOW.plusSeconds(30)).reject(42, 401));
    }

    @Test
    void ownershipIsCheckedBeforeLockingOrder() {
        when(offers.findDriverId(42)).thenReturn(Optional.of(8L));
        when(offers.findMetadata(401)).thenReturn(Optional.of(new DriverOfferRepository.OfferMetadata(401, 301, 7)));
        assertThrows(ForbiddenException.class, () -> service(NOW).accept(42, 401));
        verifyNoInteractions(assignments);
    }

    @Test
    void missingProfileAndOfferAreNotFound() {
        assertThrows(NotFoundException.class, () -> service(NOW).accept(42, 401));
        when(offers.findDriverId(42)).thenReturn(Optional.of(7L));
        assertThrows(NotFoundException.class, () -> service(NOW).reject(42, 401));
    }

    @Test
    void reloadedMetadataMustStillMatchLockedParents() {
        given(DRIVER, "OFFERED", null, "PLACED");
        when(offers.lockOffer(401)).thenReturn(Optional.of(new DriverOfferRepository.OfferSnapshot(
                401, 302, 7, "OFFERED", NOW, NOW.plusSeconds(30), null)));
        assertThrows(ConflictException.class, () -> service(NOW).accept(42, 401));
    }

    @Test
    void invalidIdFailsBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class, () -> service(NOW).accept(42, 0));
        verifyNoInteractions(offers, assignments);
    }
}
