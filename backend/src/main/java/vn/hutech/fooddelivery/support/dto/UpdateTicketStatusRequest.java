package vn.hutech.fooddelivery.support.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * DTO yêu cầu cập nhật trạng thái ticket hỗ trợ dành cho Admin.
 */
public record UpdateTicketStatusRequest(
        @NotBlank(message = "Trạng thái không được để trống")
        @Pattern(regexp = "OPEN|IN_PROGRESS|RESOLVED|CLOSED", message = "Trạng thái chỉ có thể là OPEN, IN_PROGRESS, RESOLVED hoặc CLOSED")
        @Schema(description = "Trạng thái ticket", allowableValues = {"OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"}, example = "RESOLVED")
        String status
) {
}
