package vn.hutech.fooddelivery.driver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.security.JwtService;
import vn.hutech.fooddelivery.user.UserRepository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chi su dung fixture/container rieng; khong thay doi tai khoan PENDING tren DB dang dung. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "debug=false", "app.admin.phone=", "app.admin.password=",
        "app.jwt.secret=driver_status_http_test_secret_at_least_32_chars"
})
@Testcontainers(disabledWithoutDocker = true)
class DriverStatusIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    private static final AtomicInteger sequence = new AtomicInteger();

    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    NamedParameterJdbcTemplate jdbc;
    @Autowired
    DataSource dataSource;
    @Autowired
    UserRepository users;
    @Autowired
    JwtService jwt;

    @Test
    void approvedDriverCanUsePostLocationThenGoOnlineWithoutChangingOtherDataOrDriver() throws Exception {
        var driver = createDriver("APPROVED", false, null, null, null);
        var other = createDriver("APPROVED", false, BigDecimal.ZERO, BigDecimal.ZERO, now());
        var otherBefore = snapshot(other.driverId());
        assertEquals(HttpStatus.CREATED, rest.postForEntity("/api/driver/location",
                new HttpEntity<>("{\"lat\":10.7769,\"lng\":106.7009}", headers(driver.token())), JsonNode.class)
                .getStatusCode());
        var before = withoutOnline(snapshot(driver.driverId()));

        var response = patch(driver.token(), "{\"isOnline\":true}", "?driverId=" + other.driverId());

        assertEquals(200, response.statusCode());
        assertEquals(mapper.readTree("{\"isOnline\":true}"), mapper.readTree(response.body()));
        assertEquals(true, snapshot(driver.driverId()).get("is_online"));
        assertEquals(before, withoutOnline(snapshot(driver.driverId())));
        assertEquals(otherBefore, snapshot(other.driverId()));
        var profile = rest.exchange("/api/driver/profile", HttpMethod.GET,
                new HttpEntity<>(headers(driver.token())), JsonNode.class).getBody();
        assertNotNull(profile);
        assertTrue(profile.path("isOnline").asBoolean());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "REJECTED", "SUSPENDED"})
    void nonApprovedDriverWithFreshGpsCannotGoOnline(String status) throws Exception {
        var driver = createDriver(status, false, BigDecimal.ZERO, BigDecimal.ZERO, now());
        var before = snapshot(driver.driverId());

        var response = patch(driver.token(), "{\"isOnline\":true}", "");

        assertEquals(403, response.statusCode());
        assertEquals(403, mapper.readTree(response.body()).path("status").asInt());
        assertEquals(before, snapshot(driver.driverId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingLat", "missingLng", "missingTime", "old", "future", "invalidLat", "invalidLng"})
    void invalidGpsReturns409WithoutUpdatingStoredState(String kind) throws Exception {
        BigDecimal lat = "missingLat".equals(kind) ? null : BigDecimal.ZERO;
        BigDecimal lng = "missingLng".equals(kind) ? null : BigDecimal.ZERO;
        LocalDateTime at = "missingTime".equals(kind) ? null : now();
        if ("old".equals(kind)) at = now().minusSeconds(120);
        if ("future".equals(kind)) at = now().plusSeconds(120);
        if ("invalidLat".equals(kind)) lat = new BigDecimal("91");
        if ("invalidLng".equals(kind)) lng = new BigDecimal("181");
        var driver = createDriver("APPROVED", false, lat, lng, at);
        var before = snapshot(driver.driverId());

        var response = patch(driver.token(), "{\"isOnline\":true}", "");

        assertEquals(409, response.statusCode());
        assertEquals(409, mapper.readTree(response.body()).path("status").asInt());
        assertEquals(before, snapshot(driver.driverId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "APPROVED", "REJECTED", "SUSPENDED"})
    void allStatusesCanGoOfflineRepeatedlyWithoutGps(String status) throws Exception {
        var driver = createDriver(status, true, null, null, null);
        var before = withoutOnline(snapshot(driver.driverId()));

        for (int attempt = 0; attempt < 2; attempt++) {
            var response = patch(driver.token(), "{\"isOnline\":false}", "");
            assertEquals(200, response.statusCode());
            assertEquals(mapper.readTree("{\"isOnline\":false}"), mapper.readTree(response.body()));
            assertEquals(false, snapshot(driver.driverId()).get("is_online"));
            assertEquals(before, withoutOnline(snapshot(driver.driverId())));
        }
    }

    @Test
    void offlineWithOldGpsLeavesDeliveringOrderUnchanged() throws Exception {
        var driver = createDriver("APPROVED", true, BigDecimal.ZERO, BigDecimal.ZERO, now().minusDays(1));
        long customerId = users.insert("Customer Test", phone(), null, "test-only-hash", "CUSTOMER");
        jdbc.update("""
                INSERT INTO orders (code, customer_id, restaurant_id, driver_id, status,
                                    receiver_name, receiver_phone, delivery_address, delivery_lat, delivery_lng,
                                    subtotal, total_amount)
                VALUES ('STATUS-DELIVERING', :customerId, 1, :driverId, 'DELIVERING',
                        'Receiver Test', '0999999999', 'Test address', 10, 106, 50000, 50000)
                """, Map.of("customerId", customerId, "driverId", driver.driverId()));
        var orderBefore = jdbc.queryForMap("SELECT * FROM orders WHERE code = :code",
                Map.of("code", "STATUS-DELIVERING"));

        assertEquals(200, patch(driver.token(), "{\"isOnline\":false}", "").statusCode());

        assertEquals(orderBefore, jdbc.queryForMap("SELECT * FROM orders WHERE code = :code",
                Map.of("code", "STATUS-DELIVERING")));
    }

    @Test
    void driverWithoutProfileReceives404() throws Exception {
        long userId = users.insert("Status Test", phone(), null, "test-only-hash", "DRIVER");
        var response = patch(jwt.generateToken(userId, "DRIVER"), "{\"isOnline\":false}", "");
        assertEquals(404, response.statusCode());
        assertEquals(404, mapper.readTree(response.body()).path("status").asInt());
    }

    @Test
    void patchWaitingForConcurrentSuspensionCannotEnableOnline() throws Exception {
        var driver = createDriver("APPROVED", false, BigDecimal.ZERO, BigDecimal.ZERO, now());
        try (var connection = dataSource.getConnection(); var executor = Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.prepareStatement("UPDATE drivers SET status = 'SUSPENDED' WHERE id = ?")) {
                    statement.setLong(1, driver.driverId());
                    assertEquals(1, statement.executeUpdate());
                }
                var started = new CountDownLatch(1);
                var request = executor.submit(() -> {
                    started.countDown();
                    return patch(driver.token(), "{\"isOnline\":true}", "");
                });
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> request.get(200, TimeUnit.MILLISECONDS));
                connection.commit();

                assertEquals(403, request.get(10, TimeUnit.SECONDS).statusCode());
                assertEquals("SUSPENDED", snapshot(driver.driverId()).get("status"));
                assertEquals(false, snapshot(driver.driverId()).get("is_online"));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void swaggerDocumentsStrictBooleanRequestAndStoredOnlineResponse() {
        var response = rest.getForEntity("/v3/api-docs", JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode api = response.getBody();
        assertNotNull(api);
        var patch = api.path("paths").path("/api/driver/status").path("patch");
        assertFalse(patch.isMissingNode());
        assertFalse(patch.has("parameters"));
        for (String code : Set.of("200", "400", "401", "403", "404", "409", "500")) {
            assertTrue(patch.path("responses").has(code), code);
        }
        var requestSchema = api.path("components").path("schemas").path("DriverStatusRequest");
        assertEquals(1, requestSchema.path("properties").size());
        assertEquals("boolean", requestSchema.path("properties").path("isOnline").path("type").asText());
        assertFalse(requestSchema.path("additionalProperties").asBoolean(true));
        assertEquals(1, api.path("components").path("schemas").path("DriverStatusResponse").path("properties").size());
    }

    private HttpResponse<String> patch(String token, String body, String query) throws Exception {
        // HttpClient cua JDK 21 ho tro PATCH, khong can them dependency HTTP cho test.
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/driver/status" + query))
                    .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json").method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                    .build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private HttpHeaders headers(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private Map<String, Object> snapshot(long driverId) {
        return jdbc.queryForMap("SELECT * FROM drivers WHERE id = :id", Map.of("id", driverId));
    }

    private Map<String, Object> withoutOnline(Map<String, Object> snapshot) {
        var fields = new HashMap<>(snapshot);
        fields.remove("is_online");
        return fields;
    }

    private LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private String phone() {
        return String.format("096%07d", sequence.incrementAndGet());
    }

    private DriverFixture createDriver(String status, boolean online, BigDecimal lat, BigDecimal lng, LocalDateTime at) {
        long userId = users.insert("Status Test", phone(), null, "test-only-hash", "DRIVER");
        long driverId = 2000 + userId;
        var params = new MapSqlParameterSource().addValue("id", driverId).addValue("userId", userId)
                .addValue("status", status).addValue("online", online).addValue("lat", lat).addValue("lng", lng)
                .addValue("locationAt", at == null ? null : Timestamp.valueOf(at));
        jdbc.update("""
                INSERT INTO drivers (id, user_id, vehicle_type, status, is_online, current_lat, current_lng, last_location_at)
                VALUES (:id, :userId, 'BICYCLE', :status, :online, :lat, :lng, :locationAt)
                """, params);
        return new DriverFixture(driverId, jwt.generateToken(userId, "DRIVER"));
    }

    private record DriverFixture(long driverId, String token) {
    }
}
