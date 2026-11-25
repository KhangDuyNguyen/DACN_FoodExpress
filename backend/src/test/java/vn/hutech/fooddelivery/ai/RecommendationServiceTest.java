package vn.hutech.fooddelivery.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.hutech.fooddelivery.ai.RecommendationRepository.ActiveMenuItemRecord;
import vn.hutech.fooddelivery.ai.dto.AiEngineDtos.OrderBasketPayload;
import vn.hutech.fooddelivery.ai.dto.AiEngineDtos.UserHistorySignalPayload;
import vn.hutech.fooddelivery.ai.dto.AiEngineDtos.UserInteractionPayload;
import vn.hutech.fooddelivery.ai.dto.RecommendationResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationServiceTest {

    private FakeRecommendationRepository repository;
    private HttpServer mockAiServer;

    private static class FakeRecommendationRepository extends RecommendationRepository {
        List<ActiveMenuItemRecord> activeItems = new ArrayList<>();
        List<PopularItemStats> popularItems = new ArrayList<>();
        Map<Long, List<UserHistorySignalPayload>> userSignals = new HashMap<>();
        Map<Long, List<Long>> cartItems = new HashMap<>();

        FakeRecommendationRepository() {
            super(null);
        }

        @Override
        public List<ActiveMenuItemRecord> findActiveAvailableItems() {
            return activeItems;
        }

        @Override
        public List<PopularItemStats> findPopularActiveItems(Long restaurantId, int limit) {
            return popularItems.stream()
                    .filter(p -> restaurantId == null || activeItems.stream()
                            .anyMatch(a -> a.id() == p.itemId() && a.restaurantId() == restaurantId))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<UserHistorySignalPayload> findUserHistorySignals(long userId) {
            return userSignals.getOrDefault(userId, List.of());
        }

        @Override
        public List<Long> findUserCartItemIds(long userId) {
            return cartItems.getOrDefault(userId, List.of());
        }

        @Override
        public List<UserInteractionPayload> findSnapshotInteractions() {
            return List.of(new UserInteractionPayload(1L, 1L, 5.0, "ORDER"));
        }

        @Override
        public List<OrderBasketPayload> findSnapshotOrderBaskets(int maxOrders) {
            return List.of(new OrderBasketPayload(10L, 1L, List.of(1L, 2L), true));
        }
    }

    @BeforeEach
    void setUp() {
        repository = new FakeRecommendationRepository();
        repository.activeItems = List.of(
                new ActiveMenuItemRecord(1L, "Phở bò tái", "Ngon", 55000, null, true, 1L, "Phở 24", "Q1", "OPEN", 4.5, List.of("Phở")),
                new ActiveMenuItemRecord(2L, "Quẩy giòn", "Giòn", 10000, null, true, 1L, "Phở 24", "Q1", "OPEN", 4.5, List.of("Phở")),
                new ActiveMenuItemRecord(3L, "Gà rán", "Giòn tan", 45000, null, true, 2L, "KFC", "Q3", "OPEN", 4.2, List.of("Gà rán"))
        );
        repository.popularItems = List.of(
                new RecommendationRepository.PopularItemStats(3L, 20L, 50L),
                new RecommendationRepository.PopularItemStats(1L, 10L, 30L)
        );
        repository.userSignals.put(5L, List.of(new UserHistorySignalPayload(1L, 5.0, "ORDER")));
    }

    @AfterEach
    void tearDown() {
        if (mockAiServer != null) {
            mockAiServer.stop(0);
        }
    }

    @Test
    @DisplayName("AI Service down -> tự động fallback về POPULARITY từ database, không lỗi")
    void getRecommendations_whenAiServiceDown_fallsBackToPopularity() {
        AiServiceClient downClient = new AiServiceClient("http://localhost:59998");
        RecommendationService service = new RecommendationService(repository, downClient);

        RecommendationResponse resp = service.getRecommendations(5L, 5, null);

        assertEquals("POPULARITY", resp.strategy());
        assertTrue(resp.fallbackUsed());
        assertFalse(resp.items().isEmpty());
        assertEquals(3L, resp.items().get(0).id());
    }

    @Test
    @DisplayName("AI Service báo needsSnapshot -> tự động sync rồi trả về PERSONALIZED, đồng thời lọc bỏ món không khả dụng")
    void getRecommendations_syncsSnapshotAndFiltersUnavailableItems() throws IOException {
        AtomicInteger recommendCalls = new AtomicInteger();
        AtomicInteger syncCalls = new AtomicInteger();

        mockAiServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockAiServer.createContext("/recommendations/sync", exchange -> {
            syncCalls.incrementAndGet();
            byte[] body = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        mockAiServer.createContext("/recommendations", exchange -> {
            int call = recommendCalls.incrementAndGet();
            String json = call == 1
                    ? "{\"recommendations\":[],\"strategy\":\"POPULARITY\",\"needsSnapshot\":true}"
                    // AI trả về item 999 (không tồn tại/ngừng bán) và item 2 (đang mở bán)
                    : "{\"recommendations\":[{\"itemId\":999,\"score\":0.99,\"reason\":\"Invalid\"},{\"itemId\":2,\"score\":0.91,\"reason\":\"Thường được đặt cùng với Phở bò tái\"}],\"strategy\":\"PERSONALIZED\",\"needsSnapshot\":false}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        mockAiServer.start();

        int port = mockAiServer.getAddress().getPort();
        AiServiceClient client = new AiServiceClient("http://127.0.0.1:" + port);
        RecommendationService service = new RecommendationService(repository, client);

        RecommendationResponse resp = service.getRecommendations(5L, 5, null);

        assertEquals(1, syncCalls.get());
        assertEquals(2, recommendCalls.get());
        assertEquals("PERSONALIZED", resp.strategy());
        assertFalse(resp.fallbackUsed());
        assertEquals(1, resp.items().size());
        assertEquals(2L, resp.items().get(0).id());
        assertEquals("Phở 24", resp.items().get(0).restaurantName());
    }

    @Test
    @DisplayName("Guest (userId=null) nhận POPULARITY và lọc được theo restaurantId")
    void getRecommendations_guestWithRestaurantFilter() {
        AiServiceClient downClient = new AiServiceClient("http://localhost:59998");
        RecommendationService service = new RecommendationService(repository, downClient);

        RecommendationResponse resp = service.getRecommendations(null, 5, 1L);

        assertEquals("POPULARITY", resp.strategy());
        assertTrue(resp.items().stream().allMatch(it -> it.restaurantId() == 1L));
    }
}
