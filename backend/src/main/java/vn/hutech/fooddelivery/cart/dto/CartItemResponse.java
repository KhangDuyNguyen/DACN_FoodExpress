package vn.hutech.fooddelivery.cart.dto;

import vn.hutech.fooddelivery.cart.CartLine;

import java.math.BigDecimal;

/** 1 dong trong gio tra ve client. isAvailable dat ten giong menu cua Duong (MenuItemDTO). */
public record CartItemResponse(
        long id,
        long menuItemId,
        String name,
        String imageUrl,
        BigDecimal unitPrice,
        int quantity,
        String note,
        BigDecimal lineTotal,
        boolean isAvailable
) {

    public static CartItemResponse from(CartLine line) {
        return new CartItemResponse(line.cartItemId(), line.menuItemId(), line.name(), line.imageUrl(),
                line.unitPrice(), line.quantity(), line.note(), line.lineTotal(), line.available());
    }
}
