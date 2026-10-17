package vn.hutech.fooddelivery.restaurant.dto;

/**
 * DTO đại diện cho tóm tắt thông tin nhà hàng, dùng cho danh sách và tìm kiếm.
 * <p>
 * Lưu ý: cuisineId và cuisineName là món ĂN CHÍNH (primary cuisine) của nhà hàng,
 * được lấy từ bản ghi đầu tiên trong bảng restaurant_cuisines khi join.
 * Một nhà hàng có thể có nhiều loại món, nhưng DTO này chỉ hiển thị 1 loại đại diện.
 * </p>
 */
public record RestaurantSummaryDTO(
        long id,
        String name,
        int cuisineId,
        String cuisineName,
        String address,
        double lat,
        double lng,
        String logoUrl,
        double avgRating,
        int ratingCount,
        long minOrderAmount,
        int prepTimeMinutes,
        String openStatus
) {
}
