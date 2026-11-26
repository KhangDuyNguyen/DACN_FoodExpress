package vn.hutech.fooddelivery.order;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import vn.hutech.fooddelivery.cart.CartLine;
import vn.hutech.fooddelivery.cart.CartLineOption;
import vn.hutech.fooddelivery.order.dto.AdminOrderSummaryResponse;
import vn.hutech.fooddelivery.order.dto.MerchantOrderSummaryResponse;
import vn.hutech.fooddelivery.order.dto.OrderHistoryResponse;
import vn.hutech.fooddelivery.order.dto.OrderItemOptionResponse;
import vn.hutech.fooddelivery.order.dto.OrderItemResponse;
import vn.hutech.fooddelivery.order.dto.OrderSummaryResponse;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** SQL thuan cho bang orders, order_items, order_item_options, order_status_history (va doc phi ship, quan). */
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

    /**
     * Them 1 don (PLACED voi COD, PENDING_PAYMENT voi VNPay), tra ve id.
     * Trung ma don (cot code UNIQUE) thi nem DuplicateKeyException.
     */
    public long insertOrder(NewOrder order) {
        String sql = """
                INSERT INTO orders (code, customer_id, restaurant_id, status,
                                    receiver_name, receiver_phone, delivery_address, delivery_lat, delivery_lng, note,
                                    subtotal, delivery_fee, discount_amount, total_amount, payment_method, distance_km)
                VALUES (:code, :customerId, :restaurantId, :status,
                        :receiverName, :receiverPhone, :deliveryAddress, :deliveryLat, :deliveryLng, :note,
                        :subtotal, :deliveryFee, :discountAmount, :totalAmount, :paymentMethod, :distanceKm)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", order.code())
                .addValue("customerId", order.customerId())
                .addValue("restaurantId", order.restaurantId())
                .addValue("status", order.status())
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

    /**
     * Luu mon va cac tuy chon cua mon TAI THOI DIEM DAT (ten, gia), de sau nay quan doi menu thi don cu khong doi.
     * unit_price = gia mon + gia cac tuy chon, nen thanh tien van la unit_price x quantity.
     */
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
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder, new String[]{"id"});
        long orderItemId = keyHolder.getKey().longValue();

        String optionSql = """
                INSERT INTO order_item_options (order_item_id, option_id, option_name, extra_price)
                VALUES (:orderItemId, :optionId, :optionName, :extraPrice)
                """;
        for (CartLineOption option : line.options()) {
            jdbc.update(optionSql, new MapSqlParameterSource()
                    .addValue("orderItemId", orderItemId)
                    .addValue("optionId", option.id())
                    .addValue("optionName", option.name())
                    .addValue("extraPrice", option.extraPrice()));
        }
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
                SELECT o.id, o.code, o.customer_id, u.full_name AS customer_name, o.restaurant_id,
                       r.name AS restaurant_name, r.phone AS restaurant_phone, r.address AS restaurant_address,
                       o.status, o.receiver_name, o.receiver_phone, o.delivery_address, o.note,
                       o.subtotal, o.delivery_fee, o.discount_amount, o.total_amount, o.payment_method,
                       o.distance_km, o.cancel_reason, o.created_at, o.accepted_at, o.delivered_at, o.cancelled_at,
                       o.driver_id, du.full_name AS driver_name, du.phone AS driver_phone,
                       d.vehicle_type, d.license_plate
                FROM orders o
                JOIN restaurants r ON r.id = o.restaurant_id
                JOIN users u ON u.id = o.customer_id
                LEFT JOIN drivers d ON d.id = o.driver_id
                LEFT JOIN users du ON du.id = d.user_id
                WHERE o.id = :id
                """;
        return jdbc.query(sql, Map.of("id", orderId), (rs, i) -> new OrderRow(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getLong("customer_id"),
                rs.getString("customer_name"),
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
                toLocalDateTime(rs.getTimestamp("cancelled_at")),
                // getObject(..., Long.class) giu duoc NULL khi don chua co tai xe
                rs.getObject("driver_id", Long.class),
                rs.getString("driver_name"),
                rs.getString("driver_phone"),
                rs.getString("vehicle_type"),
                rs.getString("license_plate")
        )).stream().findFirst();
    }

    /**
     * Doc vai cot cua don VA KHOA dong do den het transaction (SELECT ... FOR UPDATE).
     * Luc tai xe nhan don (code cua An) cung ghi vao dong nay, nen 2 ben phai cho nhau, khong chen ngang:
     * vd khach bam huy dung luc tai xe bam nhan thi 1 trong 2 phai doi ben kia xong roi doc lai.
     * Chi khoa bang orders (khong JOIN) de khong khoa lan sang users, restaurants. (Y tuong tu PR #18 cua An.)
     */
    public Optional<LockedOrder> lockForUpdate(long orderId) {
        String sql = """
                SELECT id, customer_id, restaurant_id, driver_id, status, delivery_fee
                FROM orders WHERE id = :id
                FOR UPDATE
                """;
        return jdbc.query(sql, Map.of("id", orderId), (rs, i) -> new LockedOrder(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                rs.getLong("restaurant_id"),
                rs.getObject("driver_id", Long.class),
                rs.getString("status"),
                rs.getBigDecimal("delivery_fee")
        )).stream().findFirst();
    }

    /** Vai cot cua don doc kem khoa dong (lockForUpdate). driverId null = chua co tai xe nhan. */
    public record LockedOrder(long id, long customerId, long restaurantId, Long driverId, String status, BigDecimal deliveryFee) {
    }

    /** id tai xe (bang drivers cua module An) ung voi tai khoan users.id; chua dang ky tai xe thi rong. */
    public Optional<Long> findDriverIdByUserId(long userId) {
        return jdbc.queryForList("SELECT id FROM drivers WHERE user_id = :userId",
                Map.of("userId", userId), Long.class).stream().findFirst();
    }

    /** Cac mon (kem tuy chon) cua 1 don. Dung chung cach doc voi findItemsByOrderIds. */
    public List<OrderItemResponse> findItems(long orderId) {
        return findItemsByOrderIds(List.of(orderId)).getOrDefault(orderId, List.of());
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

    // ----- Danh sach don (Module D) -----

    private static final RowMapper<OrderSummaryResponse> SUMMARY_MAPPER = (rs, i) -> new OrderSummaryResponse(
            rs.getLong("id"),
            rs.getString("code"),
            rs.getLong("restaurant_id"),
            rs.getString("restaurant_name"),
            rs.getString("status"),
            rs.getBigDecimal("total_amount"),
            rs.getInt("item_count"),
            rs.getString("receiver_name"),
            rs.getString("delivery_address"),
            rs.getString("driver_name"),
            toLocalDateTime(rs.getTimestamp("created_at"))
    );

    /** Don cua 1 khach, moi nhat truoc. status null = moi trang thai. */
    public List<OrderSummaryResponse> findSummariesByCustomer(long customerId, String status, int limit, long offset) {
        String sql = """
                SELECT o.id, o.code, o.restaurant_id, r.name AS restaurant_name, o.status, o.total_amount,
                       (SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi WHERE oi.order_id = o.id) AS item_count,
                       o.receiver_name, o.delivery_address, o.created_at, du.full_name AS driver_name
                FROM orders o
                JOIN restaurants r ON r.id = o.restaurant_id
                LEFT JOIN drivers d ON d.id = o.driver_id
                LEFT JOIN users du ON du.id = d.user_id
                WHERE o.customer_id = :customerId
                  AND (:status IS NULL OR o.status = :status)
                ORDER BY o.created_at DESC, o.id DESC
                LIMIT :limit OFFSET :offset
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("customerId", customerId)
                .addValue("status", status)
                .addValue("limit", limit)
                .addValue("offset", offset);
        return jdbc.query(sql, params, SUMMARY_MAPPER);
    }

    public long countByCustomer(long customerId, String status) {
        String sql = """
                SELECT COUNT(*) FROM orders o
                WHERE o.customer_id = :customerId
                  AND (:status IS NULL OR o.status = :status)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("customerId", customerId)
                .addValue("status", status);
        Long total = jdbc.queryForObject(sql, params, Long.class);
        return total == null ? 0 : total;
    }

    /**
     * Don cua cac quan thuoc chu quan ownerUserId (JOIN merchants theo owner_user_id).
     * restaurantId null = moi chi nhanh cua chu quan; status null = moi trang thai.
     */
    public List<OrderSummaryResponse> findSummariesByOwner(long ownerUserId, Long restaurantId, String status,
                                                           int limit, long offset) {
        String sql = """
                SELECT o.id, o.code, o.restaurant_id, r.name AS restaurant_name, o.status, o.total_amount,
                       (SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi WHERE oi.order_id = o.id) AS item_count,
                       o.receiver_name, o.delivery_address, o.created_at, du.full_name AS driver_name
                FROM orders o
                JOIN restaurants r ON r.id = o.restaurant_id
                JOIN merchants m ON m.id = r.merchant_id
                LEFT JOIN drivers d ON d.id = o.driver_id
                LEFT JOIN users du ON du.id = d.user_id
                WHERE m.owner_user_id = :ownerUserId
                  AND (:restaurantId IS NULL OR o.restaurant_id = :restaurantId)
                  AND (:status IS NULL OR o.status = :status)
                  -- Quan chi thay don COD hoac don VNPay DA tra tien (don chua tra tien quan chua tung nhan)
                  AND (o.payment_method = 'COD'
                       OR EXISTS (SELECT 1 FROM payments p
                                  WHERE p.order_id = o.id AND p.status IN ('SUCCESS', 'REFUNDED')))
                ORDER BY o.created_at DESC, o.id DESC
                LIMIT :limit OFFSET :offset
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ownerUserId", ownerUserId)
                .addValue("restaurantId", restaurantId)
                .addValue("status", status)
                .addValue("limit", limit)
                .addValue("offset", offset);
        return jdbc.query(sql, params, SUMMARY_MAPPER);
    }

    public long countByOwner(long ownerUserId, Long restaurantId, String status) {
        String sql = """
                SELECT COUNT(*)
                FROM orders o
                JOIN restaurants r ON r.id = o.restaurant_id
                JOIN merchants m ON m.id = r.merchant_id
                WHERE m.owner_user_id = :ownerUserId
                  AND (:restaurantId IS NULL OR o.restaurant_id = :restaurantId)
                  AND (:status IS NULL OR o.status = :status)
                  -- Quan chi thay don COD hoac don VNPay DA tra tien (don chua tra tien quan chua tung nhan)
                  AND (o.payment_method = 'COD'
                       OR EXISTS (SELECT 1 FROM payments p
                                  WHERE p.order_id = o.id AND p.status IN ('SUCCESS', 'REFUNDED')))
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ownerUserId", ownerUserId)
                .addValue("restaurantId", restaurantId)
                .addValue("status", status);
        Long total = jdbc.queryForObject(sql, params, Long.class);
        return total == null ? 0 : total;
    }

    /** Quan restaurantId co thuoc chu quan ownerUserId khong. */
    public boolean isRestaurantOwnedBy(long restaurantId, long ownerUserId) {
        String sql = """
                SELECT COUNT(*)
                FROM restaurants r
                JOIN merchants m ON m.id = r.merchant_id
                WHERE r.id = :restaurantId AND m.owner_user_id = :ownerUserId
                """;
        Long count = jdbc.queryForObject(sql, Map.of("restaurantId", restaurantId, "ownerUserId", ownerUserId),
                Long.class);
        return count != null && count > 0;
    }

    /** Cung dieu kien voi danh sach don cua quan: don COD, hoac don VNPay da tra tien. */
    public boolean isVisibleToMerchant(long orderId) {
        String sql = """
                SELECT COUNT(*) FROM orders o
                WHERE o.id = :orderId
                  AND (o.payment_method = 'COD'
                       OR EXISTS (SELECT 1 FROM payments p
                                  WHERE p.order_id = o.id AND p.status IN ('SUCCESS', 'REFUNDED')))
                """;
        Long count = jdbc.queryForObject(sql, Map.of("orderId", orderId), Long.class);
        return count != null && count > 0;
    }

    /** Kiểm tra nhà hàng có tồn tại trong hệ thống không. */
    public boolean restaurantExists(long restaurantId) {
        String sql = "SELECT COUNT(*) FROM restaurants WHERE id = :restaurantId";
        Long count = jdbc.queryForObject(sql, Map.of("restaurantId", restaurantId), Long.class);
        return count != null && count > 0;
    }

    /**
     * Danh sách đơn của cửa hàng kèm danh sách món ăn, SĐT nhận hàng và ghi chú đơn hàng.
     * Dùng truy vấn batch order_items theo danh sách orderIds để tránh N+1 query.
     */
    public List<MerchantOrderSummaryResponse> findMerchantSummariesByOwner(long ownerUserId, Long restaurantId,
                                                                           String status, int limit, long offset) {
        String sql = """
                SELECT o.id, o.code, o.restaurant_id, r.name AS restaurant_name, o.status, o.total_amount,
                       (SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi WHERE oi.order_id = o.id) AS item_count,
                       o.receiver_name, o.receiver_phone, o.delivery_address, o.note, o.payment_method, o.created_at,
                       du.full_name AS driver_name
                FROM orders o
                JOIN restaurants r ON r.id = o.restaurant_id
                JOIN merchants m ON m.id = r.merchant_id
                LEFT JOIN drivers d ON d.id = o.driver_id
                LEFT JOIN users du ON du.id = d.user_id
                WHERE m.owner_user_id = :ownerUserId
                  AND (:restaurantId IS NULL OR o.restaurant_id = :restaurantId)
                  AND (:status IS NULL OR o.status = :status)
                  -- Quan chi thay don COD hoac don VNPay DA tra tien (don chua tra tien quan chua tung nhan)
                  AND (o.payment_method = 'COD'
                       OR EXISTS (SELECT 1 FROM payments p
                                  WHERE p.order_id = o.id AND p.status IN ('SUCCESS', 'REFUNDED')))
                ORDER BY o.created_at DESC, o.id DESC
                LIMIT :limit OFFSET :offset
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ownerUserId", ownerUserId)
                .addValue("restaurantId", restaurantId)
                .addValue("status", status)
                .addValue("limit", limit)
                .addValue("offset", offset);

        List<MerchantOrderSummaryResponse> baseRows = jdbc.query(sql, params, (rs, i) -> new MerchantOrderSummaryResponse(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getLong("restaurant_id"),
                rs.getString("restaurant_name"),
                rs.getString("status"),
                rs.getBigDecimal("total_amount"),
                rs.getInt("item_count"),
                rs.getString("receiver_name"),
                rs.getString("receiver_phone"),
                rs.getString("delivery_address"),
                rs.getString("note"),
                rs.getString("payment_method"),
                List.of(),
                toLocalDateTime(rs.getTimestamp("created_at")),
                rs.getString("driver_name")
        ));

        if (baseRows.isEmpty()) {
            return baseRows;
        }

        List<Long> orderIds = baseRows.stream().map(MerchantOrderSummaryResponse::id).toList();
        Map<Long, List<OrderItemResponse>> itemsByOrderId = findItemsByOrderIds(orderIds);

        return baseRows.stream()
                .map(row -> new MerchantOrderSummaryResponse(
                        row.id(),
                        row.code(),
                        row.restaurantId(),
                        row.restaurantName(),
                        row.status(),
                        row.totalAmount(),
                        row.itemCount(),
                        row.receiverName(),
                        row.receiverPhone(),
                        row.deliveryAddress(),
                        row.note(),
                        row.paymentMethod(),
                        itemsByOrderId.getOrDefault(row.id(), List.of()),
                        row.createdAt(),
                        row.driverName()
                ))
                .toList();
    }

    public Map<Long, List<OrderItemResponse>> findItemsByOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<OrderItemOptionResponse>> optionsByItemId = findOptionsByOrderIds(orderIds);
        String sql = """
                SELECT id, order_id, menu_item_id, item_name, unit_price, quantity, note
                FROM order_items
                WHERE order_id IN (:orderIds)
                ORDER BY id
                """;
        Map<Long, List<OrderItemResponse>> result = new LinkedHashMap<>();
        jdbc.query(sql, Map.of("orderIds", orderIds), rs -> {
            long orderId = rs.getLong("order_id");
            BigDecimal unitPrice = rs.getBigDecimal("unit_price");
            int quantity = rs.getInt("quantity");
            OrderItemResponse item = new OrderItemResponse(
                    rs.getObject("menu_item_id", Long.class),
                    rs.getString("item_name"),
                    unitPrice,
                    quantity,
                    rs.getString("note"),
                    unitPrice.multiply(BigDecimal.valueOf(quantity)),
                    optionsByItemId.getOrDefault(rs.getLong("id"), List.of())
            );
            result.computeIfAbsent(orderId, k -> new ArrayList<>()).add(item);
        });
        return result;
    }

    /** Tuy chon cua cac mon trong nhung don nay (ten, gia LUC DAT), gom theo order_items.id. */
    private Map<Long, List<OrderItemOptionResponse>> findOptionsByOrderIds(List<Long> orderIds) {
        String sql = """
                SELECT oio.order_item_id, oio.option_name, oio.extra_price
                FROM order_item_options oio
                JOIN order_items oi ON oi.id = oio.order_item_id
                WHERE oi.order_id IN (:orderIds)
                ORDER BY oio.id
                """;
        Map<Long, List<OrderItemOptionResponse>> result = new HashMap<>();
        jdbc.query(sql, Map.of("orderIds", orderIds), rs -> {
            OrderItemOptionResponse option = new OrderItemOptionResponse(rs.getString("option_name"),
                    rs.getBigDecimal("extra_price"));
            result.computeIfAbsent(rs.getLong("order_item_id"), k -> new ArrayList<>()).add(option);
        });
        return result;
    }

    // ----- Don toan san cho admin (tuan 6) -----

    /** FROM + WHERE dung chung cho danh sach va dem. Admin thay MOI don, ke ca don VNPay chua tra tien. */
    private static final String ADMIN_FROM_WHERE = """
             FROM orders o
             JOIN restaurants r ON r.id = o.restaurant_id
             JOIN users cu ON cu.id = o.customer_id
             LEFT JOIN drivers d ON d.id = o.driver_id
             LEFT JOIN users du ON du.id = d.user_id
             WHERE (:status IS NULL OR o.status = :status)
               AND (:restaurantId IS NULL OR o.restaurant_id = :restaurantId)
               AND (:paymentMethod IS NULL OR o.payment_method = :paymentMethod)
               AND (:keyword IS NULL OR o.code LIKE :keyword OR cu.phone LIKE :keyword)
               AND (:fromTime IS NULL OR o.created_at >= :fromTime)
               AND (:toTime IS NULL OR o.created_at < :toTime)
            """;

    public List<AdminOrderSummaryResponse> findAdminSummaries(AdminOrderFilter filter, int limit, long offset) {
        String sql = """
                SELECT o.id, o.code, o.restaurant_id, r.name AS restaurant_name,
                       cu.full_name AS customer_name, cu.phone AS customer_phone,
                       o.status, o.payment_method, o.total_amount, du.full_name AS driver_name, o.created_at
                """ + ADMIN_FROM_WHERE + " ORDER BY o.created_at DESC, o.id DESC LIMIT :limit OFFSET :offset";
        MapSqlParameterSource params = adminFilterParams(filter)
                .addValue("limit", limit)
                .addValue("offset", offset);
        return jdbc.query(sql, params, (rs, i) -> new AdminOrderSummaryResponse(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getLong("restaurant_id"),
                rs.getString("restaurant_name"),
                rs.getString("customer_name"),
                rs.getString("customer_phone"),
                rs.getString("status"),
                rs.getString("payment_method"),
                rs.getBigDecimal("total_amount"),
                rs.getString("driver_name"),
                toLocalDateTime(rs.getTimestamp("created_at"))
        ));
    }

    public long countAdmin(AdminOrderFilter filter) {
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + ADMIN_FROM_WHERE, adminFilterParams(filter), Long.class);
        return total == null ? 0 : total;
    }

    /**
     * Doi bo loc thanh tham so SQL: tu khoa tim theo DAU chuoi (LIKE 'abc%'), ngay "den" tinh ca ngay do
     * (created_at < 0 gio ngay hom sau).
     */
    private static MapSqlParameterSource adminFilterParams(AdminOrderFilter f) {
        return new MapSqlParameterSource()
                .addValue("status", f.status())
                .addValue("restaurantId", f.restaurantId())
                .addValue("paymentMethod", f.paymentMethod())
                .addValue("keyword", f.keyword() == null ? null : f.keyword() + "%")
                .addValue("fromTime", f.fromDate() == null ? null : f.fromDate().atStartOfDay())
                .addValue("toTime", f.toDate() == null ? null : f.toDate().plusDays(1).atStartOfDay());
    }

    // ----- Doi trang thai (Module D) -----

    /**
     * Doi trang thai CHI KHI don van dang o fromStatus (dieu kien "AND status = :fromStatus").
     * Neu giua luc doc va luc ghi co nguoi khac vua doi, cau lenh khop 0 dong -> tra ve 0.
     * Cac moc thoi gian va ly do huy duoc ghi kem theo trang thai moi.
     */
    public int updateStatus(long orderId, String fromStatus, String toStatus, String cancelReason) {
        String sql = """
                UPDATE orders
                SET status = :toStatus,
                    accepted_at   = CASE WHEN :toStatus = 'PREPARING' THEN NOW() ELSE accepted_at END,
                    delivered_at  = CASE WHEN :toStatus = 'DELIVERED' THEN NOW() ELSE delivered_at END,
                    cancelled_at  = CASE WHEN :toStatus = 'CANCELLED' THEN NOW() ELSE cancelled_at END,
                    cancel_reason = CASE WHEN :toStatus = 'CANCELLED' THEN :cancelReason ELSE cancel_reason END
                WHERE id = :orderId AND status = :fromStatus
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("toStatus", toStatus)
                .addValue("cancelReason", cancelReason)
                .addValue("orderId", orderId)
                .addValue("fromStatus", fromStatus);
        return jdbc.update(sql, params);
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
