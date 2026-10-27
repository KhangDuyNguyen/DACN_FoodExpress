package vn.hutech.fooddelivery.restaurant.dto;

/**
 * Giờ mở cửa của nhà hàng theo ngày.
 * dayOfWeek: 1=Thứ 2 ... 7=Chủ nhật
 */
public record RestaurantHoursDTO(
        int dayOfWeek,
        String openTime,
        String closeTime
) {}
