package vn.hutech.fooddelivery.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Doi mat khau: phai nhap dung mat khau hien tai. Mat khau moi cung luat voi luc dang ky. */
public record ChangePasswordRequest(
        @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
        String currentPassword,

        // BCrypt chi dung 72 byte dau nen gioi han 72 ky tu (giong RegisterRequest)
        @NotBlank(message = "Vui lòng nhập mật khẩu mới")
        @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự")
        String newPassword
) {
}
