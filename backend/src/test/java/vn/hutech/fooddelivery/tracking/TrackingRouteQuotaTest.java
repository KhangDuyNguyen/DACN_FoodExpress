package vn.hutech.fooddelivery.tracking;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static vn.hutech.fooddelivery.tracking.TrackingRouteException.Reason.*;

class TrackingRouteQuotaTest {
    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private static final VietmapRoutingClient.RouteData ROUTE =
            new VietmapRoutingClient.RouteData(null, 1, 1, null, NOW);
    private final TrackingRouteQuota quota = new TrackingRouteQuota();

    @Test
    void sameOrderAndScopeCoalescesWhileDifferentScopesAndAccountsAreIsolated() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var joiningThread = new AtomicReference<Thread>();
        var calls = new AtomicInteger();
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var leader = threads.submit(() -> quota.execute(scope(1, 1), NOW, () -> {
                calls.incrementAndGet();
                started.countDown();
                return hold(release);
            }));
            try {
                assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
                var joiner = threads.submit(() -> {
                    joiningThread.set(Thread.currentThread());
                    return quota.execute(scope(1, 1), NOW, () -> {
                        calls.incrementAndGet();
                        return ROUTE;
                    });
                });
                await().atMost(2, TimeUnit.SECONDS).until(() -> joiningThread.get() != null
                        && joiningThread.get().getState() == Thread.State.WAITING);
                limited(scope(1, 2), NOW);
                limited(new TrackingRouteQuota.Scope(1, 1, 30, "car", new RoutePoint(10, 106)), NOW);
                limited(scope(2, 1), NOW);
                release.countDown();
                assertThat(leader.get(2, TimeUnit.SECONDS)).isSameAs(ROUTE);
                assertThat(joiner.get(2, TimeUnit.SECONDS)).isSameAs(ROUTE);
                assertThat(calls.get()).isEqualTo(1);
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void cooldownAndOrderBudgetIncludeFailedAttemptsAndSurviveDayChange() {
        var scope = scope(1, 1);
        assertThatThrownBy(() -> quota.execute(scope, NOW, () -> {
            throw new TrackingRouteException(ROUTE_UPSTREAM_ERROR);
        })).isInstanceOfSatisfying(TrackingRouteException.class,
                error -> assertThat(error.reason).isEqualTo(ROUTE_UPSTREAM_ERROR));
        limited(scope, NOW.plusSeconds(14));
        for (int i = 1; i <= 4; i++) {
            run(scope, NOW.plusSeconds(15L * i));
        }
        limited(scope, NOW.plusSeconds(80));
        limited(scope, NOW.plusSeconds(86_400));
    }

    @Test
    void limitsAccountAndDailyConsumptionAndResetsAtVietnamMidnight() {
        var closeOfDay = Instant.parse("2026-11-25T16:59:59Z");
        for (int i = 1; i <= 15; i++) {
            run(scope(i, 1), closeOfDay);
        }
        limited(scope(16, 1), closeOfDay);
        for (int i = 16; i <= 50; i++) {
            run(scope(i, i), closeOfDay);
        }
        limited(scope(51, 2), closeOfDay);
        run(scope(51, 1), closeOfDay.plusSeconds(1));
    }

    @Test
    void boundsGlobalInFlightAndDoesNotChargeRejectedWork() throws Exception {
        var started = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var work = new ArrayList<java.util.concurrent.Future<VietmapRoutingClient.RouteData>>();
            for (long id : List.of(1L, 2L, 3L, 4L)) {
                work.add(threads.submit(() -> quota.execute(scope(id, id), NOW, () -> {
                    started.countDown();
                    return hold(release);
                })));
            }
            try {
                assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
                limited(scope(5, 5), NOW);
            } finally {
                release.countDown();
            }
            for (var task : work) {
                assertThat(task.get(2, TimeUnit.SECONDS)).isSameAs(ROUTE);
            }
            run(scope(5, 5), NOW);
        }
    }

    private static VietmapRoutingClient.RouteData hold(CountDownLatch release) {
        try {
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return ROUTE;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    private void run(TrackingRouteQuota.Scope scope, Instant now) {
        assertThat(quota.execute(scope, now, () -> ROUTE)).isSameAs(ROUTE);
    }

    private void limited(TrackingRouteQuota.Scope scope, Instant now) {
        assertThatThrownBy(() -> quota.execute(scope, now, () -> {
            throw new AssertionError("Over-budget request reached upstream");
        })).isInstanceOfSatisfying(TrackingRouteException.class,
                error -> assertThat(error.reason).isEqualTo(ROUTE_RATE_LIMITED));
    }

    private static TrackingRouteQuota.Scope scope(long order, long customer) {
        return new TrackingRouteQuota.Scope(order, customer, 12, "motorcycle", new RoutePoint(10, 106));
    }
}
