package vn.hutech.fooddelivery.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 1 dong trong trang admin don toan san. driverName null khi chua co tai xe. */
public record AdminOrderSummaryResponse(
        long id,
        String code,
        long restaurantId,
        String restaurantName,
        String customerName,
        String customerPhone,
        String status,
        String paymentMethod,
        BigDecimal totalAmount,
        String driverName,
        LocalDateTime createdAt
) {
}
