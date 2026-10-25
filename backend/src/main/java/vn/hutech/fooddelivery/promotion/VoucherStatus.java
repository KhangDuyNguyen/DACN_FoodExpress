package vn.hutech.fooddelivery.promotion;

/**
 * Trạng thái hoạt động của voucher:
 * - ACTIVE: Đang hoạt động, có thể áp dụng khi đặt món.
 * - PAUSED: Tạm dừng chương trình khuyến mãi.
 * - EXPIRED: Đã hết hạn sử dụng hoặc hết ngân sách lượt dùng.
 *
 * Người phụ trách: Sầm Xuân Điệp.
 */
public enum VoucherStatus {
    ACTIVE,
    PAUSED,
    EXPIRED
}
