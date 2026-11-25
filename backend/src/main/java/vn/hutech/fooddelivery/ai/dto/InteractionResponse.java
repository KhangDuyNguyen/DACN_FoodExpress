package vn.hutech.fooddelivery.ai.dto;

import java.time.LocalDateTime;

/**
 * Kết quả trả về sau khi ghi nhận hành vi tương tác với món ăn.
 */
public record InteractionResponse(
        long id,
        long userId,
        long menuItemId,
        long restaurantId,
        String type,
        int quantity,
        Long orderId,
        String eventId,
        boolean deduplicated,
        LocalDateTime createdAt
) {
}

