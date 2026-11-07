package vn.hutech.fooddelivery.assignment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentRecoveryServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);
    @Mock AssignmentRecoveryRepository repository;
    @Mock AssignmentOfferLifecycleService lifecycle;
    @Mock AssignmentService assignments;

    private AssignmentRecoveryService service(int batch) {
        var zone = ZoneId.of("Asia/Ho_Chi_Minh");
        return new AssignmentRecoveryService(repository, lifecycle, assignments,
                Clock.fixed(NOW.atZone(zone).toInstant(), zone), batch);
    }

    @Test
    void expiryRetriesOnlyAfterLifecycleReturnsAndCountsRealChanges() {
        when(repository.findRecoverableOfferIds(0, 50, NOW)).thenReturn(List.of(401L));
        when(lifecycle.reconcileOffer(401)).thenReturn(Optional.of(
                new AssignmentOfferLifecycleService.CompletedOffer(401, 301, "EXPIRED")));
        when(repository.isWaitingWithoutOffer(301)).thenReturn(true);
        when(assignments.createOffer(301)).thenReturn(Optional.of(
                new AssignmentOffer(402, 301, 8, BigDecimal.ONE, NOW, NOW.plusSeconds(30))));

        var report = service(50).expireAndReassign();

        assertEquals(new AssignmentRecoveryService.Report(1, 1, 0, 1, 1, 0, 0, 0), report);
        var order = inOrder(lifecycle, assignments);
        order.verify(lifecycle).reconcileOffer(401);
        order.verify(assignments).createOffer(301);
    }

    @Test
    void cancellingOfferForInvalidOrderDoesNotReassign() {
        when(repository.findRecoverableOfferIds(0, 50, NOW)).thenReturn(List.of(401L));
        when(lifecycle.reconcileOffer(401)).thenReturn(Optional.of(
                new AssignmentOfferLifecycleService.CompletedOffer(401, 301, "CANCELLED")));

        var report = service(50).expireAndReassign();

        assertEquals(1, report.cancelled());
        assertEquals(0, report.attempted());
        verifyNoInteractions(assignments);
    }

    @Test
    void oneOfferSqlFailureDoesNotStopNextOfferOrLookLikeNoCandidates() {
        when(repository.findRecoverableOfferIds(0, 50, NOW)).thenReturn(List.of(401L, 402L));
        when(lifecycle.reconcileOffer(401)).thenThrow(new CannotAcquireLockException("Test lock failure"));
        when(lifecycle.reconcileOffer(402)).thenReturn(Optional.of(
                new AssignmentOfferLifecycleService.CompletedOffer(402, 302, "EXPIRED")));
        when(repository.isWaitingWithoutOffer(302)).thenReturn(true);

        var report = service(50).expireAndReassign();

        assertEquals(1, report.errors());
        assertEquals(1, report.expired());
        assertEquals(1, report.noCandidates());
        verify(assignments).createOffer(302);
    }

    @Test
    void recoveryCleansExpiredOffersBeforeReassignment() {
        when(repository.findWaitingOrderIds(0, 50, NOW)).thenReturn(List.of(301L));
        when(repository.findOfferedIdsForOrder(301, 50)).thenReturn(List.of(401L));
        when(lifecycle.reconcileOffer(401)).thenReturn(Optional.of(
                new AssignmentOfferLifecycleService.CompletedOffer(401, 301, "EXPIRED")));
        when(repository.isWaitingWithoutOffer(301)).thenReturn(true);

        var report = service(50).recoverWaitingOrders();

        assertEquals(1, report.expired());
        assertEquals(1, report.attempted());
        var order = inOrder(lifecycle, assignments);
        order.verify(lifecycle).reconcileOffer(401);
        order.verify(assignments).createOffer(301);
    }

    @Test
    void assignmentFailureIsCountedAndLaterOrderStillRuns() {
        when(repository.findWaitingOrderIds(0, 50, NOW)).thenReturn(List.of(301L, 302L));
        when(repository.isWaitingWithoutOffer(301)).thenReturn(true);
        when(repository.isWaitingWithoutOffer(302)).thenReturn(true);
        when(assignments.createOffer(301)).thenThrow(new CannotAcquireLockException("Test retry failure"));

        var report = service(50).recoverWaitingOrders();

        assertEquals(2, report.attempted());
        assertEquals(1, report.errors());
        assertEquals(1, report.noCandidates());
        verify(assignments).createOffer(302);
    }

    @Test
    void concurrentOfferWinnerIsSkippedRatherThanReportedAsNoCandidates() {
        when(repository.findWaitingOrderIds(0, 50, NOW)).thenReturn(List.of(301L));
        when(repository.isWaitingWithoutOffer(301)).thenReturn(true).thenReturn(false);

        var report = service(50).recoverWaitingOrders();

        assertEquals(1, report.attempted());
        assertEquals(0, report.noCandidates());
        assertEquals(1, report.skipped());
    }

    @Test
    void keysetCursorAdvancesDespiteNoCandidatesThenWraps() {
        when(repository.findWaitingOrderIds(0, 2, NOW)).thenReturn(List.of(301L, 302L));
        when(repository.findWaitingOrderIds(302, 2, NOW)).thenReturn(List.of(303L));
        when(repository.findWaitingOrderIds(303, 2, NOW)).thenReturn(List.of());
        var recovery = service(2);

        assertEquals(2, recovery.recoverWaitingOrders().scanned());
        assertEquals(1, recovery.recoverWaitingOrders().scanned());
        assertEquals(2, recovery.recoverWaitingOrders().scanned());

        verify(repository).findWaitingOrderIds(302, 2, NOW);
        verify(repository).findWaitingOrderIds(303, 2, NOW);
    }

    @Test
    void scanFailureIsPropagatedInsteadOfReturningSuccessfulEmptyBatch() {
        var failure = new DataAccessResourceFailureException("Test database unavailable");
        when(repository.findRecoverableOfferIds(0, 50, NOW)).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class, () -> service(50).expireAndReassign()));
        verifyNoInteractions(lifecycle, assignments);
    }

    @Test
    void validPendingOfferOrAssignedOrderIsNotRetried() {
        when(repository.findWaitingOrderIds(0, 50, NOW)).thenReturn(List.of(301L));
        assertEquals(1, service(50).recoverWaitingOrders().skipped());
        verify(assignments, never()).createOffer(301);
    }

    @Test
    void invalidBatchSizeFailsBeforeAccessingDatabase() {
        assertThrows(IllegalArgumentException.class, () -> service(0));
        assertThrows(IllegalArgumentException.class, () -> service(51));
        verifyNoInteractions(repository, lifecycle, assignments);
    }
}
