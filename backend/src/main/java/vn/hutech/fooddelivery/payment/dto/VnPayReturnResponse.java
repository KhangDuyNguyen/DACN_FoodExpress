package vn.hutech.fooddelivery.payment.dto;

/**
 * Ket qua cho trang /payment/vnpay-return cua web.
 *
 * @param paymentStatus PENDING (chua xac nhan duoc), SUCCESS hoac FAILED
 * @param message       cau hien cho khach, kem ly do theo bang ma loi VNPay khi that bai
 */
public record VnPayReturnResponse(
        long orderId,
        String orderCode,
        String paymentStatus,
        boolean success,
        String message
) {
}
