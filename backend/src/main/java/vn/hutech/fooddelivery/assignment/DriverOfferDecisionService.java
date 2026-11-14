package vn.hutech.fooddelivery.assignment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.order.OrderRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static vn.hutech.fooddelivery.assignment.AssignmentOfferRepository.InvitationHistory.NEVER_INVITED;

/** Bean transaction rieng: Order -> Driver -> Offer, khong goi lai AssignmentService trong transaction nay. */
@Service
public class DriverOfferDecisionService {

    private final DriverOfferRepository offers;
    private final AssignmentOfferRepository assignments;
    private final OrderRepository orders;
    private final Clock clock;

    @Autowired
    public DriverOfferDecisionService(DriverOfferRepository offers, AssignmentOfferRepository assignments,
                                      OrderRepository orders) {
        this(offers, assignments, orders, Clock.systemDefaultZone());
    }

    DriverOfferDecisionService(DriverOfferRepository offers, AssignmentOfferRepository assignments,
                               OrderRepository orders, Clock clock) {
        this.offers = offers;
        this.assignments = assignments;
        this.orders = orders;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public DriverOfferActionResponse accept(long userId, long offerId) {
        var locked = lockOwnedOffer(userId, offerId);
        var order = locked.order();
        var offer = locked.offer();
        // Replay chi xac nhan ket qua da commit; khong bat GPS/Online hien tai thay doi ket qua cu.
        if ("ACCEPTED".equals(offer.status())) {
            if (!order.acceptsDriver() || order.driverId() == null
                    || order.driverId().longValue() != offer.driverId()) {
                throw new ConflictException("Đơn hàng không còn phù hợp với lời mời đã nhận");
            }
            return offer.response();
        }
        if (!order.canAssign() || !"OFFERED".equals(offer.status())) {
            throw new ConflictException("Đơn hàng hoặc lời mời không còn nhận được");
        }
        if (assignments.hasActiveOrder(offer.driverId()) || offers.hasOtherOffered(offer.driverId(), offer.id())) {
            throw new ConflictException("Tài xế đang có đơn hoặc lời mời khác chờ xử lý");
        }
        var history = assignments.findInvitationHistory(order.id()).getOrDefault(offer.driverId(), NEVER_INVITED);
        LocalDateTime now = LocalDateTime.now(clock); // Lay gio sau khi cho du Order/Driver/Offer lock.
        if (!offer.isEffective(now) || !eligibleDriver(locked.driver(), now) || !history.canInvite(now)) {
            throw new ConflictException("Lời mời đã hết hiệu lực hoặc tài xế không còn đủ điều kiện");
        }
        LocalDateTime respondedAt = now.truncatedTo(ChronoUnit.SECONDS);
        offers.respond(offer, "ACCEPTED", respondedAt);
        offers.assignOrder(order.id(), offer.driverId());
        // Chi Accept tu PLACED moi bat dau chuan bi; Order lock ngan Merchant/Accept chuyen lui.
        // Dung chung CAS/history cua Order trong cung transaction voi assignment va driver_id.
        if ("PLACED".equals(order.status())) {
            if (orders.updateStatus(order.id(), "PLACED", "PREPARING", null) != 1) {
                throw new ConflictException("Đơn vừa được cập nhật, vui lòng tải lại");
            }
            orders.insertStatusHistory(order.id(), "PREPARING", userId, null);
        }
        return new DriverOfferActionResponse(offer.id(), order.id(), "ACCEPTED", respondedAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public DriverOfferActionResponse reject(long userId, long offerId) {
        var locked = lockOwnedOffer(userId, offerId);
        var offer = locked.offer();
        if ("REJECTED".equals(offer.status())) return offer.response();
        LocalDateTime now = LocalDateTime.now(clock);
        if (!locked.order().canAssign() || !offer.isEffective(now)) {
            throw new ConflictException("Đơn hàng hoặc lời mời không còn từ chối được");
        }
        LocalDateTime respondedAt = now.truncatedTo(ChronoUnit.SECONDS);
        // Tu choi khong doi Online/GPS va khong gan hay huy Order.
        offers.respond(offer, "REJECTED", respondedAt);
        return new DriverOfferActionResponse(offer.id(), offer.orderId(), "REJECTED", respondedAt);
    }

    private LockedOffer lockOwnedOffer(long userId, long offerId) {
        if (offerId <= 0) throw new IllegalArgumentException("offerId phải là số dương");
        long driverId = offers.findDriverId(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ tài xế"));
        var metadata = offers.findMetadata(offerId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lời mời"));
        if (metadata.driverId() != driverId) throw new ForbiddenException("Lời mời không thuộc tài xế hiện tại");
        var order = assignments.lockOrder(metadata.orderId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng"));
        var driver = offers.lockDriverForUser(driverId, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ tài xế"));
        var offer = offers.lockOffer(offerId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lời mời"));
        if (offer.orderId() != order.id() || offer.driverId() != driver.driverId()) {
            throw new ConflictException("Thông tin lời mời đã thay đổi. Vui lòng tải lại");
        }
        return new LockedOffer(order, driver, offer);
    }

    private static boolean eligibleDriver(DriverCandidate driver, LocalDateTime now) {
        return "APPROVED".equals(driver.status()) && driver.isOnline()
                && validCoordinate(driver.lat(), 90) && validCoordinate(driver.lng(), 180)
                && driver.lastLocationAt() != null && !driver.lastLocationAt().isAfter(now)
                && Duration.between(driver.lastLocationAt(), now).compareTo(Duration.ofSeconds(60)) <= 0;
    }

    private static boolean validCoordinate(Double value, int limit) {
        return value != null && Double.isFinite(value) && value >= -limit && value <= limit;
    }

    private record LockedOffer(AssignmentOfferRepository.OrderSnapshot order, DriverCandidate driver,
                               DriverOfferRepository.OfferSnapshot offer) {
    }
}
