package vn.hutech.fooddelivery.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Khach sua thong tin cua chinh minh. Luat giong luc dang ky (RegisterRequest).
 * Khong co so dien thoai: so dien thoai dung de dang nhap nen khong cho doi.
 */
public record UpdateProfileRequest(
        @NotBlank(message = "Vui lòng nhập họ tên")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        String fullName,

        // Khong bat buoc: de trong la bo email. @Email cho phep null va chuoi rong.
        @Email(message = "Email không hợp lệ")
        @Size(max = 150, message = "Email tối đa 150 ký tự")
        String email
) {
    /**
     * Cat khoang trang 2 dau NGAY KHI tao doi tuong, truoc buoc kiem tra:
     * neu khong, @Email se bao loi voi "  ten@mail.com " du nguoi dung chi go thua dau cach.
     */
    public UpdateProfileRequest {
        fullName = fullName == null ? null : fullName.trim();
        email = email == null ? null : email.trim();
    }
}
