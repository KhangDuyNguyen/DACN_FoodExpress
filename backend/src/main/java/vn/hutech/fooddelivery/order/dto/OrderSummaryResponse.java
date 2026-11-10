package vn.hutech.fooddelivery.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 1 dong trong danh sach don (cua khach hoac cua cua hang). itemCount = tong so phan. */
public record OrderSummaryResponse(
        long id,
        String code,
        long restaurantId,
        String restaurantName,
        String status,
        BigDecimal totalAmount,
        int itemCount,
        String receiverName,
        String deliveryAddress,
        LocalDateTime createdAt
) {
}
