package vn.hutech.fooddelivery.restaurant;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.hutech.fooddelivery.restaurant.dto.RestaurantSummaryDTO;

import java.util.List;

/**
 * Repository quản lý truy vấn dữ liệu bảng restaurants và restaurant_cuisines.
 */
@Repository
public class RestaurantRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public RestaurantRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Tìm kiếm nhà hàng theo từ khóa và loại món ăn.
     * <p>
     * Lọc theo keyword (LIKE %keyword% trên tên nhà hàng) và cuisineId (qua bảng restaurant_cuisines).
     * Chỉ trả về các nhà hàng có status = 'ACTIVE'.
     * Sắp xếp theo avg_rating giảm dần (nhà hàng có rating cao hơn lên trước).
     * </p>
     *
     * @param keyword   từ khóa tìm kiếm (nullable), so khớp với tên nhà hàng
     * @param cuisineId ID loại món ăn (nullable), lọc các nhà hàng có loại món này
     * @return danh sách nhà hàng phù hợp
     */
    public List<RestaurantSummaryDTO> search(String keyword, Integer cuisineId) {
        // LEFT JOIN với restaurant_cuisines và cuisines để lấy cuisine đầu tiên (primary)
        // Sử dụng subquery để lấy cuisine_id đầu tiên cho mỗi restaurant
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
}
