package vn.hutech.fooddelivery.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Thông tin một đơn hàng trong danh sách đơn của cửa hàng và payload WebSocket realtime.
 * Bao gồm các trường tóm tắt cùng danh sách món ăn, số điện thoại nhận hàng và ghi chú đơn hàng.
 */
public record MerchantOrderSummaryResponse(
        long id,
        String code,
        long restaurantId,
        String restaurantName,
        String status,
        BigDecimal totalAmount,
        int itemCount,
        String receiverName,
        String receiverPhone,
        String deliveryAddress,
        String note,
        String paymentMethod,
        List<OrderItemResponse> items,
        LocalDateTime createdAt
) {
}

