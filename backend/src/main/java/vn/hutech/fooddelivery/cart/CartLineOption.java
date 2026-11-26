package vn.hutech.fooddelivery.cart;

import java.math.BigDecimal;

/**
 * 1 tuy chon khach da chon cho 1 dong trong gio (vd "Lớn" +10.000d).
 * Ten, gia, trang thai la gia tri HIEN TAI trong bang options (gio khong luu gia).
 * isAvailable dat ten giong menu cua Duong (OptionDTO).
 */
public record CartLineOption(long id, String name, BigDecimal extraPrice, boolean isAvailable) {
}
