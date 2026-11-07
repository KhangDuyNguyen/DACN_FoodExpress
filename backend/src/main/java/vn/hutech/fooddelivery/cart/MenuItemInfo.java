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

    /**
     * Quan phai da duoc duyet va dang mo cua (OPEN) moi dat mon duoc.
     * Tu tuan 4 admin duyet quan thanh APPROVED (PR #16 cua Diep); ACTIVE la trang thai cu, van coi la da duyet.
     */
    public boolean restaurantCanOrder() {
        boolean approved = "APPROVED".equals(restaurantStatus) || "ACTIVE".equals(restaurantStatus);
        return approved && "OPEN".equals(restaurantOpenStatus);
    }
}
