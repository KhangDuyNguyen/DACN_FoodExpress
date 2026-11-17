package vn.hutech.fooddelivery.review;

import java.time.LocalDateTime;

/** Phản hồi của chủ quán */
public record ReviewReplyResponse(
        long id,
        String merchantName,
        String content,
        LocalDateTime createdAt
) {}
