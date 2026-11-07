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

    /**
     * Quan phai da duoc duyet va dang mo cua (OPEN).
     * Tu tuan 4 admin duyet quan thanh APPROVED (PR #16 cua Diep); ACTIVE la trang thai cu, van coi la da duyet.
     */
    public boolean canOrder() {
        boolean approved = "APPROVED".equals(status) || "ACTIVE".equals(status);
        return approved && "OPEN".equals(openStatus);
    }
}
