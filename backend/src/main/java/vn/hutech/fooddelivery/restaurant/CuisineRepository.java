package vn.hutech.fooddelivery.restaurant;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.hutech.fooddelivery.restaurant.dto.CuisineDTO;

import java.util.List;

/**
 * Repository quản lý truy vấn dữ liệu bảng cuisines (loại món ăn).
 */
@Repository
public class CuisineRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public CuisineRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Lấy tất cả loại món ăn, sắp xếp theo tên.
     */
    public List<CuisineDTO> findAll() {
        String sql = "SELECT id, name, icon_url FROM cuisines ORDER BY name";
        return jdbc.query(sql, (rs, rowNum) -> new CuisineDTO(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("icon_url")
        ));
    }
}
