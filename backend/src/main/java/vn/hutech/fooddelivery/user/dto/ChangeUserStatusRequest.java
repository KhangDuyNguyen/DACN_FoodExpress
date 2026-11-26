package vn.hutech.fooddelivery.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Admin khoa (LOCKED) hoac mo khoa (ACTIVE) 1 tai khoan. */
public record ChangeUserStatusRequest(
        @NotBlank(message = "Vui lòng chọn trạng thái")
        @Pattern(regexp = "ACTIVE|LOCKED", message = "Trạng thái chỉ được là ACTIVE hoặc LOCKED")
        String status
) {
}
