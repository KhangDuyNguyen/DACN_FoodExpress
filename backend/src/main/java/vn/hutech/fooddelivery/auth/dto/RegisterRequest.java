package vn.hutech.fooddelivery.auth.dto;

// ===== BẮT ĐẦU BỔ SUNG - AN: REGISTER A2 DRIVER - VALIDATION =====
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
// ===== KẾT THÚC BỔ SUNG - AN: REGISTER A2 DRIVER - VALIDATION =====
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
        String role,

        // ===== BẮT ĐẦU BỔ SUNG - AN: REGISTER A2 DRIVER - PHƯƠNG TIỆN =====
        @Schema(description = "Bắt buộc khi role là DRIVER; CUSTOMER/MERCHANT không cần",
                allowableValues = {"MOTORBIKE", "BICYCLE", "CAR"})
        String vehicleType,

        @Schema(description = "DRIVER: tối đa 20 ký tự; MOTORBIKE/CAR bắt buộc, BICYCLE có thể để trống",
                maxLength = 20)
        String licensePlate
        // ===== KẾT THÚC BỔ SUNG - AN: REGISTER A2 DRIVER - PHƯƠNG TIỆN =====
) {

    // ===== BẮT ĐẦU BỔ SUNG - AN: REGISTER A2 DRIVER - KIỂM TRA THEO ROLE =====
    public RegisterRequest {
        if ("DRIVER".equals(role)) {
            licensePlate = licensePlate == null || licensePlate.isBlank() ? null : licensePlate.trim();
        }
    }

    /** Giu constructor cu cho cac cho goi CUSTOMER/MERCHANT bang Java. */
    public RegisterRequest(String fullName, String phone, String email, String password, String role) {
        this(fullName, phone, email, password, role, null, null);
    }

    @AssertTrue(message = "DRIVER phải chọn vehicleType là MOTORBIKE, BICYCLE hoặc CAR")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isDriverVehicleTypeValid() {
        return !"DRIVER".equals(role) || "MOTORBIKE".equals(vehicleType)
                || "BICYCLE".equals(vehicleType) || "CAR".equals(vehicleType);
    }

    @AssertTrue(message = "Biển số tối đa 20 ký tự và bắt buộc với MOTORBIKE/CAR")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isDriverLicensePlateValid() {
        if (!"DRIVER".equals(role)) {
            return true;
        }
        if (licensePlate != null && licensePlate.length() > 20) {
            return false;
        }
        return !("MOTORBIKE".equals(vehicleType) || "CAR".equals(vehicleType)) || licensePlate != null;
    }
    // ===== KẾT THÚC BỔ SUNG - AN: REGISTER A2 DRIVER - KIỂM TRA THEO ROLE =====
}
