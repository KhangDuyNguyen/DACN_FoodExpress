package vn.hutech.fooddelivery.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Goi API querydr cua VNPay de hoi ket qua 1 giao dich (may chu minh goi thang may chu VNPay).
 * Tach rieng class nay de test thay bang mock, khong phai goi Internet.
 * Tai lieu: https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr&refund.html
 */
@Component
public class VnPayClient {

    private static final Logger log = LoggerFactory.getLogger(VnPayClient.class);

    private final VnPayProperties props;
    private final RestClient restClient;

    public VnPayClient(VnPayProperties props) {
        this.props = props;
        // Het thoi gian cho thi bo, de trang quay ve khong bi treo lau khi VNPay cham
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /**
     * Hoi VNPay giao dich txnRef da thanh toan chua.
     *
     * @param transactionDate thoi diem tao giao dich, phai bang vnp_CreateDate da gui luc tao URL thanh toan
     * @return ket qua; loi mang hoac phan hoi sai chu ky thi tra ma "99" (chua ro), khong nem loi
     */
    public QueryResult queryTransaction(String txnRef, LocalDateTime transactionDate) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("vnp_RequestId", UUID.randomUUID().toString().replace("-", "")); // 32 ky tu, khong trung
        body.put("vnp_Version", VnPayService.VERSION);
        body.put("vnp_Command", "querydr");
        body.put("vnp_TmnCode", props.tmnCode());
        body.put("vnp_TxnRef", txnRef);
        body.put("vnp_OrderInfo", "Truy van giao dich " + txnRef);
        body.put("vnp_TransactionDate", transactionDate.format(VnPayService.VNPAY_TIME));
        body.put("vnp_CreateDate", LocalDateTime.now(VnPayService.VIETNAM).format(VnPayService.VNPAY_TIME));
        body.put("vnp_IpAddr", serverIp());
        body.put("vnp_SecureHash", VnPaySigner.hmacSha512(props.hashSecret(), VnPaySigner.queryRequestData(body)));
        try {
            Map<String, Object> response = restClient.post()
                    .uri(props.apiUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (response == null) {
                return QueryResult.unknown();
            }
            // Kiem tra phan hoi dung la cua VNPay (ky bang cung khoa bi mat)
            String expected = VnPaySigner.hmacSha512(props.hashSecret(), VnPaySigner.queryResponseData(response));
            if (!VnPaySigner.sameHash(expected, text(response.get("vnp_SecureHash")))) {
                log.warn("Phan hoi querydr cua giao dich {} sai chu ky, bo qua", txnRef);
                return QueryResult.unknown();
            }
            return new QueryResult(text(response.get("vnp_ResponseCode")), text(response.get("vnp_TransactionStatus")),
                    text(response.get("vnp_Amount")), response);
        } catch (RestClientException ex) {
            log.warn("Khong goi duoc querydr cua VNPay cho giao dich {}: {}", txnRef, ex.getMessage());
            return QueryResult.unknown();
        }
    }

    /** IP cua may chu goi API (tai lieu querydr yeu cau vnp_IpAddr la IP may chu, khong phai cua khach). */
    private static String serverIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException ex) {
            return "127.0.0.1";
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Ket qua querydr.
     *
     * @param responseCode      "00" = hoi thanh cong (xem transactionStatus); "91" = VNPay khong co giao dich nay;
     *                          con lai (ke ca "99" minh tu dat khi loi mang/sai chu ky) = chua ro
     * @param transactionStatus "00" = da thanh toan; "01" = chua xong; ma khac = loi, khong thanh cong
     * @param amount            so tien x 100 nhu VNPay gui
     * @param raw               toan bo phan hoi, luu vao payments.gateway_payload de doi soat
     */
    public record QueryResult(String responseCode, String transactionStatus, String amount, Map<String, Object> raw) {

        static QueryResult unknown() {
            return new QueryResult("99", null, null, Map.of());
        }
    }
}
