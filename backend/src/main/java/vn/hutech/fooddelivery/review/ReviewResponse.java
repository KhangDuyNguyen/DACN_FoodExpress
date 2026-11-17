package vn.hutech.fooddelivery.review;

import java.time.LocalDateTime;

/** Một đánh giá hiển thị ra ngoài */
public record ReviewResponse(
        long id,
        long orderId,
        String customerName,
        int restaurantRating,
        Integer driverRating,
        String comment,
        LocalDateTime createdAt,
        ReviewReplyResponse reply   // null nếu chưa có phản hồi
) {}
