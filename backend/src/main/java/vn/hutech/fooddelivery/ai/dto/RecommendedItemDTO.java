package vn.hutech.fooddelivery.ai.dto;

/**
 * Món ăn được gợi ý trả về cho Web và App khách.
 * Tương thích trường cơ bản với MenuItemDTO (id, name, description, price, imageUrl, isAvailable)
 * và bổ sung ngữ cảnh nhà hàng + điểm/lý do gợi ý.
 */
public record RecommendedItemDTO(
        long id,
        String name,
        String description,
        long price,
        String imageUrl,
        boolean isAvailable,
        long restaurantId,
        String restaurantName,
        String restaurantAddress,
        String restaurantOpenStatus,
        double restaurantAvgRating,
        double score,
        String reason
) {
}

