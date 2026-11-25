package vn.hutech.fooddelivery.ai;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.ai.dto.RecommendationResponse;
import vn.hutech.fooddelivery.security.AuthUser;

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
    private final RecommendationService recommendationService;

    public AiController(AiServiceClient aiServiceClient, RecommendationService recommendationService) {
        this.aiServiceClient = aiServiceClient;
        this.recommendationService = recommendationService;
    }

    @GetMapping("/health")
    @Operation(summary = "Kiểm tra kết nối từ Spring Boot sang AI Service (Python FastAPI)")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        Map<String, Object> result = aiServiceClient.checkHealth();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/recommendations")
    @Operation(
            summary = "Lấy danh sách món ăn gợi ý cho bạn",
            description = "Nếu đã đăng nhập và có lịch sử tương tác -> PERSONALIZED; chưa đăng nhập hoặc người dùng mới -> POPULARITY (món bán chạy)."
    )
    public ResponseEntity<RecommendationResponse> getRecommendations(
            @AuthenticationPrincipal AuthUser user,
            @Parameter(description = "Số lượng món gợi ý tối đa (1 đến 24)")
            @RequestParam(defaultValue = "8") @Min(1) @Max(24) int limit,
            @Parameter(description = "Lọc gợi ý theo id quán (tùy chọn)")
            @RequestParam(required = false) Long restaurantId
    ) {
        Long userId = user != null ? user.id() : null;
        return ResponseEntity.ok(recommendationService.getRecommendations(userId, limit, restaurantId));
    }
}
