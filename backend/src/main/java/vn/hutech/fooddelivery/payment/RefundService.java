package vn.hutech.fooddelivery.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Hoan tien VNPay (tuan 7) theo tai lieu refund cua VNPay, 2 buoc:
 *   1. requestRefundIfPaid: don bi huy ma da tra tien VNPay -> ghi 1 yeu cau hoan tien PENDING
 *      (trong cung transaction huy don);
 *   2. sendPendingRefunds: tac vu dinh ky (moi phut) gui cac yeu cau PENDING sang VNPay va ghi ket qua.
 * Tach 2 buoc de huy don khong phai cho goi mang, va loi mang thi phut sau tu gui lai.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);
    /** Moi lan quet gui toi da 50 yeu cau, con lai de lan sau. */
    private static final int BATCH = 50;
    /** Bang ma refund: 00 = VNPay nhan yeu cau; 94 = yeu cau gui truoc do VNPay dang xu ly (lan truoc da gui duoc). */
    private static final Set<String> ACCEPTED = Set.of("00", "94");
    /** VNPay tu choi han: 91 = khong thay giao dich; 95 = giao dich khong thanh cong ben VNPay. */
    private static final Set<String> REJECTED = Set.of("91", "95");

    private final VnPayProperties props;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final VnPayClient vnPayClient;
    private final PaymentResultService paymentResultService;

    public RefundService(VnPayProperties props, PaymentRepository paymentRepository,
                              RefundRepository refundRepository, VnPayClient vnPayClient,
                              PaymentResultService paymentResultService) {
        this.props = props;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.vnPayClient = vnPayClient;
        this.paymentResultService = paymentResultService;
    }

    /**
     * Don bi huy: neu khach da tra tien online thi ghi 1 yeu cau hoan TOAN BO so tien da tra.
     * Goi ben trong transaction huy don (OrderManagementService): huy loi thi yeu cau nay cung rollback.
     * COD hoac chua tra tien thi khong co gi de hoan.
     */
    @Transactional
    public void requestRefundIfPaid(long orderId, String reason) {
        paymentRepository.findSuccessByOrderId(orderId).ifPresent(payment -> {
            if (!refundRepository.existsForPayment(payment.id())) {
                refundRepository.insertPending(payment.id(), payment.amount(), reason);
            }
        });
    }

    /**
     * Gui sang VNPay cac yeu cau hoan tien dang cho. VnPayCleanupScheduler goi moi phut.
     *
     * @return so yeu cau da co ket qua (VNPay nhan hoac tu choi)
     */
    public int sendPendingRefunds() {
        if (!props.isConfigured()) {
            return 0; // chua co khoa VNPay: de nguyen PENDING, co khoa roi gui sau
        }
        int finished = 0;
        for (RefundRow refund : refundRepository.findPendingVnPay(BATCH)) {
            try {
                if (send(refund)) {
                    finished++;
                }
            } catch (RuntimeException ex) {
                // 1 yeu cau loi khong chan cac yeu cau con lai; lan quet sau gui lai
                log.error("Khong xu ly duoc yeu cau hoan tien #{}", refund.id(), ex);
            }
        }
        return finished;
    }

    /** @return true neu da ghi ket qua, false neu de nguyen PENDING cho lan sau */
    private boolean send(RefundRow refund) {
        VnPayClient.RefundResult result = vnPayClient.refund(refund);
        if (ACCEPTED.contains(result.responseCode())) {
            paymentResultService.finishRefund(refund, true);
            return true;
        }
        if (REJECTED.contains(result.responseCode())) {
            log.warn("VNPay tu choi hoan tien #{} cho giao dich {} (ma {}), can admin xu ly", refund.id(),
                    refund.transactionRef(), result.responseCode());
            paymentResultService.finishRefund(refund, false);
            return true;
        }
        // Loi mang, phan hoi sai chu ky, ma khac (vd 99): chua ro VNPay da nhan chua -> gui lai lan sau.
        // Neu thuc ra VNPay da nhan, lan sau VNPay tra 94 va minh ghi hoan tat.
        return false;
    }
}
