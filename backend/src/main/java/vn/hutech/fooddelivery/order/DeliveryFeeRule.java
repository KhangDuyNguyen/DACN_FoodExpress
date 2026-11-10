package vn.hutech.fooddelivery.order;

import java.math.BigDecimal;

/** Dong phi giao hang dang bat trong bang delivery_fee_rules. */
public record DeliveryFeeRule(BigDecimal baseFee, BigDecimal baseDistanceKm, BigDecimal perKmFee) {
}
