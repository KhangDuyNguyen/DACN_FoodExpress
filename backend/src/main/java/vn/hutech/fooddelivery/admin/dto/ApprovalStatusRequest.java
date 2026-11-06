package vn.hutech.fooddelivery.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request thay đổi trạng thái phê duyệt của Chủ quán, Nhà hàng hoặc Tài xế.
 * Người phụ trách: Sầm Xuân Điệp.
 */
public record ApprovalStatusRequest(
                @NotBlank(message = "Trạng thái phê duyệt không được để trống") @Pattern(regexp = "PENDING|APPROVED|REJECTED", message = "Trạng thái chỉ có thể là PENDING, APPROVED hoặc REJECTED") @Schema(description = "Trạng thái phê duyệt", allowableValues = {
                                "PENDING", "APPROVED", "REJECTED" }, example = "APPROVED") String status,

                @Schema(description = "Lý do phê duyệt hoặc từ chối (tùy chọn)", example = "Hồ sơ hợp lệ") String reason) {
}
