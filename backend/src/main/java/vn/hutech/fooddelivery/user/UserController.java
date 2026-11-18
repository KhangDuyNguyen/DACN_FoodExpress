package vn.hutech.fooddelivery.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.security.AuthUser;
import vn.hutech.fooddelivery.user.dto.ChangePasswordRequest;
import vn.hutech.fooddelivery.user.dto.UpdateProfileRequest;
import vn.hutech.fooddelivery.user.dto.UserResponse;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Thông tin người dùng")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Lấy thông tin người dùng đang đăng nhập")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal AuthUser user) {
        // SecurityConfig da bat buoc dang nhap cho duong dan nay, nen user khong null
        return ResponseEntity.ok(userService.getById(user.id()));
    }

    @PutMapping("/me")
    @Operation(summary = "Sửa họ tên, email của chính mình (số điện thoại không đổi được)")
    public ResponseEntity<UserResponse> updateMe(@AuthenticationPrincipal AuthUser user,
                                                 @Valid @RequestBody UpdateProfileRequest req) {
        return ResponseEntity.ok(userService.updateProfile(user.id(), req));
    }

    @PutMapping("/me/password")
    @Operation(summary = "Đổi mật khẩu của chính mình (phải nhập đúng mật khẩu hiện tại)")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthUser user,
                                               @Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(user.id(), req);
        return ResponseEntity.noContent().build();
    }
}
