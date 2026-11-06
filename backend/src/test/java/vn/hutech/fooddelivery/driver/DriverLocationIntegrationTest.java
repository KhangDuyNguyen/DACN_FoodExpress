package vn.hutech.fooddelivery.driver;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.security.JwtService;
import vn.hutech.fooddelivery.user.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

/** HTTP/JWT/MySQL that; chi ghi du lieu va gay loi rang buoc trong container test rieng. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "debug=false",
        "app.jwt.secret=driver_location_http_test_secret_at_least_32_chars",
        "app.admin.phone=", "app.admin.password="
})
@Testcontainers(disabledWithoutDocker = true)
class DriverLocationIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    private static final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    TestRestTemplate rest;
    @Autowired
    NamedParameterJdbcTemplate jdbc;
    @Autowired
    UserRepository users;
    @Autowired
    JwtService jwtService;
    @MockitoSpyBean
    DriverRepository driverRepository;
    @MockitoSpyBean
    DriverLocationRepository driverLocationRepository;

    @ParameterizedTest
    @CsvSource({"PENDING,false", "APPROVED,false", "APPROVED,true", "REJECTED,false", "SUSPENDED,false"})
    void savesOneHistoryPerRequestAndSnapshotWithoutChangingProfilesOrAnotherDriver(String status, boolean online) {
        var driver = createDriver(status, online);
        var other = createDriver("PENDING", false);
        var otherBefore = snapshot(other.driverId());
        var profileBefore = rest.exchange("/api/driver/profile", HttpMethod.GET,
                new HttpEntity<>(headers(driver.token())), JsonNode.class);
        var before = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        var response = rest.exchange("/api/driver/location?driverId=" + other.driverId(), HttpMethod.POST,
                new HttpEntity<>("{\"lat\":10.7769,\"lng\":106.7009}", headers(driver.token())), JsonNode.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        JsonNode body = response.getBody();
        assertNotNull(body);
        assertEquals(3, body.size());
        var recordedAt = LocalDateTime.parse(body.path("recordedAt").asText());
        assertFalse(recordedAt.isBefore(before));
        assertFalse(recordedAt.isAfter(LocalDateTime.now()));
        var history = jdbc.queryForList("SELECT * FROM driver_locations WHERE driver_id = :driverId",
                Map.of("driverId", driver.driverId()));
        assertEquals(1, history.size());
        assertEquals(new BigDecimal("10.7769000"), history.getFirst().get("lat"));
        assertEquals(new BigDecimal("106.7009000"), history.getFirst().get("lng"));
        assertNull(history.getFirst().get("order_id"));
        assertEquals(recordedAt, history.getFirst().get("recorded_at"));
        var current = snapshot(driver.driverId());
        assertEquals(new BigDecimal("10.7769000"), current.get("current_lat"));
        assertEquals(new BigDecimal("106.7009000"), current.get("current_lng"));
        assertEquals(recordedAt, current.get("last_location_at"));
        assertEquals(status, current.get("status"));
        assertEquals(online, current.get("is_online"));
        assertEquals(otherBefore, snapshot(other.driverId()));
        assertEquals(0, historyCount(other.driverId()));
        assertEquals(profileBefore.getBody(), rest.exchange("/api/driver/profile", HttpMethod.GET,
                new HttpEntity<>(headers(driver.token())), JsonNode.class).getBody());

        assertEquals(HttpStatus.CREATED, rest.exchange("/api/driver/location", HttpMethod.POST,
                new HttpEntity<>("{\"lat\":10.7769,\"lng\":106.7009}", headers(driver.token())), JsonNode.class)
                .getStatusCode());
        assertEquals(2, historyCount(driver.driverId()));
    }

    @Test
    void missingDriverProfileReturns404AndDoesNotInsertHistory() {
        long userId = createUser();
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM driver_locations", Map.of(), Long.class);

        var response = post(jwtService.generateToken(userId, "DRIVER"), "{\"lat\":10,\"lng\":106}");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM driver_locations", Map.of(), Long.class));
    }

    @Test
    void spoofedIdsAndClientTimestampAreRejectedWithoutWrites() {
        var driver = createDriver("PENDING", false);
        var before = snapshot(driver.driverId());

        var response = post(driver.token(), "{\"lat\":10,\"lng\":106,\"driverId\":9999,"
                + "\"orderId\":9999,\"recordedAt\":\"2000-01-01T00:00:00\"}");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(before, snapshot(driver.driverId()));
        assertEquals(0, historyCount(driver.driverId()));
    }

    @Test
    void snapshotSqlFailureRollsBackPreviouslyInsertedHistory() {
        var driver = createDriver("PENDING", false);
        var before = snapshot(driver.driverId());
        // Gay loi NOT NULL that o MySQL sau khi Service da INSERT history, khong doi schema.
        doAnswer(invocation -> jdbc.update("UPDATE drivers SET user_id = NULL WHERE id = :driverId",
                Map.of("driverId", driver.driverId())))
                .when(driverRepository).updateLocation(eq(driver.driverId()), any(), any(), any());

        var response = post(driver.token(), "{\"lat\":10,\"lng\":106}");

        assertStorageError(response);
        assertEquals(0, historyCount(driver.driverId()));
        assertEquals(before, snapshot(driver.driverId()));
    }

    @Test
    void insertSqlFailureDoesNotChangeSnapshot() {
        var driver = createDriver("APPROVED", true);
        var before = snapshot(driver.driverId());
        // Driver khong ton tai vi pham FK trong container test, INSERT that se that bai.
        doAnswer(invocation -> jdbc.update("""
                INSERT INTO driver_locations (driver_id, lat, lng)
                VALUES (:missingDriverId, 10, 106)
                """, Map.of("missingDriverId", 999999L)))
                .when(driverLocationRepository).insert(eq(driver.driverId()), any(), any(), any());

        var response = post(driver.token(), "{\"lat\":10,\"lng\":106}");

        assertStorageError(response);
        assertEquals(0, historyCount(driver.driverId()));
        assertEquals(before, snapshot(driver.driverId()));
    }

    @Test
    void swaggerDocumentsMinimalNumericRequestAndLocationResponse() {
        JsonNode api = rest.getForObject("/v3/api-docs", JsonNode.class);
        assertNotNull(api);
        JsonNode post = api.path("paths").path("/api/driver/location").path("post");
        assertFalse(post.isMissingNode());
        assertFalse(post.has("parameters"));
        for (String code : Set.of("201", "400", "401", "403", "404", "500")) {
            assertTrue(post.path("responses").has(code), code);
        }
        var request = api.path("components").path("schemas").path("DriverLocationRequest");
        assertEquals(2, request.path("properties").size());
        assertEquals("number", request.path("properties").path("lat").path("type").asText());
        assertEquals("number", request.path("properties").path("lng").path("type").asText());
        assertFalse(request.path("additionalProperties").asBoolean(true));
        assertEquals(3, api.path("components").path("schemas").path("DriverLocationResponse")
                .path("properties").size());
    }

    private void assertStorageError(org.springframework.http.ResponseEntity<JsonNode> response) {
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(500, body.path("status").asInt());
        assertEquals("Không thể lưu vị trí. Vui lòng thử lại", body.path("message").asText());
        assertTrue(body.path("fieldErrors").isObject());
        assertFalse(body.has("trace"));
    }

    private org.springframework.http.ResponseEntity<JsonNode> post(String token, String body) {
        return rest.exchange("/api/driver/location", HttpMethod.POST,
                new HttpEntity<>(body, headers(token)), JsonNode.class);
    }

    private HttpHeaders headers(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private Map<String, Object> snapshot(long driverId) {
        return jdbc.queryForMap("SELECT * FROM drivers WHERE id = :driverId", Map.of("driverId", driverId));
    }

    private long historyCount(long driverId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM driver_locations WHERE driver_id = :driverId",
                Map.of("driverId", driverId), Long.class);
    }

    private long createUser() {
        return users.insert("Location Test", String.format("095%07d", sequence.incrementAndGet()), null,
                "test-only-hash", "DRIVER");
    }

    private DriverFixture createDriver(String status, boolean online) {
        long userId = createUser();
        long driverId = 1000 + userId;
        jdbc.update("""
                INSERT INTO drivers (id, user_id, vehicle_type, status, is_online,
                                     current_lat, current_lng, last_location_at)
                VALUES (:id, :userId, 'BICYCLE', :status, :online, 1, 2, '2000-01-01 00:00:00')
                """, Map.of("id", driverId, "userId", userId, "status", status, "online", online));
        return new DriverFixture(userId, driverId, jwtService.generateToken(userId, "DRIVER"));
    }

    private record DriverFixture(long userId, long driverId, String token) {
    }
}
