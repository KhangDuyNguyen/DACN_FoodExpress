package vn.hutech.fooddelivery.ai;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller tích hợp các tính năng AI.
 * Người phụ trách: Sầm Xuân Điệp.
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI Service", description = "Tích hợp service AI (Gợi ý món, Chatbot, Dự báo doanh thu)")
public class AiController {

    private final AiServiceClient aiServiceClient;

    public AiController(AiServiceClient aiServiceClient) {
        this.aiServiceClient = aiServiceClient;
    }

    @GetMapping("/health")
    @Operation(summary = "Kiểm tra kết nối từ Spring Boot sang AI Service (Python FastAPI)")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        Map<String, Object> result = aiServiceClient.checkHealth();
        return ResponseEntity.ok(result);
    }
}
