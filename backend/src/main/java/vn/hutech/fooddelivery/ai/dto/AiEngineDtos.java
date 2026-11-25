package vn.hutech.fooddelivery.ai.dto;

import java.util.List;

/**
 * Các DTO nội bộ giao tiếp JSON giữa Spring Boot và Python FastAPI AI Service.
 */
public final class AiEngineDtos {

    private AiEngineDtos() {
    }

    public record ActiveItemPayload(
            long itemId,
            long restaurantId,
            String name,
            long price,
            List<String> cuisineNames,
            boolean isAvailable
    ) {
    }

    public record UserInteractionPayload(
            long userId,
            long itemId,
            double weight,
            String type
    ) {
    }

    public record OrderBasketPayload(
            Long orderId,
            Long userId,
            List<Long> itemIds,
            boolean isSimulated
    ) {
    }

    public record PopularItemPayload(
            long itemId,
            long orderCount,
            long quantitySold
    ) {
    }

    public record SnapshotPayload(
            String snapshotVersion,
            List<ActiveItemPayload> items,
            List<UserInteractionPayload> interactions,
            List<OrderBasketPayload> orderBaskets,
            List<PopularItemPayload> popularItems
    ) {
    }

    public record UserHistorySignalPayload(
            long itemId,
            double weight,
            String type
    ) {
    }

    public record AiRecommendRequest(
            Long userId,
            int limit,
            Long restaurantId,
            List<Long> excludeItemIds,
            boolean excludeOrdered,
            List<UserHistorySignalPayload> userInteractions,
            String snapshotVersion,
            SnapshotPayload snapshot
    ) {
    }

    public record AiRecommendItem(
            long itemId,
            double score,
            String reason,
            double cosineScore,
            double coOccurrenceScore
    ) {
    }

    public record AiRecommendResponse(
            List<AiRecommendItem> recommendations,
            String strategy,
            boolean needsSnapshot,
            String snapshotVersion,
            String message
    ) {
    }
}

