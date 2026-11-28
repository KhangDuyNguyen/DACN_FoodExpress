package vn.hutech.fooddelivery.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.security.AuthUser;

@RestController
@RequestMapping("/api/devices/token")
class DeviceTokenController {
    private final DeviceTokenRepository tokens;

    DeviceTokenController(DeviceTokenRepository tokens) {
        this.tokens = tokens;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void register(@AuthenticationPrincipal AuthUser user, @RequestBody @Valid DeviceTokenRequest request) {
        requireAndroidPushRole(user);
        tokens.registerAndroid(user.id(), request.token());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@AuthenticationPrincipal AuthUser user, @RequestBody @Valid DeviceTokenRequest request) {
        requireAndroidPushRole(user);
        tokens.remove(user.id(), request.token());
    }

    private static void requireAndroidPushRole(AuthUser user) {
        if (user == null || !("CUSTOMER".equals(user.role()) || "DRIVER".equals(user.role()))) {
            throw new ForbiddenException("Chỉ khách hàng và tài xế được đăng ký thiết bị Android");
        }
    }

    record DeviceTokenRequest(@NotBlank @Size(max = 255) @Pattern(regexp = "\\S+") String token) {}
}
