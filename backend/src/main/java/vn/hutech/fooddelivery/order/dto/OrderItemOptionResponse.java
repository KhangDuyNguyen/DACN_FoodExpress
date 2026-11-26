package vn.hutech.fooddelivery.order.dto;

import java.math.BigDecimal;

/** 1 tuy chon cua mon trong don (vd "Lớn" +10.000d). Ten va gia la gia tri LUC DAT (bang order_item_options). */
public record OrderItemOptionResponse(String name, BigDecimal extraPrice) {
}
