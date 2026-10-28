package vn.hutech.fooddelivery.driver;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HTTP that, JWT that va MySQL rieng; khong dung tai khoan/database dang demo. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=",
        "app.jwt.secret=driver_profile_http_test_secret_at_least_32_chars",
        "app.admin.phone=",
        "app.admin.password=",
        "debug=false"
})
@Testcontainers(disabledWithoutDocker = true)
class DriverProfileIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    TestRestTemplate rest;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @Test
    void pendingDriverCanPutThenGetWhileForbiddenFieldsAndApprovedEditsLeaveDatabaseUnchanged() {
        var registration = rest.postForEntity("/api/auth/register", Map.of(
                "fullName", "Driver Integration", "phone", "0944444444",
                "password", "test-password-123", "role", "DRIVER"), JsonNode.class);
        assertEquals(HttpStatus.CREATED, registration.getStatusCode());
        JsonNode auth = registration.getBody();
        assertNotNull(auth);
        long userId = auth.path("user").path("id").asLong();
        jdbc.update("""
                INSERT INTO drivers (user_id, vehicle_type, license_plate)
                VALUES (:userId, 'MOTORBIKE', '59A1-12345')
                """, Map.of("userId", userId));
        var headers = new HttpHeaders();
        headers.setBearerAuth(auth.path("token").asText());
        headers.setContentType(MediaType.APPLICATION_JSON);

        var put = rest.exchange("/api/driver/profile", HttpMethod.PUT,
                new HttpEntity<>("{\"vehicleType\":\"BICYCLE\"}", headers), JsonNode.class);
        assertEquals(HttpStatus.OK, put.getStatusCode());
        JsonNode profile = put.getBody();
        assertNotNull(profile);
        assertEquals("BICYCLE", profile.path("vehicleType").asText());
        assertTrue(profile.path("licensePlate").isNull());
        assertEquals("PENDING", profile.path("status").asText());
        assertFalse(profile.path("isOnline").asBoolean());
        assertFalse(profile.has("passwordHash"));

        var get = rest.exchange("/api/driver/profile", HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class);
        assertEquals(HttpStatus.OK, get.getStatusCode());
        assertEquals(profile, get.getBody());

        var forbiddenFields = rest.exchange("/api/driver/profile", HttpMethod.PUT,
                new HttpEntity<>("{\"vehicleType\":\"CAR\",\"licensePlate\":\"51A-12345\","
                        + "\"status\":\"APPROVED\",\"isOnline\":true,\"role\":\"ADMIN\"}", headers), JsonNode.class);
        assertEquals(HttpStatus.BAD_REQUEST, forbiddenFields.getStatusCode());
        assertEquals(profile, rest.exchange("/api/driver/profile", HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class).getBody());

        jdbc.update("UPDATE drivers SET status = 'APPROVED' WHERE user_id = :userId", Map.of("userId", userId));
        var approvedEdit = rest.exchange("/api/driver/profile", HttpMethod.PUT,
                new HttpEntity<>("{\"vehicleType\":\"CAR\",\"licensePlate\":\"51A-12345\"}", headers), JsonNode.class);
        assertEquals(HttpStatus.FORBIDDEN, approvedEdit.getStatusCode());
        var after = rest.exchange("/api/driver/profile", HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class).getBody();
        assertNotNull(after);
        assertEquals("BICYCLE", after.path("vehicleType").asText());
        assertEquals("APPROVED", after.path("status").asText());
    }

    @Test
    void swaggerDocumentsPutWithOnlyVehicleFieldsAndExpectedErrors() {
        var response = rest.getForEntity("/v3/api-docs", JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode api = response.getBody();
        assertNotNull(api);
        JsonNode path = api.path("paths").path("/api/driver/profile");
        assertTrue(path.has("get"));
        assertTrue(path.has("put"));
        assertFalse(path.path("put").has("parameters"));
        for (String code : Set.of("200", "400", "401", "403", "404", "409")) {
            assertTrue(path.path("put").path("responses").has(code), code);
        }
        JsonNode requestSchema = api.path("components").path("schemas").path("UpdateDriverProfileRequest");
        assertEquals(2, requestSchema.path("properties").size());
        assertTrue(requestSchema.path("properties").has("vehicleType"));
        assertTrue(requestSchema.path("properties").has("licensePlate"));
        assertFalse(requestSchema.path("additionalProperties").asBoolean(true));

        var ui = rest.getForEntity("/swagger-ui/index.html", String.class);
        assertEquals(HttpStatus.OK, ui.getStatusCode());
        assertNotNull(ui.getBody());
        assertTrue(ui.getBody().contains("Swagger UI"));
    }
}
