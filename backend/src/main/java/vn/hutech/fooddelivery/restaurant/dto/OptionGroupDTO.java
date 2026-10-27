package vn.hutech.fooddelivery.restaurant.dto;

import java.util.List;

/**
 * Nhóm tùy chọn của một món ăn.
 * Ví dụ: "Chọn size", "Thêm topping", "Mức đường".
 * isRequired: bắt buộc chọn hay không.
 * minSelect/maxSelect: giới hạn số lựa chọn.
 */
public record OptionGroupDTO(
        long id,
        String name,
        boolean isRequired,
        int minSelect,
        int maxSelect,
        int sortOrder,
        List<OptionDTO> options
) {}
