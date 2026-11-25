package vn.hutech.fooddelivery.ai;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.hutech.fooddelivery.ai.dto.AiEngineDtos.ActiveItemPayload;
import vn.hutech.fooddelivery.ai.dto.AiEngineDtos.OrderBasketPayload;
import vn.hutech.fooddelivery.ai.dto.AiEngineDtos.PopularItemPayload;
import vn.hutech.fooddelivery.ai.dto.AiEngineDtos.UserHistorySignalPayload;
import vn.hutech.fooddelivery.ai.dto.AiEngineDtos.UserInteractionPayload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Truy vấn SQL thuần (NamedParameterJdbcTemplate) phục vụ gợi ý món ăn AI và fallback món bán chạy.
 * Luôn lọc: món đang bán (mi.is_available = 1), nhóm món bật (mc.is_active = 1),
 * quán đã duyệt và đang mở (r.status IN ('APPROVED','ACTIVE') AND r.open_status = 'OPEN'),
 * và loại bỏ đơn đã hủy / chưa thanh toán khỏi thống kê mua hàng.
 */
@Repository
public class RecommendationRepository {

    private static final String ACTIVE_ITEM_FILTER = "\n" + """
            mi.is_available = 1
            AND mc.is_active = 1
            AND r.status IN ('APPROVED', 'ACTIVE')
            AND r.open_status = 'OPEN'
            """;

    private static final String VALID_ORDER_STATUS_FILTER =
            "\no.status NOT IN ('CANCELLED', 'REJECTED', 'PENDING_PAYMENT')\n";

    private final NamedParameterJdbcTemplate jdbc;

