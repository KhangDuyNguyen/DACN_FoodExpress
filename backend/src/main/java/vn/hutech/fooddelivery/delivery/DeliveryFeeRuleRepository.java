package vn.hutech.fooddelivery.delivery;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** SQL thuan cho bang delivery_fee_rules. */
@Repository
public class DeliveryFeeRuleRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, name, base_fee, base_distance_km, per_km_fee, is_active, created_at
            FROM delivery_fee_rules
            """;

    private static final RowMapper<DeliveryFeeRuleRow> MAPPER = (rs, i) -> {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new DeliveryFeeRuleRow(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getBigDecimal("base_fee"),
                rs.getBigDecimal("base_distance_km"),
                rs.getBigDecimal("per_km_fee"),
                rs.getBoolean("is_active"),
                createdAt == null ? null : createdAt.toLocalDateTime());
    };

    private final NamedParameterJdbcTemplate jdbc;

    public DeliveryFeeRuleRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Moi quy tac, moi nhat (id lon nhat) truoc. */
    public List<DeliveryFeeRuleRow> findAll() {
        return jdbc.query(SELECT_COLUMNS + " ORDER BY id DESC", Map.of(), MAPPER);
    }

    public Optional<DeliveryFeeRuleRow> findById(long id) {
        return jdbc.query(SELECT_COLUMNS + " WHERE id = :id", Map.of("id", id), MAPPER).stream().findFirst();
    }

    /** Them quy tac moi o trang thai dang bat, tra ve id. */
    public long insert(String name, BigDecimal baseFee, BigDecimal baseDistanceKm, BigDecimal perKmFee) {
        String sql = """
                INSERT INTO delivery_fee_rules (name, base_fee, base_distance_km, per_km_fee, is_active)
                VALUES (:name, :baseFee, :baseDistanceKm, :perKmFee, 1)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("name", name)
                .addValue("baseFee", baseFee)
                .addValue("baseDistanceKm", baseDistanceKm)
                .addValue("perKmFee", perKmFee);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder, new String[]{"id"});
        return keyHolder.getKey().longValue();
    }

    /**
     * id cac quy tac dang bat, KHOA cac dong do den het transaction (SELECT ... FOR UPDATE):
     * 2 admin cung luc tat 2 quy tac cuoi thi nguoi sau phai cho nguoi truoc xong roi dem lai.
     */
    public List<Long> lockActiveIds() {
        return jdbc.queryForList("SELECT id FROM delivery_fee_rules WHERE is_active = 1 FOR UPDATE",
                Map.of(), Long.class);
    }

    public void updateActive(long id, boolean active) {
        jdbc.update("UPDATE delivery_fee_rules SET is_active = :active WHERE id = :id",
                Map.of("active", active, "id", id));
    }
}
