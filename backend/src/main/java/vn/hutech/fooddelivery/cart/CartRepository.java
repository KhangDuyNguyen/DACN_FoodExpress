package vn.hutech.fooddelivery.cart;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** SQL thuan cho bang carts va cart_items. */
@Repository
public class CartRepository {

    private static final RowMapper<CartRow> CART_MAPPER =
            (rs, i) -> new CartRow(rs.getLong("id"), rs.getLong("restaurant_id"));

    private final NamedParameterJdbcTemplate jdbc;

    public CartRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ----- Bang carts -----

    public Optional<CartRow> findByUserId(long userId) {
        String sql = "SELECT id, restaurant_id FROM carts WHERE user_id = :userId";
        return jdbc.query(sql, Map.of("userId", userId), CART_MAPPER).stream().findFirst();
    }

    /**
     * Giong findByUserId nhung khoa dong gio (FOR UPDATE) den khi transaction ket thuc.
     * Request khac muon sua cung gio phai cho, nen 2 request cung luc khong ghi de len nhau.
     */
    public Optional<CartRow> findByUserIdForUpdate(long userId) {
        String sql = "SELECT id, restaurant_id FROM carts WHERE user_id = :userId FOR UPDATE";
        return jdbc.query(sql, Map.of("userId", userId), CART_MAPPER).stream().findFirst();
    }

