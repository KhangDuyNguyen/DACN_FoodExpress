package vn.hutech.fooddelivery.review;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ReviewRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ReviewRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Kiểm tra điều kiện ─────────────────────────────────────────────────

    /** Trả về restaurantId nếu đơn DELIVERED và thuộc customer. Else empty. */
    public Optional<Long> findDeliveredRestaurantId(long orderId, long customerId) {
        String sql = """
            SELECT restaurant_id FROM orders
            WHERE id = :orderId AND customer_id = :customerId AND status = 'DELIVERED'
            """;
        var rows = jdbc.queryForList(sql,
                new MapSqlParameterSource().addValue("orderId", orderId).addValue("customerId", customerId));
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(((Number) rows.get(0).get("restaurant_id")).longValue());
    }

    /** true nếu đơn đã được đánh giá */
    public boolean existsByOrderId(long orderId) {
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reviews WHERE order_id = :orderId",
                new MapSqlParameterSource("orderId", orderId), Integer.class);
        return cnt != null && cnt > 0;
    }

    // ── Tạo review ───────────────────────────────────────────────────────

    @Transactional
    public long createReview(long orderId, long customerId, long restaurantId,
                              int restaurantRating, Integer driverRating, String comment) {
        String sql = """
            INSERT INTO reviews
                (order_id, customer_id, restaurant_id, restaurant_rating, driver_rating, comment)
            VALUES (:orderId, :customerId, :restaurantId, :rr, :dr, :comment)
            """;
        var key = new GeneratedKeyHolder();
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("orderId", orderId).addValue("customerId", customerId)
                .addValue("restaurantId", restaurantId).addValue("rr", restaurantRating)
                .addValue("dr", driverRating).addValue("comment", comment), key);

        // Cập nhật avg_rating và rating_count của quán
        recalcRestaurantRating(restaurantId);

        return key.getKey().longValue();
    }

    /** Tính lại avg_rating + rating_count cho quán */
    private void recalcRestaurantRating(long restaurantId) {
        String sql = """
            UPDATE restaurants r
            SET r.avg_rating   = (SELECT ROUND(AVG(restaurant_rating), 1) FROM reviews WHERE restaurant_id = :rid),
                r.rating_count = (SELECT COUNT(*) FROM reviews WHERE restaurant_id = :rid),
                r.updated_at   = NOW()
            WHERE r.id = :rid
            """;
        jdbc.update(sql, new MapSqlParameterSource("rid", restaurantId));
    }

    // ── Lấy danh sách review ─────────────────────────────────────────────

    public List<ReviewResponse> findByRestaurantId(long restaurantId, int limit, long offset) {
        String sql = """
            SELECT r.id, r.order_id, u.full_name AS customer_name,
                   r.restaurant_rating, r.driver_rating, r.comment, r.created_at,
                   rp.id AS reply_id, rp.content AS reply_content, rp.created_at AS reply_at,
                   ru.full_name AS merchant_name
            FROM reviews r
            JOIN users u ON u.id = r.customer_id
            LEFT JOIN review_replies rp ON rp.review_id = r.id
            LEFT JOIN users ru ON ru.id = rp.merchant_user_id
            WHERE r.restaurant_id = :rid
            ORDER BY r.created_at DESC
            LIMIT :limit OFFSET :offset
            """;
        var rows = jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("rid", restaurantId).addValue("limit", limit).addValue("offset", offset));
        return rows.stream().map(this::mapRow).toList();
    }

    public long countByRestaurantId(long restaurantId) {
        Long cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reviews WHERE restaurant_id = :rid",
                new MapSqlParameterSource("rid", restaurantId), Long.class);
        return cnt != null ? cnt : 0L;
    }

    /** Review của đơn cụ thể (khách xem lại đánh giá của mình) */
    public Optional<ReviewResponse> findByOrderId(long orderId, long customerId) {
        String sql = """
            SELECT r.id, r.order_id, u.full_name AS customer_name,
                   r.restaurant_rating, r.driver_rating, r.comment, r.created_at,
                   rp.id AS reply_id, rp.content AS reply_content, rp.created_at AS reply_at,
                   ru.full_name AS merchant_name
            FROM reviews r
            JOIN users u ON u.id = r.customer_id
            LEFT JOIN review_replies rp ON rp.review_id = r.id
            LEFT JOIN users ru ON ru.id = rp.merchant_user_id
            WHERE r.order_id = :orderId AND r.customer_id = :customerId
            """;
        var rows = jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("orderId", orderId).addValue("customerId", customerId));
        return rows.isEmpty() ? Optional.empty() : Optional.of(mapRow(rows.get(0)));
    }

    // ── Reply ────────────────────────────────────────────────────────────

    /** Kiểm tra review thuộc quán của merchant không */
    public boolean isReviewOfMerchant(long reviewId, long merchantUserId) {
        String sql = """
            SELECT COUNT(*) FROM reviews rv
            JOIN restaurants r ON r.id = rv.restaurant_id
            JOIN merchants m ON m.id = r.merchant_id
            WHERE rv.id = :reviewId AND m.owner_user_id = :uid
            """;
        Integer cnt = jdbc.queryForObject(sql,
                new MapSqlParameterSource().addValue("reviewId", reviewId).addValue("uid", merchantUserId),
                Integer.class);
        return cnt != null && cnt > 0;
    }

    /** Kiểm tra quán thuộc merchant không (FIX: thêm method này) */
    public boolean isRestaurantOwnedByMerchant(long restaurantId, long merchantUserId) {
        String sql = """
            SELECT COUNT(*) FROM restaurants r
            JOIN merchants m ON m.id = r.merchant_id
            WHERE r.id = :rid AND m.owner_user_id = :uid
            """;
        Integer cnt = jdbc.queryForObject(sql,
                new MapSqlParameterSource().addValue("rid", restaurantId).addValue("uid", merchantUserId),
                Integer.class);
        return cnt != null && cnt > 0;
    }

    public boolean replyExists(long reviewId) {
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM review_replies WHERE review_id = :rid",
                new MapSqlParameterSource("rid", reviewId), Integer.class);
        return cnt != null && cnt > 0;
    }

    public long createReply(long reviewId, long merchantUserId, String content) {
        String sql = """
            INSERT INTO review_replies (review_id, merchant_user_id, content)
            VALUES (:reviewId, :uid, :content)
            """;
        var key = new GeneratedKeyHolder();
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("reviewId", reviewId).addValue("uid", merchantUserId)
                .addValue("content", content), key);
        return key.getKey().longValue();
    }

    // ── Helper mapping ────────────────────────────────────────────────────

    private ReviewResponse mapRow(Map<String, Object> r) {
        ReviewReplyResponse reply = null;
        if (r.get("reply_id") != null) {
            reply = new ReviewReplyResponse(
                    ((Number) r.get("reply_id")).longValue(),
                    (String) r.get("merchant_name"),
                    (String) r.get("reply_content"),
                    toLocalDT(r.get("reply_at")));
        }
        Integer dr = r.get("driver_rating") != null ? ((Number) r.get("driver_rating")).intValue() : null;
        return new ReviewResponse(
                ((Number) r.get("id")).longValue(),
                ((Number) r.get("order_id")).longValue(),
                (String) r.get("customer_name"),
                ((Number) r.get("restaurant_rating")).intValue(),
                dr,
                (String) r.get("comment"),
                toLocalDT(r.get("created_at")),
                reply);
    }

    private java.time.LocalDateTime toLocalDT(Object val) {
        if (val == null) return null;
        if (val instanceof java.time.LocalDateTime ldt) return ldt;
        if (val instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        return null;
    }
}
