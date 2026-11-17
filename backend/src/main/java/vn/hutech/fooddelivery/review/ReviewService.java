package vn.hutech.fooddelivery.review;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.common.PageResponse;

import java.util.List;

@Service
public class ReviewService {

    private final ReviewRepository repo;

    public ReviewService(ReviewRepository repo) {
        this.repo = repo;
    }

    // ── Khách tạo review ─────────────────────────────────────────────────

    @Transactional
    public ReviewResponse createReview(long orderId, long customerId, CreateReviewRequest req) {
        // 1. Đơn phải DELIVERED và thuộc customer
        long restaurantId = repo.findDeliveredRestaurantId(orderId, customerId)
                .orElseThrow(() -> new ForbiddenException(
                        "Không thể đánh giá: đơn chưa giao xong hoặc không phải đơn của bạn"));

        // 2. Mỗi đơn chỉ review 1 lần
        if (repo.existsByOrderId(orderId))
            throw new ConflictException("Đơn này đã được đánh giá trước đó");

        repo.createReview(orderId, customerId, restaurantId,
                req.restaurantRating(), req.driverRating(), req.comment());

        return repo.findByOrderId(orderId, customerId)
                .orElseThrow(() -> new RuntimeException("Lỗi lưu đánh giá"));
    }

    // ── Lấy review của đơn (khách xem lại) ──────────────────────────────

    public ReviewResponse getByOrder(long orderId, long customerId) {
        return repo.findByOrderId(orderId, customerId)
                .orElseThrow(() -> new NotFoundException("Đơn này chưa có đánh giá"));
    }

    // ── Danh sách review của quán (public) ──────────────────────────────

    public PageResponse<ReviewResponse> getByRestaurant(long restaurantId, int page, int size) {
        long total = repo.countByRestaurantId(restaurantId);
        List<ReviewResponse> items = repo.findByRestaurantId(restaurantId, size, (long) page * size);
        return PageResponse.of(items, page, size, total);
    }

    // ── Merchant xem review của quán mình ───────────────────────────────

    public PageResponse<ReviewResponse> getMerchantReviews(long restaurantId, long merchantUserId, int page, int size) {
        // Kiểm tra quán có thuộc merchant không
        if (!repo.isRestaurantOwnedByMerchant(restaurantId, merchantUserId))
            throw new ForbiddenException("Quán này không thuộc về bạn");

        long total = repo.countByRestaurantId(restaurantId);
        List<ReviewResponse> items = repo.findByRestaurantId(restaurantId, size, (long) page * size);
        return PageResponse.of(items, page, size, total);
    }

    // ── Merchant trả lời review ──────────────────────────────────────────

    @Transactional
    public ReviewReplyResponse createReply(long reviewId, long merchantUserId, CreateReplyRequest req) {
        // Kiểm tra review thuộc quán của merchant
        if (!repo.isReviewOfMerchant(reviewId, merchantUserId))
            throw new ForbiddenException("Review này không thuộc quán của bạn");

        // Mỗi review chỉ trả lời 1 lần
        if (repo.replyExists(reviewId))
            throw new ConflictException("Review này đã được trả lời rồi");

        repo.createReply(reviewId, merchantUserId, req.content());

        // Trả về reply vừa tạo
        return new ReviewReplyResponse(0L, null, req.content(), java.time.LocalDateTime.now());
    }
}
