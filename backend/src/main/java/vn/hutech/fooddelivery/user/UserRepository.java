package vn.hutech.fooddelivery.user;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Truy van bang users bang SQL thuan. */
@Repository
public class UserRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, full_name, email, phone, password_hash, role, status,
                   avatar_url, last_login_at, created_at
            FROM users
            """;

    private static final RowMapper<UserRow> USER_MAPPER = (rs, i) -> new UserRow(
            rs.getLong("id"),
            rs.getString("full_name"),
            rs.getString("email"),
            rs.getString("phone"),
            rs.getString("password_hash"),
            rs.getString("role"),
            rs.getString("status"),
            rs.getString("avatar_url"),
            toLocalDateTime(rs.getTimestamp("last_login_at")),
            toLocalDateTime(rs.getTimestamp("created_at"))
    );

    private final NamedParameterJdbcTemplate jdbc;

    public UserRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRow> findById(long id) {
        String sql = SELECT_COLUMNS + " WHERE id = :id";
        return jdbc.query(sql, Map.of("id", id), USER_MAPPER).stream().findFirst();
    }

    public Optional<UserRow> findByPhone(String phone) {
        String sql = SELECT_COLUMNS + " WHERE phone = :phone";
        return jdbc.query(sql, Map.of("phone", phone), USER_MAPPER).stream().findFirst();
    }

    public Optional<UserRow> findByEmail(String email) {
        String sql = SELECT_COLUMNS + " WHERE email = :email";
        return jdbc.query(sql, Map.of("email", email), USER_MAPPER).stream().findFirst();
    }

    /** Dung khi dang nhap: identifier co the la so dien thoai hoac email. */
    public Optional<UserRow> findByPhoneOrEmail(String identifier) {
        String sql = SELECT_COLUMNS + " WHERE phone = :identifier OR email = :identifier";
        return jdbc.query(sql, Map.of("identifier", identifier), USER_MAPPER).stream().findFirst();
    }

    public boolean existsByRole(String role) {
        String sql = "SELECT COUNT(*) FROM users WHERE role = :role";
        Long count = jdbc.queryForObject(sql, Map.of("role", role), Long.class);
        return count != null && count > 0;
    }

    /** Them user moi, tra ve id vua tao. email co the null. */
    public long insert(String fullName, String phone, String email, String passwordHash, String role) {
        String sql = """
                INSERT INTO users (full_name, phone, email, password_hash, role, status)
                VALUES (:fullName, :phone, :email, :passwordHash, :role, 'ACTIVE')
                """;
        // Dung MapSqlParameterSource vi Map.of() khong chap nhan gia tri null (email)
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fullName", fullName)
                .addValue("phone", phone)
                .addValue("email", email)
                .addValue("passwordHash", passwordHash)
                .addValue("role", role);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder, new String[]{"id"});
        return keyHolder.getKey().longValue();
    }

    public void updateLastLogin(long id) {
        String sql = "UPDATE users SET last_login_at = NOW() WHERE id = :id";
        jdbc.update(sql, Map.of("id", id));
    }

    // ----- Trang admin quan ly nguoi dung (tuan 6) -----

    /** Dieu kien loc dung chung cho danh sach va dem. Tham so null = khong loc theo tieu chi do. */
    private static final String ADMIN_FILTER = """
             WHERE (:role IS NULL OR role = :role)
               AND (:status IS NULL OR status = :status)
               AND (:keyword IS NULL OR full_name LIKE :keyword OR phone LIKE :keyword OR email LIKE :keyword)
            """;

    /** Danh sach user cho admin, moi nhat truoc. keyword tim trong ho ten, SDT, email (chua %...%). */
    public List<UserRow> findForAdmin(String role, String status, String keyword, int limit, long offset) {
        String sql = SELECT_COLUMNS + ADMIN_FILTER + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset";
        MapSqlParameterSource params = adminFilterParams(role, status, keyword)
                .addValue("limit", limit)
                .addValue("offset", offset);
        return jdbc.query(sql, params, USER_MAPPER);
    }

    public long countForAdmin(String role, String status, String keyword) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM users" + ADMIN_FILTER,
                adminFilterParams(role, status, keyword), Long.class);
        return total == null ? 0 : total;
    }

    /** Admin khoa / mo khoa tai khoan. JwtAuthFilter doc lai trang thai o moi request nen co hieu luc ngay. */
    public void updateStatus(long id, String status) {
        jdbc.update("UPDATE users SET status = :status WHERE id = :id", Map.of("status", status, "id", id));
    }

    private static MapSqlParameterSource adminFilterParams(String role, String status, String keyword) {
        return new MapSqlParameterSource()
                .addValue("role", role)
                .addValue("status", status)
                .addValue("keyword", keyword == null ? null : "%" + keyword + "%");
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
