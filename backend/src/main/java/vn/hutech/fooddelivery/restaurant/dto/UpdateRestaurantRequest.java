package vn.hutech.fooddelivery.restaurant.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body cho PUT /api/merchant/restaurants/{id} - Cập nhật thông tin quán.
 */
public record UpdateRestaurantRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 200) String address,
        @Size(max = 20) String phone,
        @Size(max = 500) String logoUrl,
        @Size(max = 500) String coverUrl,
        @Min(0) Long minOrderAmount,
        @Min(0) Integer prepTimeMinutes
) {}
