package vn.hutech.fooddelivery.assignment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.function.Supplier;

/** Chi duoc dang ky boi AssignmentSchedulingConfiguration khi bat cong tac scheduling. */
public class AssignmentScheduler {
    private static final Logger log = LoggerFactory.getLogger(AssignmentScheduler.class);
    private final AssignmentRecoveryService recovery;

    public AssignmentScheduler(AssignmentRecoveryService recovery) { this.recovery = recovery; }

    @Scheduled(fixedDelayString = "${app.assignment.scheduling.expire-delay-ms:5000}",
            initialDelayString = "${app.assignment.scheduling.expire-initial-delay-ms:5000}")
    public void expireOffers() { run("expire", recovery::expireAndReassign); }

    @Scheduled(fixedDelayString = "${app.assignment.scheduling.recovery-delay-ms:30000}",
            initialDelayString = "${app.assignment.scheduling.recovery-initial-delay-ms:30000}")
    public void recoverOrders() { run("recovery", recovery::recoverWaitingOrders); }

    private void run(String phase, Supplier<AssignmentRecoveryService.Report> task) {
        try {
            var report = task.get();
            if (report.hasWork()) {
                log.info("Assignment {} scanned={} expired={} cancelled={} attempted={} created={} noCandidates={} skipped={} errors={}",
                        phase, report.scanned(), report.expired(), report.cancelled(), report.attempted(),
                        report.created(), report.noCandidates(), report.skipped(), report.errors());
            }
        } catch (RuntimeException failure) {
            // Loi quet ID khong duoc bao thanh cong; chu ky tiep theo van co the phuc hoi.
            log.error("Assignment {} scan failed", phase, failure);
        }
    }
}
