package vn.hutech.fooddelivery.restaurant.dto;

import java.util.List;

/**
 * Toàn bộ menu của một nhà hàng.
 * Trả về danh sách nhóm món, mỗi nhóm chứa các món, mỗi món chứa tùy chọn.
 */
public record MenuDTO(
        long restaurantId,
        List<MenuCategoryDTO> categories
) {}
