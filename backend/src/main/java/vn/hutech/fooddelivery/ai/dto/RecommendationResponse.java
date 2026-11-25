package vn.hutech.fooddelivery.ai.dto;

import java.util.List;

/**
 * Response cho GET /api/ai/recommendations.
 * strategy phân biệt rõ "PERSONALIZED" (cá nhân hóa từ AI) và "POPULARITY" (Cold Start / fallback món bán chạy).
 */
public record RecommendationResponse(
        String strategy,
        boolean fallbackUsed,
        String message,
        List<RecommendedItemDTO> items
) {
}

