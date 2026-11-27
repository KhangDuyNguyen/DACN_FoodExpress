package vn.hutech.fooddelivery.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 1 yeu cau hoan tien dang cho gui cong thanh toan, kem thong tin giao dich goc can cho API hoan tien.
 *
 * @param amount               so tien hoan (= so tien khach da tra, hoan toan phan)
 * @param transactionRef       ma tham chieu da gui cong thanh toan luc thanh toan (= ma don)
 * @param paymentCreatedAt     thoi diem tao giao dich thanh toan (VNPay: = vnp_CreateDate luc thanh toan)
 * @param gatewayTransactionNo ma giao dich phia cong thanh toan (VNPay: vnp_TransactionNo), lay tu du lieu
 *                             cong thanh toan da luu luc thanh toan thanh cong
 */
public record RefundRow(long id, long paymentId, BigDecimal amount, String transactionRef,
                        LocalDateTime paymentCreatedAt, String gatewayTransactionNo) {
}
