package vn.hutech.fooddelivery.payment;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** SQL thuan cho bang refunds (yeu cau hoan tien cho giao dich da thanh toan online). */
@Repository
public class RefundRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public RefundRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Ghi 1 yeu cau hoan tien dang cho (PENDING). */
    public void insertPending(long paymentId, BigDecimal amount, String reason) {
        String sql = """
                INSERT INTO refunds (payment_id, amount, reason, status)
                VALUES (:paymentId, :amount, :reason, 'PENDING')
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("paymentId", paymentId)
                .addValue("amount", amount)
                .addValue("reason", reason);
        jdbc.update(sql, params);
    }

    /** Giao dich nay da co yeu cau hoan tien chua (moi giao dich chi hoan 1 lan, toan bo so tien). */
    public boolean existsForPayment(long paymentId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM refunds WHERE payment_id = :paymentId",
                Map.of("paymentId", paymentId), Long.class);
        return count != null && count > 0;
    }

    /**
     * Yeu cau hoan tien VNPay dang cho gui, cu nhat truoc, kem thong tin giao dich goc.
     * vnp_TransactionNo lay tu du lieu VNPay da luu luc thanh toan thanh cong (payments.gateway_payload, kieu JSON):
     * JSON_EXTRACT lay gia tri theo ten truong, JSON_UNQUOTE bo dau ngoac kep.
     */
    public List<RefundRow> findPendingVnPay(int limit) {
        String sql = """
                SELECT r.id, r.payment_id, r.amount, p.transaction_ref, p.created_at,
                       JSON_UNQUOTE(JSON_EXTRACT(p.gateway_payload, '$.vnp_TransactionNo')) AS vnp_transaction_no
                FROM refunds r
                JOIN payments p ON p.id = r.payment_id
                WHERE r.status = 'PENDING' AND p.method = 'VNPAY'
                ORDER BY r.id
                LIMIT :limit
                """;
        return jdbc.query(sql, Map.of("limit", limit), (rs, i) -> new RefundRow(
                rs.getLong("id"),
                rs.getLong("payment_id"),
                rs.getBigDecimal("amount"),
                rs.getString("transaction_ref"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getString("vnp_transaction_no")
        ));
    }

    /** Ghi ket qua CHI KHI yeu cau con PENDING (lan xu ly thu 2 khop 0 dong -> biet la da xu ly roi). */
    public int updateResult(long refundId, String status) {
        String sql = """
                UPDATE refunds SET status = :status, processed_at = NOW()
                WHERE id = :id AND status = 'PENDING'
                """;
        return jdbc.update(sql, Map.of("status", status, "id", refundId));
    }
}
