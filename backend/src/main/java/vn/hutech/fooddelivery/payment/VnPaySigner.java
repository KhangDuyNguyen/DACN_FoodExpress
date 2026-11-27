package vn.hutech.fooddelivery.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Ky va kiem tra chu ky VNPay bang HMAC-SHA512, dung theo tai lieu sandbox.vnpayment.vn (phien ban 2.1.0).
 * Chi la ham tinh toan (khong doc cau hinh, khong goi database) nen unit test rieng duoc: VnPaySignerTest.
 */
public final class VnPaySigner {

    /** Thu tu truong trong chuoi ky khi GUI yeu cau querydr (theo tai lieu querydr). */
    private static final List<String> QUERY_REQUEST_FIELDS = List.of(
            "vnp_RequestId", "vnp_Version", "vnp_Command", "vnp_TmnCode", "vnp_TxnRef",
            "vnp_TransactionDate", "vnp_CreateDate", "vnp_IpAddr", "vnp_OrderInfo");

    /** Thu tu truong trong chuoi ky cua PHAN HOI querydr (theo tai lieu querydr). */
    private static final List<String> QUERY_RESPONSE_FIELDS = List.of(
            "vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message", "vnp_TmnCode", "vnp_TxnRef",
            "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo", "vnp_TransactionType",
            "vnp_TransactionStatus", "vnp_OrderInfo", "vnp_PromotionCode", "vnp_PromotionAmount");

    /** Thu tu truong trong chuoi ky khi GUI yeu cau hoan tien (theo tai lieu refund). */
    private static final List<String> REFUND_REQUEST_FIELDS = List.of(
            "vnp_RequestId", "vnp_Version", "vnp_Command", "vnp_TmnCode", "vnp_TransactionType", "vnp_TxnRef",
            "vnp_Amount", "vnp_TransactionNo", "vnp_TransactionDate", "vnp_CreateBy", "vnp_CreateDate",
            "vnp_IpAddr", "vnp_OrderInfo");

    /** Thu tu truong trong chuoi ky cua PHAN HOI hoan tien (theo tai lieu refund). */
    private static final List<String> REFUND_RESPONSE_FIELDS = List.of(
            "vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message", "vnp_TmnCode", "vnp_TxnRef",
            "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo", "vnp_TransactionType",
            "vnp_TransactionStatus", "vnp_OrderInfo");

    private VnPaySigner() {
    }

    /**
     * Chuoi du lieu de ky cua API thanh toan (dung chung cho URL thanh toan, IPN va trang quay ve):
     *   - chi lay tham so ten bat dau bang "vnp_", bo vnp_SecureHash, vnp_SecureHashType va tham so rong;
     *   - sap xep theo ten tang dan (TreeMap tu sap xep);
     *   - moi cap viet "ten=gia_tri", gia tri URL-encode (dau cach thanh "+"), noi bang "&".
     * Chuoi nay cung chinh la query string cua URL thanh toan.
     */
    public static String buildQuery(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
                .filter(e -> e.getKey().startsWith("vnp_"))
                .filter(e -> !e.getKey().equals("vnp_SecureHash") && !e.getKey().equals("vnp_SecureHashType"))
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    /** HMAC-SHA512 cua data voi khoa secret, tra ve chuoi hex chu thuong (128 ky tu). */
    public static String hmacSha512(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Khong tao duoc chu ky HMAC-SHA512", ex);
        }
    }

    /**
     * Chu ky VNPay gui kem (vnp_SecureHash) co khop voi chu ky minh tu tinh lai bang khoa bi mat khong.
     * Khop nghia la du lieu dung la cua VNPay va khong bi sua tren duong di (vi chi VNPay va minh biet khoa).
     */
    public static boolean isValid(Map<String, String> params, String secret) {
        if (secret == null || secret.isEmpty()) {
            return false;
        }
        return sameHash(hmacSha512(secret, buildQuery(params)), params.get("vnp_SecureHash"));
    }

    /**
     * So chu ky tu tinh (hex chu thuong) voi chu ky nhan duoc, khong phan biet hoa/thuong.
     * MessageDigest.isEqual so het chuoi (khong dung som o ky tu sai dau tien) de khong lo thong tin qua thoi gian.
     */
    public static boolean sameHash(String expected, String received) {
        if (received == null || received.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                received.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    /** Chuoi ky khi gui yeu cau querydr: gia tri cac truong theo dung thu tu tai lieu, noi bang "|". */
    public static String queryRequestData(Map<String, String> body) {
        return joinFields(body, QUERY_REQUEST_FIELDS);
    }

    /** Chuoi ky cua phan hoi querydr, de kiem tra phan hoi dung la cua VNPay. Truong khong co thi de rong. */
    public static String queryResponseData(Map<String, Object> response) {
        return joinFields(response, QUERY_RESPONSE_FIELDS);
    }

    /** Chuoi ky khi gui yeu cau hoan tien (vnp_Command=refund): gia tri cac truong theo thu tu tai lieu, noi "|". */
    public static String refundRequestData(Map<String, String> body) {
        return joinFields(body, REFUND_REQUEST_FIELDS);
    }

    /** Chuoi ky cua phan hoi refund, de kiem tra phan hoi dung la cua VNPay. */
    public static String refundResponseData(Map<String, Object> response) {
        return joinFields(response, REFUND_RESPONSE_FIELDS);
    }

    private static String joinFields(Map<String, ?> data, List<String> fields) {
        return fields.stream()
                .map(field -> data.get(field) == null ? "" : String.valueOf(data.get(field)))
                .collect(Collectors.joining("|"));
    }
}
