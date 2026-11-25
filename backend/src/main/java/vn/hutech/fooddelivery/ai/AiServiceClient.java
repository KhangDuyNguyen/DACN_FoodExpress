package vn.hutech.fooddelivery.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import vn.hutech.fooddelivery.ai.dto.AiEngineDtos.AiRecommendRequest;
import vn.hutech.fooddelivery.ai.dto.AiEngineDtos.AiRecommendResponse;
import vn.hutech.fooddelivery.ai.dto.AiEngineDtos.SnapshotPayload;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Client giao tiếp giữa Spring Boot backend và AI service (Python FastAPI).
 * Luôn có timeout (3s) và bắt lỗi an toàn để không làm gián đoạn ứng dụng khi AI service tắt.
 * Người phụ trách: Sầm Xuân Điệp.
 */
@Service
public class AiServiceClient {
    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    private final RestClient restClient;
    private final String aiServiceUrl;

    public AiServiceClient(@Value("${app.ai-service-url:http://localhost:8000}") String aiServiceUrl) {
        this.aiServiceUrl = aiServiceUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .baseUrl(aiServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Gọi endpoint GET /health của AI service để kiểm tra tình trạng kết nối.
     *
     * @return Map chứa trạng thái UP/DOWN, url và phản hồi từ AI service.
     */
    public Map<String, Object> checkHealth() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(Map.class);
            return Map.of(
                    "status", "UP",
                    "aiServiceUrl", aiServiceUrl,
                    "response", response != null ? response : Map.of()
            );
        } catch (Exception e) {
            log.warn("Khong the ket noi den AI service tai {}: {}", aiServiceUrl, e.getMessage());
            return Map.of(
                    "status", "DOWN",
                    "aiServiceUrl", aiServiceUrl,
                    "error", e.getMessage()
            );
        }
    }

    /**
     * Đồng bộ snapshot dữ liệu tương tác & đơn hàng sang AI Service để dựng/cache ma trận.
     */
    public boolean syncSnapshot(SnapshotPayload snapshot) {
        try {
            restClient.post()
                    .uri("/recommendations/sync")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(snapshot)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("Không thể đồng bộ snapshot tới AI service ({}): {}", aiServiceUrl, e.getMessage());
            return false;
        }
    }

    /**
     * Gọi POST /recommendations của AI Service để tính gợi ý món.
     * Trả về Optional.empty() nếu AI service tắt, lỗi mạng hoặc timeout.
     */
    public Optional<AiRecommendResponse> requestRecommendations(AiRecommendRequest request) {
        try {
            AiRecommendResponse response = restClient.post()
                    .uri("/recommendations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiRecommendResponse.class);
            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.warn("AI service không phản hồi khi gợi ý món ({}): {}", aiServiceUrl, e.getMessage());
            return Optional.empty();
        }
    }
}
