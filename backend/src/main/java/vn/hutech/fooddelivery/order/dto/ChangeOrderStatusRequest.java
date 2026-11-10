package vn.hutech.fooddelivery.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Chu quan doi trang thai don. Huy don (CANCELLED) thi bat buoc co ly do. */
public record ChangeOrderStatusRequest(
        @NotBlank(message = "Vui lòng chọn trạng thái mới")
        @Pattern(regexp = "PREPARING|DELIVERING|DELIVERED|CANCELLED",
                message = "Trạng thái chỉ được là PREPARING, DELIVERING, DELIVERED hoặc CANCELLED")
        String status,

        @Size(max = 255, message = "Lý do tối đa 255 ký tự")
        String reason
) {

    /** Kiem tra cheo 2 truong; sai thi tra 400 cung cac loi khac (giong cach An lam o RegisterRequest). */
    @AssertTrue(message = "Hủy đơn phải ghi lý do")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isReasonGivenWhenCancelled() {
        return !"CANCELLED".equals(status) || (reason != null && !reason.isBlank());
    }
}
