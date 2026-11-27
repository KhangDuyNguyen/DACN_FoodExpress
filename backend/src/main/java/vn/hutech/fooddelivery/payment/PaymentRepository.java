package vn.hutech.fooddelivery.payment;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** SQL thuan cho bang payments. */
@Repository
public class PaymentRepository {

    // getObject(..., LocalDateTime.class): doc DATETIME nguyen gia tri, khong bi doi mui gio
    private static final RowMapper<PaymentRow> MAPPER = (rs, i) -> new PaymentRow(
            rs.getLong("id"),
            rs.getLong("order_id"),
            rs.getString("method"),
            rs.getBigDecimal("amount"),
            rs.getString("status"),
            rs.getString("transaction_ref"),
            rs.getObject("created_at", LocalDateTime.class)
    );

    private final NamedParameterJdbcTemplate jdbc;

    public PaymentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Them giao dich dang cho (PENDING). created_at ghi DUNG thoi diem da gui VNPay (vnp_CreateDate),
     * vi khi hoi lai VNPay (querydr) phai gui lai gia tri nay o vnp_TransactionDate.
     */
    public long insertPending(long orderId, String method, BigDecimal amount, String transactionRef,
                              LocalDateTime createdAt) {
        String sql = """
                INSERT INTO payments (order_id, method, amount, status, transaction_ref, created_at)
                VALUES (:orderId, :method, :amount, 'PENDING', :transactionRef, :createdAt)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("method", method)
                .addValue("amount", amount)
                .addValue("transactionRef", transactionRef)
                .addValue("createdAt", createdAt);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder, new String[]{"id"});
        return keyHolder.getKey().longValue();
    }

    public Optional<PaymentRow> findById(long id) {
        String sql = """
                SELECT id, order_id, method, amount, status, transaction_ref, created_at
                FROM payments WHERE id = :id
                """;
        return jdbc.query(sql, Map.of("id", id), MAPPER).stream().findFirst();
    }

    /** Giao dich theo ma tham chieu da gui cong thanh toan (moi don chi 1 giao dich, lay dong moi nhat cho chac). */
    public Optional<PaymentRow> findByTransactionRef(String method, String transactionRef) {
        if (transactionRef == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT id, order_id, method, amount, status, transaction_ref, created_at
                FROM payments
                WHERE method = :method AND transaction_ref = :transactionRef
                ORDER BY id DESC
                LIMIT 1
                """;
        return jdbc.query(sql, Map.of("method", method, "transactionRef", transactionRef), MAPPER)
                .stream().findFirst();
    }

    /**
     * Giao dich con PENDING tao truoc thoi diem createdBefore (da qua han thanh toan), cu nhat truoc.
     * created_at luu gio Viet Nam (= vnp_CreateDate) nen createdBefore cung phai tinh theo gio Viet Nam.
     * Lay toi da limit dong moi lan quet de 1 lan quet khong qua lau.
     */
    public List<PaymentRow> findPendingCreatedBefore(String method, LocalDateTime createdBefore, int limit) {
        String sql = """
                SELECT id, order_id, method, amount, status, transaction_ref, created_at
                FROM payments
                WHERE method = :method AND status = 'PENDING' AND created_at < :createdBefore
                ORDER BY id
                LIMIT :limit
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("method", method)
                .addValue("createdBefore", createdBefore)
                .addValue("limit", limit);
        return jdbc.query(sql, params, MAPPER);
    }

    /** Giao dich online DA THANH TOAN cua don (de hoan tien khi don bi huy); COD hoac chua tra thi rong. */
    public Optional<PaymentRow> findSuccessByOrderId(long orderId) {
        String sql = """
                SELECT id, order_id, method, amount, status, transaction_ref, created_at
                FROM payments
                WHERE order_id = :orderId AND status = 'SUCCESS'
                ORDER BY id DESC
                LIMIT 1
                """;
        return jdbc.query(sql, Map.of("orderId", orderId), MAPPER).stream().findFirst();
    }

    /** Da hoan tien xong: SUCCESS -> REFUNDED. */
    public void markRefunded(long paymentId) {
        jdbc.update("UPDATE payments SET status = 'REFUNDED' WHERE id = :id AND status = 'SUCCESS'",
                Map.of("id", paymentId));
    }

    /**
     * Ghi ket qua CHI KHI giao dich con PENDING (dieu kien "AND status = 'PENDING'").
     * VNPay goi IPN nhieu lan, hoac IPN va trang quay ve den cung luc: chi 1 lan khop 1 dong,
     * cac lan sau khop 0 dong -> biet la da xu ly roi.
     */
    public int updateResult(long id, String status, String gatewayPayload) {
        String sql = """
                UPDATE payments
                SET status = :status,
                    paid_at = CASE WHEN :status = 'SUCCESS' THEN NOW() ELSE paid_at END,
                    gateway_payload = :gatewayPayload
                WHERE id = :id AND status = 'PENDING'
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("status", status)
                .addValue("gatewayPayload", gatewayPayload)
                .addValue("id", id);
        return jdbc.update(sql, params);
    }
}
