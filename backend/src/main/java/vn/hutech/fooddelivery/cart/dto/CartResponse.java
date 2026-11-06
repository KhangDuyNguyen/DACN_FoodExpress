package vn.hutech.fooddelivery.cart.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Gio hang cua khach. Gio trong: restaurantId = null, items rong.
 * itemCount = tong so phan (cong so luong cac dong), dung cho so tren icon gio hang.
 */
public record CartResponse(
        Long restaurantId,
        String restaurantName,
        BigDecimal minOrderAmount,
        List<CartItemResponse> items,
        int itemCount,
        BigDecimal subtotal
) {

    public static CartResponse empty() {
        return new CartResponse(null, null, BigDecimal.ZERO, List.of(), 0, BigDecimal.ZERO);
    }
}
