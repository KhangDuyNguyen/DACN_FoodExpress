package vn.hutech.fooddelivery.assignment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.NotFoundException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverOfferServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);
    private static final DriverOfferActionResponse REJECTED = new DriverOfferActionResponse(401, 301, "REJECTED", NOW);
    @Mock DriverOfferRepository offers;
    @Mock DriverOfferDecisionService decisions;
    @Mock AssignmentService assignments;

    private DriverOfferService service() {
        var zone = ZoneId.of("Asia/Ho_Chi_Minh");
        return new DriverOfferService(offers, decisions, assignments, Clock.fixed(NOW.atZone(zone).toInstant(), zone));
    }

    @Test
    void getResolvesDriverFromAuthenticatedUserAndReturnsServerTime() {
        when(offers.findDriverId(42)).thenReturn(Optional.of(7L));
        when(offers.findActiveOffers(7, NOW)).thenReturn(List.of());
        assertEquals(new DriverOffersResponse(List.of(), NOW), service().getOffers(42));
        verify(offers).findActiveOffers(7, NOW);
        verifyNoInteractions(decisions, assignments);
    }

    @Test
    void getWithoutProfileIsNotFound() {
        assertThrows(NotFoundException.class, () -> service().getOffers(42));
    }

    @Test
    void acceptDoesNotRunReassignment() {
        var accepted = new DriverOfferActionResponse(401, 301, "ACCEPTED", NOW);
        when(decisions.accept(42, 401)).thenReturn(accepted);
        assertEquals(accepted, service().accept(42, 401));
        verifyNoInteractions(assignments);
    }

    @Test
    void rejectCallsAssignmentOnlyAfterDecisionBeanReturns() {
        when(decisions.reject(42, 401)).thenReturn(REJECTED);
        assertEquals(REJECTED, service().reject(42, 401));
        var order = inOrder(decisions, assignments);
        order.verify(decisions).reject(42, 401);
        order.verify(assignments).createOffer(301);
    }

    @Test
    void failedRejectDoesNotReassign() {
        when(decisions.reject(42, 401)).thenThrow(new ConflictException("Expired"));
        assertThrows(ConflictException.class, () -> service().reject(42, 401));
        verifyNoInteractions(assignments);
    }

    @Test
    void reassignmentFailureIsExplicitAndRejectedReplayRetriesIt() {
        when(decisions.reject(42, 401)).thenReturn(REJECTED);
        var failure = new CannotAcquireLockException("Simulated deadlock after reject commit");
        when(assignments.createOffer(301)).thenThrow(failure).thenReturn(Optional.empty());

        var error = assertThrows(DriverOfferService.ReassignmentFailedException.class,
                () -> service().reject(42, 401));
        assertSame(failure, error.getCause());
        assertEquals(REJECTED, service().reject(42, 401));
        verify(assignments, times(2)).createOffer(301);
    }
}
