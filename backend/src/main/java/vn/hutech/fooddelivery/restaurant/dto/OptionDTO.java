package vn.hutech.fooddelivery.restaurant.dto;

/**
 * Một lựa chọn trong nhóm tùy chọn (option_groups).
 * Ví dụ: size "Nhỏ" giá thêm 0đ, "Vừa" +5000đ, "Lớn" +10000đ.
 */
public record OptionDTO(
        long id,
        String name,
        long extraPrice,
        boolean isAvailable,
        int sortOrder
) {}
