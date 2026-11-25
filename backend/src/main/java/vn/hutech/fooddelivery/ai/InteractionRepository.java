package vn.hutech.fooddelivery.ai;

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

/**
 * Truy vấn SQL thuần cho bảng user_item_interactions và kiểm tra dữ liệu liên quan.
 */
@Repository
public class InteractionRepository {

    private static final RowMapper<InteractionRow> ROW_MAPPER = (rs, i) -> new InteractionRow(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getLong("menu_item_id"),
            rs.getLong("restaurant_id"),
            rs.getString("type"),
            rs.getInt("quantity"),
            rs.getObject("order_id", Long.class),
            rs.getString("event_id"),
            toLocalDateTime(rs.getTimestamp("created_at"))
    );

    private final NamedParameterJdbcTemplate jdbc;

    public InteractionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> findMenuItemRestaurantId(long menuItemId) {
        String sql = """
                SELECT mc.restaurant_id
                FROM menu_items mi
                JOIN menu_categories mc ON mc.id = mi.category_id
                WHERE mi.id = :menuItemId
                """;
        return jdbc.query(sql, Map.of("menuItemId", menuItemId), (rs, i) -> rs.getLong("restaurant_id"))
                .stream()
                .findFirst();
    }

    public Optional<OrderCheckRow> findOrderForInteraction(long orderId) {
        String sql = """
                SELECT id, customer_id, restaurant_id, status
                FROM orders
                WHERE id = :orderId
                """;
        return jdbc.query(sql, Map.of("orderId", orderId), (rs, i) -> new OrderCheckRow(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                rs.getLong("restaurant_id"),
                rs.getString("status")
        )).stream().findFirst();
    }

    public List<OrderItemQtyRow> findOrderItemQuantities(long orderId) {
        String sql = """
                SELECT menu_item_id, SUM(quantity) AS total_qty
                FROM order_items
                WHERE order_id = :orderId AND menu_item_id IS NOT NULL
                GROUP BY menu_item_id
                """;
        return jdbc.query(sql, Map.of("orderId", orderId), (rs, i) -> new OrderItemQtyRow(
                rs.getLong("menu_item_id"),
                rs.getInt("total_qty")
        ));
    }

    public Optional<InteractionRow> findById(long id) {
        String sql = """
                SELECT uii.id, uii.user_id, uii.menu_item_id, mc.restaurant_id, uii.type,
                       uii.quantity, uii.order_id, uii.event_id, uii.created_at
                FROM user_item_interactions uii
                JOIN menu_items mi ON mi.id = uii.menu_item_id
                JOIN menu_categories mc ON mc.id = mi.category_id
                WHERE uii.id = :id
                """;
        return jdbc.query(sql, Map.of("id", id), ROW_MAPPER).stream().findFirst();
    }

    public Optional<InteractionRow> findByEventId(String eventId) {
        String sql = """
                SELECT uii.id, uii.user_id, uii.menu_item_id, mc.restaurant_id, uii.type,
                       uii.quantity, uii.order_id, uii.event_id, uii.created_at
                FROM user_item_interactions uii
                JOIN menu_items mi ON mi.id = uii.menu_item_id
                JOIN menu_categories mc ON mc.id = mi.category_id
                WHERE uii.event_id = :eventId
                """;
        return jdbc.query(sql, Map.of("eventId", eventId), ROW_MAPPER).stream().findFirst();
    }

    public Optional<InteractionRow> findByOrderAndItemAndType(long orderId, long menuItemId, String type) {
        String sql = """
                SELECT uii.id, uii.user_id, uii.menu_item_id, mc.restaurant_id, uii.type,
                       uii.quantity, uii.order_id, uii.event_id, uii.created_at
                FROM user_item_interactions uii
                JOIN menu_items mi ON mi.id = uii.menu_item_id
                JOIN menu_categories mc ON mc.id = mi.category_id
                WHERE uii.order_id = :orderId AND uii.menu_item_id = :menuItemId AND uii.type = :type
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("menuItemId", menuItemId)
                .addValue("type", type);
        return jdbc.query(sql, params, ROW_MAPPER).stream().findFirst();
    }

    public long insert(long userId, long menuItemId, String type, int quantity, Long orderId, String eventId) {
        String sql = """
                INSERT INTO user_item_interactions (user_id, menu_item_id, type, quantity, order_id, event_id)
                VALUES (:userId, :menuItemId, :type, :quantity, :orderId, :eventId)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("menuItemId", menuItemId)
                .addValue("type", type)
                .addValue("quantity", quantity)
                .addValue("orderId", orderId)
                .addValue("eventId", eventId);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder, new String[]{"id"});
        return keyHolder.getKey().longValue();
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    public record InteractionRow(
            long id,
            long userId,
            long menuItemId,
            long restaurantId,
            String type,
            int quantity,
            Long orderId,
            String eventId,
            LocalDateTime createdAt
    ) {
    }

    public record OrderCheckRow(long id, long customerId, long restaurantId, String status) {
    }

    public record OrderItemQtyRow(long menuItemId, int quantity) {
    }
}

