package vn.hutech.fooddelivery.tracking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.security.JwtService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.config.import=",
        "app.jwt.secret=tracking_integration_test_secret_at_least_32_characters",
        "app.admin.phone=",
        "app.admin.password="
})
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class OrderTrackingIntegrationTest {
    private static final String URL = "/api/orders/{id}/tracking";
    private static final long OWNER = 610001;
    private static final long OTHER_CUSTOMER = 610002;
    private static final long DRIVER_USER = 610003;
    private static final long FORMER_DRIVER_USER = 610004;
    private static final long DRIVER = 610011;
    private static final long FORMER_DRIVER = 610012;
    private static final long ORDER = 610021;
    private static final long OTHER_ORDER = 610022;
    private static final LocalDateTime FIX_TIME = LocalDateTime.parse("2026-11-18T10:15:30.123");

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @Autowired
    JwtService jwt;

    @BeforeEach
    void createFixtures() {
        insertUser(OWNER, "CUSTOMER");
        insertUser(OTHER_CUSTOMER, "CUSTOMER");
        insertUser(DRIVER_USER, "DRIVER");
        insertUser(FORMER_DRIVER_USER, "DRIVER");
        jdbc.update("""
                INSERT INTO drivers (id, user_id, current_lat, current_lng, last_location_at)
                VALUES (:driver, :driverUser, 10.70, 106.70, :lastFix),
                       (:formerDriver, :formerUser, NULL, NULL, NULL)
                """, Map.of("driver", DRIVER, "driverUser", DRIVER_USER,
                "formerDriver", FORMER_DRIVER, "formerUser", FORMER_DRIVER_USER, "lastFix", FIX_TIME));
        // Restaurant 1 is created by the Flyway demo seed.
        jdbc.update("""
                INSERT INTO orders (id, code, customer_id, restaurant_id, driver_id, status,
                                    receiver_name, receiver_phone, delivery_address, delivery_lat, delivery_lng,
                                    subtotal, total_amount)
                VALUES (:orderId, 'TRACKING-1', :owner, 1, :driver, 'DELIVERING',
                        'Tracking Test', '0900610001', 'Test address', 10.76, 106.66, 50000, 50000),
                       (:otherOrder, 'TRACKING-2', :otherCustomer, 1, :driver, 'DELIVERING',
                        'Tracking Test', '0900610002', 'Test address', 10.76, 106.66, 50000, 50000)
                """, Map.of("orderId", ORDER, "owner", OWNER, "driver", DRIVER,
                "otherOrder", OTHER_ORDER, "otherCustomer", OTHER_CUSTOMER));
    }

    @Test
    void ownerGetsOrderAndLatestLocationByRecordedTimeThenId() throws Exception {
        insertLocation(ORDER, DRIVER, "10.78", "106.68", FIX_TIME);
        insertLocation(ORDER, DRIVER, "10.79", "106.69", FIX_TIME);
        insertLocation(ORDER, DRIVER, "10.77", "106.67", FIX_TIME.minusSeconds(1));

        tracking(ORDER, OWNER, "CUSTOMER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(ORDER))
                .andExpect(jsonPath("$.orderCode").value("TRACKING-1"))
                .andExpect(jsonPath("$.status").value("DELIVERING"))
                .andExpect(jsonPath("$.latestLocation.lat").value(10.79))
                .andExpect(jsonPath("$.latestLocation.lng").value(106.69))
                .andExpect(jsonPath("$.latestLocation.recordedAt").value("2026-11-18T10:15:30.123"));
    }

    @Test
    void anotherCustomerGetsForbiddenApiError() throws Exception {
        insertLocation(ORDER, DRIVER, "10.78", "106.68", FIX_TIME);

        tracking(ORDER, OTHER_CUSTOMER, "CUSTOMER")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.latestLocation").doesNotExist());
    }

    @Test
    void noOrderGpsReturnsExplicitNullDespiteDriverProfileLocation() throws Exception {
        tracking(ORDER, OWNER, "CUSTOMER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestLocation").value(nullValue()));
    }

    @Test
    void locationOfAnotherOrderOrWithoutOrderIsNotUsedForSameDriver() throws Exception {
        insertLocation(OTHER_ORDER, DRIVER, "10.79", "106.69", FIX_TIME);
        insertLocation(null, DRIVER, "10.78", "106.68", FIX_TIME);

        tracking(ORDER, OWNER, "CUSTOMER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestLocation").value(nullValue()));
    }

    @Test
    void locationOfFormerDriverIsNotUsedForSameOrder() throws Exception {
        insertLocation(ORDER, DRIVER, "10.78", "106.68", FIX_TIME);
        insertLocation(ORDER, FORMER_DRIVER, "10.79", "106.69", FIX_TIME.plusSeconds(1));

        tracking(ORDER, OWNER, "CUSTOMER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestLocation.lat").value(10.78))
                .andExpect(jsonPath("$.latestLocation.lng").value(106.68));
    }

    @Test
    void missingOrderGetsNotFoundApiError() throws Exception {
        tracking(ORDER + 100, OWNER, "CUSTOMER")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void trackingRequiresCustomerRole() throws Exception {
        mockMvc.perform(get(URL, ORDER)).andExpect(status().isUnauthorized());
        tracking(ORDER, DRIVER_USER, "DRIVER").andExpect(status().isForbidden());
    }

    private ResultActions tracking(long orderId, long userId, String role) throws Exception {
        return mockMvc.perform(get(URL, orderId)
                .header("Authorization", "Bearer " + jwt.generateToken(userId, role)));
    }

    private void insertUser(long id, String role) {
        jdbc.update("""
                INSERT INTO users (id, full_name, phone, password_hash, role)
                VALUES (:id, 'Tracking Test', :phone, 'unused', :role)
                """, Map.of("id", id, "phone", "0900" + id, "role", role));
    }

    private void insertLocation(Long orderId, long driverId, String lat, String lng, LocalDateTime recordedAt) {
        jdbc.update("""
                INSERT INTO driver_locations (order_id, driver_id, lat, lng, recorded_at)
                VALUES (:orderId, :driverId, :lat, :lng, :recordedAt)
                """, new MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("driverId", driverId)
                .addValue("lat", new BigDecimal(lat))
                .addValue("lng", new BigDecimal(lng))
                .addValue("recordedAt", recordedAt));
    }
}
