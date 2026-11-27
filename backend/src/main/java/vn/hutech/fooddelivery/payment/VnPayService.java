package vn.hutech.fooddelivery.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.common.BadRequestException;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.payment.dto.VnPayIpnResponse;
import vn.hutech.fooddelivery.payment.dto.VnPayReturnResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Thanh toan VNPay sandbox theo tai lieu chinh thuc (phien ban 2.1.0), 3 buoc:
 *   1. createPayment: don VNPay vua tao -> ghi giao dich PENDING va tra URL thanh toan da ky.
 *   2. handleIpn: may chu VNPay bao ket qua -> kiem tra theo dung thu tu tai lieu roi cap nhat.
 *   3. handleReturn: trinh duyet cua khach quay ve -> kiem tra chu ky; giao dich con cho thi tu hoi VNPay (querydr).
 * Khong log khoa bi mat va chu ky.
 */
@Service
public class VnPayService {

    static final String VERSION = "2.1.0";
    /** Thoi gian VNPay dung: gio Viet Nam (GMT+7), dang yyyyMMddHHmmss. */
    static final DateTimeFormatter VNPAY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final Logger log = LoggerFactory.getLogger(VnPayService.class);
    private static final String METHOD = "VNPAY";
    private static final int EXPIRE_MINUTES = 15;
    /** Ma 11 trong bang ma loi VNPay: "Da het han cho thanh toan". Ghi vao ly do huy don qua han. */
    private static final String EXPIRED_CODE = "11";
    /** Moi lan quet xu ly toi da 50 giao dich qua han, con lai de lan sau. */
    private static final int CLEANUP_BATCH = 50;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Ly do theo vnp_ResponseCode (bang ma loi trong tai lieu VNPay) de hien cho khach. */
    private static final Map<String, String> RESPONSE_REASONS = Map.ofEntries(
            Map.entry("07", "Trừ tiền thành công nhưng giao dịch bị nghi ngờ"),
            Map.entry("09", "Thẻ/Tài khoản chưa đăng ký dịch vụ InternetBanking tại ngân hàng"),
            Map.entry("10", "Xác thực thông tin thẻ/tài khoản không đúng quá 3 lần"),
            Map.entry("11", "Đã hết hạn chờ thanh toán"),
            Map.entry("12", "Thẻ/Tài khoản bị khóa"),
            Map.entry("13", "Nhập sai mật khẩu xác thực giao dịch (OTP)"),
            Map.entry("24", "Khách hàng hủy giao dịch"),
            Map.entry("51", "Tài khoản không đủ số dư"),
            Map.entry("65", "Tài khoản đã vượt quá hạn mức giao dịch trong ngày"),
            Map.entry("75", "Ngân hàng thanh toán đang bảo trì"),
            Map.entry("79", "Nhập sai mật khẩu thanh toán quá số lần quy định"));

    private final VnPayProperties props;
    private final PaymentRepository paymentRepository;
    private final PaymentResultService paymentResultService;
    private final VnPayClient vnPayClient;
    private final ObjectMapper objectMapper;

    public VnPayService(VnPayProperties props, PaymentRepository paymentRepository,
                        PaymentResultService paymentResultService, VnPayClient vnPayClient,
                        ObjectMapper objectMapper) {
        this.props = props;
        this.paymentRepository = paymentRepository;
        this.paymentResultService = paymentResultService;
        this.vnPayClient = vnPayClient;
        this.objectMapper = objectMapper;
    }

    // ----- 1. Tao URL thanh toan -----

    /**
     * Goi ben trong transaction dat hang cua OrderService: loi o day (vd chua cau hinh VNPay)
     * thi ca don cung rollback, khong tao don "nua voi".
     *
     * @param txnRef ma tham chieu gui VNPay = ma don (da duy nhat); moi don chi thanh toan 1 lan
     */
    @Transactional
    public String createPayment(long orderId, String txnRef, BigDecimal amount, String clientIp) {
        if (!props.isConfigured()) {
            throw new ConflictException("Hệ thống chưa cấu hình VNPay, vui lòng chọn thanh toán khi nhận hàng");
        }
        LocalDateTime createDate = LocalDateTime.now(VIETNAM).withNano(0);
        paymentRepository.insertPending(orderId, METHOD, amount, txnRef, createDate);

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", VERSION);
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", props.tmnCode());
        params.put("vnp_Amount", amount.multiply(HUNDRED).toBigIntegerExact().toString()); // VNPay yeu cau x 100
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", "Thanh toan don hang " + txnRef); // khong dau, khong ky tu dac biet
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", props.returnUrl());
        params.put("vnp_IpAddr", clientIp);
        params.put("vnp_CreateDate", createDate.format(VNPAY_TIME));
        // Qua han nay ma chua tra thi tac vu dinh ky huy don (settleExpiredPayments, tuan 6)
        params.put("vnp_ExpireDate", createDate.plusMinutes(EXPIRE_MINUTES).format(VNPAY_TIME));
        String query = VnPaySigner.buildQuery(params);
        return props.payUrl() + "?" + query + "&vnp_SecureHash=" + VnPaySigner.hmacSha512(props.hashSecret(), query);
    }

