package vn.hutech.fooddelivery.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.driver.DriverRepository;
import vn.hutech.fooddelivery.security.JwtService;
import vn.hutech.fooddelivery.user.UserRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;

/** Test A2 cua An: HTTP, BCrypt, JWT va MySQL that trong container rieng. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "debug=false", "app.admin.phone=", "app.admin.password=",
        "app.jwt.secret=register_a2_http_test_secret_at_least_32_chars"
})
@Testcontainers(disabledWithoutDocker = true)
class RegisterA2IntegrationTest {

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
    PasswordEncoder encoder;
    @Autowired
    JwtService jwt;
    @MockitoSpyBean
    DriverRepository drivers;

    @ParameterizedTest
    @MethodSource("driverVehicles")
    void registerCreatesLinkedPendingOfflineDriverWithoutGps(String type, String plate, String expectedPlate) {
        var request = account("DRIVER");
        request.put("vehicleType", type);
        request.put("licensePlate", plate);
        var result = rest.postForEntity("/api/auth/register", request, JsonNode.class);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        var auth = result.getBody();
        assertNotNull(auth);
        long userId = auth.path("user").path("id").asLong();
        var user = users.findById(userId).orElseThrow();
        assertEquals(request.get("phone"), user.phone());
        assertEquals("DRIVER", user.role());
        assertEquals("ACTIVE", user.status());
        assertNull(user.email());
        assertNotEquals(request.get("password"), user.passwordHash());
        assertTrue(encoder.matches((String) request.get("password"), user.passwordHash()));
        assertEquals(userId, jwt.parse(auth.path("token").asText()).id());
        assertEquals("DRIVER", jwt.parse(auth.path("token").asText()).role());
        assertFalse(auth.path("user").has("passwordHash"));
        assertFalse(auth.path("user").has("password"));

        var rows = jdbc.queryForList("SELECT * FROM drivers WHERE user_id = :userId", Map.of("userId", userId));
        assertEquals(1, rows.size());
        var driver = rows.getFirst();
        assertEquals(type, driver.get("vehicle_type"));
        assertEquals(expectedPlate, driver.get("license_plate"));
        assertEquals("PENDING", driver.get("status"));
        assertEquals(false, driver.get("is_online"));
        assertNull(driver.get("current_lat"));
        assertNull(driver.get("current_lng"));
        assertNull(driver.get("last_location_at"));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM driver_locations WHERE driver_id = :id",
                Map.of("id", driver.get("id")), Long.class));

        var headers = new HttpHeaders();
        headers.setBearerAuth(auth.path("token").asText());
        var profile = rest.exchange("/api/driver/profile", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        assertEquals(HttpStatus.OK, profile.getStatusCode());
        assertNotNull(profile.getBody());
        assertEquals("PENDING", profile.getBody().path("status").asText());
        assertEquals(type, profile.getBody().path("vehicleType").asText());
    }

    static Stream<Arguments> driverVehicles() {
        return Stream.of(Arguments.of("MOTORBIKE", " 59A1-12345 ", "59A1-12345"),
                Arguments.of("CAR", "51A-12345", "51A-12345"), Arguments.of("BICYCLE", null, null),
                Arguments.of("BICYCLE", "", null), Arguments.of("BICYCLE", "   ", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MERCHANT"})
    void existingRolesRegisterWithOldPayloadAndDoNotCreateDriver(String role) {
        var result = rest.postForEntity("/api/auth/register", account(role), JsonNode.class);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        var auth = result.getBody();
        assertNotNull(auth);
        long userId = auth.path("user").path("id").asLong();
        assertEquals(role, auth.path("user").path("role").asText());
        assertEquals(role, jwt.parse(auth.path("token").asText()).role());
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM drivers WHERE user_id = :id",
                Map.of("id", userId), Long.class));
    }

    @Test
    void duplicatePhoneReturns409WithoutExtraUserOrDriver() {
        var request = bicycle();
        assertEquals(HttpStatus.CREATED, rest.postForEntity("/api/auth/register", request, JsonNode.class).getStatusCode());
        long usersBefore = count("SELECT COUNT(*) FROM users");
        long driversBefore = count("SELECT COUNT(*) FROM drivers");

        var duplicate = rest.postForEntity("/api/auth/register", request, JsonNode.class);

        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
        assertNotNull(duplicate.getBody());
        assertEquals(409, duplicate.getBody().path("status").asInt());
        assertEquals(usersBefore, count("SELECT COUNT(*) FROM users"));
        assertEquals(driversBefore, count("SELECT COUNT(*) FROM drivers"));
    }

    @Test
    void duplicateEmailRemainsCaseInsensitiveAndDoesNotCreateOrphanUser() {
        var first = bicycle();
        String email = "a2-" + first.get("phone") + "@example.com";
        first.put("email", email);
        assertEquals(HttpStatus.CREATED, rest.postForEntity("/api/auth/register", first, JsonNode.class).getStatusCode());
        var duplicate = bicycle();
        duplicate.put("email", email.toUpperCase());

        assertEquals(HttpStatus.CONFLICT, rest.postForEntity("/api/auth/register", duplicate, JsonNode.class).getStatusCode());
        assertTrue(users.findByPhone((String) duplicate.get("phone")).isEmpty());
    }

    @Test
    void sqlFailureCreatingDriverRollsBackInsertedUserAndEmail() {
        var request = bicycle();
        String email = "rollback-" + request.get("phone") + "@example.com";
        request.put("email", email);
        long usersBefore = count("SELECT COUNT(*) FROM users");
        long driversBefore = count("SELECT COUNT(*) FROM drivers");
        // Loi ENUM that tren MySQL, sau khi AuthService da INSERT users; khong doi schema.
        doAnswer(invocation -> {
            jdbc.update("""
                    INSERT INTO drivers (user_id, vehicle_type, status, is_online)
                    VALUES (:userId, 'INVALID_VEHICLE', 'PENDING', 0)
                    """, Map.of("userId", invocation.<Long>getArgument(0)));
            return null;
        }).when(drivers).insert(anyLong(), anyString(), nullable(String.class));

        var result = rest.postForEntity("/api/auth/register", request, JsonNode.class);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
        assertTrue(users.findByPhone((String) request.get("phone")).isEmpty());
        assertTrue(users.findByEmail(email).isEmpty());
        assertEquals(usersBefore, count("SELECT COUNT(*) FROM users"));
        assertEquals(driversBefore, count("SELECT COUNT(*) FROM drivers"));
        assertNotNull(result.getBody());
        assertFalse(result.getBody().has("token"));
        assertFalse(result.getBody().has("trace"));
    }

    @Test
    void bcryptAndLoginByPhoneOrEmailRemainCompatibleWithDriverRegistration() {
        var request = bicycle();
        String email = "login-" + request.get("phone") + "@example.com";
        request.put("email", email.toUpperCase());
        var registered = rest.postForEntity("/api/auth/register", request, JsonNode.class);
        assertEquals(HttpStatus.CREATED, registered.getStatusCode());
        var user = users.findByPhone((String) request.get("phone")).orElseThrow();
        assertEquals(email, user.email());
        assertTrue(encoder.matches((String) request.get("password"), user.passwordHash()));

        for (String identifier : new String[]{(String) request.get("phone"), email.toUpperCase()}) {
            var result = rest.postForEntity("/api/auth/login",
                    Map.of("identifier", identifier, "password", request.get("password")), JsonNode.class);
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertEquals(user.id(), jwt.parse(result.getBody().path("token").asText()).id());
            assertFalse(result.getBody().path("user").has("passwordHash"));
        }
    }

    @Test
    void publicAdminRegistrationIsRejectedWithoutDatabaseWrites() {
        var request = account("ADMIN");
        long usersBefore = count("SELECT COUNT(*) FROM users");
        var result = rest.postForEntity("/api/auth/register", request, JsonNode.class);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        assertEquals(usersBefore, count("SELECT COUNT(*) FROM users"));
        assertTrue(users.findByPhone((String) request.get("phone")).isEmpty());
    }

    @Test
    void swaggerAddsOnlyVehicleFieldsAndKeepsExistingAuthResponseContract() {
        var api = rest.getForObject("/v3/api-docs", JsonNode.class);
        assertNotNull(api);
        var schemas = api.path("components").path("schemas");
        var request = schemas.path("RegisterRequest").path("properties");
        assertEquals(7, request.size());
        assertTrue(request.has("vehicleType"));
        assertTrue(request.has("licensePlate"));
        assertFalse(request.has("confirmPassword"));
        assertFalse(request.has("driverVehicleTypeValid"));
        assertFalse(request.has("driverLicensePlateValid"));
        assertEquals(2, schemas.path("AuthResponse").path("properties").size());
        assertEquals(8, schemas.path("UserResponse").path("properties").size());
    }

    private Map<String, Object> bicycle() {
        var request = account("DRIVER");
        request.put("vehicleType", "BICYCLE");
        return request;
    }

    private Map<String, Object> account(String role) {
        var request = new HashMap<String, Object>();
        request.put("fullName", "Register A2 Test");
        request.put("phone", String.format("098%07d", sequence.incrementAndGet()));
        request.put("password", "test-password-123");
        request.put("role", role);
        return request;
    }

    private long count(String constantSql) {
        return jdbc.queryForObject(constantSql, Map.of(), Long.class);
    }
}
