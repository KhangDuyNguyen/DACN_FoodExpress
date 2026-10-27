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

    /**
     * Tìm kiếm nhà hàng theo keyword và cuisineId.
     */
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

    /**
     * Lấy chi tiết một nhà hàng theo id.
     * Trả về Optional.empty() nếu không tìm thấy hoặc status != ACTIVE.
     */
    public Optional<RestaurantDetailDTO> findById(long id) {
        // Query thông tin cơ bản của quán
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

        // Lấy danh sách cuisines của quán
        List<CuisineDTO> cuisines = findCuisinesByRestaurantId(id);

        // Lấy giờ mở cửa
        List<RestaurantHoursDTO> hours = findHoursByRestaurantId(id);

        return Optional.of(new RestaurantDetailDTO(
                ((Number) row.get("id")).longValue(),
                (String) row.get("name"),
                (String) row.get("description"),
                (String) row.get("phone"),
                (String) row.get("address"),
                ((Number) row.get("lat")).doubleValue(),
                ((Number) row.get("lng")).doubleValue(),
                (String) row.get("logo_url"),
                (String) row.get("cover_url"),
                ((Number) row.get("avg_rating")).doubleValue(),
                ((Number) row.get("rating_count")).intValue(),
                ((Number) row.get("min_order_amount")).longValue(),
                ((Number) row.get("prep_time_minutes")).intValue(),
                (String) row.get("open_status"),
                cuisines,
                hours
        ));
    }

    /**
     * Lấy danh sách cuisines của một nhà hàng.
     */
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

    /**
     * Lấy giờ mở cửa của một nhà hàng.
     */
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

    /**
     * Lấy toàn bộ menu của một nhà hàng:
     * categories -> items -> option_groups -> options.
     * Dùng một query JOIN lớn để tránh N+1.
     */
    public List<MenuCategoryDTO> findMenuByRestaurantId(long restaurantId) {
        String sql = """
            SELECT
                mc.id        AS cat_id,
                mc.name      AS cat_name,
                mc.sort_order AS cat_sort,
                mi.id        AS item_id,
                mi.name      AS item_name,
                mi.description AS item_desc,
                mi.price     AS item_price,
                mi.image_url AS item_image,
                mi.is_available AS item_available,
                mi.sort_order AS item_sort,
                og.id        AS og_id,
                og.name      AS og_name,
                og.is_required AS og_required,
                og.min_select AS og_min,
                og.max_select AS og_max,
                og.sort_order AS og_sort,
                o.id         AS opt_id,
                o.name       AS opt_name,
                o.extra_price AS opt_price,
                o.is_available AS opt_available,
                o.sort_order AS opt_sort
            FROM menu_categories mc
            LEFT JOIN menu_items mi      ON mi.category_id = mc.id
            LEFT JOIN option_groups og   ON og.menu_item_id = mi.id
            LEFT JOIN options o          ON o.group_id = og.id
            WHERE mc.restaurant_id = :restaurantId
              AND mc.is_active = 1
            ORDER BY mc.sort_order, mc.id,
                     mi.sort_order, mi.id,
                     og.sort_order, og.id,
                     o.sort_order, o.id
            """;

        List<Map<String, Object>> rows = jdbc.queryForList(sql,
                new MapSqlParameterSource("restaurantId", restaurantId));

        // Build cây category -> item -> option_group -> option từ flat rows
        // Dùng LinkedHashMap để giữ thứ tự insert
        Map<Long, MenuCategoryDTO> catMap  = new LinkedHashMap<>();
        Map<Long, MenuItemDTO>     itemMap = new LinkedHashMap<>();
        Map<Long, OptionGroupDTO>  ogMap   = new LinkedHashMap<>();

        // Temp holders dùng List để tránh immutable record
        Map<Long, List<OptionDTO>>      ogOptions  = new LinkedHashMap<>();
        Map<Long, List<OptionGroupDTO>> itemGroups = new LinkedHashMap<>();
        Map<Long, List<MenuItemDTO>>    catItems   = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            long catId   = ((Number) row.get("cat_id")).longValue();
            Object itemIdObj = row.get("item_id");
            Object ogIdObj   = row.get("og_id");
            Object optIdObj  = row.get("opt_id");

            // Category
            catMap.computeIfAbsent(catId, k -> {
                catItems.put(k, new ArrayList<>());
                return new MenuCategoryDTO(k,
                        (String) row.get("cat_name"),
                        ((Number) row.get("cat_sort")).intValue(),
                        catItems.get(k));
            });

            if (itemIdObj == null) continue;
            long itemId = ((Number) itemIdObj).longValue();

            // MenuItem
            itemMap.computeIfAbsent(itemId, k -> {
                itemGroups.put(k, new ArrayList<>());
                MenuItemDTO item = new MenuItemDTO(k,
                        (String) row.get("item_name"),
                        (String) row.get("item_desc"),
                        ((Number) row.get("item_price")).longValue(),
                        (String) row.get("item_image"),
                        ((Number) row.get("item_available")).intValue() == 1,
                        ((Number) row.get("item_sort")).intValue(),
                        itemGroups.get(k));
                catItems.get(catId).add(item);
                return item;
            });

            if (ogIdObj == null) continue;
            long ogId = ((Number) ogIdObj).longValue();

            // OptionGroup
            ogMap.computeIfAbsent(ogId, k -> {
                ogOptions.put(k, new ArrayList<>());
                OptionGroupDTO og = new OptionGroupDTO(k,
                        (String) row.get("og_name"),
                        ((Number) row.get("og_required")).intValue() == 1,
                        ((Number) row.get("og_min")).intValue(),
                        ((Number) row.get("og_max")).intValue(),
                        ((Number) row.get("og_sort")).intValue(),
                        ogOptions.get(k));
                itemGroups.get(itemId).add(og);
                return og;
            });

            if (optIdObj == null) continue;
            long optId = ((Number) optIdObj).longValue();

            // Option
            ogOptions.get(ogId).add(new OptionDTO(
                    optId,
                    (String) row.get("opt_name"),
                    ((Number) row.get("opt_price")).longValue(),
                    ((Number) row.get("opt_available")).intValue() == 1,
                    ((Number) row.get("opt_sort")).intValue()
            ));
        }

        return new ArrayList<>(catMap.values());
    }
}
