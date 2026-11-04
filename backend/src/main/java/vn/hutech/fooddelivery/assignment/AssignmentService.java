package vn.hutech.fooddelivery.assignment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static vn.hutech.fooddelivery.assignment.AssignmentOfferRepository.InvitationHistory.NEVER_INVITED;

/** Dieu phoi noi bo. Chi goi sau khi transaction tao Order da commit. */
@Service
public class AssignmentService {

    private final AssignmentRepository candidates;
    private final AssignmentOfferRepository offers;
    private final OfferCreationService creation;
    private final Clock clock;
    private final NearestDriverSelector selector = new NearestDriverSelector();

    @Autowired
    public AssignmentService(AssignmentRepository candidates, AssignmentOfferRepository offers,
                             OfferCreationService creation) {
        this(candidates, offers, creation, Clock.systemDefaultZone());
    }

    AssignmentService(AssignmentRepository candidates, AssignmentOfferRepository offers,
                      OfferCreationService creation, Clock clock) {
        this.candidates = candidates;
        this.offers = offers;
        this.creation = creation;
        this.clock = clock;
    }

    /**
     * Khong dung snapshot cua transaction ben goi; moi lan thu ghi co transaction rieng.
     * Optional rong chi bieu thi khong con ung vien hop le; loi JDBC/lock van truyen ra ngoai.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<AssignmentOffer> createOffer(long orderId) {
        if (orderId <= 0) {
            throw new IllegalArgumentException("orderId phai duong");
        }
        var order = offers.findOrder(orderId);
        if (order.isEmpty() || !order.get().canAssign() || offers.hasOfferedForOrder(orderId)) {
            return Optional.empty();
        }
        var restaurant = offers.findRestaurant(order.get().restaurantId()).orElseThrow(
                () -> new IllegalStateException("Khong tim thay nha hang cua don hang"));
        LocalDateTime now = LocalDateTime.now(clock);
        var history = offers.findInvitationHistory(orderId);
        var eligible = candidates.findEligibleCandidates(now).stream()
                .filter(driver -> history.getOrDefault(driver.driverId(), NEVER_INVITED).canInvite(now)).toList();
        Map<Long, DriverCandidate> byId = eligible.stream()
                .collect(Collectors.toMap(DriverCandidate::driverId, Function.identity()));
        var ranked = new ArrayList<>(selector.rank(restaurant.lat().doubleValue(), restaurant.lng().doubleValue(),
                eligible, now));
        // Sort on dinh: trong moi nhom van giu khoang cach/driverId cua selector NV2.1.
        ranked.sort(Comparator.comparing(driver -> history
                .getOrDefault(driver.driverId(), NEVER_INVITED).previouslyInvited()));
        for (RankedDriver driver : ranked) {
            var result = creation.tryCreateOffer(orderId, byId.get(driver.driverId()), restaurant);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }
}
