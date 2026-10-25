package vn.hutech.fooddelivery.promotion;

/**
 * Loại giảm giá của voucher:
 * - PERCENT: Giảm theo % giá trị đơn hàng (ví dụ: 10%, 20%).
 * - FIXED: Giảm số tiền cố định (ví dụ: 20.000đ, 50.000đ).
 * - FREE_SHIP: Miễn phí giao hàng (hoặc giảm phí ship theo max_discount).
 *
 * Người phụ trách: Sầm Xuân Điệp.
 */
public enum DiscountType {
    PERCENT,
    FIXED,
    FREE_SHIP
}
