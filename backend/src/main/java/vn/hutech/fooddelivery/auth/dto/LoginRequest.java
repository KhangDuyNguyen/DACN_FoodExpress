package vn.hutech.fooddelivery.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** identifier la so dien thoai hoac email. */
public record LoginRequest(
        @NotBlank(message = "Vui lòng nhập số điện thoại hoặc email")
        String identifier,

        @NotBlank(message = "Vui lòng nhập mật khẩu")
        String password
) {
}
