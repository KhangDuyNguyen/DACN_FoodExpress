package vn.hutech.fooddelivery.cart;

import java.math.BigDecimal;

/**
 * Mot dong trong gio, da ghep voi bang menu_items de lay gia va trang thai HIEN TAI cua mon.
 * Gio khong luu gia: moi lan xem gio hay dat hang deu doc lai gia tu database.
 * available = mon con ban VA nhom mon dang bat.
 */
public record CartLine(
        long cartItemId,
        long menuItemId,
        String name,
        String imageUrl,
        BigDecimal unitPrice,
        int quantity,
        String note,
        boolean available
) {

    /** Thanh tien cua dong = don gia x so luong. */
    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
