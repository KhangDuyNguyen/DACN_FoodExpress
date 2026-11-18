package vn.hutech.fooddelivery.driver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.order.OrderManagementService;
import vn.hutech.fooddelivery.order.OrderRepository;
import vn.hutech.fooddelivery.security.JwtService;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.config.import=",
        "app.jwt.secret=driver_location_tracking_test_secret_at_least_32_characters",
        "app.admin.phone=", "app.admin.password=", "app.assignment.scheduling.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class DriverLocationTrackingIntegrationTest {
    private static final AtomicLong IDS = new AtomicLong(620000);
    private static final long SEED_RESTAURANT = 1;
    private static final long SEED_MERCHANT_USER = 1;

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired OrderManagementService orders;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean DriverLocationRepository locations;
    @MockitoSpyBean OrderRepository orderRepository;

    private long customerId;
    private Driver driver;

    @BeforeEach
    void createUsers() {
        customerId = createUser("CUSTOMER");
        driver = createDriver();
    }

    @Test
    void deliveredOrderStopsReceivingPointsAndNextOrderStartsWithAFreshFix() throws Exception {
        long first = createOrder(driver.id(), "DELIVERING");
        long second = createOrder(null, "PLACED");

        location(driver, "10.71").andExpect(status().isCreated());
        tracking(first).andExpect(jsonPath("$.latestLocation.lat").value(10.71));
        driverStatus(driver, first, "DELIVERED").andExpect(status().isOk());

        location(driver, "10.72").andExpect(status().isCreated());
        assertNull(lastLocationOrder(driver.id()));
        assertEquals(1, locationCount(first));

        long offer = offer(second, driver.id());
        mvc.perform(post("/api/driver/offers/{id}/accept", offer)
                        .header("Authorization", auth(driver.userId(), "DRIVER")))
                .andExpect(status().isOk());
        tracking(second).andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.latestLocation").value(nullValue()));
        location(driver, "10.73").andExpect(status().isCreated());
        assertNull(lastLocationOrder(driver.id()));
        assertProfileLatitude(driver.id(), "10.7300000");
        assertEquals(0, locationCount(second));

        merchantStatus(second, "PREPARING").andExpect(status().isOk());
        merchantStatus(second, "READY_FOR_PICKUP").andExpect(status().isOk());
        driverStatus(driver, second, "DELIVERING").andExpect(status().isOk());
        tracking(second).andExpect(jsonPath("$.latestLocation").value(nullValue()));

        location(driver, "10.74").andExpect(status().isCreated());
        assertEquals(second, lastLocationOrder(driver.id()));
        tracking(second).andExpect(jsonPath("$.latestLocation.lat").value(10.74));
        tracking(first).andExpect(jsonPath("$.latestLocation.lat").value(10.71));
        assertEquals(1, locationCount(first));
        assertEquals(1, locationCount(second));
    }

    @Test
    void gpsFromAnotherDriverCannotBeAttachedToSomeoneElsesOrder() throws Exception {
        long orderId = createOrder(driver.id(), "DELIVERING");
        Driver other = createDriver();

        mvc.perform(post("/api/driver/location").param("orderId", Long.toString(orderId))
                        .header("Authorization", auth(other.userId(), "DRIVER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lat\":10.75,\"lng\":106.70}"))
                .andExpect(status().isCreated());

        assertNull(lastLocationOrder(other.id()));
        assertProfileLatitude(other.id(), "10.7500000");
        assertEquals(0, locationCount(orderId));
        tracking(orderId).andExpect(jsonPath("$.latestLocation").value(nullValue()));
    }

    @Test
    void multipleDeliveringOrdersKeepProfileGpsAndWriteUnlinkedHistory() throws Exception {
        long first = createOrder(driver.id(), "DELIVERING");
        long second = createOrder(driver.id(), "DELIVERING");

        location(driver, "10.76").andExpect(status().isCreated());

        assertNull(lastLocationOrder(driver.id()));
        assertProfileLatitude(driver.id(), "10.7600000");
        assertEquals(0, locationCount(first));
        assertEquals(0, locationCount(second));
    }

    @Test
    void completionHoldingOrderLockPreventsLateGpsFromBeingAttached() throws Exception {
        long orderId = createOrder(driver.id(), "DELIVERING");
        var gpsReachedOrderLock = new CountDownLatch(1);
        doAnswer(invocation -> {
            gpsReachedOrderLock.countDown();
            return invocation.callRealMethod();
        }).when(locations).lockActiveOrderIds(driver.userId());

        try (var executor = Executors.newSingleThreadExecutor()) {
            var request = new TransactionTemplate(transactions).execute(transaction -> {
                orders.changeStatusByDriver(driver.userId(), orderId, "DELIVERED");
                var pending = executor.submit(() -> location(driver, "10.77")
                        .andExpect(status().isCreated()));
                try {
                    assertTrue(gpsReachedOrderLock.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
                return pending;
            });
            request.get(15, TimeUnit.SECONDS);
        }

        assertEquals(0, locationCount(orderId));
        assertNull(lastLocationOrder(driver.id()));
        assertProfileLatitude(driver.id(), "10.7700000");
    }

    @Test
    void gpsHoldingOrderLockAttachesPointBeforeConcurrentCompletion() throws Exception {
        long orderId = createOrder(driver.id(), "DELIVERING");
        var gpsHasOrderLock = new CountDownLatch(1);
        var completionReachedOrderLock = new CountDownLatch(1);
        var releaseGps = new CountDownLatch(1);
        doAnswer(invocation -> {
            var lockedIds = invocation.callRealMethod();
            assertEquals(List.of(orderId), lockedIds);
            gpsHasOrderLock.countDown();
            assertTrue(releaseGps.await(30, TimeUnit.SECONDS), "GPS did not receive the release signal");
            return lockedIds;
        }).doCallRealMethod().when(locations).lockActiveOrderIds(driver.userId());
        doAnswer(invocation -> {
            assertEquals(1L, releaseGps.getCount(), "Completion must reach the lock before GPS is released");
            completionReachedOrderLock.countDown();
            var lockedOrder = invocation.callRealMethod();
            assertEquals(0L, releaseGps.getCount(), "Completion acquired the order lock before GPS was released");
            return lockedOrder;
        }).when(orderRepository).lockForUpdate(orderId);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var gps = executor.submit(() -> location(driver, "10.78").andExpect(status().isCreated()));
            Future<ResultActions> completion;
            try {
                assertTrue(gpsHasOrderLock.await(10, TimeUnit.SECONDS), "GPS did not acquire the order lock");
                completion = executor.submit(() -> driverStatus(driver, orderId, "DELIVERED")
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("DELIVERED")));
                assertTrue(completionReachedOrderLock.await(10, TimeUnit.SECONDS),
                        "Completion did not reach the order lock while GPS held it");
            } finally {
                releaseGps.countDown();
            }
            gps.get(15, TimeUnit.SECONDS);
            completion.get(15, TimeUnit.SECONDS);
        }

        assertEquals(orderId, lastLocationOrder(driver.id()));
        assertEquals(1, locationCount(orderId));

        location(driver, "10.79").andExpect(status().isCreated());
        assertProfileLatitude(driver.id(), "10.7900000");
        assertNull(lastLocationOrder(driver.id()));
        assertEquals(1, locationCount(orderId));
        tracking(orderId).andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.latestLocation.lat").value(10.78));
    }

    private long createUser(String role) {
        long id = IDS.incrementAndGet();
        jdbc.update("""
                INSERT INTO users (id, full_name, phone, password_hash, role)
                VALUES (:id, 'Tracking Test', :phone, 'test-only-hash', :role)
                """, Map.of("id", id, "phone", "0900" + id, "role", role));
        return id;
    }

    private Driver createDriver() {
        long userId = createUser("DRIVER");
        long id = IDS.incrementAndGet();
        jdbc.update("""
                INSERT INTO drivers (id, user_id, status, is_online)
                VALUES (:id, :userId, 'APPROVED', 1)
                """, Map.of("id", id, "userId", userId));
        return new Driver(id, userId);
    }

    private long createOrder(Long driverId, String status) {
        long id = IDS.incrementAndGet();
        jdbc.update("""
                INSERT INTO orders (id, code, customer_id, restaurant_id, driver_id, status,
                                    receiver_name, receiver_phone, delivery_address, delivery_lat, delivery_lng,
                                    subtotal, total_amount)
                VALUES (:id, :code, :customerId, :restaurantId, :driverId, :status,
                        'Tracking Test', '0900000000', 'Test address', 10.76, 106.66, 50000, 50000)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("code", "TRACK-" + id)
                .addValue("customerId", customerId)
                .addValue("restaurantId", SEED_RESTAURANT)
                .addValue("driverId", driverId)
                .addValue("status", status));
        return id;
    }

    private long offer(long orderId, long driverId) {
        long id = IDS.incrementAndGet();
        var now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        jdbc.update("""
                INSERT INTO order_assignments (id, order_id, driver_id, status, offered_at, expires_at)
                VALUES (:id, :orderId, :driverId, 'OFFERED', :offeredAt, :expiresAt)
                """, Map.of("id", id, "orderId", orderId, "driverId", driverId,
                "offeredAt", Timestamp.valueOf(now.minusSeconds(1)), "expiresAt", Timestamp.valueOf(now.plusSeconds(30))));
        return id;
    }

    private ResultActions location(Driver driver, String lat) throws Exception {
        return mvc.perform(post("/api/driver/location")
                .header("Authorization", auth(driver.userId(), "DRIVER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lat\":" + lat + ",\"lng\":106.70}"));
    }

    private ResultActions driverStatus(Driver driver, long id, String next) throws Exception {
        return changeStatus("/api/driver/orders/{id}/status", id, auth(driver.userId(), "DRIVER"), next);
    }

    private ResultActions merchantStatus(long id, String next) throws Exception {
        return changeStatus("/api/merchant/orders/{id}/status", id, auth(SEED_MERCHANT_USER, "MERCHANT"), next);
    }

    private ResultActions changeStatus(String path, long id, String token, String next) throws Exception {
        return mvc.perform(patch(path, id).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + next + "\"}"));
    }

    private ResultActions tracking(long orderId) throws Exception {
        return mvc.perform(get("/api/orders/{id}/tracking", orderId)
                        .header("Authorization", auth(customerId, "CUSTOMER")))
                .andExpect(status().isOk());
    }

    private String auth(long userId, String role) {
        return "Bearer " + jwt.generateToken(userId, role);
    }

    private long locationCount(long orderId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM driver_locations WHERE order_id = :id",
                Map.of("id", orderId), Long.class);
    }

    private Long lastLocationOrder(long driverId) {
        return jdbc.queryForObject("""
                SELECT order_id FROM driver_locations WHERE driver_id = :id ORDER BY id DESC LIMIT 1
                """, Map.of("id", driverId), Long.class);
    }

    private void assertProfileLatitude(long driverId, String expected) {
        assertEquals(new BigDecimal(expected), jdbc.queryForObject(
                "SELECT current_lat FROM drivers WHERE id = :id", Map.of("id", driverId), BigDecimal.class));
    }

    private record Driver(long id, long userId) {
    }
}
