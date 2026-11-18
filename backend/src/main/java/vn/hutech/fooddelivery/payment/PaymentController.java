package vn.hutech.fooddelivery.payment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.payment.dto.VnPayIpnResponse;
import vn.hutech.fooddelivery.payment.dto.VnPayReturnResponse;

import java.util.Map;

/**
 * 2 API VNPay. SecurityConfig de cong khai (/api/payments/{@literal *}/ipn va /return) vi VNPay va trang quay ve
 * goi khong kem token; thay vao do moi yeu cau deu phai dung chu ky HMAC-SHA512 (VnPaySigner).
 */
@RestController
@RequestMapping("/api/payments/vnpay")
@Tag(name = "Payments", description = "Thanh toán VNPay sandbox (công khai, xác thực bằng chữ ký của VNPay)")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final VnPayService vnPayService;

    public PaymentController(VnPayService vnPayService) {
        this.vnPayService = vnPayService;
    }

    @GetMapping("/ipn")
    @Operation(summary = "VNPay gọi báo kết quả thanh toán (IPN); trả RspCode theo tài liệu VNPay")
    public VnPayIpnResponse ipn(@RequestParam Map<String, String> params) {
        try {
            return vnPayService.handleIpn(params);
        } catch (RuntimeException ex) {
            // Loi bat ngo: phan ghi DB da rollback; tra 99 de VNPay goi lai sau
            log.error("Loi xu ly IPN VNPay cho giao dich {}", params.get("vnp_TxnRef"), ex);
            return VnPayIpnResponse.UNKNOWN_ERROR;
        }
    }

    @GetMapping("/return")
    @Operation(summary = "Trang web gọi khi khách quay về từ VNPay: kiểm tra chữ ký, hỏi lại VNPay nếu đơn còn chờ")
    public VnPayReturnResponse vnpayReturn(@RequestParam Map<String, String> params) {
        return vnPayService.handleReturn(params);
    }
}
