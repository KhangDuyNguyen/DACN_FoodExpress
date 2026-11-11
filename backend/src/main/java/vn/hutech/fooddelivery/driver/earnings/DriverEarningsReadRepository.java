package vn.hutech.fooddelivery.driver.earnings;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.hutech.fooddelivery.driver.DriverReadTime;

import java.util.List;

/** An - Tuan 5C: chi SELECT, tach rieng khoi repository ghi earnings cua 5B. */
@Repository
public class DriverEarningsReadRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public DriverEarningsReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean supportsDateBounds(Long fromEpoch, Long toExclusiveEpoch) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT (:fromEpoch IS NULL OR FROM_UNIXTIME(:fromEpoch) IS NOT NULL)
                   AND (:toEpoch IS NULL OR FROM_UNIXTIME(:toEpoch) IS NOT NULL)
                """, bounds(fromEpoch, toExclusiveEpoch), Boolean.class));
    }

    public List<DriverEarningsResponse.Item> findEarnings(long driverId, Long fromEpoch, Long toExclusiveEpoch) {
        return jdbc.query("""
                SELECT e.order_id, o.code AS order_code, e.amount, e.bonus,
                       UNIX_TIMESTAMP(e.created_at) AS created_at_epoch
                FROM driver_earnings e
                JOIN orders o ON o.id = e.order_id
                WHERE e.driver_id = :driverId
                  AND (:fromEpoch IS NULL OR e.created_at >= FROM_UNIXTIME(:fromEpoch))
                  AND (:toEpoch IS NULL OR e.created_at < FROM_UNIXTIME(:toEpoch))
                ORDER BY e.created_at DESC, e.id DESC
                """, bounds(fromEpoch, toExclusiveEpoch).addValue("driverId", driverId), (rs, i) -> {
            var amount = rs.getBigDecimal("amount");
            var bonus = rs.getBigDecimal("bonus");
            return new DriverEarningsResponse.Item(rs.getLong("order_id"), rs.getString("order_code"),
                    amount, bonus, amount.add(bonus), DriverReadTime.vietnamTime(rs, "created_at_epoch"));
        });
    }

    private static MapSqlParameterSource bounds(Long fromEpoch, Long toEpoch) {
        return new MapSqlParameterSource().addValue("fromEpoch", fromEpoch).addValue("toEpoch", toEpoch);
    }
}