    // ----- 2. IPN -----

    /**
     * Theo dung thu tu trong tai lieu: chu ky -> tim giao dich -> so tien -> da xu ly chua -> cap nhat.
     * Loi bat ngo thi PaymentController tra "99" (phan ghi DB da rollback, VNPay se goi lai).
     */
    public VnPayIpnResponse handleIpn(Map<String, String> params) {
        if (!VnPaySigner.isValid(params, props.hashSecret())) {
            return VnPayIpnResponse.INVALID_SIGNATURE;
        }
        Optional<PaymentRow> found = paymentRepository.findByTransactionRef(METHOD, params.get("vnp_TxnRef"));
        if (found.isEmpty()) {
            return VnPayIpnResponse.ORDER_NOT_FOUND;
        }
        PaymentRow payment = found.get();
        if (!sameAmount(payment, params.get("vnp_Amount"))) {
            return VnPayIpnResponse.INVALID_AMOUNT;
        }
        if (!"PENDING".equals(payment.status())) {
            return VnPayIpnResponse.ALREADY_CONFIRMED;
        }
        // Thanh cong khi CA HAI ma deu "00": ket qua xu ly (vnp_ResponseCode) va tinh trang giao dich
        // (vnp_TransactionStatus). Mau PHP cua VNPay dung "hoac"; nhom chon "va" cho chac: chi giao mon khi ca 2 dong y.
        boolean success = "00".equals(params.get("vnp_ResponseCode"))
                && "00".equals(params.get("vnp_TransactionStatus"));
        boolean updated = paymentResultService.apply(payment, success, params.get("vnp_ResponseCode"), toJson(params));
        return updated ? VnPayIpnResponse.CONFIRMED : VnPayIpnResponse.ALREADY_CONFIRMED;
    }

    // ----- 4. Don qua han thanh toan (tuan 6) -----

    /**
     * Xu ly giao dich VNPay da qua han thanh toan ma van PENDING (khach bo ngang trang VNPay, IPN khong toi).
     * VnPayCleanupScheduler goi ham nay moi phut. Moi giao dich hoi VNPay lan cuoi (querydr) roi moi quyet:
     *   - da tra (00 + 00, dung so tien): xac nhan don nhu IPN, khong huy nham don khach vua tra sat gio;
     *   - VNPay khong co giao dich (91), hoac co nhung chua thanh cong: huy don voi ma 11 ("Da het han cho thanh toan");
     *   - khong hoi duoc (loi mang, sai chu ky phan hoi...) hoac so tien lech: de nguyen, lan quet sau hoi lai.
     *
     * @return so giao dich da cap nhat
     */
    public int settleExpiredPayments() {
        if (!props.isConfigured()) {
            return 0; // chua co khoa VNPay thi khong hoi VNPay duoc (va cung khong tao duoc don VNPay moi)
        }
        // Qua han thanh toan (15 phut) them 1 phut cho chac. created_at luu gio Viet Nam nen so bang gio Viet Nam.
        LocalDateTime createdBefore = LocalDateTime.now(VIETNAM).minusMinutes(EXPIRE_MINUTES + 1L);
        int settled = 0;
        for (PaymentRow payment : paymentRepository.findPendingCreatedBefore(METHOD, createdBefore, CLEANUP_BATCH)) {
            try {
                if (settleExpired(payment)) {
                    settled++;
                }
            } catch (RuntimeException ex) {
                // 1 giao dich loi khong chan cac giao dich con lai; lan quet sau thu lai
                log.error("Khong xu ly duoc giao dich VNPay qua han {}", payment.transactionRef(), ex);
            }
        }
        return settled;
    }

    /** @return true neu da ghi ket qua (xac nhan hoac huy), false neu de nguyen cho lan sau */
    private boolean settleExpired(PaymentRow payment) {
        VnPayClient.QueryResult result = vnPayClient.queryTransaction(payment.transactionRef(), payment.createdAt());
        boolean vnPayHasIt = "00".equals(result.responseCode());
        if (vnPayHasIt && "00".equals(result.transactionStatus())) {
            if (!sameAmount(payment, result.amount())) {
                log.warn("querydr giao dich qua han {} bao da tra nhung so tien khong khop, chua cap nhat",
                        payment.transactionRef());
                return false;
            }
            return paymentResultService.apply(payment, true, "00", toJson(result.raw()));
        }
        if (vnPayHasIt || "91".equals(result.responseCode())) {
            return paymentResultService.apply(payment, false, EXPIRED_CODE, toJson(result.raw()));
        }
        return false;
    }

