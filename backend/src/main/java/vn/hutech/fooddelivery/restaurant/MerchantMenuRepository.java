package vn.hutech.fooddelivery.restaurant;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import vn.hutech.fooddelivery.restaurant.dto.*;

import java.util.*;

@Repository
public class MerchantMenuRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public MerchantMenuRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static boolean toBool(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        return ((Number) val).intValue() != 0;
    }

    public boolean isOwner(long restaurantId, long ownerUserId) {
        String sql = """
            SELECT COUNT(*) FROM restaurants r
            JOIN merchants m ON m.id = r.merchant_id
            WHERE r.id = :restaurantId AND m.user_id = :ownerUserId
            """;
        Integer count = jdbc.queryForObject(sql,
                new MapSqlParameterSource()
                        .addValue("restaurantId", restaurantId)
                        .addValue("ownerUserId", ownerUserId),
                Integer.class);
        return count != null && count > 0;
    }

    public boolean isCategoryOfRestaurant(long categoryId, long restaurantId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM menu_categories WHERE id = :id AND restaurant_id = :rid",
                new MapSqlParameterSource().addValue("id", categoryId).addValue("rid", restaurantId),
                Integer.class);
        return count != null && count > 0;
    }

    public boolean isItemOfRestaurant(long itemId, long restaurantId) {
        String sql = """
            SELECT COUNT(*) FROM menu_items mi
            JOIN menu_categories mc ON mc.id = mi.category_id
            WHERE mi.id = :itemId AND mc.restaurant_id = :rid
            """;
        Integer count = jdbc.queryForObject(sql,
                new MapSqlParameterSource().addValue("itemId", itemId).addValue("rid", restaurantId),
                Integer.class);
        return count != null && count > 0;
    }

    public boolean isGroupOfItem(long groupId, long itemId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM option_groups WHERE id = :id AND menu_item_id = :itemId",
                new MapSqlParameterSource().addValue("id", groupId).addValue("itemId", itemId),
                Integer.class);
        return count != null && count > 0;
    }

    public boolean isOptionOfGroup(long optionId, long groupId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM options WHERE id = :id AND group_id = :gid",
                new MapSqlParameterSource().addValue("id", optionId).addValue("gid", groupId),
                Integer.class);
        return count != null && count > 0;
    }

    public Optional<Long> findRestaurantIdByItemId(long itemId) {
        String sql = """
            SELECT mc.restaurant_id FROM menu_items mi
            JOIN menu_categories mc ON mc.id = mi.category_id
            WHERE mi.id = :itemId
            """;
        var rows = jdbc.queryForList(sql, new MapSqlParameterSource("itemId", itemId));
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(((Number) rows.get(0).get("restaurant_id")).longValue());
    }

    // ── Cập nhật thông tin quán ──────────────────────────────────────────────

    public void updateRestaurant(long restaurantId, UpdateRestaurantRequest req) {
        String sql = """
            UPDATE restaurants SET name=:name, description=:description, address=:address,
                phone=:phone, logo_url=:logoUrl, cover_url=:coverUrl,
                min_order_amount=:minOrderAmount, prep_time_minutes=:prepTimeMinutes, updated_at=NOW()
            WHERE id=:id
            """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", restaurantId).addValue("name", req.name())
                .addValue("description", req.description()).addValue("address", req.address())
                .addValue("phone", req.phone()).addValue("logoUrl", req.logoUrl())
                .addValue("coverUrl", req.coverUrl()).addValue("minOrderAmount", req.minOrderAmount())
                .addValue("prepTimeMinutes", req.prepTimeMinutes()));
    }

    public void updateHours(long restaurantId, List<UpdateHoursRequest.HourEntry> hours) {
        jdbc.update("DELETE FROM restaurant_hours WHERE restaurant_id = :id",
                new MapSqlParameterSource("id", restaurantId));
        String sql = """
            INSERT INTO restaurant_hours (restaurant_id, day_of_week, open_time, close_time)
            VALUES (:rid, :day, :open, :close)
            """;
        for (var h : hours) {
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("rid", restaurantId).addValue("day", h.dayOfWeek())
                    .addValue("open", h.openTime()).addValue("close", h.closeTime()));
        }
    }

    public void updateOpenStatus(long restaurantId, String openStatus) {
        jdbc.update("UPDATE restaurants SET open_status=:status, updated_at=NOW() WHERE id=:id",
                new MapSqlParameterSource().addValue("id", restaurantId).addValue("status", openStatus));
    }

    // ── CRUD categories ──────────────────────────────────────────────────────

    public List<MenuCategoryDTO> findCategoriesByRestaurantId(long restaurantId) {
        String sql = "SELECT id, name, sort_order FROM menu_categories WHERE restaurant_id=:rid ORDER BY sort_order, id";
        return jdbc.query(sql, new MapSqlParameterSource("rid", restaurantId),
                (rs, i) -> new MenuCategoryDTO(rs.getLong("id"), rs.getString("name"), rs.getInt("sort_order"), List.of()));
    }

    public long createCategory(long restaurantId, CategoryRequest req) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("INSERT INTO menu_categories (restaurant_id, name, sort_order) VALUES (:rid, :name, :sort)",
                new MapSqlParameterSource().addValue("rid", restaurantId)
                        .addValue("name", req.name()).addValue("sort", req.sortOrder()), key);
        return key.getKey().longValue();
    }

    public void updateCategory(long categoryId, CategoryRequest req) {
        jdbc.update("UPDATE menu_categories SET name=:name, sort_order=:sort WHERE id=:id",
                new MapSqlParameterSource().addValue("id", categoryId)
                        .addValue("name", req.name()).addValue("sort", req.sortOrder()));
    }

    public void deleteCategory(long categoryId) {
        jdbc.update("DELETE FROM menu_categories WHERE id=:id", new MapSqlParameterSource("id", categoryId));
    }

    // ── CRUD menu items ──────────────────────────────────────────────────────

    public List<Map<String, Object>> findItemsByRestaurantId(long restaurantId) {
        String sql = """
            SELECT mi.id, mi.name, mi.description, mi.price, mi.image_url,
                   mi.is_available, mi.sort_order, mc.id AS category_id, mc.name AS category_name
            FROM menu_items mi
            JOIN menu_categories mc ON mc.id = mi.category_id
            WHERE mc.restaurant_id = :rid
            ORDER BY mc.sort_order, mc.id, mi.sort_order, mi.id
            """;
        return jdbc.queryForList(sql, new MapSqlParameterSource("rid", restaurantId));
    }

    public long createItem(MenuItemRequest req) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO menu_items (category_id, name, description, price, image_url, is_available, sort_order)
                VALUES (:cid, :name, :desc, :price, :img, :avail, :sort)
                """,
                new MapSqlParameterSource()
                        .addValue("cid", req.categoryId()).addValue("name", req.name())
                        .addValue("desc", req.description()).addValue("price", req.price())
                        .addValue("img", req.imageUrl()).addValue("avail", req.isAvailable())
                        .addValue("sort", req.sortOrder()), key);
        return key.getKey().longValue();
    }

    public void updateItem(long itemId, MenuItemRequest req) {
        jdbc.update("""
                UPDATE menu_items SET category_id=:cid, name=:name, description=:desc,
                    price=:price, image_url=:img, is_available=:avail, sort_order=:sort
                WHERE id=:id
                """,
                new MapSqlParameterSource().addValue("id", itemId)
                        .addValue("cid", req.categoryId()).addValue("name", req.name())
                        .addValue("desc", req.description()).addValue("price", req.price())
                        .addValue("img", req.imageUrl()).addValue("avail", req.isAvailable())
                        .addValue("sort", req.sortOrder()));
    }

    public void deleteItem(long itemId) {
        jdbc.update("DELETE FROM menu_items WHERE id=:id", new MapSqlParameterSource("id", itemId));
    }

    public void patchAvailability(long itemId, boolean isAvailable) {
        jdbc.update("UPDATE menu_items SET is_available=:avail WHERE id=:id",
                new MapSqlParameterSource().addValue("id", itemId).addValue("avail", isAvailable));
    }

    // ── CRUD option groups ───────────────────────────────────────────────────

    public List<OptionGroupDTO> findOptionGroupsByItemId(long itemId) {
        String sql = """
            SELECT og.id, og.name, og.is_required, og.min_select, og.max_select, og.sort_order,
                   o.id AS opt_id, o.name AS opt_name, o.extra_price, o.is_available AS opt_avail, o.sort_order AS opt_sort
            FROM option_groups og
            LEFT JOIN options o ON o.group_id = og.id
            WHERE og.menu_item_id = :itemId
            ORDER BY og.sort_order, og.id, o.sort_order, o.id
            """;
        var rows = jdbc.queryForList(sql, new MapSqlParameterSource("itemId", itemId));
        var groups = new LinkedHashMap<Long, OptionGroupDTO>();
        var groupOptions = new LinkedHashMap<Long, List<OptionDTO>>();
        for (var row : rows) {
            long ogId = ((Number) row.get("id")).longValue();
            groups.computeIfAbsent(ogId, k -> {
                groupOptions.put(k, new ArrayList<>());
                return new OptionGroupDTO(k, (String) row.get("name"),
                        toBool(row.get("is_required")),
                        ((Number) row.get("min_select")).intValue(),
                        ((Number) row.get("max_select")).intValue(),
                        ((Number) row.get("sort_order")).intValue(),
                        groupOptions.get(k));
            });
            if (row.get("opt_id") != null) {
                groupOptions.get(ogId).add(new OptionDTO(
                        ((Number) row.get("opt_id")).longValue(),
                        (String) row.get("opt_name"),
                        ((Number) row.get("extra_price")).longValue(),
                        toBool(row.get("opt_avail")),
                        ((Number) row.get("opt_sort")).intValue()));
            }
        }
        return new ArrayList<>(groups.values());
    }

    public long createOptionGroup(long itemId, OptionGroupRequest req) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO option_groups (menu_item_id, name, is_required, min_select, max_select, sort_order)
                VALUES (:itemId, :name, :req, :min, :max, :sort)
                """,
                new MapSqlParameterSource().addValue("itemId", itemId).addValue("name", req.name())
                        .addValue("req", req.isRequired()).addValue("min", req.minSelect())
                        .addValue("max", req.maxSelect()).addValue("sort", req.sortOrder()), key);
        return key.getKey().longValue();
    }

    public void updateOptionGroup(long groupId, OptionGroupRequest req) {
        jdbc.update("""
                UPDATE option_groups SET name=:name, is_required=:req,
                    min_select=:min, max_select=:max, sort_order=:sort WHERE id=:id
                """,
                new MapSqlParameterSource().addValue("id", groupId).addValue("name", req.name())
                        .addValue("req", req.isRequired()).addValue("min", req.minSelect())
                        .addValue("max", req.maxSelect()).addValue("sort", req.sortOrder()));
    }

    public void deleteOptionGroup(long groupId) {
        jdbc.update("DELETE FROM option_groups WHERE id=:id", new MapSqlParameterSource("id", groupId));
    }

    // ── CRUD options ─────────────────────────────────────────────────────────

    public long createOption(long groupId, OptionRequest req) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO options (group_id, name, extra_price, is_available, sort_order)
                VALUES (:gid, :name, :price, :avail, :sort)
                """,
                new MapSqlParameterSource().addValue("gid", groupId).addValue("name", req.name())
                        .addValue("price", req.extraPrice()).addValue("avail", req.isAvailable())
                        .addValue("sort", req.sortOrder()), key);
        return key.getKey().longValue();
    }

    public void updateOption(long optionId, OptionRequest req) {
        jdbc.update("""
                UPDATE options SET name=:name, extra_price=:price, is_available=:avail, sort_order=:sort
                WHERE id=:id
                """,
                new MapSqlParameterSource().addValue("id", optionId).addValue("name", req.name())
                        .addValue("price", req.extraPrice()).addValue("avail", req.isAvailable())
                        .addValue("sort", req.sortOrder()));
    }

    public void deleteOption(long optionId) {
        jdbc.update("DELETE FROM options WHERE id=:id", new MapSqlParameterSource("id", optionId));
    }
}
