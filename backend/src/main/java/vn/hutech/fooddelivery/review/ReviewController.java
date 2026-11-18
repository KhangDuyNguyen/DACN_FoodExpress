package vn.hutech.fooddelivery.review;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.hutech.fooddelivery.common.PageResponse;
import vn.hutech.fooddelivery.security.AuthUser;

/**
 * Review APIs:
 *   POST   /api/orders/{id}/review                     → CUSTOMER tạo review (đơn DELIVERED)
 *   GET    /api/orders/{id}/review                     → CUSTOMER xem review của đơn mình
 *   GET    /api/restaurants/{id}/reviews               → Public: xem reviews của quán
 *   GET    /api/merchant/restaurants/{id}/reviews      → MERCHANT: xem reviews quán của mình
 *   POST   /api/merchant/reviews/{id}/reply            → MERCHANT trả lời review
 */
@RestController
@Tag(name = "Reviews", description = "Đánh giá quán và tài xế sau khi đơn hoàn thành")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    // ── CUSTOMER ──────────────────────────────────────────────────────────

    @PostMapping("/api/orders/{orderId}/review")
    @Operation(summary = "Tạo đánh giá cho đơn đã giao (chỉ CUSTOMER, đơn phải DELIVERED)")
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable long orderId,
            @Valid @RequestBody CreateReviewRequest req,
            @AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.createReview(orderId, user.id(), req));
    }

    @GetMapping("/api/orders/{orderId}/review")
    @Operation(summary = "Xem đánh giá của đơn mình (CUSTOMER)")
    public ResponseEntity<ReviewResponse> getMyReview(
            @PathVariable long orderId,
            @AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(reviewService.getByOrder(orderId, user.id()));
    }

    // ── PUBLIC ────────────────────────────────────────────────────────────

    @GetMapping("/api/restaurants/{restaurantId}/reviews")
    @Operation(summary = "Danh sách đánh giá của quán (public)")
    public ResponseEntity<PageResponse<ReviewResponse>> getRestaurantReviews(
            @PathVariable long restaurantId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(reviewService.getByRestaurant(restaurantId, page, size));
    }

    // ── MERCHANT ──────────────────────────────────────────────────────────

    @GetMapping("/api/merchant/restaurants/{restaurantId}/reviews")
    @Operation(summary = "Chủ quán xem đánh giá của quán mình")
    public ResponseEntity<PageResponse<ReviewResponse>> getMerchantReviews(
            @PathVariable long restaurantId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(reviewService.getMerchantReviews(restaurantId, user.id(), page, size));
    }

    @PostMapping("/api/merchant/reviews/{reviewId}/reply")
    @Operation(summary = "Chủ quán trả lời đánh giá")
    public ResponseEntity<ReviewReplyResponse> createReply(
            @PathVariable long reviewId,
            @Valid @RequestBody CreateReplyRequest req,
            @AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.createReply(reviewId, user.id(), req));
    }
}