    // ----- 3. Trang quay ve -----

    /**
     * Trinh duyet cua khach quay ve tu VNPay. Tai lieu ghi: o day chi kiem tra chu ky va hien ket qua,
     * KHONG cap nhat DB theo tham so tren URL. Chay o localhost thi VNPay khong goi IPN duoc, nen neu giao dich
     * con cho thi server tu hoi VNPay (querydr) va cap nhat theo cau tra loi cua VNPay (cach 1 trong ke hoach).
     */
    public VnPayReturnResponse handleReturn(Map<String, String> params) {
        if (!VnPaySigner.isValid(params, props.hashSecret())) {
            throw new BadRequestException("Sai chữ ký VNPay, không xác nhận được kết quả thanh toán");
        }
        PaymentRow payment = paymentRepository.findByTransactionRef(METHOD, params.get("vnp_TxnRef"))
                .orElseThrow(() -> new NotFoundException("Không tìm thấy giao dịch thanh toán"));
        String returnCode = params.get("vnp_ResponseCode");
        if ("PENDING".equals(payment.status())) {
            confirmWithVnPay(payment, returnCode);
            payment = paymentRepository.findById(payment.id()).orElseThrow();
        }
        return toReturnResponse(payment, returnCode);
    }

    /** Hoi VNPay (querydr) roi cap nhat giong IPN. VNPay chua tra loi ro thi giu nguyen, cho IPN hoac lan sau. */
    private void confirmWithVnPay(PaymentRow payment, String returnCode) {
        VnPayClient.QueryResult result = vnPayClient.queryTransaction(payment.transactionRef(), payment.createdAt());
        if ("00".equals(result.responseCode())) {
            String status = result.transactionStatus();
            if ("00".equals(status)) {
                if (sameAmount(payment, result.amount())) {
                    paymentResultService.apply(payment, true, "00", toJson(result.raw()));
                } else {
                    log.warn("querydr giao dich {} bao da tra nhung so tien khong khop, chua cap nhat",
                            payment.transactionRef());
                }
            } else if (!"01".equals(status)) {
                // "01" = giao dich chua xong: cho tiep. Ma khac (02 loi, 04 dao, 07 nghi ngo...) = khong thanh cong
                paymentResultService.apply(payment, false, returnCode, toJson(result.raw()));
            }
        } else if ("91".equals(result.responseCode()) && !"00".equals(returnCode)) {
            // VNPay KHONG co giao dich nao voi ma nay va khach quay ve voi ket qua that bai (vd 24 = bam huy):
            // chac chan chua ai bi tru tien, nen huy don duoc
            paymentResultService.apply(payment, false, returnCode, toJson(result.raw()));
        }
        // Con lai (loi mang, phan hoi sai chu ky...): khong doi gi
    }

    private static VnPayReturnResponse toReturnResponse(PaymentRow payment, String returnCode) {
        String message = switch (payment.status()) {
            case "SUCCESS" -> "Thanh toán thành công";
            case "FAILED" -> "Thanh toán không thành công: " + reason(returnCode);
            default -> "00".equals(returnCode)
                    ? "Đang chờ VNPay xác nhận thanh toán"
                    : "Thanh toán chưa hoàn tất: " + reason(returnCode);
        };
        return new VnPayReturnResponse(payment.orderId(), payment.transactionRef(), payment.status(),
                "SUCCESS".equals(payment.status()), message);
    }

    /** Ly do theo bang ma loi cua VNPay; ma khong co trong bang thi bao chung. */
    private static String reason(String responseCode) {
        return RESPONSE_REASONS.getOrDefault(responseCode, "Lỗi khác (mã " + responseCode + ")");
    }

    /** VNPay gui so tien x 100 (vd 60.000d -> "6000000"). */
    private static boolean sameAmount(PaymentRow payment, String vnpAmount) {
        if (vnpAmount == null || !vnpAmount.matches("\\d+")) {
            return false;
        }
        return new BigDecimal(vnpAmount).compareTo(payment.amount().multiply(HUNDRED)) == 0;
    }

    /** Du lieu VNPay gui (bo chu ky) doi thanh JSON de luu vao payments.gateway_payload. */
    private String toJson(Map<String, ?> data) {
        Map<String, Object> copy = new TreeMap<>(data);
        copy.remove("vnp_SecureHash");
        try {
            return objectMapper.writeValueAsString(copy);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Khong doi duoc du lieu VNPay sang JSON", ex);
        }
    }
}
