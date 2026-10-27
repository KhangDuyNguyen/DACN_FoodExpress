package vn.hutech.fooddelivery.restaurant.dto;

import java.util.List;

/**
 * Một món ăn trong menu.
 * Bao gồm thông tin cơ bản và danh sách nhóm tùy chọn (nếu có).
 */
public record MenuItemDTO(
        long id,
        String name,
        String description,
        long price,
        String imageUrl,
        boolean isAvailable,
        int sortOrder,
        List<OptionGroupDTO> optionGroups
) {}
