package vn.hutech.fooddelivery.delivery;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 1 dong bang delivery_fee_rules. Phi = baseFee trong baseDistanceKm dau, sau do cong perKmFee moi km. */
public record DeliveryFeeRuleRow(
        long id,
        String name,
        BigDecimal baseFee,
        BigDecimal baseDistanceKm,
        BigDecimal perKmFee,
        boolean active,
        LocalDateTime createdAt
) {
}