    /** Tao gio moi gan voi quan restaurantId, tra ve id vua tao. */
    public long insertCart(long userId, long restaurantId) {
        String sql = "INSERT INTO carts (user_id, restaurant_id) VALUES (:userId, :restaurantId)";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("restaurantId", restaurantId);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder, new String[]{"id"});
        return keyHolder.getKey().longValue();
    }

    public void updateRestaurant(long cartId, long restaurantId) {
        String sql = "UPDATE carts SET restaurant_id = :restaurantId WHERE id = :cartId";
        jdbc.update(sql, Map.of("restaurantId", restaurantId, "cartId", cartId));
    }

    /** Xoa gio; cac dong cart_items tu xoa theo (khoa ngoai ON DELETE CASCADE trong V1). */
    public void deleteCart(long cartId) {
        jdbc.update("DELETE FROM carts WHERE id = :cartId", Map.of("cartId", cartId));
    }

    public void deleteByUserId(long userId) {
        jdbc.update("DELETE FROM carts WHERE user_id = :userId", Map.of("userId", userId));
    }

    // ----- Bang cart_items -----

    /**
     * Cac dong trong gio, ghep voi menu_items de lay ten, gia, trang thai hien tai cua mon.
     * available = mon con ban VA nhom mon dang bat.
     */
    public List<CartLine> findLines(long cartId) {
        String sql = """
                SELECT ci.id, ci.menu_item_id, mi.name, mi.image_url, mi.price, ci.quantity, ci.note,
                       (mi.is_available = 1 AND mc.is_active = 1) AS available
                FROM cart_items ci
                JOIN menu_items mi ON mi.id = ci.menu_item_id
                JOIN menu_categories mc ON mc.id = mi.category_id
                WHERE ci.cart_id = :cartId
                ORDER BY ci.id
                """;
        return jdbc.query(sql, Map.of("cartId", cartId), (rs, i) -> new CartLine(
                rs.getLong("id"),
                rs.getLong("menu_item_id"),
                rs.getString("name"),
                rs.getString("image_url"),
                rs.getBigDecimal("price"),
                rs.getInt("quantity"),
                rs.getString("note"),
                rs.getBoolean("available")
        ));
    }

    public int countItems(long cartId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE cart_id = :cartId",
                Map.of("cartId", cartId), Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * Tim dong da co cung mon va cung ghi chu, de cong don so luong thay vi them dong moi.
     * "<=>" la phep so sanh bang cua MySQL coi NULL <=> NULL la dung (dau "=" thi NULL = NULL la sai).
     */
    public Optional<CartItemRow> findSameItem(long cartId, long menuItemId, String note) {
        String sql = """
                SELECT id, quantity FROM cart_items
                WHERE cart_id = :cartId AND menu_item_id = :menuItemId AND note <=> :note
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cartId", cartId)
                .addValue("menuItemId", menuItemId)
                .addValue("note", note);
        return jdbc.query(sql, params, (rs, i) -> new CartItemRow(rs.getLong("id"), rs.getInt("quantity")))
                .stream().findFirst();
    }

    public void insertItem(long cartId, long menuItemId, int quantity, String note) {
        String sql = """
                INSERT INTO cart_items (cart_id, menu_item_id, quantity, note)
                VALUES (:cartId, :menuItemId, :quantity, :note)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cartId", cartId)
                .addValue("menuItemId", menuItemId)
                .addValue("quantity", quantity)
                .addValue("note", note);
        jdbc.update(sql, params);
    }

    public void updateQuantity(long cartItemId, int quantity) {
        String sql = "UPDATE cart_items SET quantity = :quantity WHERE id = :id";
        jdbc.update(sql, Map.of("quantity", quantity, "id", cartItemId));
    }

    public void deleteAllItems(long cartId) {
        jdbc.update("DELETE FROM cart_items WHERE cart_id = :cartId", Map.of("cartId", cartId));
    }

    /**
     * Sua 1 dong, CHI KHI dong do nam trong gio cua userId (JOIN carts kiem tra chu gio).
     * Tra ve so dong khop: 0 nghia la khong co hoac la gio cua nguoi khac.
     */
    public int updateItemOfUser(long userId, long cartItemId, int quantity, String note) {
        String sql = """
                UPDATE cart_items ci
                JOIN carts c ON c.id = ci.cart_id
                SET ci.quantity = :quantity, ci.note = :note
                WHERE ci.id = :cartItemId AND c.user_id = :userId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("quantity", quantity)
                .addValue("note", note)
                .addValue("cartItemId", cartItemId)
                .addValue("userId", userId);
        return jdbc.update(sql, params);
    }

    /** Xoa 1 dong, chi khi dong do nam trong gio cua userId. Tra ve so dong da xoa. */
    public int deleteItemOfUser(long userId, long cartItemId) {
        String sql = """
                DELETE ci FROM cart_items ci
                JOIN carts c ON c.id = ci.cart_id
                WHERE ci.id = :cartItemId AND c.user_id = :userId
                """;
        return jdbc.update(sql, Map.of("cartItemId", cartItemId, "userId", userId));
    }

    // ----- Doc bang cua module menu / restaurant (chi SELECT) -----

    public Optional<MenuItemInfo> findMenuItemInfo(long menuItemId) {
        String sql = """
                SELECT mi.id, mi.name, mi.is_available, mc.is_active AS category_active,
                       mc.restaurant_id, r.status AS restaurant_status, r.open_status
                FROM menu_items mi
                JOIN menu_categories mc ON mc.id = mi.category_id
                JOIN restaurants r ON r.id = mc.restaurant_id
                WHERE mi.id = :menuItemId
                """;
        return jdbc.query(sql, Map.of("menuItemId", menuItemId), (rs, i) -> new MenuItemInfo(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getBoolean("is_available"),
                rs.getBoolean("category_active"),
                rs.getLong("restaurant_id"),
                rs.getString("restaurant_status"),
                rs.getString("open_status")
        )).stream().findFirst();
    }

    /** Ten va don toi thieu cua quan, de hien tren gio. */
    public Optional<CartRestaurant> findRestaurant(long restaurantId) {
        String sql = "SELECT id, name, min_order_amount FROM restaurants WHERE id = :id";
        return jdbc.query(sql, Map.of("id", restaurantId), (rs, i) -> new CartRestaurant(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getBigDecimal("min_order_amount")
        )).stream().findFirst();
    }

    /** id va so luong cua 1 dong cart_items. */
    public record CartItemRow(long id, int quantity) {
    }

    /** Quan cua gio: ten va gia tri don toi thieu. */
    public record CartRestaurant(long id, String name, BigDecimal minOrderAmount) {
    }
}
