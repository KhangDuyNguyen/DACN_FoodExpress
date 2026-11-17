package vn.hutech.fooddelivery.order;

/**
 * Su kien "don doi trang thai", phat ra moi lan doi (OrderManagementService), changedBy la users.id.
 * Nghe bang @TransactionalEventListener(phase = AFTER_COMMIT), vi du:
 *   - An: gui thong bao cho khach (tuan 6), ghi thu nhap tai xe khi toStatus = DELIVERED;
 *   - Diep: cap nhat dashboard cua hang theo thoi gian thuc.
 */
public record OrderStatusChangedEvent(long orderId, long restaurantId, long customerId,
                                      String fromStatus, String toStatus, long changedBy) {
}
