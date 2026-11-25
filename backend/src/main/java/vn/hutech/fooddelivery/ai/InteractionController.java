package vn.hutech.fooddelivery.ai;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.ai.dto.InteractionResponse;
import vn.hutech.fooddelivery.ai.dto.RecordInteractionRequest;
import vn.hutech.fooddelivery.security.AuthUser;

/**
 * Controller ghi nhận hành vi tương tác món ăn phục vụ hệ thống AI gợi ý.
 * Yêu cầu người dùng đã đăng nhập (userId luôn lấy từ JWT).
 */
@RestController
@RequestMapping("/api/interactions")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User Interactions", description = "Ghi nhận hành vi xem, thêm giỏ, đặt món cho AI gợi ý")
public class InteractionController {

    private final InteractionService interactionService;

    public InteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @PostMapping
    @Operation(
            summary = "Ghi nhận hành vi tương tác với món ăn",
            description = "Hỗ trợ VIEW, ADD_TO_CART, ORDER, FAVORITE. Hỗ trợ eventId / orderId để chống trùng lặp khi retry."
    )
    public ResponseEntity<InteractionResponse> recordInteraction(
            @AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody RecordInteractionRequest req
    ) {
        InteractionResponse response = interactionService.recordInteraction(user.id(), req);
        HttpStatus status = response.deduplicated() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }
}

