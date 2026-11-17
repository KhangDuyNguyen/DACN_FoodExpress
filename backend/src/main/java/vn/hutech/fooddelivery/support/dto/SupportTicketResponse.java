package vn.hutech.fooddelivery.support.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * DTO phản hồi thông tin ticket hỗ trợ.
 */
public record SupportTicketResponse(
        @Schema(description = "ID ticket", example = "1")
        Long id,

        @Schema(description = "ID người dùng", example = "10")
        Long userId,

        @Schema(description = "Họ tên người dùng", example = "Nguyễn Văn A")
        String userName,

        @Schema(description = "Email người dùng", example = "user@example.com")
        String userEmail,

        @Schema(description = "Họ tên khách hàng (alias của userName)", example = "Nguyễn Văn A")
        String customerName,

        @Schema(description = "Email khách hàng (alias của userEmail)", example = "user@example.com")
        String customerEmail,

        @Schema(description = "ID đơn hàng liên quan nếu có", example = "101")
        Long orderId,

        @Schema(description = "Tiêu đề yêu cầu hỗ trợ", example = "Đơn hàng giao trễ")
        String subject,

        @Schema(description = "Mô tả chi tiết", example = "Tài xế giao trễ hơn 45 phút...")
        String description,

        @Schema(description = "Trạng thái ticket (OPEN, IN_PROGRESS, RESOLVED, CLOSED)", example = "OPEN")
        String status,

        @Schema(description = "Thời điểm tạo ticket")
        LocalDateTime createdAt,

        @Schema(description = "Thời điểm xử lý xong/đóng ticket nếu có")
        LocalDateTime resolvedAt
) {
    public SupportTicketResponse(
            Long id,
            Long userId,
            String userName,
            String userEmail,
            Long orderId,
            String subject,
            String description,
            String status,
            LocalDateTime createdAt,
            LocalDateTime resolvedAt
    ) {
        this(id, userId, userName, userEmail, userName, userEmail, orderId, subject, description, status, createdAt, resolvedAt);
    }

    @JsonProperty("user_id")
    public Long getUserIdSnake() {
        return userId;
    }

    @JsonProperty("order_id")
    public Long getOrderIdSnake() {
        return orderId;
    }

    @JsonProperty("created_at")
    public LocalDateTime getCreatedAtSnake() {
        return createdAt;
    }

    @JsonProperty("resolved_at")
    public LocalDateTime getResolvedAtSnake() {
        return resolvedAt;
    }
}
