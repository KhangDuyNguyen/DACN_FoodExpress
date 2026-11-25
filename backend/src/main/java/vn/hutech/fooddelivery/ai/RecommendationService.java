package vn.hutech.fooddelivery.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.ai.RecommendationRepository.ActiveMenuItemRecord;
import vn.hutech.fooddelivery.ai.RecommendationRepository.PopularItemStats;
import vn.hutech.fooddelivery.ai.dto.AiEngineDtos.AiRecommendItem;
import vn.hutech.fooddelivery.ai.dto.AiEngineDtos.AiRecommendRequest;
import vn.hutech.fooddelivery.ai.dto.AiEngineDtos.AiRecommendResponse;
import vn.hutech.fooddelivery.ai.dto.AiEngineDtos.OrderBasketPayload;
import vn.hutech.fooddelivery.ai.dto.AiEngineDtos.SnapshotPayload;
import vn.hutech.fooddelivery.ai.dto.AiEngineDtos.UserHistorySignalPayload;
import vn.hutech.fooddelivery.ai.dto.AiEngineDtos.UserInteractionPayload;
import vn.hutech.fooddelivery.ai.dto.RecommendationResponse;
import vn.hutech.fooddelivery.ai.dto.RecommendedItemDTO;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Điều phối quy trình gợi ý món ăn:
 * 1. Chuẩn bị và cache snapshot dữ liệu (TTL 5 phút) từ MySQL qua RecommendationRepository.
 * 2. Lấy lịch sử tương tác + giỏ hàng hiện tại của người dùng (nếu đã đăng nhập).
 * 3. Gọi Python FastAPI AI Service (có tái sử dụng cache ma trận và đồng bộ lại khi cần).
 * 4. Luôn lọc lại món hợp lệ từ DB (còn bán, quán đang mở) và kích hoạt fallback POPULARITY khi cần.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final Duration SNAPSHOT_TTL = Duration.ofMinutes(5);
    private static final int MAX_BASKETS_FOR_SNAPSHOT = 5000;
    private static final int POPULAR_POOL_SIZE = 50;

    private final RecommendationRepository repository;
    private final AiServiceClient aiServiceClient;

    private final Object snapshotLock = new Object();
    private volatile CachedSnapshot cachedSnapshot;

    public RecommendationService(RecommendationRepository repository, AiServiceClient aiServiceClient) {
        this.repository = repository;
        this.aiServiceClient = aiServiceClient;
    }

    @Transactional(readOnly = true)
    public RecommendationResponse getRecommendations(Long userId, int limit, Long restaurantId) {
        int safeLimit = Math.max(1, Math.min(limit, 24));

        // 1. Lấy dữ liệu món đang hoạt động và snapshot cache
        CachedSnapshot snapshot = getOrRefreshSnapshot();
        Map<Long, ActiveMenuItemRecord> activeMap = snapshot.activeItemMap();

        if (activeMap.isEmpty()) {
            return new RecommendationResponse(
                    "POPULARITY",
                    false,
                    "Hiện chưa có món ăn đang mở bán để gợi ý",
                    List.of()
            );
        }

        // 2. Lấy lịch sử hành vi và giỏ hàng của người dùng (nếu đã đăng nhập)
        List<UserHistorySignalPayload> userSignals = userId != null
                ? repository.findUserHistorySignals(userId)
                : List.of();
        List<Long> excludeItemIds = userId != null
                ? repository.findUserCartItemIds(userId)
                : List.of();

        // 3. Gọi AI Service (kèm snapshotVersion để tránh tính lại ma trận mỗi request)
        AiRecommendRequest request = new AiRecommendRequest(
                userId,
                safeLimit,
                restaurantId,
                excludeItemIds,
                true,
                userSignals,
                snapshot.payload().snapshotVersion(),
                null
        );

        Optional<AiRecommendResponse> aiRespOpt = aiServiceClient.requestRecommendations(request);
        if (aiRespOpt.isPresent() && aiRespOpt.get().needsSnapshot()) {
            // AI Service mới khởi động lại hoặc chưa có cache -> đẩy snapshot rồi gọi lại 1 lần
            if (aiServiceClient.syncSnapshot(snapshot.payload())) {
                aiRespOpt = aiServiceClient.requestRecommendations(request);
            } else {
                aiRespOpt = Optional.empty();
            }
        }

        // 4. Ánh xạ kết quả từ AI Service (kiểm tra lại điều kiện còn bán / mở cửa)
        if (aiRespOpt.isPresent()) {
            AiRecommendResponse aiResp = aiRespOpt.get();
            List<RecommendedItemDTO> mapped = mapAiResults(aiResp.recommendations(), activeMap, restaurantId, safeLimit);
            if (!mapped.isEmpty()) {
                String strategy = "PERSONALIZED".equalsIgnoreCase(aiResp.strategy()) ? "PERSONALIZED" : "POPULARITY";
                String message = aiResp.message() != null && !aiResp.message().isBlank()
                        ? aiResp.message()
                        : ("PERSONALIZED".equals(strategy)
                                ? "Gợi ý cá nhân hóa dành riêng cho bạn"
                                : "Món bán chạy được nhiều khách yêu thích");
                return new RecommendationResponse(strategy, false, message, mapped);
            }
        }

        // 5. Fallback an toàn tại backend khi AI Service tắt / lỗi / trả rỗng
        log.debug("Dùng fallback POPULARITY từ database cho userId={}, restaurantId={}", userId, restaurantId);
        List<RecommendedItemDTO> fallbackItems = buildBackendPopularityFallback(
                activeMap,
                snapshot.popularStats(),
                restaurantId,
                Set.copyOf(excludeItemIds),
                safeLimit
        );
        return new RecommendationResponse(
                "POPULARITY",
                true,
                fallbackItems.isEmpty()
                        ? "Hiện chưa có dữ liệu món bán chạy"
                        : "Món bán chạy nổi bật trên FoodExpress",
                fallbackItems
        );
    }

    private CachedSnapshot getOrRefreshSnapshot() {
        CachedSnapshot current = cachedSnapshot;
        Instant now = Instant.now();
        if (current != null && Duration.between(current.loadedAt(), now).compareTo(SNAPSHOT_TTL) < 0) {
            return current;
        }
        synchronized (snapshotLock) {
            current = cachedSnapshot;
            if (current != null && Duration.between(current.loadedAt(), now).compareTo(SNAPSHOT_TTL) < 0) {
                return current;
            }
            List<ActiveMenuItemRecord> activeItems = repository.findActiveAvailableItems();
            Map<Long, ActiveMenuItemRecord> activeMap = new LinkedHashMap<>();
            for (ActiveMenuItemRecord item : activeItems) {
                activeMap.put(item.id(), item);
            }
            List<PopularItemStats> popularStats = repository.findPopularActiveItems(null, POPULAR_POOL_SIZE);
            List<UserInteractionPayload> interactions = repository.findSnapshotInteractions();
            List<OrderBasketPayload> baskets = repository.findSnapshotOrderBaskets(MAX_BASKETS_FOR_SNAPSHOT);

            SnapshotPayload payload = new SnapshotPayload(
                    UUID.randomUUID().toString(),
                    repository.toActivePayloads(activeItems),
                    interactions,
                    baskets,
                    repository.toPopularPayloads(popularStats)
            );
            CachedSnapshot fresh = new CachedSnapshot(payload, Map.copyOf(activeMap), popularStats, now);
            cachedSnapshot = fresh;
            return fresh;
        }
    }

    private List<RecommendedItemDTO> mapAiResults(
            List<AiRecommendItem> aiItems,
            Map<Long, ActiveMenuItemRecord> activeMap,
            Long restaurantId,
            int limit
    ) {
        if (aiItems == null || aiItems.isEmpty()) {
            return List.of();
        }
        List<RecommendedItemDTO> results = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (AiRecommendItem aiItem : aiItems) {
            if (!seen.add(aiItem.itemId())) {
                continue;
            }
            ActiveMenuItemRecord record = activeMap.get(aiItem.itemId());
            if (record == null || !record.isAvailable()) {
                continue;
            }
            if (restaurantId != null && record.restaurantId() != restaurantId) {
                continue;
            }
            results.add(toRecommendedDto(record, aiItem.score(), aiItem.reason()));
            if (results.size() >= limit) {
                break;
            }
        }
        return results;
    }

    private List<RecommendedItemDTO> buildBackendPopularityFallback(
            Map<Long, ActiveMenuItemRecord> activeMap,
            List<PopularItemStats> popularStats,
            Long restaurantId,
            Set<Long> excludeIds,
            int limit
    ) {
        List<PopularItemStats> stats = restaurantId != null
                ? repository.findPopularActiveItems(restaurantId, limit + excludeIds.size())
                : popularStats;

        List<RecommendedItemDTO> results = new ArrayList<>();
        Set<Long> added = new HashSet<>();
        long maxQty = stats.stream().mapToLong(PopularItemStats::quantitySold).max().orElse(1L);
        if (maxQty <= 0) {
            maxQty = 1L;
        }

        for (PopularItemStats stat : stats) {
            if (excludeIds.contains(stat.itemId()) || added.contains(stat.itemId())) {
                continue;
            }
            ActiveMenuItemRecord record = activeMap.get(stat.itemId());
            if (record == null || !record.isAvailable()) {
                continue;
            }
            if (restaurantId != null && record.restaurantId() != restaurantId) {
                continue;
            }
            double score = Math.round(Math.max(0.1, (double) stat.quantitySold() / maxQty) * 10000.0) / 10000.0;
            results.add(toRecommendedDto(record, score, "Món bán chạy được nhiều khách yêu thích"));
            added.add(stat.itemId());
            if (results.size() >= limit) {
                return results;
            }
        }

        // Nếu chưa có đơn hàng nào (DB mới), trả về các món đang mở bán thuộc quán rating cao
        if (results.size() < limit) {
            for (ActiveMenuItemRecord record : activeMap.values()) {
                if (excludeIds.contains(record.id()) || added.contains(record.id()) || !record.isAvailable()) {
                    continue;
                }
                if (restaurantId != null && record.restaurantId() != restaurantId) {
                    continue;
                }
                results.add(toRecommendedDto(record, 0.5, "Món nổi bật tại quán"));
                added.add(record.id());
                if (results.size() >= limit) {
                    break;
                }
            }
        }
        return results;
    }

    private static RecommendedItemDTO toRecommendedDto(ActiveMenuItemRecord r, double score, String reason) {
        return new RecommendedItemDTO(
                r.id(),
                r.name(),
                r.description(),
                r.price(),
                r.imageUrl(),
                r.isAvailable(),
                r.restaurantId(),
                r.restaurantName(),
                r.restaurantAddress(),
                r.restaurantOpenStatus(),
                r.restaurantAvgRating(),
                score,
                reason
        );
    }

    private record CachedSnapshot(
            SnapshotPayload payload,
            Map<Long, ActiveMenuItemRecord> activeItemMap,
            List<PopularItemStats> popularStats,
            Instant loadedAt
    ) {
    }
}

