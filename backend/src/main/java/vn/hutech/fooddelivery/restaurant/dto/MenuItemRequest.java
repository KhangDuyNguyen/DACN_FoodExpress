package vn.hutech.fooddelivery.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body cho POST/PUT /api/merchant/restaurants/{id}/items - Quản lý món ăn.
 */
public record MenuItemRequest(
        @NotNull Long categoryId,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 1000) String description,
        @NotNull @Min(0) Long price,
        @Size(max = 500) String imageUrl,
        @JsonProperty("isAvailable") boolean isAvailable,
        @Min(0) int sortOrder
) {}
