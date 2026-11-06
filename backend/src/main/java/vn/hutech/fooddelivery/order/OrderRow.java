package vn.hutech.fooddelivery.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 1 dong bang orders, kem ten/so dien thoai/dia chi quan (JOIN restaurants). */
public record OrderRow(
        long id,
        String code,
        long customerId,
        long restaurantId,
        String restaurantName,
        String restaurantPhone,
        String restaurantAddress,
        String status,
        String receiverName,
        String receiverPhone,
        String deliveryAddress,
        String note,
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        String paymentMethod,
        BigDecimal distanceKm,
        String cancelReason,
        LocalDateTime createdAt,
        LocalDateTime acceptedAt,
        LocalDateTime deliveredAt,
        LocalDateTime cancelledAt
) {
}
