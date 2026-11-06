package vn.hutech.fooddelivery.order;

import java.math.BigDecimal;

/** Thong tin quan can khi dat hang (doc tu bang restaurants cua module Duong, chi SELECT). */
public record RestaurantForOrder(
        long id,
        String name,
        double lat,
        double lng,
        String status,
        String openStatus,
        BigDecimal minOrderAmount
) {

    /** Quan phai dang hoat dong (ACTIVE) va dang mo cua (OPEN). */
    public boolean canOrder() {
        return "ACTIVE".equals(status) && "OPEN".equals(openStatus);
    }
}
