package vn.hutech.fooddelivery.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Client giao tiếp giữa Spring Boot backend và AI service (Python FastAPI).
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
        requestFactory.setReadTimeout(Duration.ofSeconds(3));

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
}
