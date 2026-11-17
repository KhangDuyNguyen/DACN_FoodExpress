package vn.hutech.fooddelivery.order;

/**
 * Su kien "co don moi", phat ra trong transaction dat hang (OrderService.createOrder).
 * Module khac nghe bang @TransactionalEventListener(phase = AFTER_COMMIT) de chi chay khi don da luu xong:
 *   - An: moi tai xe gan nhat (OrderAssignmentListener goi AssignmentService);
 *   - Diep: day don moi len dashboard cua hang qua WebSocket (tuan 5).
 */
public record OrderPlacedEvent(long orderId, long restaurantId, long customerId) {
}
