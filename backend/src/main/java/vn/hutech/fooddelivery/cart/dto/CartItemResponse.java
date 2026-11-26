package vn.hutech.fooddelivery.cart.dto;

import vn.hutech.fooddelivery.cart.CartLine;
import vn.hutech.fooddelivery.cart.CartLineOption;

import java.math.BigDecimal;
import java.util.List;

/**
 * 1 dong trong gio tra ve client. unitPrice = gia mon + gia cac tuy chon da chon.
 * isAvailable dat ten giong menu cua Duong (MenuItemDTO): false khi mon hoac 1 tuy chon da het.
 */
public record CartItemResponse(
        long id,
        long menuItemId,
        String name,
        String imageUrl,
        BigDecimal unitPrice,
        int quantity,
        String note,
        BigDecimal lineTotal,
        boolean isAvailable,
        List<CartLineOption> options
) {

    public static CartItemResponse from(CartLine line) {
        return new CartItemResponse(line.cartItemId(), line.menuItemId(), line.name(), line.imageUrl(),
                line.unitPrice(), line.quantity(), line.note(), line.lineTotal(), line.available(), line.options());
    }
}
