package vn.hutech.fooddelivery.restaurant.dto;

import jakarta.validation.constraints.*;

/**
 * Request body cho POST /api/merchant/restaurants - Tạo chi nhánh mới.
 * lat/lng bắt buộc để hiện trên bản đồ và tính khoảng cách với khách.
 */
public record CreateRestaurantRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 255) String address,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng,
        @Size(max = 20) String phone,
        @Size(max = 500) String logoUrl,
        @Size(max = 500) String coverUrl,
        @Min(0) long minOrderAmount,
        @Min(1) int prepTimeMinutes
) {}
