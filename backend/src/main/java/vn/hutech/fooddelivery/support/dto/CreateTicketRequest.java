package vn.hutech.fooddelivery.support.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO yêu cầu tạo ticket hỗ trợ từ người dùng.
 */
public record CreateTicketRequest(
        @NotBlank(message = "Tiêu đề không được để trống")
        @Size(max = 200, message = "Tiêu đề không được vượt quá 200 ký tự")
        @Schema(description = "Tiêu đề ticket hỗ trợ", example = "Đơn hàng giao trễ")
        String subject,

        @Size(max = 5000, message = "Mô tả không được vượt quá 5000 ký tự")
        @Schema(description = "Mô tả chi tiết vấn đề cần hỗ trợ", example = "Đơn hàng của tôi đã quá thời gian dự kiến 45 phút...")
        String description,

        @JsonAlias({"order_id", "orderId"})
        @Schema(description = "ID đơn hàng liên quan (tuỳ chọn)", example = "101")
        Long orderId
) {
}
