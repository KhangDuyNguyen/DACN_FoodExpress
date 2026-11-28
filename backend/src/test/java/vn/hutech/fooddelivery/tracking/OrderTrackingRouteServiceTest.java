package vn.hutech.fooddelivery.tracking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static vn.hutech.fooddelivery.tracking.TrackingRouteException.Reason.*;

class OrderTrackingRouteServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-25T12:34:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final RoutePoint ORIGIN = new RoutePoint(10.79628, 106.70592);
    private static final RoutePoint DESTINATION = new RoutePoint(10.80189, 106.70661);

    private final OrderTrackingRepository repository = mock(OrderTrackingRepository.class);
    private final VietmapRoutingClient client = mock(VietmapRoutingClient.class);
    private final OrderTrackingRouteService service =
            new OrderTrackingRouteService(repository, client, new TrackingRouteQuota(), CLOCK);

    @Test
    void sixtySecondBoundaryUsesBackendClockAndSerializesCorrectInstant() {
        var snapshot = snapshot("MOTORBIKE", DESTINATION, LocalDateTime.now(CLOCK).minusSeconds(60));
        when(repository.findRouteSnapshot(11, 30, true)).thenReturn(Optional.of(snapshot));
        when(repository.findRouteSnapshot(11, 30, false)).thenReturn(Optional.of(snapshot));
        when(client.isAvailable()).thenReturn(true);
        when(client.getRoute(eq("motorcycle"), eq(ORIGIN), eq(DESTINATION), any()))
                .thenAnswer(call -> new VietmapRoutingClient.RouteData(null, 600, 180_000, call.getArgument(3), NOW));
        assertThat(service.getRoute(30, 11).origin().recordedAt()).isEqualTo(NOW.minusSeconds(60));
    }

    @Test
    void missingDestinationNeverDispatches() {
        when(repository.findRouteSnapshot(11, 30, true))
                .thenReturn(Optional.of(snapshot("CAR", null, LocalDateTime.now(CLOCK))));
        reject(ROUTE_DESTINATION_INVALID);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"BICYCLE", "UNKNOWN"})
    void missingOrUnsupportedProfileNeverDispatches(String type) {
        when(repository.findRouteSnapshot(11, 30, true))
                .thenReturn(Optional.of(snapshot(type, DESTINATION, LocalDateTime.now(CLOCK))));
        reject(ROUTE_PROFILE_UNSUPPORTED);
    }

    private void reject(TrackingRouteException.Reason reason) {
        assertThatThrownBy(() -> service.getRoute(30, 11)).isInstanceOfSatisfying(TrackingRouteException.class,
                error -> assertThat(error.reason).isEqualTo(reason));
        verifyNoInteractions(client);
    }

    private static OrderTrackingRepository.RouteSnapshot snapshot(String type, RoutePoint destination,
                                                                  LocalDateTime recordedAt) {
        return new OrderTrackingRepository.RouteSnapshot(30, true, "DELIVERING", 12L, type, destination,
                new OrderTrackingRepository.RouteFix(100, ORIGIN, recordedAt));
    }
}
