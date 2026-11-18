package vn.hutech.fooddelivery.assignment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/** Moi offer la mot transaction rieng; chi retry AssignmentService SAU khi proxy nay tra ve/commit. */
@Service
public class AssignmentOfferLifecycleService {
    private final AssignmentOfferRepository parents;
    private final DriverOfferRepository offers;
    private final AssignmentRecoveryRepository recovery;
    private final Clock clock;

    @Autowired
    public AssignmentOfferLifecycleService(AssignmentOfferRepository parents, DriverOfferRepository offers,
                                           AssignmentRecoveryRepository recovery) {
        this(parents, offers, recovery, Clock.systemDefaultZone());
    }

    AssignmentOfferLifecycleService(AssignmentOfferRepository parents, DriverOfferRepository offers,
                                    AssignmentRecoveryRepository recovery, Clock clock) {
        this.parents = parents;
        this.offers = offers;
        this.recovery = recovery;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Optional<CompletedOffer> reconcileOffer(long offerId) {
        var metadata = offers.findMetadata(offerId);
        if (metadata.isEmpty()) return Optional.empty();
        var order = parents.lockOrder(metadata.get().orderId());
        if (order.isEmpty()) return Optional.empty();
        var driver = parents.lockDriver(metadata.get().driverId());
        if (driver.isEmpty()) return Optional.empty();
        var offer = offers.lockOffer(offerId);
        if (offer.isEmpty() || offer.get().orderId() != order.get().id()
                || offer.get().driverId() != driver.get().driverId() || !"OFFERED".equals(offer.get().status())) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now(clock); // Lay lai gio sau khi cho khoa.
        String status;
        if (!order.get().canAssign()) {
            status = "CANCELLED"; // Chi don PLACED/chua gan driver con nhan duoc offer.
        } else if (offer.get().expiresAt() == null || !offer.get().expiresAt().isAfter(now)) {
            status = "EXPIRED";
        } else {
            return Optional.empty();
        }
        // expires_at NULL duoc gan now khi EXPIRED de cooldown 60s khong tro thanh vo han.
        recovery.completeOffer(offer.get(), status, now);
        return Optional.of(new CompletedOffer(offerId, order.get().id(), status));
    }

    public record CompletedOffer(long offerId, long orderId, String status) {
    }
}
