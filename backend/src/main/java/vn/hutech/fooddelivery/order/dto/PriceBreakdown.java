package vn.hutech.fooddelivery.order.dto;

import java.math.BigDecimal;

/**
 * Bang tien cua mot don (dung cho xem truoc va khi dat hang).
 * Tien la VND (scale 0), distanceKm co 2 so le.
 */
public record PriceBreakdown(
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        BigDecimal distanceKm
) {
}
