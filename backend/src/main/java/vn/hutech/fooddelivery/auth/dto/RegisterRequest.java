package vn.hutech.fooddelivery.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Vui lòng nhập họ tên")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        String fullName,

        @NotBlank(message = "Vui lòng nhập số điện thoại")
        @Pattern(regexp = "0\\d{9}", message = "Số điện thoại phải gồm 10 chữ số, bắt đầu bằng 0")
        String phone,

        // Khong bat buoc. @Email cho phep null va chuoi rong.
        @Email(message = "Email không hợp lệ")
        @Size(max = 150, message = "Email tối đa 150 ký tự")
        String email,

        @NotBlank(message = "Vui lòng nhập mật khẩu")
        @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự")
        String password,

        // Khong cho dang ky ADMIN
        @NotBlank(message = "Vui lòng chọn vai trò")
        @Pattern(regexp = "CUSTOMER|MERCHANT|DRIVER", message = "Vai trò chỉ được là CUSTOMER, MERCHANT hoặc DRIVER")
        String role
) {
}
