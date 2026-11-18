package vn.hutech.fooddelivery.driver.earnings;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Map;

@Repository
public class DriverEarningsRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public DriverEarningsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
}
