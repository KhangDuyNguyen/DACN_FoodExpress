package vn.hutech.fooddelivery.restaurant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.restaurant.dto.MerchantRestaurantDTO;
import vn.hutech.fooddelivery.security.AuthUser;

import java.util.List;

/**
 * API cho chủ quán: các quán/chi nhánh của chính mình.
 * Đường dẫn /api/merchant/** chỉ MERCHANT gọi được (SecurityConfig).
 */
@RestController
@RequestMapping("/api/merchant/restaurants")
@Tag(name = "Merchant - Quán của tôi", description = "Quán/chi nhánh của chủ quán đang đăng nhập")
public class MerchantRestaurantController {

    private final RestaurantService restaurantService;

    public MerchantRestaurantController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    @GetMapping
    @Operation(summary = "Danh sách quán/chi nhánh của chủ quán đang đăng nhập")
    public ResponseEntity<List<MerchantRestaurantDTO>> myRestaurants(@AuthenticationPrincipal AuthUser user) {
        // Lấy id chủ quán từ token, không nhận từ client, nên không xem được quán của người khác
        return ResponseEntity.ok(restaurantService.getRestaurantsOfOwner(user.id()));
    }
}
