package vn.hutech.fooddelivery.restaurant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.hutech.fooddelivery.common.PageResponse;
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

    /**
     * Tìm kiếm nhà hàng với các bộ lọc tùy chọn, có phân trang.
     * Các annotation @Min/@Max/@DecimalMin/@DecimalMax trên tham số được Spring MVC tự kiểm tra,
     * sai thì trả 400 (GlobalExceptionHandler), không chạy xuống database.
     */
    @GetMapping("/restaurants")
    @Operation(summary = "Tìm quán theo tên, loại món, có phân trang; gửi lat/lng thì quán gần nhất lên trước")
    public ResponseEntity<PageResponse<RestaurantSummaryDTO>> searchRestaurants(
            @Parameter(description = "Từ khóa trong tên quán")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "Id loại món (lấy từ GET /api/cuisines)")
            @RequestParam(required = false) Integer cuisineId,

            @Parameter(description = "Vĩ độ người dùng (-90 đến 90)")
            @RequestParam(required = false) @DecimalMin("-90") @DecimalMax("90") Double lat,

            @Parameter(description = "Kinh độ người dùng (-180 đến 180)")
            @RequestParam(required = false) @DecimalMin("-180") @DecimalMax("180") Double lng,

            @Parameter(description = "Số trang, bắt đầu từ 0")
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(description = "Số quán mỗi trang (1 đến 50)")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size
    ) {
        return ResponseEntity.ok(restaurantService.searchRestaurants(keyword, cuisineId, lat, lng, page, size));
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

    /** Chi tiết một món. Nằm dưới /api/restaurants/** nên công khai, không cần token. */
    @GetMapping("/restaurants/{restaurantId}/menu-items/{itemId}")
    @Operation(summary = "Chi tiết một món của quán (404 nếu món không thuộc quán này)")
    public ResponseEntity<MenuItemDetailDTO> getMenuItem(
            @Parameter(description = "Id quán") @PathVariable long restaurantId,
            @Parameter(description = "Id món") @PathVariable long itemId
    ) {
        return ResponseEntity.ok(restaurantService.getMenuItem(restaurantId, itemId));
    }
}
