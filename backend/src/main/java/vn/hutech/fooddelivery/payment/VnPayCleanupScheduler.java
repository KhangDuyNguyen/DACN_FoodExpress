package vn.hutech.fooddelivery.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Tac vu dinh ky cua VNPay, moi phut:
 *   - xu ly cac don VNPay qua han thanh toan (khach bo ngang trang VNPay): VnPayService.settleExpiredPayments;
 *   - gui cac yeu cau hoan tien dang cho sang VNPay (tuan 7): RefundService.sendPendingRefunds.
 * Bat mac dinh; tat bang app.vnpay.cleanup.enabled=false (bien moi truong VNPAY_CLEANUP_ENABLED=false).
 * Logic nam trong cac service de test goi thang duoc, lop nay chi lo hen gio.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "app.vnpay.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VnPayCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(VnPayCleanupScheduler.class);

    private final VnPayService vnPayService;
    private final RefundService refundService;

    public VnPayCleanupScheduler(VnPayService vnPayService, RefundService refundService) {
        this.vnPayService = vnPayService;
        this.refundService = refundService;
    }

    /** fixedDelay: chay xong lan truoc 60 giay moi chay lan sau (khong chong cheo 2 lan quet). */
    @Scheduled(fixedDelayString = "${app.vnpay.cleanup.delay-ms:60000}",
            initialDelayString = "${app.vnpay.cleanup.initial-delay-ms:60000}")
    public void settleExpiredPayments() {
        try {
            int settled = vnPayService.settleExpiredPayments();
            if (settled > 0) {
                log.info("Da xu ly {} giao dich VNPay qua han thanh toan", settled);
            }
        } catch (RuntimeException ex) {
            // Loi 1 lan quet khong duoc lam chet lich: phut sau quet lai
            log.error("Loi khi xu ly giao dich VNPay qua han", ex);
        }
    }

    /** Gui yeu cau hoan tien dang cho; loi mang thi giu PENDING, phut sau gui lai. */
    @Scheduled(fixedDelayString = "${app.vnpay.cleanup.delay-ms:60000}",
            initialDelayString = "${app.vnpay.cleanup.initial-delay-ms:60000}")
    public void sendPendingRefunds() {
        try {
            int finished = refundService.sendPendingRefunds();
            if (finished > 0) {
                log.info("Da co ket qua {} yeu cau hoan tien VNPay", finished);
            }
        } catch (RuntimeException ex) {
            log.error("Loi khi gui yeu cau hoan tien VNPay", ex);
        }
    }
}
