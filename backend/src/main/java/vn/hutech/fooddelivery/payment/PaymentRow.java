package vn.hutech.fooddelivery.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 1 dong bang payments: 1 lan thanh toan cua 1 don. transactionRef = vnp_TxnRef da gui VNPay (= ma don). */
public record PaymentRow(
        long id,
        long orderId,
        String method,
        BigDecimal amount,
        String status,
        String transactionRef,
        LocalDateTime createdAt
) {
}
