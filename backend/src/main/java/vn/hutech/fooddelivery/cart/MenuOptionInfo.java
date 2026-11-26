package vn.hutech.fooddelivery.cart;

/**
 * 1 tuy chon cua mon (vd "Lớn") kem luat cua nhom chua no (vd nhom "Size" bat buoc, chon toi da 1).
 * Doc (SELECT) tu bang options, option_groups cua module menu (Duong), khong ghi vao cac bang do.
 */
public record MenuOptionInfo(
        long id,
        String name,
        boolean available,
        long groupId,
        String groupName,
        boolean groupRequired,
        int minSelect,
        int maxSelect
) {
}
