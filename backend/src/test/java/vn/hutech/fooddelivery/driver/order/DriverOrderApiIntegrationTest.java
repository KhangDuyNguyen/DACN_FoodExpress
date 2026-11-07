package vn.hutech.fooddelivery.driver.order;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.security.JwtProperties;
import vn.hutech.fooddelivery.security.JwtService;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

/** HTTP/JWT that va MySQL 8.4/Flyway trong container rieng; khong ket noi database thu cong cua An. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "debug=false", "app.admin.phone=", "app.admin.password=",
        "app.jwt.secret=driver_current_order_test_secret_at_least_32_chars",
        "app.assignment.scheduling.enabled=false",
        "logging.level.org.springframework.jdbc=INFO",
        "logging.level.org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration=ERROR"
})
@Import(DriverOrderApiIntegrationTest.Services.class)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DriverOrderApiIntegrationTest {
    private static final String URL = "/api/driver/orders/current";
    private static final String DATABASE = "driver_current_order_test";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 12, 0);
    private static final Set<String> ORDER_FIELDS = Set.of("orderId", "orderCode", "status", "createdAt", "updatedAt",
            "restaurantId", "restaurantName", "restaurantAddress", "restaurantPhone", "pickupLat", "pickupLng",
            "receiverName", "receiverPhone", "deliveryAddress", "deliveryLat", "deliveryLng", "note");

    @Container @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4").withDatabaseName(DATABASE);

    @Autowired TestRestTemplate rest;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired JwtService jwt;
    @MockitoSpyBean DriverOrderRepository orders;

    @TestConfiguration(proxyBeanMethods = false)
    static class Services {
        @Bean @Primary
        DriverOrderService fixedClockDriverOrderService(DriverOrderRepository orders) {
            var zone = ZoneId.of("Asia/Ho_Chi_Minh");
            return new DriverOrderService(orders, Clock.fixed(NOW.atZone(zone).toInstant(), zone));
        }
    }

    @BeforeEach
    void resetOnlyDisposableContainerFixtures() throws Exception {
        assertTrue(mysql.isRunning());
        try (var connection = dataSource.getConnection()) {
            assertEquals(DATABASE, connection.getCatalog(), "Refuse fixture writes outside the dedicated test database");
            assertTrue(connection.getMetaData().getURL().contains(":" + mysql.getMappedPort(3306) + "/" + DATABASE));
        }
        jdbc.update("DELETE FROM driver_locations", Map.of());
        jdbc.update("DELETE FROM order_assignments", Map.of());
        jdbc.update("DELETE FROM orders", Map.of());
        jdbc.update("DELETE FROM drivers", Map.of());
        jdbc.update("DELETE FROM restaurants WHERE id = 900001", Map.of());
        jdbc.update("DELETE FROM merchants WHERE id = 900001", Map.of());
        jdbc.update("DELETE FROM users WHERE id >= 900000 OR id BETWEEN 42 AND 47", Map.of());
        user(900001, "CUSTOMER"); user(900002, "MERCHANT");
        user(42, "DRIVER"); user(43, "DRIVER"); user(44, "CUSTOMER");
        user(45, "MERCHANT"); user(46, "ADMIN"); user(47, "DRIVER");
        jdbc.update("""
                INSERT INTO merchants (id, owner_user_id, business_name)
                VALUES (900001, 900002, 'Current Order Fixture Merchant')
                """, Map.of());
        jdbc.update("""
                INSERT INTO restaurants (id, merchant_id, name, address, phone, lat, lng)
                VALUES (900001, 900001, 'Fixture Restaurant', 'Fixture Pickup Address', '0280000000', 10.7626221, 106.6601727)
                """, Map.of());
        jdbc.update("""
                INSERT INTO drivers (id, user_id, status, is_online, current_lat, current_lng, last_location_at)
                VALUES (7, 42, 'APPROVED', TRUE, 10.7626221, 106.6601727, :now),
                       (8, 43, 'APPROVED', TRUE, 10.8, 106.7, :now)
                """, Map.of("now", Timestamp.valueOf(NOW)));
    }

    @Test
    void noCurrentOrderReturnsExplicitNullAndServerTime() {
        var response = get(42);
        assertEquals(200, response.getStatusCode().value());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(Set.of("currentOrder", "serverTime"), fields(body));
        assertTrue(body.has("currentOrder") && body.get("currentOrder").isNull());
        assertEquals(NOW, LocalDateTime.parse(body.path("serverTime").asText()));
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_PAYMENT", "PLACED", "ACCEPTED", "PREPARING", "READY_FOR_PICKUP", "PICKED_UP", "DELIVERING"})
    void activeStatesMatchAssignmentIncludingAssignedPlacedAndPendingPayment(String status) {
        order(301, 7L, status);
        var current = current(get(42));
        assertEquals(301, current.path("orderId").asLong());
        assertEquals(status, current.path("status").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DELIVERED", "CANCELLED", "REJECTED"})
    void terminalOrderIsNotCurrentEvenWhenItsAcceptedOfferRemains(String status) {
        order(301, 7L, status);
        acceptedOffer(401, 301, 7);
        var response = get(42);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().get("currentOrder").isNull());
    }

    @Test
    void unassignedPlacedOrderIsNotCurrent() {
        order(301, null, "PLACED");
        assertTrue(get(42).getBody().get("currentOrder").isNull());
    }

    @Test
    void exactDtoUsesOrderSnapshotsAndPreservesDecimalCoordinates() {
        order(301, 7L, "PLACED");
        jdbc.update("UPDATE users SET full_name = 'Different account name', phone = '0900999888' WHERE id = 900001", Map.of());
        var current = current(get(42));
        assertEquals(ORDER_FIELDS, fields(current));
        assertEquals("CURRENT-301", current.path("orderCode").asText());
        assertEquals(NOW.minusMinutes(10), LocalDateTime.parse(current.path("createdAt").asText()));
        assertEquals(NOW.minusMinutes(5), LocalDateTime.parse(current.path("updatedAt").asText()));
        assertEquals(900001, current.path("restaurantId").asLong());
        assertEquals("Fixture Restaurant", current.path("restaurantName").asText());
        assertEquals("Fixture Pickup Address", current.path("restaurantAddress").asText());
        assertEquals("0280000000", current.path("restaurantPhone").asText());
        assertEquals(0, new BigDecimal("10.7626221").compareTo(current.path("pickupLat").decimalValue()));
        assertEquals(0, new BigDecimal("106.6601727").compareTo(current.path("pickupLng").decimalValue()));
        assertEquals("Fixture Receiver Snapshot", current.path("receiverName").asText());
        assertEquals("0900000999", current.path("receiverPhone").asText());
        assertEquals("Fixture Delivery Snapshot", current.path("deliveryAddress").asText());
        assertEquals(0, new BigDecimal("10.7712345").compareTo(current.path("deliveryLat").decimalValue()));
        assertEquals(0, new BigDecimal("106.6812345").compareTo(current.path("deliveryLng").decimalValue()));
        assertEquals("Fixture delivery note", current.path("note").asText());
    }

    @Test
    void nullablePhoneAndNoteAreExplicitJsonNullRatherThanFabricatedData() {
        order(301, 7L, "PLACED");
        jdbc.update("UPDATE restaurants SET phone = NULL WHERE id = 900001", Map.of());
        jdbc.update("UPDATE orders SET note = NULL WHERE id = 301", Map.of());
        var current = current(get(42));
        assertEquals(ORDER_FIELDS, fields(current));
        assertTrue(current.get("restaurantPhone").isNull());
        assertTrue(current.get("note").isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"offline", "missingGps", "staleGps", "futureGps"})
    void assignedOrderRemainsReadableWithoutOfferEligibility(String condition) {
        order(301, 7L, "PLACED");
        String update = switch (condition) {
            case "offline" -> "UPDATE drivers SET is_online = FALSE WHERE id = 7";
            case "missingGps" -> "UPDATE drivers SET current_lat = NULL, current_lng = NULL, last_location_at = NULL WHERE id = 7";
            case "staleGps" -> "UPDATE drivers SET last_location_at = :at WHERE id = 7";
            default -> "UPDATE drivers SET last_location_at = :at WHERE id = 7";
        };
        jdbc.update(update, Map.of("at", Timestamp.valueOf("staleGps".equals(condition) ? NOW.minusDays(1) : NOW.plusDays(1))));
        assertEquals(301, current(get(42)).path("orderId").asLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "REJECTED", "SUSPENDED"})
    void driverApprovalDoesNotHideAnAlreadyAssignedOrder(String status) {
        order(301, 7L, "PLACED");
        jdbc.update("UPDATE drivers SET status = :status WHERE id = 7", Map.of("status", status));
        assertEquals(301, current(get(42)).path("orderId").asLong());
    }

    @Test
    void closedOrHiddenRestaurantDoesNotMakeAssignedOrderDisappear() {
        order(301, 7L, "PLACED");
        jdbc.update("UPDATE restaurants SET status = 'HIDDEN', open_status = 'CLOSED' WHERE id = 900001", Map.of());
        assertEquals(301, current(get(42)).path("orderId").asLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "invalid", "expired"})
    void invalidAuthenticationIsUnauthorized(String condition) {
        var headers = new HttpHeaders();
        if ("invalid".equals(condition)) headers.setBearerAuth("invalid.jwt.token");
        if ("expired".equals(condition)) {
            var expired = new JwtService(new JwtProperties("driver_current_order_test_secret_at_least_32_chars", -1));
            headers.setBearerAuth(expired.generateToken(42, "DRIVER"));
        }
        assertError(rest.exchange(URL, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class), 401);
    }

    @ParameterizedTest
    @ValueSource(longs = {44, 45, 46})
    void wrongDatabaseRoleIsForbiddenDespiteDriverClaim(long userId) {
        assertError(get(userId), 403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOCKED", "DELETED"})
    void blockedAccountCannotReadPrivateOrderContact(String status) {
        order(301, 7L, "PLACED");
        jdbc.update("UPDATE users SET status = :status WHERE id = 42", Map.of("status", status));
        assertError(get(42), 401);
    }

    @Test
    void missingDriverProfileIs404RatherThanNullOrder() {
        assertError(get(47), 404);
    }

    @Test
    void clientQueryAndBodyCannotSelectAnotherDriversOrder() {
        order(301, 7L, "PLACED"); order(302, 8L, "DELIVERING");
        var response = rest.exchange(URL + "?driverId=8&userId=43", HttpMethod.GET,
                new HttpEntity<>("{\"driverId\":8,\"userId\":43}", headers(42)), JsonNode.class);
        assertEquals(301, current(response).path("orderId").asLong());
        assertEquals(302, current(get(43)).path("orderId").asLong());
    }

    @Test
    void anotherDriversOrderIsNotVisibleToAnUnassignedDriver() {
        order(301, 7L, "PLACED");
        assertTrue(get(43).getBody().get("currentOrder").isNull());
    }

    @Test
    void realAcceptThenFreshJwtRequestRecoversPlacedOrderFromDatabase() {
        order(301, null, "PLACED");
        var at = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        jdbc.update("UPDATE drivers SET last_location_at = :at WHERE id = 7", Map.of("at", Timestamp.valueOf(at)));
        jdbc.update("""
                INSERT INTO order_assignments (id, order_id, driver_id, status, distance_km, offered_at, expires_at)
                VALUES (401, 301, 7, 'OFFERED', 1.23, :at, :expires)
                """, Map.of("at", Timestamp.valueOf(at), "expires", Timestamp.valueOf(at.plusSeconds(30))));
        var accepted = rest.exchange("/api/driver/offers/401/accept", HttpMethod.POST,
                new HttpEntity<>(headers(42)), JsonNode.class);
        assertEquals(200, accepted.getStatusCode().value());
        assertEquals("ACCEPTED", accepted.getBody().path("status").asText());
        assertEquals("PLACED", current(get(42)).path("status").asText());
        assertEquals(301, current(get(42)).path("orderId").asLong());
        var offers = rest.exchange("/api/driver/offers", HttpMethod.GET, new HttpEntity<>(headers(42)), JsonNode.class);
        assertTrue(offers.getBody().path("offers").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void multipleActiveOrdersAre409EvenWhenMixedWithTerminalOrders(int count) {
        for (int index = 0; index < count; index++) order(301 + index, 7L, index == 0 ? "PLACED" : "PENDING_PAYMENT");
        order(310, 7L, "DELIVERED");
        assertError(get(42), 409);
    }

    @Test
    void terminalOrdersDoNotCauseFalseDuplicateConflict() {
        order(301, 7L, "PLACED"); order(302, 7L, "CANCELLED"); order(303, 7L, "DELIVERED"); order(304, 7L, "REJECTED");
        assertEquals(301, current(get(42)).path("orderId").asLong());
    }

    @Test
    void getLeavesAllRelatedBusinessTablesUnchanged() {
        order(301, 7L, "PLACED"); acceptedOffer(401, 301, 7);
        jdbc.update("INSERT INTO driver_locations (driver_id, lat, lng) VALUES (7, 10, 106)", Map.of());
        jdbc.update("INSERT INTO order_status_history (order_id, status, changed_by) VALUES (301, 'PLACED', 42)", Map.of());
        var before = snapshot();
        for (int index = 0; index < 3; index++) assertEquals(301, current(get(42)).path("orderId").asLong());
        assertEquals(before, snapshot());
    }

    @Test
    void httpReadRunsInReadOnlyReadCommittedTransaction() {
        var checked = new AtomicBoolean();
        doAnswer(invocation -> {
            checked.set(TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                    && Integer.valueOf(Connection.TRANSACTION_READ_COMMITTED)
                    .equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()));
            return invocation.callRealMethod();
        }).when(orders).findCurrentOrders(7);
        assertEquals(200, get(42).getStatusCode().value());
        assertTrue(checked.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"driverLookup", "orderLookup"})
    void actualMysqlQueryFailureReturnsApiErrorNotSuccessWithNull(String stage) {
        if ("driverLookup".equals(stage)) {
            doAnswer(invocation -> {
                jdbc.queryForObject("SELECT current_order_missing_fixture_column FROM drivers", Map.of(), Long.class);
                return invocation.callRealMethod();
            }).when(orders).findDriverId(anyLong());
        } else {
            doAnswer(invocation -> {
                jdbc.queryForObject("SELECT current_order_missing_fixture_column FROM orders", Map.of(), Long.class);
                return invocation.callRealMethod();
            }).when(orders).findCurrentOrders(anyLong());
        }
        assertError(get(42), 500);
    }

    @Test
    void uncommittedAssignmentIsNotVisibleAndCommittedAssignmentIsRecovered() throws Exception {
        order(301, null, "PLACED");
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var update = connection.prepareStatement("UPDATE orders SET driver_id = 7 WHERE id = 301")) {
                    assertEquals(1, update.executeUpdate());
                }
                assertTrue(get(42).getBody().get("currentOrder").isNull());
                connection.commit();
            } finally { connection.rollback(); }
        }
        assertEquals(301, current(get(42)).path("orderId").asLong());
    }

    @Test
    void swaggerDocumentsCurrentOrderWithoutClientIdentityParameters() throws Exception {
        var response = rest.getForEntity("/v3/api-docs", JsonNode.class);
        assertEquals(200, response.getStatusCode().value());
        var operation = response.getBody().path("paths").path(URL).path("get");
        assertFalse(operation.isMissingNode());
        assertEquals(0, operation.path("parameters").size());
        assertFalse(operation.has("requestBody"));
        for (String status : List.of("200", "401", "403", "404", "409", "500")) {
            assertTrue(operation.path("responses").has(status));
        }
        var schemas = response.getBody().path("components").path("schemas");
        Files.writeString(Path.of("target/driver-order-4b1-openapi.json"), response.getBody().toPrettyString());
        assertEquals(ORDER_FIELDS, fields(schemas.path("DriverCurrentOrder").path("properties")));
        var required = new HashSet<String>();
        schemas.path("DriverCurrentOrder").path("required").forEach(field -> required.add(field.asText()));
        assertEquals(ORDER_FIELDS, required);
        assertTrue(schemas.path("DriverCurrentOrder").path("type").toString().contains("\"null\""));
        for (String field : List.of("restaurantPhone", "note")) {
            var property = schemas.path("DriverCurrentOrder").path("properties").path(field);
            // OpenAPI 3.0 uses nullable; 3.1 represents null in the JSON Schema type array.
            boolean nullable = property.path("nullable").asBoolean();
            for (var type : property.path("type")) nullable |= "null".equals(type.asText());
            assertTrue(nullable, () -> field + " schema: " + property);
        }
    }

    private HttpHeaders headers(long userId) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(jwt.generateToken(userId, "DRIVER"));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<JsonNode> get(long userId) {
        return rest.exchange(URL, HttpMethod.GET, new HttpEntity<>(headers(userId)), JsonNode.class);
    }

    private JsonNode current(ResponseEntity<JsonNode> response) {
        assertEquals(200, response.getStatusCode().value(), () -> String.valueOf(response.getBody()));
        assertNotNull(response.getBody());
        var current = response.getBody().get("currentOrder");
        assertNotNull(current);
        assertTrue(current.isObject());
        return current;
    }

    private void assertError(ResponseEntity<JsonNode> response, int status) {
        assertEquals(status, response.getStatusCode().value());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(status, body.path("status").asInt());
        assertTrue(body.path("fieldErrors").isObject());
        assertTrue(body.has("timestamp"));
        assertFalse(body.has("currentOrder"));
        assertFalse(body.has("trace"));
        assertFalse(body.toString().contains("current_order_missing_fixture_column"));
        assertFalse(body.toString().contains("0900000999"));
    }

    private Set<String> fields(JsonNode node) {
        var result = new HashSet<String>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private void user(long id, String role) {
        jdbc.update("""
                INSERT INTO users (id, full_name, phone, password_hash, role)
                VALUES (:id, 'Current Order Fixture User', :phone, 'test-only-hash', :role)
                """, Map.of("id", id, "phone", "09" + id, "role", role));
    }

    private void order(long id, Long driverId, String status) {
        jdbc.update("""
                INSERT INTO orders (id, code, customer_id, restaurant_id, driver_id, status,
                                    receiver_name, receiver_phone, delivery_address, delivery_lat, delivery_lng,
                                    note, subtotal, total_amount, created_at, updated_at)
                VALUES (:id, :code, 900001, 900001, :driverId, :status, 'Fixture Receiver Snapshot', '0900000999',
                        'Fixture Delivery Snapshot', 10.7712345, 106.6812345, 'Fixture delivery note', 50000, 50000, :created, :updated)
                """, new MapSqlParameterSource().addValue("id", id).addValue("code", "CURRENT-" + id)
                .addValue("driverId", driverId).addValue("status", status)
                .addValue("created", Timestamp.valueOf(NOW.minusMinutes(10)))
                .addValue("updated", Timestamp.valueOf(NOW.minusMinutes(5))));
    }

    private void acceptedOffer(long id, long orderId, long driverId) {
        jdbc.update("""
                INSERT INTO order_assignments (id, order_id, driver_id, status, distance_km, responded_at)
                VALUES (:id, :orderId, :driverId, 'ACCEPTED', 1.23, :at)
                """, Map.of("id", id, "orderId", orderId, "driverId", driverId, "at", Timestamp.valueOf(NOW)));
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        var result = new HashMap<String, List<Map<String, Object>>>();
        var queries = Map.of(
                "users", "SELECT * FROM users ORDER BY id",
                "drivers", "SELECT * FROM drivers ORDER BY id",
                "orders", "SELECT * FROM orders ORDER BY id",
                "order_assignments", "SELECT * FROM order_assignments ORDER BY id",
                "restaurants", "SELECT * FROM restaurants ORDER BY id",
                "driver_locations", "SELECT * FROM driver_locations ORDER BY id",
                "order_status_history", "SELECT * FROM order_status_history ORDER BY id");
        for (var query : queries.entrySet()) {
            result.put(query.getKey(), jdbc.queryForList(query.getValue(), Map.of()));
        }
        return result;
    }
}
