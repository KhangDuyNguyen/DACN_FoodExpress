package vn.hutech.fooddelivery.promotion.dto;

import jakarta.validation.constraints.NotNull;
import vn.hutech.fooddelivery.promotion.VoucherStatus;

/**
 * DTO cập nhật trạng thái voucher (ACTIVE, PAUSED, EXPIRED).
 * Người phụ trách: Sầm Xuân Điệp.
 */
public record VoucherStatusRequest(
        @NotNull(message = "Trạng thái không được để trống")
        VoucherStatus status
) {
}

