package vn.hutech.fooddelivery.order.event;

/**
 * Su kien "don doi trang thai", phat ra moi lan doi (OrderManagementService), changedBy la users.id.
 * Ghi thu nhap tai xe dong bo trong transaction doi trang thai.
 * Thong bao hay cap nhat dashboard co the nghe sau commit.
 */
public record OrderStatusChangedEvent(long orderId, long restaurantId, long customerId,
                                      String fromStatus, String toStatus, long changedBy) {
}
