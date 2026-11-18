package vn.hutech.fooddelivery.order.dto;

import java.math.BigDecimal;

/**
 * Bang tien cua mot don (dung cho xem truoc va khi dat hang).
 * Tien la VND (scale 0), distanceKm co 2 so le.
 * 3 truong voucher (tuan 5) chi co gia tri khi khach nhap ma:
 *   - voucherCode, voucherTitle: ma va ten chuong trinh DA AP DUNG (null neu khong ap dung duoc);
 *   - voucherMessage: loi nhan cua phan voucher (thanh cong, hoac ly do ma khong dung duoc).
 */
public record PriceBreakdown(
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        BigDecimal distanceKm,
        String voucherCode,
        String voucherTitle,
        String voucherMessage
) {
}
