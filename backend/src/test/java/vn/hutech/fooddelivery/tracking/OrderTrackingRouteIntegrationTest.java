package vn.hutech.fooddelivery.tracking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.security.JwtService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.hutech.fooddelivery.tracking.TrackingRouteException.Reason.ROUTE_UPSTREAM_ERROR;

@SpringBootTest(properties = {
        "spring.config.import=", "debug=false", "app.admin.phone=", "app.admin.password=",
        "app.jwt.secret=tracking_route_fixture_secret_at_least_32_chars"
})
@AutoConfigureMockMvc
@Testcontainers
class OrderTrackingRouteIntegrationTest {
    private static final String ROUTE = "/api/orders/{id}/tracking/route";
    private static final AtomicInteger NEXT_FIXTURE = new AtomicInteger(900000);

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    MockMvc mvc;
    @Autowired
    NamedParameterJdbcTemplate jdbc;
    @Autowired
    JwtService jwt;
    @MockitoBean
    VietmapRoutingClient routing;

    private long owner;
    private long stranger;
    private long driver;
    private long otherDriver;
    private long order;
    private long otherOrder;

    @BeforeEach
    void seedNewFixture() {
        int base = NEXT_FIXTURE.getAndAdd(10);
        owner = base + 1;
        stranger = base + 2;
        driver = base + 4;
        otherDriver = base + 5;
        order = base + 6;
        otherOrder = base + 7;
        insertUser(owner, "CUSTOMER");
        insertUser(stranger, "CUSTOMER");
        insertUser(base + 3, "DRIVER");
        insertUser(base + 8, "DRIVER");
        insertDriver(driver, base + 3);
        insertDriver(otherDriver, base + 8);
        insertOrder(order, owner);
        insertOrder(otherOrder, stranger);
        when(routing.isAvailable()).thenReturn(true);
        when(routing.getRoute(anyString(), any(), any(), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return route(invocation.getArgument(3));
        });
    }

