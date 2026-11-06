package vn.hutech.fooddelivery.cart;

/**
 * Thong tin mot mon kem trang thai nhom mon va quan, dung de kiem tra truoc khi them vao gio.
 * Doc (SELECT) tu bang cua module menu/restaurant, khong ghi vao cac bang do.
 */
public record MenuItemInfo(
        long id,
        String name,
        boolean available,
        boolean categoryActive,
        long restaurantId,
        String restaurantStatus,
        String restaurantOpenStatus
) {

    /** Quan phai dang hoat dong (ACTIVE) va dang mo cua (OPEN) moi dat mon duoc. */
    public boolean restaurantCanOrder() {
        return "ACTIVE".equals(restaurantStatus) && "OPEN".equals(restaurantOpenStatus);
    }
}
