package vn.hutech.fooddelivery.order;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import vn.hutech.fooddelivery.cart.CartLine;
import vn.hutech.fooddelivery.order.dto.OrderHistoryResponse;
import vn.hutech.fooddelivery.order.dto.OrderItemResponse;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** SQL thuan cho bang orders, order_items, order_status_history (va doc phi ship, quan). */
@Repository
public class OrderRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public OrderRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ----- Doc du lieu de tinh tien -----

    public Optional<RestaurantForOrder> findRestaurant(long restaurantId) {
        String sql = """
                SELECT id, name, lat, lng, status, open_status, min_order_amount
                FROM restaurants WHERE id = :id
                """;
        return jdbc.query(sql, Map.of("id", restaurantId), (rs, i) -> new RestaurantForOrder(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getDouble("lat"),
                rs.getDouble("lng"),
                rs.getString("status"),
                rs.getString("open_status"),
                rs.getBigDecimal("min_order_amount")
        )).stream().findFirst();
    }

    /** Dong phi ship dang bat moi nhat (id lon nhat). */
    public Optional<DeliveryFeeRule> findActiveFeeRule() {
        String sql = """
                SELECT base_fee, base_distance_km, per_km_fee
                FROM delivery_fee_rules
                WHERE is_active = 1
                ORDER BY id DESC
                LIMIT 1
                """;
        return jdbc.query(sql, Map.of(), (rs, i) -> new DeliveryFeeRule(
                rs.getBigDecimal("base_fee"),
                rs.getBigDecimal("base_distance_km"),
                rs.getBigDecimal("per_km_fee")
        )).stream().findFirst();
    }

    // ----- Ghi don -----

    /** Them 1 don trang thai PLACED, tra ve id. Trung ma don (cot code UNIQUE) thi nem DuplicateKeyException. */
    public long insertOrder(NewOrder order) {
        String sql = """
                INSERT INTO orders (code, customer_id, restaurant_id, status,
                                    receiver_name, receiver_phone, delivery_address, delivery_lat, delivery_lng, note,
                                    subtotal, delivery_fee, discount_amount, total_amount, payment_method, distance_km)
                VALUES (:code, :customerId, :restaurantId, 'PLACED',
                        :receiverName, :receiverPhone, :deliveryAddress, :deliveryLat, :deliveryLng, :note,
                        :subtotal, :deliveryFee, :discountAmount, :totalAmount, :paymentMethod, :distanceKm)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", order.code())
                .addValue("customerId", order.customerId())
                .addValue("restaurantId", order.restaurantId())
                .addValue("receiverName", order.receiverName())
                .addValue("receiverPhone", order.receiverPhone())
                .addValue("deliveryAddress", order.deliveryAddress())
                .addValue("deliveryLat", order.deliveryLat())
                .addValue("deliveryLng", order.deliveryLng())
                .addValue("note", order.note())
                .addValue("subtotal", order.subtotal())
                .addValue("deliveryFee", order.deliveryFee())
                .addValue("discountAmount", order.discountAmount())
                .addValue("totalAmount", order.totalAmount())
                .addValue("paymentMethod", order.paymentMethod())
                .addValue("distanceKm", order.distanceKm());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder, new String[]{"id"});
        return keyHolder.getKey().longValue();
    }

    /** Luu ten va don gia cua mon TAI THOI DIEM DAT, de sau nay quan doi menu thi don cu khong doi. */
    public void insertOrderItem(long orderId, CartLine line) {
        String sql = """
                INSERT INTO order_items (order_id, menu_item_id, item_name, unit_price, quantity, note)
                VALUES (:orderId, :menuItemId, :itemName, :unitPrice, :quantity, :note)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("menuItemId", line.menuItemId())
                .addValue("itemName", line.name())
                .addValue("unitPrice", line.unitPrice())
                .addValue("quantity", line.quantity())
                .addValue("note", line.note());
        jdbc.update(sql, params);
    }

    public void insertStatusHistory(long orderId, String status, long changedBy, String note) {
        String sql = """
                INSERT INTO order_status_history (order_id, status, changed_by, note)
                VALUES (:orderId, :status, :changedBy, :note)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("status", status)
                .addValue("changedBy", changedBy)
                .addValue("note", note);
        jdbc.update(sql, params);
    }

    // ----- Doc don -----

    public Optional<OrderRow> findById(long orderId) {
        String sql = """
                SELECT o.id, o.code, o.customer_id, o.restaurant_id,
                       r.name AS restaurant_name, r.phone AS restaurant_phone, r.address AS restaurant_address,
                       o.status, o.receiver_name, o.receiver_phone, o.delivery_address, o.note,
                       o.subtotal, o.delivery_fee, o.discount_amount, o.total_amount, o.payment_method,
                       o.distance_km, o.cancel_reason, o.created_at, o.accepted_at, o.delivered_at, o.cancelled_at
                FROM orders o
                JOIN restaurants r ON r.id = o.restaurant_id
                WHERE o.id = :id
                """;
        return jdbc.query(sql, Map.of("id", orderId), (rs, i) -> new OrderRow(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getLong("customer_id"),
                rs.getLong("restaurant_id"),
                rs.getString("restaurant_name"),
                rs.getString("restaurant_phone"),
                rs.getString("restaurant_address"),
                rs.getString("status"),
                rs.getString("receiver_name"),
                rs.getString("receiver_phone"),
                rs.getString("delivery_address"),
                rs.getString("note"),
                rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("delivery_fee"),
                rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("total_amount"),
                rs.getString("payment_method"),
                rs.getBigDecimal("distance_km"),
                rs.getString("cancel_reason"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("accepted_at")),
                toLocalDateTime(rs.getTimestamp("delivered_at")),
                toLocalDateTime(rs.getTimestamp("cancelled_at"))
        )).stream().findFirst();
    }

    public List<OrderItemResponse> findItems(long orderId) {
        String sql = """
                SELECT menu_item_id, item_name, unit_price, quantity, note
                FROM order_items
                WHERE order_id = :orderId
                ORDER BY id
                """;
        return jdbc.query(sql, Map.of("orderId", orderId), (rs, i) -> {
            BigDecimal unitPrice = rs.getBigDecimal("unit_price");
            int quantity = rs.getInt("quantity");
            return new OrderItemResponse(
                    rs.getObject("menu_item_id", Long.class),
                    rs.getString("item_name"),
                    unitPrice,
                    quantity,
                    rs.getString("note"),
                    unitPrice.multiply(BigDecimal.valueOf(quantity))
            );
        });
    }

    public List<OrderHistoryResponse> findHistory(long orderId) {
        String sql = """
                SELECT status, changed_at, note
                FROM order_status_history
                WHERE order_id = :orderId
                ORDER BY changed_at, id
                """;
        return jdbc.query(sql, Map.of("orderId", orderId), (rs, i) -> new OrderHistoryResponse(
                rs.getString("status"),
                toLocalDateTime(rs.getTimestamp("changed_at")),
                rs.getString("note")
        ));
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
