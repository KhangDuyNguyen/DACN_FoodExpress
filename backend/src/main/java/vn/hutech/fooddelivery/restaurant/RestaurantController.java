package vn.hutech.fooddelivery.restaurant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.hutech.fooddelivery.restaurant.dto.*;

import java.util.List;

/**
 * REST API cho tìm kiếm nhà hàng, chi tiết quán và menu.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Restaurants", description = "Cuisine, restaurant search, detail and menu APIs")
public class RestaurantController {

    private final CuisineService cuisineService;
    private final RestaurantService restaurantService;

    public RestaurantController(CuisineService cuisineService, RestaurantService restaurantService) {
        this.cuisineService = cuisineService;
        this.restaurantService = restaurantService;
    }

    /** Lấy danh sách tất cả loại món ăn. */
    @GetMapping("/cuisines")
    @Operation(summary = "Get all cuisines", description = "Returns list of all cuisine types with icons")
    public ResponseEntity<List<CuisineDTO>> getAllCuisines() {
        return ResponseEntity.ok(cuisineService.getAllCuisines());
    }

    /** Tìm kiếm nhà hàng với các bộ lọc tùy chọn. */
    @GetMapping("/restaurants")
    @Operation(summary = "Search restaurants",
            description = "Search and filter by keyword, cuisine, and distance from user location")
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
        return ResponseEntity.ok(restaurantService.searchRestaurants(keyword, cuisine, lat, lng));
    }

    /** Lấy chi tiết một nhà hàng theo id. */
    @GetMapping("/restaurants/{id}")
    @Operation(summary = "Get restaurant detail",
            description = "Returns full restaurant info including cuisines and opening hours")
    public ResponseEntity<RestaurantDetailDTO> getRestaurantById(
            @Parameter(description = "Restaurant ID") @PathVariable long id
    ) {
        return ResponseEntity.ok(restaurantService.getRestaurantById(id));
    }

    /** Lấy menu đầy đủ của một nhà hàng (categories -> items -> option_groups -> options). */
    @GetMapping("/restaurants/{id}/menu")
    @Operation(summary = "Get restaurant menu",
            description = "Returns full menu: categories with items, each item with option groups and options")
    public ResponseEntity<MenuDTO> getMenu(
            @Parameter(description = "Restaurant ID") @PathVariable long id
    ) {
        return ResponseEntity.ok(restaurantService.getMenuByRestaurantId(id));
    }
}
