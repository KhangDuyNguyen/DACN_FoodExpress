package vn.hutech.fooddelivery.delivery.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 1 quy tac phi giao cho trang admin.
 *
 * @param applied true neu day la quy tac dat hang dang dung (quy tac dang bat moi nhat)
 */
public record DeliveryFeeRuleResponse(
        long id,
        String name,
        BigDecimal baseFee,
        BigDecimal baseDistanceKm,
        BigDecimal perKmFee,
        boolean active,
        boolean applied,
        LocalDateTime createdAt
) {
}
