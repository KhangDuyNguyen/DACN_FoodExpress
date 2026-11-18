package vn.hutech.fooddelivery.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Moi phut xu ly cac don VNPay qua han thanh toan (khach bo ngang trang VNPay): xem VnPayService.settleExpiredPayments.
 * Bat mac dinh; tat bang app.vnpay.cleanup.enabled=false (bien moi truong VNPAY_CLEANUP_ENABLED=false).
 * Logic nam trong VnPayService de test goi thang duoc, lop nay chi lo hen gio.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "app.vnpay.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VnPayCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(VnPayCleanupScheduler.class);

    private final VnPayService vnPayService;

    public VnPayCleanupScheduler(VnPayService vnPayService) {
        this.vnPayService = vnPayService;
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
}
