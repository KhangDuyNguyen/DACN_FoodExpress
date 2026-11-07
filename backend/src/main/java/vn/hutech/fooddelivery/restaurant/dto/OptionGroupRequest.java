package vn.hutech.fooddelivery.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body cho POST/PUT /api/merchant/items/{id}/option-groups - Quản lý nhóm tùy chọn.
 */
public record OptionGroupRequest(
        @NotBlank @Size(max = 100) String name,
        @JsonProperty("isRequired") boolean isRequired,
        @Min(0) int minSelect,
        @Min(1) int maxSelect,
        @Min(0) int sortOrder
) {}
