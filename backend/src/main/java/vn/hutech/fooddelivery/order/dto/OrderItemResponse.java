package vn.hutech.fooddelivery.order.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 1 mon trong don. Ten, don gia va tuy chon la gia tri LUC DAT (luu trong order_items, order_item_options),
 * nen quan doi menu sau nay thi don cu khong bi doi. menuItemId null neu mon da bi xoa khoi menu.
 * unitPrice da gom gia cac tuy chon, nen lineTotal = unitPrice x quantity.
 */
public record OrderItemResponse(
        Long menuItemId,
        String name,
        BigDecimal unitPrice,
        int quantity,
        String note,
        BigDecimal lineTotal,
        List<OrderItemOptionResponse> options
) {

    /** Mon khong co tuy chon. Giu constructor 6 tham so de code cu (ca cua cac ban khac) van chay. */
    public OrderItemResponse(Long menuItemId, String name, BigDecimal unitPrice, int quantity, String note,
                             BigDecimal lineTotal) {
        this(menuItemId, name, unitPrice, quantity, note, lineTotal, List.of());
    }
}