    public RecommendationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Lấy toàn bộ món đang mở bán hợp lệ kèm thông tin quán và danh sách loại món.
     */
    public List<ActiveMenuItemRecord> findActiveAvailableItems() {
        String sql = """
                SELECT mi.id, mi.name, mi.description, mi.price, mi.image_url,
                       CAST(mi.is_available AS UNSIGNED) AS is_available,
                       r.id AS restaurant_id, r.name AS restaurant_name, r.address AS restaurant_address,
                       r.open_status AS restaurant_open_status, r.avg_rating AS restaurant_avg_rating,
                       c.name AS cuisine_name
                FROM menu_items mi
                JOIN menu_categories mc ON mc.id = mi.category_id
                JOIN restaurants r ON r.id = mc.restaurant_id
                LEFT JOIN restaurant_cuisines rc ON rc.restaurant_id = r.id
                LEFT JOIN cuisines c ON c.id = rc.cuisine_id
                WHERE """ + ACTIVE_ITEM_FILTER + "\n" + """
                ORDER BY r.avg_rating DESC, mi.sort_order ASC, mi.id ASC
                """;

        List<Map<String, Object>> rows = jdbc.queryForList(sql, Map.of());
        Map<Long, ActiveMenuItemBuilder> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            long itemId = ((Number) row.get("id")).longValue();
            ActiveMenuItemBuilder builder = grouped.computeIfAbsent(itemId, id -> new ActiveMenuItemBuilder(
                    id,
                    (String) row.get("name"),
                    (String) row.get("description"),
                    ((Number) row.get("price")).longValue(),
                    (String) row.get("image_url"),
                    toBool(row.get("is_available")),
                    ((Number) row.get("restaurant_id")).longValue(),
                    (String) row.get("restaurant_name"),
                    (String) row.get("restaurant_address"),
                    (String) row.get("restaurant_open_status"),
                    ((Number) row.get("restaurant_avg_rating")).doubleValue()
            ));
            String cuisine = (String) row.get("cuisine_name");
            if (cuisine != null && !cuisine.isBlank() && !builder.cuisines.contains(cuisine)) {
                builder.cuisines.add(cuisine);
            }
        }
        return grouped.values().stream().map(ActiveMenuItemBuilder::build).toList();
    }

    /**
     * Lấy danh sách món bán chạy nhất từ đơn hàng hợp lệ (tận dụng 3000 đơn mô phỏng + đơn thật, loại đơn hủy).
     */
    public List<PopularItemStats> findPopularActiveItems(Long restaurantId, int limit) {
        String sql = """
                SELECT mi.id AS item_id,
                       COUNT(DISTINCT o.id) AS order_count,
                       COALESCE(SUM(oi.quantity), 0) AS quantity_sold
                FROM order_items oi
                JOIN orders o ON o.id = oi.order_id
                JOIN menu_items mi ON mi.id = oi.menu_item_id
                JOIN menu_categories mc ON mc.id = mi.category_id
                JOIN restaurants r ON r.id = mc.restaurant_id
                WHERE """ + VALID_ORDER_STATUS_FILTER + "\n AND " + ACTIVE_ITEM_FILTER + "\n" + """
                  AND (:restaurantId IS NULL OR r.id = :restaurantId)
                GROUP BY mi.id
                ORDER BY quantity_sold DESC, order_count DESC, mi.id ASC
                LIMIT :limit
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("restaurantId", restaurantId)
                .addValue("limit", limit);
        return jdbc.query(sql, params, (rs, i) -> new PopularItemStats(
                rs.getLong("item_id"),
                rs.getLong("order_count"),
                rs.getLong("quantity_sold")
        ));
    }

    /**
     * Tổng hợp lịch sử tương tác (từ user_item_interactions + đơn hàng hợp lệ của riêng user) cho một user.
     */
    public List<UserHistorySignalPayload> findUserHistorySignals(long userId) {
        String interactionSql = """
                SELECT uii.menu_item_id, uii.type, COALESCE(SUM(uii.quantity), COUNT(*)) AS total_qty
                FROM user_item_interactions uii
                JOIN menu_items mi ON mi.id = uii.menu_item_id
                JOIN menu_categories mc ON mc.id = mi.category_id
                JOIN restaurants r ON r.id = mc.restaurant_id
                WHERE uii.user_id = :userId
                  AND """ + ACTIVE_ITEM_FILTER + "\n" + """
                GROUP BY uii.menu_item_id, uii.type
                """;

        List<UserHistorySignalPayload> signals = new ArrayList<>();
        jdbc.query(interactionSql, Map.of("userId", userId), (rs, i) -> {
            long itemId = rs.getLong("menu_item_id");
            String typeStr = rs.getString("type");
            int qty = Math.max(1, rs.getInt("total_qty"));
            InteractionType type = InteractionType.fromString(typeStr);
            double weight = type.weight() * (1.0 + Math.log1p(qty - 1.0));
            return new UserHistorySignalPayload(itemId, weight, type.name());
        }).forEach(signals::add);

        // Bổ sung đơn hàng đã đặt hợp lệ của user (nếu đơn tạo trước khi có listener hoặc dữ liệu seed)
        String orderSql = """
                SELECT oi.menu_item_id, SUM(oi.quantity) AS total_qty
                FROM order_items oi
                JOIN orders o ON o.id = oi.order_id
                JOIN menu_items mi ON mi.id = oi.menu_item_id
                JOIN menu_categories mc ON mc.id = mi.category_id
                JOIN restaurants r ON r.id = mc.restaurant_id
                WHERE o.customer_id = :userId
                  AND oi.menu_item_id IS NOT NULL
                  AND """ + VALID_ORDER_STATUS_FILTER + "\n AND " + ACTIVE_ITEM_FILTER + "\n" + """
                  AND NOT EXISTS (
                      SELECT 1 FROM user_item_interactions uii
                      WHERE uii.order_id = o.id AND uii.menu_item_id = oi.menu_item_id AND uii.type = 'ORDER'
                  )
                GROUP BY oi.menu_item_id
                """;
        jdbc.query(orderSql, Map.of("userId", userId), (rs, i) -> {
            long itemId = rs.getLong("menu_item_id");
            int qty = Math.max(1, rs.getInt("total_qty"));
            double weight = InteractionType.ORDER.weight() * (1.0 + Math.log1p(qty - 1.0));
            return new UserHistorySignalPayload(itemId, weight, InteractionType.ORDER.name());
        }).forEach(signals::add);

        return signals;
    }

    /**
     * Lấy danh sách menu_item_id đang có trong giỏ hàng của user để loại trừ khỏi gợi ý.
     */
    public List<Long> findUserCartItemIds(long userId) {
        String sql = """
                SELECT ci.menu_item_id
                FROM cart_items ci
                JOIN carts c ON c.id = ci.cart_id
                WHERE c.user_id = :userId
                """;
        return jdbc.queryForList(sql, Map.of("userId", userId), Long.class);
    }

    /**
     * Lấy dữ liệu user_item_interactions toàn hệ thống để xây dựng ma trận User-Item.
     */
    public List<UserInteractionPayload> findSnapshotInteractions() {
        String sql = """
                SELECT uii.user_id, uii.menu_item_id, uii.type, SUM(uii.quantity) AS total_qty
                FROM user_item_interactions uii
                JOIN menu_items mi ON mi.id = uii.menu_item_id
                JOIN menu_categories mc ON mc.id = mi.category_id
                JOIN restaurants r ON r.id = mc.restaurant_id
                WHERE """ + ACTIVE_ITEM_FILTER + "\n" + """
                GROUP BY uii.user_id, uii.menu_item_id, uii.type
                LIMIT 15000
                """;
        return jdbc.query(sql, Map.of(), (rs, i) -> {
            long userId = rs.getLong("user_id");
            long itemId = rs.getLong("menu_item_id");
            String typeStr = rs.getString("type");
            int qty = Math.max(1, rs.getInt("total_qty"));
            InteractionType type = InteractionType.fromString(typeStr);
            double weight = type.weight() * (1.0 + Math.log1p(qty - 1.0));
            return new UserInteractionPayload(userId, itemId, weight, type.name());
        });
    }

    /**
     * Lấy giỏ đơn hàng hợp lệ (gồm cả 3000 đơn mô phỏng MOCK-% từ tuần 6, loại đơn đã hủy) cho Co-occurrence.
     */
    public List<OrderBasketPayload> findSnapshotOrderBaskets(int maxOrders) {
        String sql = """
                SELECT o.id AS order_id, o.customer_id, o.code, oi.menu_item_id
                FROM (
                    SELECT id, customer_id, code
                    FROM orders o
                    WHERE """ + VALID_ORDER_STATUS_FILTER + "\n" + """
                    ORDER BY created_at DESC, id DESC
                    LIMIT :maxOrders
                ) o
                JOIN order_items oi ON oi.order_id = o.id
                JOIN menu_items mi ON mi.id = oi.menu_item_id
                JOIN menu_categories mc ON mc.id = mi.category_id
                JOIN restaurants r ON r.id = mc.restaurant_id
                WHERE oi.menu_item_id IS NOT NULL
                  AND """ + ACTIVE_ITEM_FILTER + "\n" + """
                ORDER BY o.id
                """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, Map.of("maxOrders", maxOrders));
        Map<Long, OrderBasketBuilder> baskets = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            long orderId = ((Number) row.get("order_id")).longValue();
            long customerId = ((Number) row.get("customer_id")).longValue();
            String code = (String) row.get("code");
            long itemId = ((Number) row.get("menu_item_id")).longValue();
            boolean simulated = code != null && code.startsWith("MOCK-");

            OrderBasketBuilder builder = baskets.computeIfAbsent(
                    orderId,
                    id -> new OrderBasketBuilder(id, customerId, simulated)
            );
            builder.itemIds.add(itemId);
        }
        return baskets.values().stream()
                .map(b -> new OrderBasketPayload(b.orderId, b.userId, b.itemIds, b.isSimulated))
                .toList();
    }

    public List<PopularItemPayload> toPopularPayloads(List<PopularItemStats> stats) {
        return stats.stream()
                .map(s -> new PopularItemPayload(s.itemId(), s.orderCount(), s.quantitySold()))
                .toList();
    }

    public List<ActiveItemPayload> toActivePayloads(List<ActiveMenuItemRecord> records) {
        return records.stream()
                .map(r -> new ActiveItemPayload(
                        r.id(),
                        r.restaurantId(),
                        r.name(),
                        r.price(),
                        r.cuisineNames(),
                        r.isAvailable()
                ))
                .toList();
    }

    private static boolean toBool(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        return ((Number) val).intValue() != 0;
    }

    public record ActiveMenuItemRecord(
            long id,
            String name,
            String description,
            long price,
            String imageUrl,
            boolean isAvailable,
            long restaurantId,
            String restaurantName,
            String restaurantAddress,
            String restaurantOpenStatus,
            double restaurantAvgRating,
            List<String> cuisineNames
    ) {
    }

    public record PopularItemStats(long itemId, long orderCount, long quantitySold) {
    }

    private static final class ActiveMenuItemBuilder {
        final long id;
        final String name;
        final String description;
        final long price;
        final String imageUrl;
        final boolean isAvailable;
        final long restaurantId;
        final String restaurantName;
        final String restaurantAddress;
        final String restaurantOpenStatus;
        final double restaurantAvgRating;
        final List<String> cuisines = new ArrayList<>();

        ActiveMenuItemBuilder(long id, String name, String description, long price, String imageUrl,
                              boolean isAvailable, long restaurantId, String restaurantName,
                              String restaurantAddress, String restaurantOpenStatus, double restaurantAvgRating) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.price = price;
            this.imageUrl = imageUrl;
            this.isAvailable = isAvailable;
            this.restaurantId = restaurantId;
            this.restaurantName = restaurantName;
            this.restaurantAddress = restaurantAddress;
            this.restaurantOpenStatus = restaurantOpenStatus;
            this.restaurantAvgRating = restaurantAvgRating;
        }

        ActiveMenuItemRecord build() {
            return new ActiveMenuItemRecord(
                    id, name, description, price, imageUrl, isAvailable,
                    restaurantId, restaurantName, restaurantAddress, restaurantOpenStatus,
                    restaurantAvgRating, List.copyOf(cuisines)
            );
        }
    }

    private static final class OrderBasketBuilder {
        final long orderId;
        final long userId;
        final boolean isSimulated;
        final List<Long> itemIds = new ArrayList<>();

        OrderBasketBuilder(long orderId, long userId, boolean isSimulated) {
            this.orderId = orderId;
            this.userId = userId;
            this.isSimulated = isSimulated;
        }
    }
}

