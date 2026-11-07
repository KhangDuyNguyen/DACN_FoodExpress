package vn.hutech.fooddelivery.user.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Them/sua 1 dia chi giao hang. Toa do bat buoc (chon tren ban do) vi can de tinh phi ship.
 * isDefault = true: dat lam dia chi mac dinh (cac dia chi khac tu bo mac dinh).
 */
public record AddressRequest(
        @Size(max = 50, message = "Tên gợi nhớ tối đa 50 ký tự")
        String label,

        @NotBlank(message = "Vui lòng nhập tên người nhận")
        @Size(max = 100, message = "Tên người nhận tối đa 100 ký tự")
        String receiverName,

        @NotBlank(message = "Vui lòng nhập số điện thoại người nhận")
        @Pattern(regexp = "0\\d{9}", message = "Số điện thoại phải gồm 10 chữ số, bắt đầu bằng 0")
        String receiverPhone,

        @NotBlank(message = "Vui lòng nhập địa chỉ")
        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
        String addressLine,

        @NotNull(message = "Vui lòng chọn vị trí trên bản đồ")
        @DecimalMin(value = "-90", message = "Vĩ độ không hợp lệ")
        @DecimalMax(value = "90", message = "Vĩ độ không hợp lệ")
        Double lat,

        @NotNull(message = "Vui lòng chọn vị trí trên bản đồ")
        @DecimalMin(value = "-180", message = "Kinh độ không hợp lệ")
        @DecimalMax(value = "180", message = "Kinh độ không hợp lệ")
        Double lng,

        // Khong gui thi mac dinh false
        boolean isDefault
) {
}
