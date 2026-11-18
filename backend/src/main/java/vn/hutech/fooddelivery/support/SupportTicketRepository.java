package vn.hutech.fooddelivery.support;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.common.PageResponse;
import vn.hutech.fooddelivery.support.dto.CreateTicketRequest;
import vn.hutech.fooddelivery.support.dto.SupportTicketResponse;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repository truy vấn bảng support_tickets bằng NamedParameterJdbcTemplate (SQL thuần).
 */
@Repository
public class SupportTicketRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private final RowMapper<SupportTicketResponse> rowMapper = (rs, rowNum) -> {
        long id = rs.getLong("id");
        long userId = rs.getLong("user_id");
        String userName = rs.getString("user_name");
        String userEmail = rs.getString("user_email");
        long oid = rs.getLong("order_id");
        Long orderId = rs.wasNull() ? null : oid;
        String subject = rs.getString("subject");
        String description = rs.getString("description");
        String status = rs.getString("status");
        Timestamp ct = rs.getTimestamp("created_at");
        LocalDateTime createdAt = ct != null ? ct.toLocalDateTime() : null;
        Timestamp rt = rs.getTimestamp("resolved_at");
        LocalDateTime resolvedAt = rt != null ? rt.toLocalDateTime() : null;

        return new SupportTicketResponse(
                id,
                userId,
                userName,
                userEmail,
                orderId,
                subject,
                description,
                status,
                createdAt,
                resolvedAt
        );
    };

    public SupportTicketRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Tạo ticket hỗ trợ mới với status mặc định là 'OPEN'.
     */
    @Transactional
    public SupportTicketResponse createTicket(long userId, CreateTicketRequest req) {
        if (req.orderId() != null && !orderExists(req.orderId())) {
            throw new NotFoundException("Đơn hàng với ID " + req.orderId() + " không tồn tại");
        }

        String sql = """
            INSERT INTO support_tickets (user_id, order_id, subject, description, status, created_at)
            VALUES (:userId, :orderId, :subject, :description, 'OPEN', CURRENT_TIMESTAMP)
            """;

        var keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("orderId", req.orderId())
                .addValue("subject", req.subject())
                .addValue("description", req.description());

        jdbc.update(sql, params, keyHolder);
        long ticketId = Objects.requireNonNull(keyHolder.getKey()).longValue();

        return findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy ticket vừa tạo với ID: " + ticketId));
    }

    /**
     * Tìm ticket theo ID kèm thông tin người tạo.
     */
    public Optional<SupportTicketResponse> findById(long id) {
        String sql = """
            SELECT st.id, st.user_id, u.full_name AS user_name, u.email AS user_email,
                   st.order_id, st.subject, st.description, st.status,
                   st.created_at, st.resolved_at
            FROM support_tickets st
            JOIN users u ON u.id = st.user_id
            WHERE st.id = :id
            """;
        List<SupportTicketResponse> list = jdbc.query(sql, new MapSqlParameterSource("id", id), rowMapper);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * Kiểm tra ticket có tồn tại không.
     */
    public boolean existsById(long id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM support_tickets WHERE id = :id",
                new MapSqlParameterSource("id", id),
                Integer.class
        );
        return count != null && count > 0;
    }

    /**
     * Kiểm tra đơn hàng có tồn tại không.
     */
    public boolean orderExists(long orderId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE id = :orderId",
                new MapSqlParameterSource("orderId", orderId),
                Integer.class
        );
        return count != null && count > 0;
    }

    /**
     * Khách hàng lấy danh sách ticket của mình, tuỳ chọn lọc theo status, sắp xếp created_at DESC.
     */
    public PageResponse<SupportTicketResponse> findCustomerTickets(long userId, String status, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        long offset = (long) safePage * safeSize;

        StringBuilder whereClause = new StringBuilder(" WHERE st.user_id = :userId");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("limit", safeSize)
                .addValue("offset", offset);

        if (status != null && !status.isBlank()) {
            whereClause.append(" AND st.status = :status");
            params.addValue("status", status.trim().toUpperCase());
        }

        String countSql = "SELECT COUNT(*) FROM support_tickets st" + whereClause;
        Long total = jdbc.queryForObject(countSql, params, Long.class);
        long totalItems = total != null ? total : 0L;

        if (totalItems == 0) {
            return PageResponse.of(List.of(), safePage, safeSize, 0L);
        }

        String querySql = """
            SELECT st.id, st.user_id, u.full_name AS user_name, u.email AS user_email,
                   st.order_id, st.subject, st.description, st.status,
                   st.created_at, st.resolved_at
            FROM support_tickets st
            JOIN users u ON u.id = st.user_id
            """ + whereClause + " ORDER BY st.created_at DESC LIMIT :limit OFFSET :offset";

        List<SupportTicketResponse> items = jdbc.query(querySql, params, rowMapper);
        return PageResponse.of(items, safePage, safeSize, totalItems);
    }

    /**
     * Admin lấy danh sách tất cả các ticket, hỗ trợ tìm kiếm keyword và lọc status, sắp xếp created_at DESC.
     */
    public PageResponse<SupportTicketResponse> findAdminTickets(String status, String keyword, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        long offset = (long) safePage * safeSize;

        StringBuilder whereClause = new StringBuilder(" WHERE 1=1");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", safeSize)
                .addValue("offset", offset);

        if (status != null && !status.isBlank()) {
            whereClause.append(" AND st.status = :status");
            params.addValue("status", status.trim().toUpperCase());
        }

        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim() + "%";
            whereClause.append(" AND (st.subject LIKE :kw OR st.description LIKE :kw OR u.full_name LIKE :kw OR u.email LIKE :kw OR u.phone LIKE :kw");
            params.addValue("kw", kw);

            try {
                long numId = Long.parseLong(keyword.trim());
                whereClause.append(" OR st.id = :numId OR st.order_id = :numId");
                params.addValue("numId", numId);
            } catch (NumberFormatException ignored) {
            }

            whereClause.append(")");
        }

        String countSql = """
            SELECT COUNT(*)
            FROM support_tickets st
            JOIN users u ON u.id = st.user_id
            """ + whereClause;

        Long total = jdbc.queryForObject(countSql, params, Long.class);
        long totalItems = total != null ? total : 0L;

        if (totalItems == 0) {
            return PageResponse.of(List.of(), safePage, safeSize, 0L);
        }

        String querySql = """
            SELECT st.id, st.user_id, u.full_name AS user_name, u.email AS user_email,
                   st.order_id, st.subject, st.description, st.status,
                   st.created_at, st.resolved_at
            FROM support_tickets st
            JOIN users u ON u.id = st.user_id
            """ + whereClause + " ORDER BY st.created_at DESC LIMIT :limit OFFSET :offset";

        List<SupportTicketResponse> items = jdbc.query(querySql, params, rowMapper);
        return PageResponse.of(items, safePage, safeSize, totalItems);
    }

    /**
     * Cập nhật trạng thái ticket. Nếu là RESOLVED hoặc CLOSED thì set resolved_at = CURRENT_TIMESTAMP, ngược lại set NULL.
     */
    @Transactional
    public SupportTicketResponse updateStatus(long ticketId, String status) {
        if (!existsById(ticketId)) {
            throw new NotFoundException("Ticket hỗ trợ không tồn tại với ID: " + ticketId);
        }

        String upperStatus = status.trim().toUpperCase();
        String sql = """
            UPDATE support_tickets
            SET status = :status,
                resolved_at = CASE WHEN :status IN ('RESOLVED', 'CLOSED') THEN CURRENT_TIMESTAMP ELSE NULL END
            WHERE id = :id
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", ticketId)
                .addValue("status", upperStatus);

        jdbc.update(sql, params);
        return findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket hỗ trợ không tồn tại với ID: " + ticketId));
    }
}
