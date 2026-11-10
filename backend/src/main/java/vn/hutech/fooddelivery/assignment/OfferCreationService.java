package vn.hutech.fooddelivery.assignment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static vn.hutech.fooddelivery.assignment.AssignmentOfferRepository.InvitationHistory.NEVER_INVITED;

/** Bean rieng de loi goi tu AssignmentService di qua Spring transaction proxy. */
@Service
public class OfferCreationService {

    private final AssignmentOfferRepository offers;
    private final Clock clock;
    private final NearestDriverSelector selector = new NearestDriverSelector();

    @Autowired
    public OfferCreationService(AssignmentOfferRepository offers) {
        this(offers, Clock.systemDefaultZone());
    }

    OfferCreationService(AssignmentOfferRepository offers, Clock clock) {
        this.offers = offers;
        this.clock = clock;
    }

    /**
     * REQUIRES_NEW bao dam READ_COMMITTED ngay ca khi ben goi dang o REPEATABLE_READ.
     * Khoa Order -> Driver duoc giu den commit. Cac buoc ghi offer/accept/expire sau nay
     * cung phai tuan thu giao thuc nay. Khong nuot deadlock/lock timeout/loi insert.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Optional<AssignmentOffer> tryCreateOffer(long orderId, DriverCandidate expectedDriver,
                                                    AssignmentOfferRepository.RestaurantLocation expectedRestaurant) {
        var order = offers.lockOrder(orderId);
        if (order.isEmpty() || !order.get().canAssign()
                || order.get().restaurantId() != expectedRestaurant.id()) {
            return Optional.empty();
        }
        var driver = offers.lockDriver(expectedDriver.driverId());
        // Khong ghi du lieu dua tren snapshot toa do/status/GPS da thay doi khi cho khoa.
        if (driver.isEmpty() || !driver.get().equals(expectedDriver)) {
            return Optional.empty();
        }
        if (offers.hasOfferedForOrder(orderId) || offers.hasActiveOrder(expectedDriver.driverId())
                || offers.hasOfferedForDriver(expectedDriver.driverId())) {
            return Optional.empty();
        }
        // Giu toa do nha hang on dinh tu lan kiem tra cuoi den commit; nhieu don van co the doc chung.
        var restaurant = offers.lockRestaurantLocation(order.get().restaurantId());
        if (restaurant.isEmpty() || !restaurant.get().equals(expectedRestaurant)) {
            return Optional.empty();
        }
        var history = offers.findInvitationHistory(orderId).getOrDefault(expectedDriver.driverId(), NEVER_INVITED);
        // Lay lai gio SAU khi co khoa va doc dieu kien; GPS co the da cu trong luc cho.
        LocalDateTime now = LocalDateTime.now(clock);
        if (!history.canInvite(now)) {
            return Optional.empty();
        }
        var ranked = selector.selectNearest(restaurant.get().lat().doubleValue(), restaurant.get().lng().doubleValue(),
                List.of(driver.get()), now);
        if (ranked.isEmpty()) {
            return Optional.empty();
        }
        var offeredAt = now.truncatedTo(ChronoUnit.SECONDS);
        return Optional.of(offers.insertOffer(orderId, expectedDriver.driverId(), ranked.get().distanceKm(), offeredAt));
    }
}
