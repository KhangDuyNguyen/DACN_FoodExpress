package vn.hutech.fooddelivery.tracking;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static vn.hutech.fooddelivery.tracking.TrackingRouteException.Reason.*;

@Service
public class OrderTrackingRouteService {
    private static final Duration MAX_GPS_AGE = Duration.ofSeconds(60);

    private final OrderTrackingRepository repository;
    private final VietmapRoutingClient client;
    private final TrackingRouteQuota quota;
    private final Clock clock;

    @Autowired
    public OrderTrackingRouteService(OrderTrackingRepository repository, VietmapRoutingClient client,
                                     TrackingRouteQuota quota) {
        // The Driver GPS writer stores LocalDateTime.now() in DATETIME.
        this(repository, client, quota, Clock.systemDefaultZone());
    }

    OrderTrackingRouteService(OrderTrackingRepository repository, VietmapRoutingClient client,
                              TrackingRouteQuota quota, Clock clock) {
        this.repository = repository;
        this.client = client;
        this.quota = quota;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OrderTrackingRouteResponse getRoute(long customerId, long orderId) {
        var snapshot = ownedSnapshot(customerId, orderId, true);
        var scope = eligibleScope(orderId, snapshot);
        Instant now = clock.instant();
        var origin = freshOrigin(snapshot, now);
        if (!client.isAvailable()) {
            throw new TrackingRouteException(ROUTE_UNAVAILABLE);
        }

        var route = quota.execute(scope, now, () -> client.getRoute(scope.profile(),
                new RoutePoint(origin.lat(), origin.lng()), scope.destination(), origin));

        var current = ownedSnapshot(customerId, orderId, false);
        if (!"DELIVERING".equals(current.status()) || !Long.valueOf(scope.driverId()).equals(current.driverId())
                || !snapshot.vehicleType().equals(current.vehicleType())
                || !scope.destination().equals(current.destination())) {
            throw new TrackingRouteException(ROUTE_CONTEXT_CHANGED);
        }

        return new OrderTrackingRouteResponse(orderId, scope.profile(), route.geometry(), route.distanceMeters(),
                route.durationMs(), route.origin(), route.generatedAt());
    }

    private OrderTrackingRepository.RouteSnapshot ownedSnapshot(long customerId, long orderId, boolean withLocation) {
        var snapshot = repository.findRouteSnapshot(orderId, customerId, withLocation)
                .orElseThrow(() -> new TrackingRouteException(ROUTE_ORDER_NOT_FOUND));
        if (!snapshot.allowed()) {
            throw new TrackingRouteException(ROUTE_FORBIDDEN);
        }
        return snapshot;
    }

    private TrackingRouteQuota.Scope eligibleScope(long orderId, OrderTrackingRepository.RouteSnapshot snapshot) {
        if (!"DELIVERING".equals(snapshot.status()) || snapshot.driverId() == null) {
            throw new TrackingRouteException(ROUTE_ORDER_NOT_READY);
        }
        String profile = switch (snapshot.vehicleType()) {
            case "MOTORBIKE" -> "motorcycle";
            case "CAR" -> "car";
            case null, default -> throw new TrackingRouteException(ROUTE_PROFILE_UNSUPPORTED);
        };
        if (snapshot.destination() == null || !snapshot.destination().isValid()) {
            throw new TrackingRouteException(ROUTE_DESTINATION_INVALID);
        }
        return new TrackingRouteQuota.Scope(orderId, snapshot.customerId(), snapshot.driverId(),
                profile, snapshot.destination());
    }

    private OrderTrackingRouteResponse.Origin freshOrigin(OrderTrackingRepository.RouteSnapshot snapshot, Instant now) {
        var fix = snapshot.fix();
        if (fix == null || fix.point() == null || !fix.point().isValid() || fix.recordedAt() == null) {
            throw new TrackingRouteException(ROUTE_GPS_UNAVAILABLE);
        }
        var offsets = clock.getZone().getRules().getValidOffsets(fix.recordedAt());
        if (offsets.size() != 1) {
            throw new TrackingRouteException(ROUTE_GPS_UNAVAILABLE);
        }
        var recordedAt = fix.recordedAt().toInstant(offsets.getFirst());
        if (recordedAt.isAfter(now) || recordedAt.isBefore(now.minus(MAX_GPS_AGE))) {
            throw new TrackingRouteException(ROUTE_GPS_UNAVAILABLE);
        }
        return new OrderTrackingRouteResponse.Origin(fix.id(), fix.point().lat(), fix.point().lng(), recordedAt);
    }
}
