package vn.hutech.fooddelivery.cart;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.cart.dto.AddCartItemRequest;
import vn.hutech.fooddelivery.cart.dto.CartResponse;
import vn.hutech.fooddelivery.cart.dto.UpdateCartItemRequest;
import vn.hutech.fooddelivery.security.AuthUser;

/** Gio hang cua khach dang dang nhap. SecurityConfig chi cho CUSTOMER goi /api/cart/**. */
@RestController
@RequestMapping("/api/cart")
@Tag(name = "Cart", description = "Giỏ hàng (chỉ CUSTOMER)")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    @Operation(summary = "Xem giỏ hàng (giá luôn đọc lại từ database)")
    public ResponseEntity<CartResponse> getCart(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(cartService.getCart(user.id()));
    }

    @PostMapping("/items")
    @Operation(summary = "Thêm món vào giỏ; giỏ đang có món quán khác thì 409, gửi replaceCart=true để thay giỏ")
    public ResponseEntity<CartResponse> addItem(@AuthenticationPrincipal AuthUser user,
                                                @Valid @RequestBody AddCartItemRequest req) {
        return ResponseEntity.ok(cartService.addItem(user.id(), req));
    }

    @PatchMapping("/items/{cartItemId}")
    @Operation(summary = "Đổi số lượng và ghi chú của 1 món trong giỏ")
    public ResponseEntity<CartResponse> updateItem(@AuthenticationPrincipal AuthUser user,
                                                   @PathVariable long cartItemId,
                                                   @Valid @RequestBody UpdateCartItemRequest req) {
        return ResponseEntity.ok(cartService.updateItem(user.id(), cartItemId, req));
    }

    @DeleteMapping("/items/{cartItemId}")
    @Operation(summary = "Xóa 1 món khỏi giỏ (xóa món cuối cùng thì xóa luôn giỏ)")
    public ResponseEntity<CartResponse> removeItem(@AuthenticationPrincipal AuthUser user,
                                                   @PathVariable long cartItemId) {
        return ResponseEntity.ok(cartService.removeItem(user.id(), cartItemId));
    }

    @DeleteMapping
    @Operation(summary = "Xóa cả giỏ hàng")
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal AuthUser user) {
        cartService.clearCart(user.id());
        return ResponseEntity.noContent().build();
    }
}
