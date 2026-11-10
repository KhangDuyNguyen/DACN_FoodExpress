package vn.hutech.fooddelivery.order;

import java.math.BigDecimal;

/** Du lieu de INSERT 1 dong vao bang orders (trang thai luon bat dau la PLACED). */
public record NewOrder(
        String code,
        long customerId,
        long restaurantId,
        String receiverName,
        String receiverPhone,
        String deliveryAddress,
        double deliveryLat,
        double deliveryLng,
        String note,
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        String paymentMethod,
        BigDecimal distanceKm
) {
}
