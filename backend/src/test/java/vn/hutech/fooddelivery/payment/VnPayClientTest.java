package vn.hutech.fooddelivery.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Noi dung yeu cau hoan tien gui VNPay (chi tao du lieu, khong goi mang). */
class VnPayClientTest {

    private static final String SECRET = "khoa_gia_cho_test";

    @Test
    void refundRequestHasEveryDocumentedFieldAndAValidSignature() {
        VnPayClient client = new VnPayClient(new VnPayProperties("TESTTMN1", SECRET,
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction",
                "http://localhost:5173/payment/vnpay-return"));
        // Don 60.000d, tra tien luc 09:30:05 ngay 27/11/2026, ma giao dich VNPay 14226112
        RefundRow refund = new RefundRow(7, 3, new BigDecimal("60000"), "FD261127000001",
                LocalDateTime.of(2026, 11, 27, 9, 30, 5), "14226112");

        Map<String, String> body = client.refundRequest(refund);

        assertEquals("2.1.0", body.get("vnp_Version"));
        assertEquals("refund", body.get("vnp_Command"));
        assertEquals("TESTTMN1", body.get("vnp_TmnCode"));
        assertEquals("02", body.get("vnp_TransactionType"));         // hoan toan phan
        assertEquals("FD261127000001", body.get("vnp_TxnRef"));
        assertEquals("6000000", body.get("vnp_Amount"));              // so tien x 100 nhu luc thanh toan
        assertEquals("14226112", body.get("vnp_TransactionNo"));
        assertEquals("20261127093005", body.get("vnp_TransactionDate")); // = vnp_CreateDate luc thanh toan
        assertEquals(32, body.get("vnp_RequestId").length());
        assertTrue(body.get("vnp_CreateDate").matches("\\d{14}"), body.get("vnp_CreateDate"));
        assertFalse(body.get("vnp_CreateBy").isBlank());
        assertFalse(body.get("vnp_IpAddr").isBlank());
        assertEquals("Hoan tien giao dich FD261127000001", body.get("vnp_OrderInfo"));
        assertEquals(VnPaySigner.hmacSha512(SECRET, VnPaySigner.refundRequestData(body)), body.get("vnp_SecureHash"));
    }
}
