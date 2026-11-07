package vn.hutech.fooddelivery.restaurant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.hutech.fooddelivery.restaurant.dto.CreateRestaurantRequest;
import vn.hutech.fooddelivery.restaurant.dto.MerchantRestaurantDTO;
import vn.hutech.fooddelivery.security.AuthUser;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * API cho chủ quán: quán/chi nhánh của chính mình.
 * GET  /api/merchant/restaurants        - danh sách quán
 * POST /api/merchant/restaurants        - tạo chi nhánh mới
 * Đường dẫn /api/merchant/** chỉ MERCHANT gọi được (SecurityConfig).
 */
@RestController
@RequestMapping("/api/merchant/restaurants")
@Tag(name = "Merchant - Quán của tôi", description = "Quán/chi nhánh của chủ quán đang đăng nhập")
public class MerchantRestaurantController {

    private final RestaurantService restaurantService;
    private final MerchantMenuService merchantMenuService;

    public MerchantRestaurantController(RestaurantService restaurantService,
                                        MerchantMenuService merchantMenuService) {
        this.restaurantService = restaurantService;
        this.merchantMenuService = merchantMenuService;
    }

    @GetMapping
    @Operation(summary = "Danh sách quán/chi nhánh của chủ quán đang đăng nhập")
    public ResponseEntity<List<MerchantRestaurantDTO>> myRestaurants(
            @AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(restaurantService.getRestaurantsOfOwner(user.id()));
    }

    @PostMapping
    @Operation(summary = "Tạo chi nhánh mới",
               description = "Quán mới sẽ có status=PENDING, cần admin duyệt trước khi hiển thị cho khách")
    public ResponseEntity<Map<String, Long>> createRestaurant(
            @Valid @RequestBody CreateRestaurantRequest req,
            @AuthenticationPrincipal AuthUser user) {
        long newId = merchantMenuService.createRestaurant(user.id(), req);
        return ResponseEntity
                .created(URI.create("/api/merchant/restaurants"))
                .body(Map.of("id", newId));
    }
}
