package vn.hutech.fooddelivery.restaurant.dto;

/**
 * DTO đại diện cho loại món ăn (ví dụ: Món Việt, Món Hàn, Đồ Ăn Nhanh).
 * Dùng cho danh sách filter và hiển thị icon.
 */
public record CuisineDTO(
        int id,
        String name,
        String iconUrl
) {
}
