package vn.hutech.fooddelivery.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.hutech.fooddelivery.ai.dto.InteractionResponse;
import vn.hutech.fooddelivery.ai.dto.RecommendationResponse;
import vn.hutech.fooddelivery.ai.dto.RecommendedItemDTO;
import vn.hutech.fooddelivery.ai.dto.RecordInteractionRequest;
import vn.hutech.fooddelivery.common.BadRequestException;
import vn.hutech.fooddelivery.common.GlobalExceptionHandler;
import vn.hutech.fooddelivery.security.AuthUser;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiAndInteractionControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InteractionService fakeInteractionService = new InteractionService(null) {
            @Override
            public InteractionResponse recordInteraction(long userId, RecordInteractionRequest req) {
                if ("BAD".equalsIgnoreCase(req.type())) {
                    throw new BadRequestException("Loại hành vi không hợp lệ");
                }
                boolean dedup = "dup-1".equals(req.eventId());
                return new InteractionResponse(
                        101L, userId, req.menuItemId(), 1L, req.type().toUpperCase(),
                        req.quantity() != null ? req.quantity() : 1,
                        req.orderId(), req.eventId(), dedup, LocalDateTime.now()
                );
            }
        };

        RecommendationService fakeRecService = new RecommendationService(null, null) {
            @Override
            public RecommendationResponse getRecommendations(Long userId, int limit, Long restaurantId) {
                if (userId == null) {
                    return new RecommendationResponse(
                            "POPULARITY", false, "Món bán chạy",
                            List.of(new RecommendedItemDTO(
                                    1L, "Phở bò tái", "Ngon", 55000, null, true,
                                    1L, "Phở 24", "Q1", "OPEN", 4.5, 0.9, "Món bán chạy"
                            ))
                    );
                }
                return new RecommendationResponse(
                        "PERSONALIZED", false, "Gợi ý cá nhân hóa",
                        List.of(new RecommendedItemDTO(
                                2L, "Quẩy giòn", "Giòn", 10000, null, true,
                                1L, "Phở 24", "Q1", "OPEN", 4.5, 0.95, "Thường được đặt cùng với Phở bò tái"
                        ))
                );
            }
        };

        InteractionController interactionController = new InteractionController(fakeInteractionService);
        AiController aiController = new AiController(new AiServiceClient("http://localhost:59999"), fakeRecService);

        mockMvc = MockMvcBuilders.standaloneSetup(interactionController, aiController)
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/interactions lấy userId từ JWT (bỏ qua userId giả mạo nếu có) -> 201")
    void postInteraction_usesAuthenticatedUser() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(new AuthUser(42L, "CUSTOMER"), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(post("/api/interactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":999,\"menuItemId\":10,\"type\":\"ADD_TO_CART\",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.type").value("ADD_TO_CART"))
                .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    @DisplayName("POST /api/interactions retry trùng eventId -> 200 OK và deduplicated=true")
    void postInteraction_dedupReturns200() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(new AuthUser(42L, "CUSTOMER"), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(post("/api/interactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuItemId\":10,\"type\":\"VIEW\",\"eventId\":\"dup-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deduplicated").value(true));
    }

    @Test
    @DisplayName("GET /api/ai/recommendations hỗ trợ cả guest (POPULARITY) lẫn user đã đăng nhập (PERSONALIZED)")
    void getRecommendations_guestAndLoggedIn() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/ai/recommendations").param("limit", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("POPULARITY"))
                .andExpect(jsonPath("$.items[0].name").value("Phở bò tái"));

        var auth = new UsernamePasswordAuthenticationToken(new AuthUser(42L, "CUSTOMER"), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        mockMvc.perform(get("/api/ai/recommendations").param("limit", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("PERSONALIZED"))
                .andExpect(jsonPath("$.items[0].name").value("Quẩy giòn"));
    }

    @Test
    @DisplayName("POST /api/interactions loại hành vi không hợp lệ -> 400 Bad Request")
    void postInteraction_invalidTypeReturns400() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(new AuthUser(42L, "CUSTOMER"), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(post("/api/interactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuItemId\":10,\"type\":\"BAD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
