package vn.hutech.fooddelivery.driver.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.server.ResponseStatusException;
import vn.hutech.fooddelivery.common.*;
import vn.hutech.fooddelivery.driver.earnings.DriverEarningsRepository;
import vn.hutech.fooddelivery.order.OrderRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverDeliveryServiceTest {
    @Mock DriverDeliveryRepository deliveries;
    @Mock OrderRepository orders;
    @Mock DriverEarningsRepository earnings;
    DriverDeliveryService service;
    private static final long USER = 700042, DRIVER = 700071, ORDER = 700301;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 12, 0);

    @BeforeEach void setup() { service = new DriverDeliveryService(deliveries, orders, earnings); }

    private DriverDeliveryRepository.DeliveryOrder order(String status) {
        return new DriverDeliveryRepository.DeliveryOrder(ORDER, DRIVER, status, new BigDecimal("15001"), NOW,
                "DELIVERED".equals(status) ? NOW : null);
    }

    private void owned(String status) {
        when(deliveries.findDriverId(USER)).thenReturn(Optional.of(DRIVER));
        when(deliveries.lockOrder(ORDER)).thenReturn(Optional.of(order(status)));
        when(deliveries.lockDriver(DRIVER, USER)).thenReturn(true);
        when(deliveries.lockAcceptedDriverIds(ORDER)).thenReturn(List.of(DRIVER));
    }

    @ParameterizedTest
    @CsvSource({"PREPARING,PICKED_UP", "READY_FOR_PICKUP,PICKED_UP", "PICKED_UP,DELIVERING", "DELIVERING,DELIVERED"})
    void successfulStepsLockInOrderAndWriteHistoryAsUserNotDriver(String from, String to) {
        owned(from);
        when(deliveries.lockOrder(ORDER)).thenReturn(Optional.of(order(from)), Optional.of(order(to)));
        when(orders.updateStatus(ORDER, from, to, null)).thenReturn(1);
        assertEquals(to, service.changeStatus(USER, ORDER, to).status());
        var calls = inOrder(deliveries, earnings, orders);
        calls.verify(deliveries).findDriverId(USER);
        calls.verify(deliveries).lockOrder(ORDER);
        calls.verify(deliveries).lockDriver(DRIVER, USER);
        calls.verify(deliveries).lockAcceptedDriverIds(ORDER);
        calls.verify(earnings).findByOrderIdForUpdate(ORDER);
        calls.verify(orders).updateStatus(ORDER, from, to, null);
        calls.verify(orders).insertStatusHistory(ORDER, to, USER, null);
        if (to.equals("DELIVERED")) calls.verify(earnings).insert(ORDER, DRIVER, new BigDecimal("12001"));
        else verify(earnings, never()).insert(anyLong(), anyLong(), any());
    }

    @ParameterizedTest
    @CsvSource({"PLACED,PICKED_UP", "PREPARING,DELIVERING", "PREPARING,DELIVERED", "PICKED_UP,DELIVERED",
            "DELIVERING,PICKED_UP", "CANCELLED,PICKED_UP", "REJECTED,DELIVERING", "ACCEPTED,PICKED_UP"})
    void invalidOrOldStepsNeverWrite(String from, String to) {
        owned(from);
        assertThrows(ConflictException.class, () -> service.changeStatus(USER, ORDER, to));
        verifyNoInteractions(orders);
        verify(earnings, never()).insert(anyLong(), anyLong(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"PICKED_UP", "DELIVERING", "DELIVERED"})
    void sameStatusReplayReturnsStoredResponseWithoutWrites(String status) {
        owned(status);
        when(deliveries.hasDriverHistory(ORDER, status, USER)).thenReturn(true);
        if (status.equals("DELIVERED")) {
            // Stored amount deliberately differs: replay must not recalculate or overwrite it.
            when(earnings.findByOrderIdForUpdate(ORDER)).thenReturn(Optional.of(
                    new DriverEarningsRepository.Earning(DRIVER, BigDecimal.ONE, BigDecimal.ZERO)));
        }
        assertEquals(order(status).response(), service.changeStatus(USER, ORDER, status));
        verifyNoInteractions(orders);
        verify(earnings, never()).insert(anyLong(), anyLong(), any());
    }

    @Test void replayWithoutHistoryIsAnExplicitConflict() {
        owned("PICKED_UP");
        assertThrows(ConflictException.class, () -> service.changeStatus(USER, ORDER, "PICKED_UP"));
        verifyNoInteractions(orders);
    }

    @Test void completedLegacyOrderWithoutEarningIsNotBackfilled() {
        owned("DELIVERED");
        assertThrows(ConflictException.class, () -> service.changeStatus(USER, ORDER, "DELIVERED"));
        verifyNoInteractions(orders);
        verify(earnings, never()).insert(anyLong(), anyLong(), any());
    }

    @Test void earningOnUnfinishedOrderIsNotOverwritten() {
        owned("DELIVERING");
        when(earnings.findByOrderIdForUpdate(ORDER)).thenReturn(Optional.of(
                new DriverEarningsRepository.Earning(DRIVER, BigDecimal.ONE, BigDecimal.ZERO)));
        assertThrows(ConflictException.class, () -> service.changeStatus(USER, ORDER, "DELIVERED"));
        verifyNoInteractions(orders);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void absentWrongOrDuplicateAcceptedAssignmentIsRejected(int variant) {
        owned("PREPARING");
        when(deliveries.lockAcceptedDriverIds(ORDER)).thenReturn(switch (variant) {
            case 0 -> List.of(); case 1 -> List.of(DRIVER + 1); default -> List.of(DRIVER, DRIVER);
        });
        assertThrows(ConflictException.class, () -> service.changeStatus(USER, ORDER, "PICKED_UP"));
        verifyNoInteractions(orders, earnings);
    }

    @Test void otherDriverCannotUpdateEvenWithAnOldAcceptedAssignment() {
        when(deliveries.findDriverId(USER)).thenReturn(Optional.of(DRIVER + 1));
        when(deliveries.lockOrder(ORDER)).thenReturn(Optional.of(order("PREPARING")));
        assertThrows(ForbiddenException.class, () -> service.changeStatus(USER, ORDER, "PICKED_UP"));
        verify(deliveries, never()).lockDriver(anyLong(), anyLong());
        verifyNoInteractions(orders, earnings);
    }

    @Test void missingProfileAndMissingOrderAre404() {
        assertThrows(NotFoundException.class, () -> service.changeStatus(USER, ORDER, "PICKED_UP"));
        when(deliveries.findDriverId(USER)).thenReturn(Optional.of(DRIVER));
        assertThrows(NotFoundException.class, () -> service.changeStatus(USER, ORDER, "PICKED_UP"));
    }

    @Test void unassignedOrderIsConflict() {
        when(deliveries.findDriverId(USER)).thenReturn(Optional.of(DRIVER));
        when(deliveries.lockOrder(ORDER)).thenReturn(Optional.of(new DriverDeliveryRepository.DeliveryOrder(
                ORDER, null, "PREPARING", BigDecimal.ZERO, NOW, null)));
        assertThrows(ConflictException.class, () -> service.changeStatus(USER, ORDER, "PICKED_UP"));
    }

    @Test void invalidIdAndUnsupportedTargetAre400BeforeReading() {
        assertThrows(ResponseStatusException.class, () -> service.changeStatus(USER, 0, "PICKED_UP"));
        assertThrows(ResponseStatusException.class, () -> service.changeStatus(USER, ORDER, "CANCELLED"));
        assertThrows(ResponseStatusException.class, () -> service.changeStatus(USER, ORDER, null));
        verifyNoInteractions(deliveries, orders, earnings);
    }

    @Test void casFailureNeverWritesHistoryOrEarning() {
        owned("DELIVERING");
        assertThrows(ConflictException.class, () -> service.changeStatus(USER, ORDER, "DELIVERED"));
        verify(orders, never()).insertStatusHistory(anyLong(), anyString(), anyLong(), any());
        verify(earnings, never()).insert(anyLong(), anyLong(), any());
    }

    @Test void historyFailurePropagatesAndDoesNotAttemptEarning() {
        owned("DELIVERING");
        when(orders.updateStatus(ORDER, "DELIVERING", "DELIVERED", null)).thenReturn(1);
        doThrow(new DataIntegrityViolationException("fixture")).when(orders)
                .insertStatusHistory(ORDER, "DELIVERED", USER, null);
        assertThrows(DataIntegrityViolationException.class, () -> service.changeStatus(USER, ORDER, "DELIVERED"));
        verify(earnings, never()).insert(anyLong(), anyLong(), any());
    }

    @Test void duplicateInsertIsConflictNotSilentlySuccessful() {
        owned("DELIVERING");
        when(orders.updateStatus(ORDER, "DELIVERING", "DELIVERED", null)).thenReturn(1);
        doThrow(new DuplicateKeyException("uq_de_order")).when(earnings).insert(ORDER, DRIVER, new BigDecimal("12001"));
        assertThrows(ConflictException.class, () -> service.changeStatus(USER, ORDER, "DELIVERED"));
    }
}
