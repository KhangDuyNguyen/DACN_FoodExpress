package vn.hutech.fooddelivery.driver;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Repository
public class DriverLocationRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DriverLocationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(long driverId, BigDecimal lat, BigDecimal lng, LocalDateTime recordedAt) {
        String sql = """
                INSERT INTO driver_locations (driver_id, lat, lng, recorded_at)
                VALUES (:driverId, :lat, :lng, :recordedAt)
                """;
        var params = new MapSqlParameterSource()
                .addValue("driverId", driverId)
                .addValue("lat", lat)
                .addValue("lng", lng)
                .addValue("recordedAt", Timestamp.valueOf(recordedAt));
        jdbc.update(sql, params);
    }
}
