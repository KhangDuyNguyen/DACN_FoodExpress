package vn.hutech.fooddelivery.restaurant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.restaurant.dto.CuisineDTO;
import vn.hutech.fooddelivery.restaurant.dto.RestaurantSummaryDTO;

import java.util.List;

/**
 * REST API cho tìm kiếm nhà hàng và loại món ăn.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Restaurants", description = "Cuisine and restaurant search APIs")
public class RestaurantController {

    private final CuisineService cuisineService;
    private final RestaurantService restaurantService;

    public RestaurantController(CuisineService cuisineService, RestaurantService restaurantService) {
        this.cuisineService = cuisineService;
        this.restaurantService = restaurantService;
    }

    /**
     * Lấy danh sách tất cả loại món ăn (cuisines).
     * <p>
     * Endpoint này trả về danh sách các loại món như Món Việt, Món Hàn, Đồ Ăn Nhanh, v.v.
     * để hiển thị bộ lọc trong giao diện người dùng.
     * </p>
     */
    @GetMapping("/cuisines")
    @Operation(summary = "Get all cuisines", description = "Returns list of all cuisine types with icons")
    public ResponseEntity<List<CuisineDTO>> getAllCuisines() {
        return ResponseEntity.ok(cuisineService.getAllCuisines());
    }

    /**
     * Tìm kiếm nhà hàng với các bộ lọc tùy chọn.
     * <p>
     * Hỗ trợ tìm kiếm theo từ khóa, lọc theo loại món ăn, và sắp xếp theo khoảng cách
     * nếu cung cấp tọa độ người dùng (lat, lng).
     * </p>
     *
     * @param keyword từ khóa tìm kiếm (tùy chọn)
     * @param cuisine ID loại món ăn để lọc (tùy chọn)
     * @param lat     vĩ độ người dùng để tính khoảng cách (tùy chọn)
     * @param lng     kinh độ người dùng để tính khoảng cách (tùy chọn)
     * @return danh sách nhà hàng phù hợp
     */
    @GetMapping("/restaurants")
    @Operation(summary = "Search restaurants",
            description = "Search and filter restaurants by keyword, cuisine type, and distance from user location")
    public ResponseEntity<List<RestaurantSummaryDTO>> searchRestaurants(
            @Parameter(description = "Search keyword (matches restaurant name)")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "Cuisine ID to filter by")
            @RequestParam(required = false) Integer cuisine,

            @Parameter(description = "User latitude for distance-based sorting")
            @RequestParam(required = false) Double lat,

            @Parameter(description = "User longitude for distance-based sorting")
            @RequestParam(required = false) Double lng
    ) {
        List<RestaurantSummaryDTO> restaurants = restaurantService.searchRestaurants(
                keyword, cuisine, lat, lng);
        return ResponseEntity.ok(restaurants);
    }
}
