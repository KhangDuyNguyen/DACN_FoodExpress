package vn.hutech.fooddelivery.order.event;

import vn.hutech.fooddelivery.order.dto.OrderDetailResponse;

/**
 * Event phát ra khi một đơn hàng mới được tạo thành công từ giỏ hàng.
 */
public record OrderPlacedEvent(
        long orderId,
        long restaurantId,
        OrderDetailResponse order
) {
    public OrderPlacedEvent(long restaurantId, OrderDetailResponse order) {
        this(order != null ? order.id() : 0L, restaurantId, order);
    }
}
