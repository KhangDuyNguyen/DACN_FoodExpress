package vn.hutech.fooddelivery.restaurant.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body cho POST/PUT /api/merchant/option-groups/{groupId}/options - Quản lý tùy chọn.
 */
public record OptionRequest(
        @NotBlank @Size(max = 100) String name,
        @Min(0) long extraPrice,
        boolean isAvailable,
        @Min(0) int sortOrder
) {}
