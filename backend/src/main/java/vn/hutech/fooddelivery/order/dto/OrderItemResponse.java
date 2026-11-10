package vn.hutech.fooddelivery.order.dto;

import java.math.BigDecimal;

/**
 * 1 mon trong don. Ten va don gia la gia tri LUC DAT (luu trong order_items),
 * nen quan doi menu sau nay thi don cu khong bi doi. menuItemId null neu mon da bi xoa khoi menu.
 */
public record OrderItemResponse(
        Long menuItemId,
        String name,
        BigDecimal unitPrice,
        int quantity,
        String note,
        BigDecimal lineTotal
) {
}