    @Test
    void motorbikeRouteUsesLatestOwnedFixIgnoresClientCoordinatesAndKeepsTrackingContract() throws Exception {
        long first = insertFix(order, driver, 10.79, 106.70, LocalDateTime.now().minusSeconds(6));
        long actual = insertFix(order, driver, 10.79628, 106.70592, LocalDateTime.now().minusSeconds(4));
        assertThat(actual).isGreaterThan(first);
        insertFix(otherOrder, driver, 20, 110, LocalDateTime.now());
        insertFix(order, otherDriver, 21, 111, LocalDateTime.now());

        mvc.perform(get(ROUTE, order).queryParam("origin", "24,115").queryParam("destination", "24,116")
                        .header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.orderId").value(order))
                .andExpect(jsonPath("$.vehicleProfile").value("motorcycle"))
                .andExpect(jsonPath("$.geometry.type").value("LineString"))
                .andExpect(jsonPath("$.geometry.coordinates[0][0]").value(106.70592))
                .andExpect(jsonPath("$.geometry.coordinates[0][1]").value(10.79628))
                .andExpect(jsonPath("$.distanceMeters").value(1790.1))
                .andExpect(jsonPath("$.durationMs").value(236318))
                .andExpect(jsonPath("$.origin.locationId").value(actual))
                .andExpect(jsonPath("$.origin.recordedAt").value(endsWith("Z")))
                .andExpect(jsonPath("$.generatedAt").value(endsWith("Z")));
        verify(routing).getRoute(eq("motorcycle"), eq(new RoutePoint(10.79628, 106.70592)),
                eq(new RoutePoint(10.80189, 106.70661)), any());

        mvc.perform(get("/api/orders/{id}/tracking", order).header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERING"))
                .andExpect(jsonPath("$.latestLocation.lat").value(10.79628))
                .andExpect(jsonPath("$.vehicleProfile").doesNotExist());
    }

    @Test
    void carUsesCarProfile() throws Exception {
        changeVehicle("CAR");
        freshFix();
        ownerRoute().andExpect(status().isOk()).andExpect(jsonPath("$.vehicleProfile").value("car"));
        verify(routing).getRoute(eq("car"), any(), any(), any());
    }

    @Test
    void legacyBicycleRejectedBeforeAnyProviderCallAndRestTrackingStillWorks() throws Exception {
        changeVehicle("BICYCLE");
        freshFix();
        assertError(ownerRoute(), 422, "ROUTE_PROFILE_UNSUPPORTED");
        verifyNoInteractions(routing);
        mvc.perform(get("/api/orders/{id}/tracking", order).header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.latestLocation.lat").value(10.79628));
    }

    @Test
    void guestWrongRoleWrongOwnerAndMissingOrderDoNotReachProvider() throws Exception {
        mvc.perform(get(ROUTE, order)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc.perform(get(ROUTE, order).header("Authorization", bearer(owner + 2, "DRIVER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        assertError(mvc.perform(get(ROUTE, order).header("Authorization", bearer(stranger, "CUSTOMER"))),
                403, "ROUTE_FORBIDDEN");
        assertError(mvc.perform(get(ROUTE, 99999999).header("Authorization", bearer(owner, "CUSTOMER"))),
                404, "ROUTE_ORDER_NOT_FOUND");
        verifyNoInteractions(routing);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLACED", "DELIVERED", "CANCELLED"})
    void onlyDeliveringIsEligible(String status) throws Exception {
        jdbc.update("UPDATE orders SET status = :status WHERE id = :id", Map.of("status", status, "id", order));
        assertError(ownerRoute(), 409, "ROUTE_ORDER_NOT_READY");
        verifyNoInteractions(routing);
    }

    @Test
    void missingDriverAndInvalidDestinationAreRejected() throws Exception {
        jdbc.update("UPDATE orders SET driver_id = NULL WHERE id = :id", Map.of("id", order));
        assertError(ownerRoute(), 409, "ROUTE_ORDER_NOT_READY");
        // V1 requires a delivery point; an out-of-range coordinate still fits the DECIMAL column.
        jdbc.update("UPDATE orders SET driver_id = :driver, delivery_lat = 91 WHERE id = :id",
                Map.of("id", order, "driver", driver));
        assertError(ownerRoute(), 409, "ROUTE_DESTINATION_INVALID");
        verifyNoInteractions(routing);
    }

    @Test
    void wrongOrderWrongDriverStaleFutureAndInvalidFixesAreRejected() throws Exception {
        var now = LocalDateTime.now();
        insertFix(otherOrder, driver, 10.79628, 106.70592, now);
        insertFix(order, otherDriver, 10.79628, 106.70592, now);
        assertError(ownerRoute(), 409, "ROUTE_GPS_UNAVAILABLE");
        insertFix(order, driver, 10.79628, 106.70592, now.minusSeconds(61));
        assertError(ownerRoute(), 409, "ROUTE_GPS_UNAVAILABLE");
        insertFix(order, driver, 92, 106.70592, now);
        assertError(ownerRoute(), 409, "ROUTE_GPS_UNAVAILABLE");
        insertFix(order, driver, 10.79628, 106.70592, now.plusSeconds(30));
        assertError(ownerRoute(), 409, "ROUTE_GPS_UNAVAILABLE");
        verifyNoInteractions(routing);
    }

    @ParameterizedTest
    @ValueSource(strings = {"status = 'DELIVERED'", "driver_id = :otherDriver", "delivery_lat = 10.88"})
    void revalidatesDeliveringDriverAndDestinationAfterProviderCall(String assignment) throws Exception {
        freshFix();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            jdbc.update("UPDATE orders SET " + assignment + " WHERE id = :id",
                    Map.of("id", order, "otherDriver", otherDriver));
            return route(invocation.getArgument(3));
        }).when(routing).getRoute(anyString(), any(), any(), any());
        assertError(ownerRoute(), 409, "ROUTE_CONTEXT_CHANGED");
    }

    @Test
    void rejectsFormerOwnerIfOwnershipChangesWhileWaiting() throws Exception {
        freshFix();
        doAnswer(invocation -> {
            jdbc.update("UPDATE orders SET customer_id = :newOwner WHERE id = :id",
                    Map.of("id", order, "newOwner", stranger));
            return route(invocation.getArgument(3));
        }).when(routing).getRoute(anyString(), any(), any(), any());
        assertError(ownerRoute(), 403, "ROUTE_FORBIDDEN");
    }

    @Test
    void rejectsOwnerLockedWhileWaiting() throws Exception {
        freshFix();
        doAnswer(invocation -> {
            jdbc.update("UPDATE users SET status = 'LOCKED' WHERE id = :customer", Map.of("customer", owner));
            return route(invocation.getArgument(3));
        }).when(routing).getRoute(anyString(), any(), any(), any());
        assertError(ownerRoute(), 403, "ROUTE_FORBIDDEN");
    }

    @Test
    void newerGpsDoesNotInvalidateRouteAndOriginalFixIsReturned() throws Exception {
        long usedId = freshFix();
        doAnswer(invocation -> {
            insertFix(order, driver, 10.8, 106.7, LocalDateTime.now());
            return route(invocation.getArgument(3));
        }).when(routing).getRoute(anyString(), any(), any(), any());
        ownerRoute().andExpect(status().isOk()).andExpect(jsonPath("$.origin.locationId").value(usedId));
    }

    @Test
    void unavailableTransportConsumesNoSlotAndProviderFailureStartsCooldown() throws Exception {
        freshFix();
        when(routing.isAvailable()).thenReturn(false);
        assertError(ownerRoute(), 503, "ROUTE_UNAVAILABLE");
        verify(routing, never()).getRoute(anyString(), any(), any(), any());
        when(routing.isAvailable()).thenReturn(true);
        doThrow(new TrackingRouteException(ROUTE_UPSTREAM_ERROR))
                .when(routing).getRoute(anyString(), any(), any(), any());
        assertError(ownerRoute(), 502, "ROUTE_UPSTREAM_ERROR")
                .andExpect(jsonPath("$.trace").doesNotExist()).andExpect(jsonPath("$.url").doesNotExist());
        assertError(ownerRoute(), 429, "ROUTE_RATE_LIMITED");
        verify(routing, times(1)).getRoute(anyString(), any(), any(), any());
    }

    private ResultActions ownerRoute() throws Exception {
        return mvc.perform(get(ROUTE, order).header("Authorization", bearer(owner, "CUSTOMER")));
    }

    private static ResultActions assertError(ResultActions response, int status, String code) throws Exception {
        return response.andExpect(status().is(status)).andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").exists()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.geometry").doesNotExist());
    }

    private static VietmapRoutingClient.RouteData route(OrderTrackingRouteResponse.Origin origin) {
        var geometry = new OrderTrackingRouteResponse.Geometry("LineString",
                List.of(List.of(origin.lng(), origin.lat()), List.of(106.70661, 10.80189)));
        return new VietmapRoutingClient.RouteData(geometry, 1790.1, 236318, origin, Instant.now());
    }

    private long freshFix() {
        return insertFix(order, driver, 10.79628, 106.70592, LocalDateTime.now().minusSeconds(2));
    }

    private long insertFix(long orderId, long driverId, double latitude, double longitude, LocalDateTime time) {
        jdbc.update("""
                INSERT INTO driver_locations (order_id, driver_id, lat, lng, recorded_at)
                VALUES (:orderId, :driver, :lat, :lng, :time)
                """, Map.of("orderId", orderId, "driver", driverId, "lat", latitude, "lng", longitude, "time", time));
        return jdbc.queryForObject("SELECT MAX(id) FROM driver_locations WHERE order_id = :id",
                Map.of("id", orderId), Long.class);
    }

    private void changeVehicle(String vehicle) {
        jdbc.update("UPDATE drivers SET vehicle_type = :vehicle WHERE id = :id",
                Map.of("id", driver, "vehicle", vehicle));
    }

    private String bearer(long user, String role) {
        return "Bearer " + jwt.generateToken(user, role);
    }

    private void insertUser(long id, String role) {
        jdbc.update("""
                INSERT INTO users (id, phone, full_name, password_hash, role) VALUES (:id, :phone, 'Route Fixture',
                    'fixture-password-hash', :role)
                """, Map.of("id", id, "role", role, "phone", "0900" + id));
    }

    private void insertDriver(long id, long userId) {
        jdbc.update("""
                INSERT INTO drivers (id, user_id, vehicle_type, status) VALUES (:id, :userId, 'MOTORBIKE', 'APPROVED')
                """, Map.of("id", id, "userId", userId));
    }

    private void insertOrder(long id, long customerId) {
        jdbc.update("""
                INSERT INTO orders (id, code, customer_id, restaurant_id, driver_id, status,
                                    receiver_name, receiver_phone, delivery_address, delivery_lat, delivery_lng,
                                    subtotal, total_amount)
                VALUES (:id, :code, :customer, 1, :driver, 'DELIVERING',
                        'Route Fixture', '0900900001', 'Fixture', 10.80189, 106.70661, 50000, 50000)
                """, Map.of("id", id, "code", "ROUTE-" + id, "customer", customerId, "driver", driver));
    }
}
