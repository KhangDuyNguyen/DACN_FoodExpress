package vn.hutech.fooddelivery.assignment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.common.NotFoundException;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class DriverOfferService {

    private final DriverOfferRepository offers;
    private final DriverOfferDecisionService decisions;
    private final AssignmentService assignments;
    private final Clock clock;

    @Autowired
    public DriverOfferService(DriverOfferRepository offers, DriverOfferDecisionService decisions,
                              AssignmentService assignments) {
        this(offers, decisions, assignments, Clock.systemDefaultZone());
    }

    DriverOfferService(DriverOfferRepository offers, DriverOfferDecisionService decisions,
                       AssignmentService assignments, Clock clock) {
        this.offers = offers;
        this.decisions = decisions;
        this.assignments = assignments;
        this.clock = clock;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public DriverOffersResponse getOffers(long userId) {
        long driverId = offers.findDriverId(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ tài xế"));
        LocalDateTime now = LocalDateTime.now(clock);
        return new DriverOffersResponse(offers.findActiveOffers(driverId, now), now);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DriverOfferActionResponse accept(long userId, long offerId) {
        return decisions.accept(userId, offerId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DriverOfferActionResponse reject(long userId, long offerId) {
        var result = decisions.reject(userId, offerId); // Bean REQUIRES_NEW da COMMIT khi tra ve.
        try {
            // Lap lai cung thu lai: phuc hoi neu lan truoc commit REJECTED nhung retry bi loi.
            assignments.createOffer(result.orderId());
        } catch (RuntimeException failure) {
            throw new ReassignmentFailedException(failure);
        }
        return result;
    }

    static class ReassignmentFailedException extends RuntimeException {
        ReassignmentFailedException(RuntimeException cause) {
            super("Đã từ chối lời mời, nhưng chưa thể tìm tài xế kế tiếp. Có thể gọi lại yêu cầu từ chối để thử lại", cause);
        }
    }
}
