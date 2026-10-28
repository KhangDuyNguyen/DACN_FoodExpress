package vn.hutech.fooddelivery.driver;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.hutech.fooddelivery.driver.dto.DriverProfileResponse;

import java.util.Map;
import java.util.Optional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Repository
public class DriverRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DriverRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<DriverProfileResponse> findProfileByUserId(long userId) {
        String sql = """
                SELECT u.full_name, u.phone, u.email,
                       d.vehicle_type, d.license_plate, d.status, d.is_online
                FROM drivers d
                JOIN users u ON u.id = d.user_id
                WHERE d.user_id = :userId
                """;
        return jdbc.query(sql, Map.of("userId", userId), (rs, i) -> new DriverProfileResponse(
                rs.getString("full_name"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("vehicle_type"),
                rs.getString("license_plate"),
                rs.getString("status"),
                rs.getBoolean("is_online")
        )).stream().findFirst();
    }

    public int updatePendingProfile(long userId, String vehicleType, String licensePlate) {
        String sql = """
                UPDATE drivers
                SET vehicle_type = :vehicleType, license_plate = :licensePlate
                WHERE user_id = :userId AND status = 'PENDING'
                """;
        // Kiem tra PENDING ngay khi ghi, ke ca khi Admin vua doi trang thai sau lan doc cua Service.
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("vehicleType", vehicleType)
                .addValue("licensePlate", licensePlate);
        return jdbc.update(sql, params);
    }

    public Optional<Long> findIdByUserIdForUpdate(long userId) {
        String sql = "SELECT id FROM drivers WHERE user_id = :userId FOR UPDATE";
        return jdbc.query(sql, Map.of("userId", userId), (rs, i) -> rs.getLong("id"))
                .stream().findFirst();
    }

    public int updateLocation(long driverId, BigDecimal lat, BigDecimal lng, LocalDateTime recordedAt) {
        String sql = """
                UPDATE drivers
                SET current_lat = :lat, current_lng = :lng, last_location_at = :recordedAt
                WHERE id = :driverId
                """;
        var params = new MapSqlParameterSource()
                .addValue("driverId", driverId)
                .addValue("lat", lat)
                .addValue("lng", lng)
                .addValue("recordedAt", Timestamp.valueOf(recordedAt));
        return jdbc.update(sql, params);
    }

    public Optional<DriverStatusRow> findStatusByUserIdForUpdate(long userId) {
        String sql = """
                SELECT id, status, is_online, current_lat, current_lng, last_location_at
                FROM drivers WHERE user_id = :userId FOR UPDATE
                """;
        return jdbc.query(sql, Map.of("userId", userId), (rs, i) -> {
            Timestamp locationAt = rs.getTimestamp("last_location_at");
            return new DriverStatusRow(rs.getLong("id"), rs.getString("status"), rs.getBoolean("is_online"),
                    rs.getBigDecimal("current_lat"), rs.getBigDecimal("current_lng"),
                    locationAt == null ? null : locationAt.toLocalDateTime());
        }).stream().findFirst();
    }

    public int updateOnline(long driverId, boolean isOnline) {
        String sql = "UPDATE drivers SET is_online = :isOnline WHERE id = :driverId";
        return jdbc.update(sql, Map.of("driverId", driverId, "isOnline", isOnline));
    }

    /** Goi trong transaction dang ky cua AuthService; khong tao toa do hay bat Online. */
    public void insert(long userId, String vehicleType, String licensePlate) {
        String sql = """
                INSERT INTO drivers (user_id, vehicle_type, license_plate, status, is_online)
                VALUES (:userId, :vehicleType, :licensePlate, 'PENDING', 0)
                """;
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("vehicleType", vehicleType)
                .addValue("licensePlate", licensePlate);
        jdbc.update(sql, params);
    }

    record DriverStatusRow(long id, String approvalStatus, boolean isOnline,
                           BigDecimal lat, BigDecimal lng, LocalDateTime lastLocationAt) {
    }
}
