package vn.hutech.fooddelivery.restaurant.dto;

import java.util.List;

/**
 * DTO chi tiết nhà hàng, dùng cho trang chi tiết quán.
 * Bao gồm đầy đủ thông tin quán + danh sách loại món + giờ mở cửa.
 */
public record RestaurantDetailDTO(
        long id,
        String name,
        String description,
        String phone,
        String address,
        double lat,
        double lng,
        String logoUrl,
        String coverUrl,
        double avgRating,
        int ratingCount,
        long minOrderAmount,
        int prepTimeMinutes,
        String openStatus,
        List<CuisineDTO> cuisines,
        List<RestaurantHoursDTO> hours
) {}
