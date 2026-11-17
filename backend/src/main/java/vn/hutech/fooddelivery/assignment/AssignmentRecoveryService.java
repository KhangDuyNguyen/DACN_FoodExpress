package vn.hutech.fooddelivery.assignment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Khong giu transaction cho batch. Cursor theo ID co vong quay, khong bo doi cac Order phia sau. */
@Service
public class AssignmentRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(AssignmentRecoveryService.class);
    private final AssignmentRecoveryRepository repository;
    private final AssignmentOfferLifecycleService lifecycle;
    private final AssignmentService assignments;
    private final Clock clock;
    private final int batchSize;
    private long offerCursor;
    private long orderCursor;

    @Autowired
    public AssignmentRecoveryService(AssignmentRecoveryRepository repository, AssignmentOfferLifecycleService lifecycle,
                                      AssignmentService assignments,
                                      @Value("${app.assignment.scheduling.batch-size:50}") int batchSize) {
        this(repository, lifecycle, assignments, Clock.systemDefaultZone(), batchSize);
    }

    AssignmentRecoveryService(AssignmentRecoveryRepository repository, AssignmentOfferLifecycleService lifecycle,
                               AssignmentService assignments, Clock clock, int batchSize) {
        if (batchSize < 1 || batchSize > 50) throw new IllegalArgumentException("Batch phai tu 1 den 50");
        this.repository = repository;
        this.lifecycle = lifecycle;
        this.assignments = assignments;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public synchronized Report expireAndReassign() {
        LocalDateTime now = LocalDateTime.now(clock);
        var ids = repository.findRecoverableOfferIds(offerCursor, batchSize, now);
        if (ids.isEmpty() && offerCursor != 0) ids = repository.findRecoverableOfferIds(0, batchSize, now);
        offerCursor = ids.isEmpty() ? 0 : ids.getLast();
        var stats = new Statistics(ids.size());
        for (long offerId : ids) {
            try {
                var completed = reconcile(offerId, stats);
                if (completed != null && "EXPIRED".equals(completed.status())) reassign(completed.orderId(), stats);
            } catch (RuntimeException failure) {
                stats.errors++;
                log.error("Assignment expiry failed offerId={}", offerId, failure);
            }
        }
        return stats.report();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public synchronized Report recoverWaitingOrders() {
        LocalDateTime now = LocalDateTime.now(clock);
        var ids = repository.findWaitingOrderIds(orderCursor, batchSize, now);
        if (ids.isEmpty() && orderCursor != 0) ids = repository.findWaitingOrderIds(0, batchSize, now);
        orderCursor = ids.isEmpty() ? 0 : ids.getLast();
        var stats = new Statistics(ids.size());
        for (long orderId : ids) {
            try {
                // Recovery van chay doc lap sau restart, ke ca truoc chu ky expire dau tien.
                List<Long> offeredIds = repository.findOfferedIdsForOrder(orderId, batchSize);
                for (long offerId : offeredIds) reconcile(offerId, stats);
                reassign(orderId, stats);
            } catch (RuntimeException failure) {
                stats.errors++;
                log.error("Assignment recovery failed orderId={}", orderId, failure);
            }
        }
        return stats.report();
    }

    private AssignmentOfferLifecycleService.CompletedOffer reconcile(long offerId, Statistics stats) {
        // Loi goi bean rieng ket thuc transaction/commit truoc khi thu reassign.
        var result = lifecycle.reconcileOffer(offerId);
        if (result.isEmpty()) { stats.skipped++; return null; }
        if ("EXPIRED".equals(result.get().status())) stats.expired++;
        else stats.cancelled++;
        return result.get();
    }

    private void reassign(long orderId, Statistics stats) {
        if (!repository.isWaitingWithoutOffer(orderId)) { stats.skipped++; return; }
        stats.attempted++;
        if (assignments.createOffer(orderId).isPresent()) stats.created++;
        // Optional.empty co the do race. Chi dem noCandidates khi Order thuc su van con cho.
        else if (repository.isWaitingWithoutOffer(orderId)) stats.noCandidates++;
        else stats.skipped++;
    }

    public record Report(int scanned, int expired, int cancelled, int attempted, int created,
                         int noCandidates, int skipped, int errors) {
        boolean hasWork() { return expired + cancelled + attempted + errors > 0; }
    }

    private static class Statistics {
        final int scanned;
        int expired, cancelled, attempted, created, noCandidates, skipped, errors;
        Statistics(int scanned) { this.scanned = scanned; }
        Report report() { return new Report(scanned, expired, cancelled, attempted, created, noCandidates, skipped, errors); }
    }
}
