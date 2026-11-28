package vn.hutech.fooddelivery.tracking;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import static vn.hutech.fooddelivery.tracking.TrackingRouteException.Reason.ROUTE_RATE_LIMITED;
import static vn.hutech.fooddelivery.tracking.TrackingRouteException.Reason.ROUTE_UNAVAILABLE;

@Component
final class TrackingRouteQuota {
    static final int MAX_CONCURRENT = 4;
    private static final int MAX_WAITERS_PER_ORDER = 4;
    private static final int MAX_PER_DAY = 50;
    private static final int MAX_PER_ACCOUNT_DAY = 15;
    private static final int MAX_PER_ORDER = 5;
    private static final int MAX_TRACKED_ORDERS = 4096;
    private static final Duration COOLDOWN = Duration.ofSeconds(15);
    private static final ZoneId QUOTA_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final Map<Long, OrderUsage> orderUsage = new HashMap<>();
    private final Map<Long, Integer> accountUsage = new HashMap<>();
    private final Map<Long, InFlight> activeOrders = new HashMap<>();
    private LocalDate budgetDay;
    private int dailyUsed;

    record Scope(long orderId, long customerId, long driverId, String profile, RoutePoint destination) {
    }

    VietmapRoutingClient.RouteData execute(Scope scope, Instant now,
                                          Supplier<VietmapRoutingClient.RouteData> call) {
        Admission admission = admit(scope, now);
        InFlight flight = admission.flight();
        if (!admission.leader()) {
            try {
                return flight.result.join();
            } catch (CompletionException failure) {
                if (failure.getCause() instanceof TrackingRouteException routeFailure) {
                    throw routeFailure;
                }
                throw new TrackingRouteException(ROUTE_UNAVAILABLE);
            } finally {
                releaseWaiter(flight);
            }
        }

        try {
            var route = call.get();
            flight.result.complete(route);
            return route;
        } catch (RuntimeException | Error failure) {
            flight.result.completeExceptionally(failure);
            throw failure;
        } finally {
            release(scope);
        }
    }

    private synchronized Admission admit(Scope scope, Instant now) {
        var active = activeOrders.get(scope.orderId());
        if (active != null) {
            if (!active.scope.equals(scope) || active.waiters >= MAX_WAITERS_PER_ORDER) {
                throw new TrackingRouteException(ROUTE_RATE_LIMITED);
            }
            active.waiters++;
            return new Admission(active, false);
        }

        rotateDay(now);
        var usage = orderUsage.get(scope.orderId());
        int accountUsed = accountUsage.getOrDefault(scope.customerId(), 0);
        if (dailyUsed >= MAX_PER_DAY || accountUsed >= MAX_PER_ACCOUNT_DAY
                || activeOrders.size() >= MAX_CONCURRENT
                || activeOrders.values().stream().anyMatch(flight -> flight.scope.customerId() == scope.customerId())
                || usage != null && (usage.count == MAX_PER_ORDER || now.isBefore(usage.last.plus(COOLDOWN)))
                || usage == null && orderUsage.size() >= MAX_TRACKED_ORDERS) {
            throw new TrackingRouteException(ROUTE_RATE_LIMITED);
        }

        orderUsage.put(scope.orderId(), new OrderUsage(usage == null ? 1 : usage.count + 1, now));
        accountUsage.put(scope.customerId(), accountUsed + 1);
        dailyUsed++;
        var flight = new InFlight(scope);
        activeOrders.put(scope.orderId(), flight);
        return new Admission(flight, true);
    }

    private void rotateDay(Instant now) {
        var today = LocalDate.ofInstant(now, QUOTA_ZONE);
        if (budgetDay == null || today.isAfter(budgetDay)) {
            budgetDay = today;
            dailyUsed = 0;
            accountUsage.clear();
        }
    }

    private synchronized void release(Scope scope) {
        activeOrders.remove(scope.orderId());
    }

    private synchronized void releaseWaiter(InFlight flight) {
        flight.waiters--;
    }

    private record OrderUsage(int count, Instant last) {
    }

    private record Admission(InFlight flight, boolean leader) {
    }

    private static final class InFlight {
        final Scope scope;
        final CompletableFuture<VietmapRoutingClient.RouteData> result = new CompletableFuture<>();
        int waiters;

        InFlight(Scope scope) {
            this.scope = scope;
        }
    }
}
