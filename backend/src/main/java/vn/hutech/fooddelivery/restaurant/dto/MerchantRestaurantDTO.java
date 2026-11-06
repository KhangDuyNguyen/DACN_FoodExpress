package vn.hutech.fooddelivery.restaurant.dto;

/**
 * Một quán/chi nhánh của chủ quán đang đăng nhập (dùng cho màn hình chọn chi nhánh).
 * status: PENDING / ACTIVE / HIDDEN / SUSPENDED (chủ quán thấy cả quán chưa duyệt).
 */
public record MerchantRestaurantDTO(
        long id,
        String name,
        String address,
        String openStatus,
        String status
) {}
