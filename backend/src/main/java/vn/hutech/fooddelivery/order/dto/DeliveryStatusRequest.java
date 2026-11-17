package vn.hutech.fooddelivery.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Tai xe bao da lay mon, dang giao (DELIVERING) hoac da giao xong (DELIVERED). */
public record DeliveryStatusRequest(
        @NotBlank(message = "Vui lòng chọn trạng thái mới")
        @Pattern(regexp = "DELIVERING|DELIVERED", message = "Tài xế chỉ cập nhật được DELIVERING hoặc DELIVERED")
        String status
) {
}
