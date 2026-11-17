package vn.hutech.fooddelivery.payment;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ky va kiem tra chu ky VNPay. Chi la tinh toan, khong can Spring hay database.
 * Gia tri mong doi duoc tinh doc lap bang openssl, vi du:
 *   printf '%s' 'chuoi_ky' | openssl dgst -sha512 -hmac 'SECRETTEST'
 */
class VnPaySignerTest {

    private static final String SECRET = "SECRETTEST";

    @Test
    void hmacSha512MatchesRfc4231TestVector() {
        // RFC 4231, test case 2
        assertEquals("164b7a7bfcf819e2e395fbe73b56e0a387bd64222e831fd610270cd7ea2505549758bf75"
                        + "c05a994a6d034f65f8f0e6fdcaeab1a34d4a6b4b636e070a38bce737",
                VnPaySigner.hmacSha512("Jefe", "what do ya want for nothing?"));
    }

    @Test
    void buildQuerySortsByNameEncodesValuesAndSkipsWhatIsNotSigned() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "FD261117000001");
        params.put("vnp_ReturnUrl", "http://localhost:5173/payment/vnpay-return");
        params.put("vnp_OrderInfo", "Thanh toan don hang FD261117000001");
        params.put("vnp_Command", "pay");
        params.put("vnp_Amount", "1000000");
        params.put("vnp_BankCode", "");                  // rong -> bo qua
        params.put("vnp_SecureHashType", "HmacSHA512");  // khong nam trong chuoi ky
        params.put("vnp_SecureHash", "abc");             // khong nam trong chuoi ky
        params.put("foo", "bar");                        // khong phai tham so vnp_ -> bo qua

        assertEquals("vnp_Amount=1000000&vnp_Command=pay"
                        + "&vnp_OrderInfo=Thanh+toan+don+hang+FD261117000001"
                        + "&vnp_ReturnUrl=http%3A%2F%2Flocalhost%3A5173%2Fpayment%2Fvnpay-return"
                        + "&vnp_TxnRef=FD261117000001",
                VnPaySigner.buildQuery(params));
    }

    @Test
    void validSignatureIsAcceptedAndAnyChangeIsRejected() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "1000000");
        params.put("vnp_Command", "pay");
        params.put("vnp_OrderInfo", "Thanh toan don hang FD261117000001");
        params.put("vnp_ReturnUrl", "http://localhost:5173/payment/vnpay-return");
        params.put("vnp_TxnRef", "FD261117000001");
        // Chu ky cua dung chuoi o test tren, tinh bang openssl
        params.put("vnp_SecureHash", "23cdd99c5cda9cb2e10b89d648fdd7498d149376956dcf9a5d776a1e78e358ad"
                + "939610a9b7152e6206ed5618dd0ebcfdfc7aa77b00348454d6cba92d77b54534");
        assertTrue(VnPaySigner.isValid(params, SECRET));

        // VNPay co the gui chu ky chu hoa: van chap nhan
        Map<String, String> upper = new HashMap<>(params);
        upper.put("vnp_SecureHash", params.get("vnp_SecureHash").toUpperCase());
        assertTrue(VnPaySigner.isValid(upper, SECRET));

        Map<String, String> tampered = new HashMap<>(params);
        tampered.put("vnp_Amount", "100");               // sua so tien
        assertFalse(VnPaySigner.isValid(tampered, SECRET));
        assertFalse(VnPaySigner.isValid(params, "KHOA_KHAC"));
        assertFalse(VnPaySigner.isValid(params, ""));   // chua cau hinh khoa

        Map<String, String> noHash = new HashMap<>(params);
        noHash.remove("vnp_SecureHash");
        assertFalse(VnPaySigner.isValid(noHash, SECRET));
    }

    @Test
    void queryRequestDataFollowsTheDocumentedFieldOrder() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("vnp_OrderInfo", "Truy van giao dich FD261117000001");  // thu tu dua vao khong quan trong
        body.put("vnp_RequestId", "req1");
        body.put("vnp_Version", "2.1.0");
        body.put("vnp_Command", "querydr");
        body.put("vnp_TmnCode", "TESTTMN1");
        body.put("vnp_TxnRef", "FD261117000001");
        body.put("vnp_TransactionDate", "20261117103000");
        body.put("vnp_CreateDate", "20261117104500");
        body.put("vnp_IpAddr", "127.0.0.1");

        String data = VnPaySigner.queryRequestData(body);
        assertEquals("req1|2.1.0|querydr|TESTTMN1|FD261117000001|20261117103000|20261117104500|127.0.0.1"
                + "|Truy van giao dich FD261117000001", data);
        assertEquals("39927d6079b375935506d945b1c81107687dd5f8afc4c712ce442dde0ce25287"
                        + "503630df15b4b4afe430059eb5d666551c86689ad252ffe7bcc1374d56974f58",
                VnPaySigner.hmacSha512(SECRET, data));
    }

    @Test
    void queryResponseDataUsesEmptyTextForMissingFields() {
        Map<String, Object> response = new HashMap<>();
        response.put("vnp_ResponseId", "resp1");
        response.put("vnp_Command", "querydr");
        response.put("vnp_ResponseCode", "00");
        response.put("vnp_Message", "QueryDR Success");
        response.put("vnp_TmnCode", "TESTTMN1");
        response.put("vnp_TxnRef", "FD261117000001");
        response.put("vnp_Amount", 1000000);             // VNPay co the tra so, khong phai chuoi
        response.put("vnp_BankCode", "NCB");
        response.put("vnp_PayDate", "20261117104000");
        response.put("vnp_TransactionNo", "14226112");
        response.put("vnp_TransactionType", "01");
        response.put("vnp_TransactionStatus", "00");
        response.put("vnp_OrderInfo", "Thanh toan don hang FD261117000001");
        // khong co vnp_PromotionCode, vnp_PromotionAmount (tuy chon)

        assertEquals("resp1|querydr|00|QueryDR Success|TESTTMN1|FD261117000001|1000000|NCB|20261117104000"
                        + "|14226112|01|00|Thanh toan don hang FD261117000001||",
                VnPaySigner.queryResponseData(response));
    }
}
