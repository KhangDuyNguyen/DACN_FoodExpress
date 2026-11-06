package vn.hutech.fooddelivery.restaurant.dto;

import java.util.List;

/**
 * Nhóm món ăn (category) trong menu của một quán.
 * Ví dụ: "Món chính", "Đồ uống", "Tráng miệng".
 */
public record MenuCategoryDTO(
        long id,
        String name,
        int sortOrder,
        List<MenuItemDTO> items
) {}
