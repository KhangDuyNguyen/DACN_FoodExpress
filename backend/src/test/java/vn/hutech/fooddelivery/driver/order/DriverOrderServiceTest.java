package vn.hutech.fooddelivery.driver.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.NotFoundException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverOrderServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 12, 0);
    @Mock DriverOrderRepository orders;

    private DriverOrderService service() {
        var zone = ZoneId.of("Asia/Ho_Chi_Minh");
        return new DriverOrderService(orders, Clock.fixed(NOW.atZone(zone).toInstant(), zone));
    }

    @Test
    void emptyQueryReturnsExplicitNullAndControlledBackendTime() {
        when(orders.findDriverId(42)).thenReturn(Optional.of(7L));
        when(orders.findCurrentOrders(7)).thenReturn(List.of());
        assertEquals(new DriverCurrentOrderResponse(null, NOW), service().getCurrentOrder(42));
    }

    @Test
    void authenticatedUserIdIsResolvedToDifferentDriverIdBeforeReadingOrder() {
        var current = order(301);
        when(orders.findDriverId(42)).thenReturn(Optional.of(7L));
        when(orders.findCurrentOrders(7)).thenReturn(List.of(current));
        var result = service().getCurrentOrder(42);
        assertSame(current, result.currentOrder());
        assertEquals(NOW, result.serverTime());
        var calls = inOrder(orders);
        calls.verify(orders).findDriverId(42);
        calls.verify(orders).findCurrentOrders(7);
        verifyNoMoreInteractions(orders);
    }

    @Test
    void missingDriverProfileIsNotAnEmptyCurrentOrder() {
        when(orders.findDriverId(42)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service().getCurrentOrder(42));
        verify(orders).findDriverId(42);
        verifyNoMoreInteractions(orders);
    }

    @Test
    void twoActiveOrdersAreAConflictRatherThanSelectingFirst() {
        when(orders.findDriverId(42)).thenReturn(Optional.of(7L));
        when(orders.findCurrentOrders(7)).thenReturn(List.of(order(301), order(302)));
        assertThrows(ConflictException.class, () -> service().getCurrentOrder(42));
    }

    @Test
    void driverLookupDatabaseFailurePropagatesUnchanged() {
        var failure = new DataAccessResourceFailureException("Fixture driver lookup failure");
        when(orders.findDriverId(42)).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class, () -> service().getCurrentOrder(42)));
        verify(orders).findDriverId(42);
        verifyNoMoreInteractions(orders);
    }

    @Test
    void orderLookupDatabaseFailureDoesNotBecomeNull() {
        var failure = new DataAccessResourceFailureException("Fixture order lookup failure");
        when(orders.findDriverId(42)).thenReturn(Optional.of(7L));
        when(orders.findCurrentOrders(7)).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class, () -> service().getCurrentOrder(42)));
    }

    @Test
    void nullableDatabaseFieldsAreNotReplacedWithInventedValues() {
        var current = order(301);
        when(orders.findDriverId(42)).thenReturn(Optional.of(7L));
        when(orders.findCurrentOrders(7)).thenReturn(List.of(current));
        var result = service().getCurrentOrder(42).currentOrder();
        assertNull(result.restaurantPhone());
        assertNull(result.note());
        assertEquals("PLACED", result.status());
    }

    private DriverCurrentOrderResponse.CurrentOrder order(long id) {
        return new DriverCurrentOrderResponse.CurrentOrder(id, "TEST-" + id, "PLACED", NOW.minusMinutes(10), NOW,
                900001, "Fixture Restaurant", "Fixture Pickup", null,
                new BigDecimal("10.7626221"), new BigDecimal("106.6601727"),
                "Fixture Receiver", "0900000999", "Fixture Delivery",
                new BigDecimal("10.7712345"), new BigDecimal("106.6812345"), null);
    }
}
