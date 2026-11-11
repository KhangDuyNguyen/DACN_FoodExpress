package vn.hutech.fooddelivery.driver.earnings;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Repository
public class DriverEarningsRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public DriverEarningsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Goi sau Order -> Driver -> Assignment lock; khong mo transaction doc lap. */
    public Optional<Earning> findByOrderIdForUpdate(long orderId) {
        return jdbc.query("""
                SELECT driver_id, amount, bonus FROM driver_earnings
                WHERE order_id = :orderId FOR UPDATE
                """, Map.of("orderId", orderId), (rs, i) -> new Earning(
                rs.getLong("driver_id"), rs.getBigDecimal("amount"), rs.getBigDecimal("bonus")))
                .stream().findFirst();
    }

    /** UNIQUE(order_id) la lop bao ve cuoi; khong IGNORE/upsert va khong sua ban ghi cu. */
    public void insert(long orderId, long driverId, BigDecimal amount) {
        int inserted = jdbc.update("""
                INSERT INTO driver_earnings (order_id, driver_id, amount, bonus)
                VALUES (:orderId, :driverId, :amount, :bonus)
                """, Map.of("orderId", orderId, "driverId", driverId,
                "amount", amount, "bonus", BigDecimal.ZERO));
        if (inserted != 1) throw new IllegalStateException("Không ghi được thu nhập tài xế");
    }

    public record Earning(long driverId, BigDecimal amount, BigDecimal bonus) {
    }
}
