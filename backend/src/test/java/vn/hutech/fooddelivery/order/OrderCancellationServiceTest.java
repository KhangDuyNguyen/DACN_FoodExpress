package vn.hutech.fooddelivery.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.order.dto.ChangeOrderStatusRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** Le Tan Binh An - MSSV 2380600006 - Tuan 5 [ADD]: Chan huy sau Accept ma khong mo rong nghiep vu Assignment. */
@ExtendWith(MockitoExtension.class)
class OrderCancellationServiceTest {
    @Mock OrderRepository orders;
    @Mock OrderService details;
    OrderManagementService service;

    @BeforeEach void setup() { service = new OrderManagementService(orders, details); }

    @Test void customerCannotCancelPlacedOrderAfterDriverAccepted() {
        when(orders.lockOrder(700301)).thenReturn(Optional.of(new OrderRepository.LockedOrder(700301, 700001, 700901, "PLACED")));
        when(orders.hasAcceptedAssignmentForUpdate(700301)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.cancelByCustomer(700001, 700301, "Đổi ý"));
        var calls = inOrder(orders);
        calls.verify(orders).lockOrder(700301);
        calls.verify(orders).hasAcceptedAssignmentForUpdate(700301);
        verify(orders, never()).updateStatus(anyLong(), anyString(), anyString(), any());
        verifyNoInteractions(details);
    }

    @Test void merchantCannotCancelPreparingOrderAfterDriverAccepted() {
        when(orders.lockOrder(700301)).thenReturn(Optional.of(new OrderRepository.LockedOrder(700301, 700001, 700901, "PREPARING")));
        when(orders.isRestaurantOwnedBy(700901, 700002)).thenReturn(true);
        when(orders.hasAcceptedAssignmentForUpdate(700301)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.changeStatusByMerchant(700002, 700301,
                new ChangeOrderStatusRequest("CANCELLED", "Hết món")));
        verify(orders, never()).updateStatus(anyLong(), anyString(), anyString(), any());
        verifyNoInteractions(details);
    }

    @Test void cancellationBeforeAcceptKeepsExistingReasonAndHistory() {
        when(orders.lockOrder(700301)).thenReturn(Optional.of(new OrderRepository.LockedOrder(700301, 700001, 700901, "PLACED")));
        when(orders.updateStatus(700301, "PLACED", "CANCELLED", "Đổi ý")).thenReturn(1);
        service.cancelByCustomer(700001, 700301, "  Đổi ý  ");
        verify(orders).insertStatusHistory(700301, "CANCELLED", 700001, "Đổi ý");
        verify(details).getDetail(700301);
    }

    @Test void customerOwnershipIsCheckedBeforeAssignmentAndWrites() {
        when(orders.lockOrder(700301)).thenReturn(Optional.of(new OrderRepository.LockedOrder(700301, 700001, 700901, "PLACED")));
        assertThrows(ForbiddenException.class, () -> service.cancelByCustomer(700099, 700301, "Đổi ý"));
        verify(orders, never()).hasAcceptedAssignmentForUpdate(anyLong());
        verify(orders, never()).updateStatus(anyLong(), anyString(), anyString(), any());
    }
}
