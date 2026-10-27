package vn.hutech.fooddelivery.restaurant;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.hutech.fooddelivery.restaurant.dto.*;

import java.util.*;

/**
 * Repository quản lý truy vấn dữ liệu bảng restaurants và các bảng liên quan.
 */
@Repository
public class RestaurantRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public RestaurantRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Helper: đọc TINYINT(1) an toàn ──────────────────────────────────────
    // MySQL JDBC driver có thể trả Boolean hoặc Number cho TINYINT(1).
    // Dùng helper này thay vì cast trực tiếp để tránh ClassCastException.
    private static boolean toBool(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        return ((Number) val).intValue() != 0;
    }

    private static int toInt(Object val) {
        if (val == null) return 0;
        return ((Number) val).intValue();
    }

    private static long toLong(Object val) {
        if (val == null) return 0L;
        return ((Number) val).longValue();
    }

    // ── Search restaurants ────────────────────────────────────────────────────

    public List<RestaurantSummaryDTO> search(String keyword, Integer cuisineId) {
        String sql = """
            SELECT
                r.id,
                r.name,
                COALESCE(rc_first.cuisine_id, 0) AS cuisine_id,
                COALESCE(c.name, '') AS cuisine_name,
                r.address,
                r.lat,
                r.lng,
                r.logo_url,
                r.avg_rating,
                r.rating_count,
                r.min_order_amount,
                r.prep_time_minutes,
                r.open_status
            FROM restaurants r
            LEFT JOIN (
                SELECT rc1.restaurant_id, MIN(rc1.cuisine_id) AS cuisine_id
                FROM restaurant_cuisines rc1
                GROUP BY rc1.restaurant_id
            ) rc_first ON r.id = rc_first.restaurant_id
            LEFT JOIN cuisines c ON rc_first.cuisine_id = c.id
            WHERE r.status = 'ACTIVE'
                AND (:keyword IS NULL OR r.name LIKE CONCAT('%', :keyword, '%'))
                AND (:cuisineId IS NULL OR EXISTS (
                    SELECT 1 FROM restaurant_cuisines rc2
                    WHERE rc2.restaurant_id = r.id AND rc2.cuisine_id = :cuisineId
                ))
            ORDER BY r.avg_rating DESC
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("keyword", keyword)
                .addValue("cuisineId", cuisineId);

        return jdbc.query(sql, params, (rs, rowNum) -> new RestaurantSummaryDTO(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getInt("cuisine_id"),
                rs.getString("cuisine_name"),
                rs.getString("address"),
                rs.getDouble("lat"),
                rs.getDouble("lng"),
                rs.getString("logo_url"),
                rs.getDouble("avg_rating"),
                rs.getInt("rating_count"),
                rs.getLong("min_order_amount"),
                rs.getInt("prep_time_minutes"),
                rs.getString("open_status")
        ));
    }

    // ── Restaurant detail ─────────────────────────────────────────────────────

    public Optional<RestaurantDetailDTO> findById(long id) {
        String sql = """
            SELECT r.id, r.name, r.description, r.phone, r.address,
                   r.lat, r.lng, r.logo_url, r.cover_url,
                   r.avg_rating, r.rating_count,
                   r.min_order_amount, r.prep_time_minutes, r.open_status
            FROM restaurants r
            WHERE r.id = :id AND r.status = 'ACTIVE'
            """;

        List<Map<String, Object>> rows = jdbc.queryForList(sql,
                new MapSqlParameterSource("id", id));

        if (rows.isEmpty()) return Optional.empty();

        Map<String, Object> row = rows.get(0);
        List<CuisineDTO> cuisines = findCuisinesByRestaurantId(id);
        List<RestaurantHoursDTO> hours = findHoursByRestaurantId(id);

        return Optional.of(new RestaurantDetailDTO(
                toLong(row.get("id")),
                (String) row.get("name"),
                (String) row.get("description"),
                (String) row.get("phone"),
                (String) row.get("address"),
                ((Number) row.get("lat")).doubleValue(),
                ((Number) row.get("lng")).doubleValue(),
                (String) row.get("logo_url"),
                (String) row.get("cover_url"),
                ((Number) row.get("avg_rating")).doubleValue(),
                toInt(row.get("rating_count")),
                toLong(row.get("min_order_amount")),
                toInt(row.get("prep_time_minutes")),
                (String) row.get("open_status"),
                cuisines,
                hours
        ));
    }

    private List<CuisineDTO> findCuisinesByRestaurantId(long restaurantId) {
        String sql = """
            SELECT c.id, c.name, c.icon_url
            FROM cuisines c
            JOIN restaurant_cuisines rc ON c.id = rc.cuisine_id
            WHERE rc.restaurant_id = :restaurantId
            ORDER BY c.name
            """;
        return jdbc.query(sql,
                new MapSqlParameterSource("restaurantId", restaurantId),
                (rs, rowNum) -> new CuisineDTO(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("icon_url")
                ));
    }

    private List<RestaurantHoursDTO> findHoursByRestaurantId(long restaurantId) {
        String sql = """
            SELECT day_of_week, open_time, close_time
            FROM restaurant_hours
            WHERE restaurant_id = :restaurantId
            ORDER BY day_of_week, open_time
            """;
        return jdbc.query(sql,
                new MapSqlParameterSource("restaurantId", restaurantId),
                (rs, rowNum) -> new RestaurantHoursDTO(
                        rs.getInt("day_of_week"),
                        rs.getString("open_time"),
                        rs.getString("close_time")
                ));
    }

    // ── Menu ──────────────────────────────────────────────────────────────────

    public List<MenuCategoryDTO> findMenuByRestaurantId(long restaurantId) {
        String sql = """
            SELECT
                mc.id           AS cat_id,
                mc.name         AS cat_name,
                mc.sort_order   AS cat_sort,
                mi.id           AS item_id,
                mi.name         AS item_name,
                mi.description  AS item_desc,
                mi.price        AS item_price,
                mi.image_url    AS item_image,
                CAST(mi.is_available AS UNSIGNED) AS item_available,
                mi.sort_order   AS item_sort,
                og.id           AS og_id,
                og.name         AS og_name,
                CAST(og.is_required AS UNSIGNED)  AS og_required,
                og.min_select   AS og_min,
                og.max_select   AS og_max,
                og.sort_order   AS og_sort,
                o.id            AS opt_id,
                o.name          AS opt_name,
                o.extra_price   AS opt_price,
                CAST(o.is_available AS UNSIGNED)  AS opt_available,
                o.sort_order    AS opt_sort
            FROM menu_categories mc
            LEFT JOIN menu_items mi    ON mi.category_id  = mc.id
            LEFT JOIN option_groups og ON og.menu_item_id = mi.id
            LEFT JOIN options o        ON o.group_id      = og.id
            WHERE mc.restaurant_id = :restaurantId
              AND mc.is_active = 1
            ORDER BY mc.sort_order, mc.id,
                     mi.sort_order, mi.id,
                     og.sort_order, og.id,
                     o.sort_order,  o.id
            """;

        List<Map<String, Object>> rows = jdbc.queryForList(sql,
                new MapSqlParameterSource("restaurantId", restaurantId));

        Map<Long, MenuCategoryDTO>     catMap    = new LinkedHashMap<>();
        Map<Long, MenuItemDTO>         itemMap   = new LinkedHashMap<>();
        Map<Long, OptionGroupDTO>      ogMap     = new LinkedHashMap<>();
        Map<Long, List<OptionDTO>>     ogOptions = new LinkedHashMap<>();
        Map<Long, List<OptionGroupDTO>> itemGroups = new LinkedHashMap<>();
        Map<Long, List<MenuItemDTO>>   catItems  = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            long catId = toLong(row.get("cat_id"));

            catMap.computeIfAbsent(catId, k -> {
                catItems.put(k, new ArrayList<>());
                return new MenuCategoryDTO(k,
                        (String) row.get("cat_name"),
                        toInt(row.get("cat_sort")),
                        catItems.get(k));
            });

            Object itemIdObj = row.get("item_id");
            if (itemIdObj == null) continue;
            long itemId = toLong(itemIdObj);

            itemMap.computeIfAbsent(itemId, k -> {
                itemGroups.put(k, new ArrayList<>());
                MenuItemDTO item = new MenuItemDTO(k,
                        (String) row.get("item_name"),
                        (String) row.get("item_desc"),
                        toLong(row.get("item_price")),
                        (String) row.get("item_image"),
                        toBool(row.get("item_available")),
                        toInt(row.get("item_sort")),
                        itemGroups.get(k));
                catItems.get(catId).add(item);
                return item;
            });

            Object ogIdObj = row.get("og_id");
            if (ogIdObj == null) continue;
            long ogId = toLong(ogIdObj);

            ogMap.computeIfAbsent(ogId, k -> {
                ogOptions.put(k, new ArrayList<>());
                OptionGroupDTO og = new OptionGroupDTO(k,
                        (String) row.get("og_name"),
                        toBool(row.get("og_required")),
                        toInt(row.get("og_min")),
                        toInt(row.get("og_max")),
                        toInt(row.get("og_sort")),
                        ogOptions.get(k));
                itemGroups.get(itemId).add(og);
                return og;
            });

            Object optIdObj = row.get("opt_id");
            if (optIdObj == null) continue;

            ogOptions.get(ogId).add(new OptionDTO(
                    toLong(optIdObj),
                    (String) row.get("opt_name"),
                    toLong(row.get("opt_price")),
                    toBool(row.get("opt_available")),
                    toInt(row.get("opt_sort"))
            ));
        }

        return new ArrayList<>(catMap.values());
    }
}
