package vn.hutech.fooddelivery.restaurant.dto;

import jakarta.validation.constraints.*;

/**
 * Request body cho POST/PUT /api/merchant/items/{id}/option-groups - Quản lý nhóm tùy chọn.
 */
public record OptionGroupRequest(
        @NotBlank @Size(max = 100) String name,
        boolean isRequired,
        @Min(0) int minSelect,
        @Min(1) int maxSelect,
        @Min(0) int sortOrder
) {}
