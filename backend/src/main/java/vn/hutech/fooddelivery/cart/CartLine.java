package vn.hutech.fooddelivery.cart;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mot dong trong gio, da ghep voi bang menu_items va options de lay gia va trang thai HIEN TAI.
 * Gio khong luu gia: moi lan xem gio hay dat hang deu doc lai gia tu database.
 *
 * @param basePrice     gia cua mon (chua gom tuy chon)
 * @param itemAvailable mon con ban VA nhom mon dang bat
 * @param options       cac tuy chon da chon, theo thu tu nhom roi thu tu tuy chon nhu tren menu
 */
public record CartLine(
        long cartItemId,
        long menuItemId,
        String name,
        String imageUrl,
        BigDecimal basePrice,
        int quantity,
        String note,
        boolean itemAvailable,
        List<CartLineOption> options
) {

    /** Don gia = gia mon + gia cac tuy chon da chon. */
    public BigDecimal unitPrice() {
        BigDecimal price = basePrice;
        for (CartLineOption option : options) {
            price = price.add(option.extraPrice());
        }
        return price;
    }

    /** Thanh tien cua dong = don gia x so luong. */
    public BigDecimal lineTotal() {
        return unitPrice().multiply(BigDecimal.valueOf(quantity));
    }

    /** Dat duoc khi mon con ban VA moi tuy chon da chon cung con ban. */
    public boolean available() {
        return itemAvailable && options.stream().allMatch(CartLineOption::isAvailable);
    }

    /** id cac tuy chon da chon. */
    public List<Long> optionIds() {
        return options.stream().map(CartLineOption::id).toList();
    }
}
