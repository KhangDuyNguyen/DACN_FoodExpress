package vn.hutech.fooddelivery.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.security.AuthUser;
import vn.hutech.fooddelivery.user.dto.AddressRequest;
import vn.hutech.fooddelivery.user.dto.AddressResponse;

import java.util.List;

/** So dia chi giao hang cua nguoi dang dang nhap (can dang nhap, SecurityConfig: anyRequest().authenticated()). */
@RestController
@RequestMapping("/api/users/me/addresses")
@Tag(name = "Addresses", description = "Sổ địa chỉ giao hàng của tôi")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    @Operation(summary = "Danh sách địa chỉ của tôi (mặc định lên đầu)")
    public ResponseEntity<List<AddressResponse>> list(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(addressService.list(user.id()));
    }

    @PostMapping
    @Operation(summary = "Thêm địa chỉ (địa chỉ đầu tiên tự thành mặc định)")
    public ResponseEntity<AddressResponse> create(@AuthenticationPrincipal AuthUser user,
                                                  @Valid @RequestBody AddressRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(addressService.create(user.id(), req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa địa chỉ; isDefault=true thì đặt làm mặc định")
    public ResponseEntity<AddressResponse> update(@AuthenticationPrincipal AuthUser user,
                                                  @PathVariable long id,
                                                  @Valid @RequestBody AddressRequest req) {
        return ResponseEntity.ok(addressService.update(user.id(), id, req));
    }

    @PatchMapping("/{id}/default")
    @Operation(summary = "Đặt địa chỉ làm mặc định")
    public ResponseEntity<AddressResponse> setDefault(@AuthenticationPrincipal AuthUser user,
                                                      @PathVariable long id) {
        return ResponseEntity.ok(addressService.setDefault(user.id(), id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa địa chỉ (xóa địa chỉ mặc định thì địa chỉ mới nhất còn lại thành mặc định)")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        addressService.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}
