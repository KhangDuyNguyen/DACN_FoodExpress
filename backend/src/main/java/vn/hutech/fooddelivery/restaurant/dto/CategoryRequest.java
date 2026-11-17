package vn.hutech.fooddelivery.restaurant.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body cho POST/PUT /api/merchant/restaurants/{id}/categories - Quản lý nhóm món.
 */
public record CategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @Min(0) int sortOrder
) {}
