package vn.hutech.fooddelivery.user;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import vn.hutech.fooddelivery.user.dto.AddressResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SQL thuan cho bang addresses. Moi cau lenh deu loc theo user_id,
 * nen khong doc/sua/xoa duoc dia chi cua nguoi khac.
 */
@Repository
public class AddressRepository {

    private static final RowMapper<AddressResponse> ADDRESS_MAPPER = (rs, i) -> new AddressResponse(
            rs.getLong("id"),
            rs.getString("label"),
            rs.getString("receiver_name"),
            rs.getString("receiver_phone"),
            rs.getString("address_line"),
            rs.getDouble("lat"),
            rs.getDouble("lng"),
            rs.getBoolean("is_default")
    );

    private final NamedParameterJdbcTemplate jdbc;

    public AddressRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Dia chi cua 1 nguoi, mac dinh len dau, roi moi tao truoc. */
    public List<AddressResponse> findByUser(long userId) {
        String sql = """
                SELECT id, label, receiver_name, receiver_phone, address_line, lat, lng, is_default
                FROM addresses
                WHERE user_id = :userId
                ORDER BY is_default DESC, created_at DESC, id DESC
                """;
        return jdbc.query(sql, Map.of("userId", userId), ADDRESS_MAPPER);
    }

    public Optional<AddressResponse> findByIdAndUser(long id, long userId) {
        String sql = """
                SELECT id, label, receiver_name, receiver_phone, address_line, lat, lng, is_default
                FROM addresses
                WHERE id = :id AND user_id = :userId
                """;
        return jdbc.query(sql, Map.of("id", id, "userId", userId), ADDRESS_MAPPER).stream().findFirst();
    }

    public int countByUser(long userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM addresses WHERE user_id = :userId",
                Map.of("userId", userId), Integer.class);
        return count == null ? 0 : count;
    }

    public long insert(long userId, String label, String receiverName, String receiverPhone,
                       String addressLine, double lat, double lng, boolean isDefault) {
        String sql = """
                INSERT INTO addresses (user_id, label, receiver_name, receiver_phone, address_line, lat, lng, is_default)
                VALUES (:userId, :label, :receiverName, :receiverPhone, :addressLine, :lat, :lng, :isDefault)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("label", label)
                .addValue("receiverName", receiverName)
                .addValue("receiverPhone", receiverPhone)
                .addValue("addressLine", addressLine)
                .addValue("lat", lat)
                .addValue("lng", lng)
                .addValue("isDefault", isDefault);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder, new String[]{"id"});
        return keyHolder.getKey().longValue();
    }

    /** Sua noi dung dia chi (khong dong vao is_default). Tra ve so dong khop. */
    public int update(long id, long userId, String label, String receiverName, String receiverPhone,
                      String addressLine, double lat, double lng) {
        String sql = """
                UPDATE addresses
                SET label = :label, receiver_name = :receiverName, receiver_phone = :receiverPhone,
                    address_line = :addressLine, lat = :lat, lng = :lng
                WHERE id = :id AND user_id = :userId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("label", label)
                .addValue("receiverName", receiverName)
                .addValue("receiverPhone", receiverPhone)
                .addValue("addressLine", addressLine)
                .addValue("lat", lat)
                .addValue("lng", lng)
                .addValue("id", id)
                .addValue("userId", userId);
        return jdbc.update(sql, params);
    }

    public int delete(long id, long userId) {
        return jdbc.update("DELETE FROM addresses WHERE id = :id AND user_id = :userId",
                Map.of("id", id, "userId", userId));
    }

    /** Bo mac dinh o moi dia chi cua nguoi dung (goi truoc khi dat 1 dia chi lam mac dinh). */
    public void clearDefault(long userId) {
        jdbc.update("UPDATE addresses SET is_default = 0 WHERE user_id = :userId", Map.of("userId", userId));
    }

    public void markDefault(long id, long userId) {
        jdbc.update("UPDATE addresses SET is_default = 1 WHERE id = :id AND user_id = :userId",
                Map.of("id", id, "userId", userId));
    }

    /** Dia chi moi tao gan nhat, dung de chon mac dinh moi khi xoa dia chi mac dinh. */
    public Optional<Long> findNewestId(long userId) {
        String sql = "SELECT id FROM addresses WHERE user_id = :userId ORDER BY created_at DESC, id DESC LIMIT 1";
        return jdbc.queryForList(sql, Map.of("userId", userId), Long.class).stream().findFirst();
    }
}
