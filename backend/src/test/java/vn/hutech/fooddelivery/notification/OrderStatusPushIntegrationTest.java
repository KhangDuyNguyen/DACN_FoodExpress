package vn.hutech.fooddelivery.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.order.event.OrderStatusChangedEvent;
import vn.hutech.fooddelivery.security.JwtService;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "app.jwt.secret=notification_test_signing_material_for_local_test_only",
        "app.assignment.scheduling.enabled=false"
})
@Testcontainers
class OrderStatusPushIntegrationTest {
    private static final long OWNER = 746001;
    private static final long OTHER = 746002;
    private static final long DRIVER = 746003;
    private static final long MERCHANT = 746004;
    private static final long ORDER = 746010;

    @Container @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired TestRestTemplate rest;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DeviceTokenRepository tokens;
    @MockitoBean FirebaseCustomerPush push;

    @BeforeEach
    void fixture() {
        jdbc.update("DELETE FROM device_tokens WHERE user_id IN (?, ?)", OWNER, OTHER);
        addUser(OWNER, "CUSTOMER");
        addUser(OTHER, "CUSTOMER");
        addUser(DRIVER, "DRIVER");
    }

    @Test
    void registersOnlyForTheAuthenticatedCustomerAndRebindsTheSameDevice() {
        assertEquals(HttpStatus.UNAUTHORIZED, rest.postForEntity("/api/devices/token",
                Map.of("token", "fixture-device"), Void.class).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, device(HttpMethod.POST, DRIVER, "DRIVER", "fixture-device"));
        assertEquals(HttpStatus.NO_CONTENT, device(HttpMethod.POST, OWNER, "CUSTOMER", "fixture-device"));
        assertEquals(java.util.List.of("fixture-device"), tokens.findActiveCustomerTokens(OWNER));
        assertEquals(HttpStatus.NO_CONTENT, device(HttpMethod.POST, OTHER, "CUSTOMER", "fixture-device"));
        assertEquals(java.util.List.of(), tokens.findActiveCustomerTokens(OWNER));
        assertEquals(HttpStatus.NO_CONTENT, device(HttpMethod.DELETE, OWNER, "CUSTOMER", "fixture-device"));
        assertEquals(java.util.List.of("fixture-device"), tokens.findActiveCustomerTokens(OTHER));
        assertEquals(HttpStatus.NO_CONTENT, device(HttpMethod.DELETE, OTHER, "CUSTOMER", "fixture-device"));
        assertEquals(java.util.List.of(), tokens.findActiveCustomerTokens(OTHER));
    }

    @Test
    void onlyACommittedTransitionPushesToTheOwnerAndFailureDoesNotRollBack() throws Exception {
        addUser(MERCHANT, "MERCHANT");
        jdbc.update("INSERT INTO merchants (id, owner_user_id, business_name) VALUES (?, ?, 'Push fixture')", MERCHANT, MERCHANT);
        jdbc.update("""
                INSERT INTO restaurants (id, merchant_id, name, address, lat, lng)
                VALUES (?, ?, 'Push fixture', 'Test pickup', 10.76, 106.66)
                """, MERCHANT, MERCHANT);
        jdbc.update("""
                INSERT INTO orders (id, code, customer_id, restaurant_id, status, receiver_name, receiver_phone,
                    delivery_address, delivery_lat, delivery_lng, subtotal, total_amount)
                VALUES (?, 'PUSH-FIXTURE', ?, ?, 'READY_FOR_PICKUP', 'Test receiver', '0919000001',
                    'Test delivery', 10.76, 106.66, 50000, 50000)
                """, ORDER, OWNER, MERCHANT);
        tokens.registerAndroid(OWNER, "owner-fixture-device");
        tokens.registerAndroid(OTHER, "other-fixture-device");
        when(push.enabled()).thenReturn(true);
        doThrow(new IOException("fixture sender unavailable")).when(push).sendOrderUpdate("owner-fixture-device");
        var tx = new TransactionTemplate(transactions);
        var event = new OrderStatusChangedEvent(ORDER, MERCHANT, OWNER, "READY_FOR_PICKUP", "DELIVERING", DRIVER);
        tx.executeWithoutResult(status -> {
            jdbc.update("UPDATE orders SET status = 'DELIVERING' WHERE id = ?", ORDER);
            events.publishEvent(event);
            status.setRollbackOnly();
            verifyNoInteractions(push);
        });
        assertEquals("READY_FOR_PICKUP", storedOrderStatus());
        tx.executeWithoutResult(status -> events.publishEvent(
                new OrderStatusChangedEvent(ORDER, MERCHANT, OWNER, "READY_FOR_PICKUP", "READY_FOR_PICKUP", DRIVER)));
        verify(push, after(200).never()).sendOrderUpdate("owner-fixture-device");
        tx.executeWithoutResult(status -> {
            jdbc.update("UPDATE orders SET status = 'DELIVERING' WHERE id = ?", ORDER);
            events.publishEvent(event);
        });
        verify(push, timeout(2500)).sendOrderUpdate("owner-fixture-device");
        verify(push, after(200).never()).sendOrderUpdate("other-fixture-device");
        assertEquals("DELIVERING", storedOrderStatus());
    }

    private String storedOrderStatus() {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, ORDER);
    }

    private void addUser(long id, String role) {
        jdbc.update("""
                INSERT IGNORE INTO users (id, full_name, phone, password_hash, role)
                VALUES (?, 'Push fixture', ?, 'fixture-only', ?)
                """, id, "0919" + id, role);
    }

    private HttpStatus device(HttpMethod method, long id, String role, String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(jwt.generateToken(id, role));
        return HttpStatus.valueOf(rest.exchange("/api/devices/token", method,
                new HttpEntity<>(Map.of("token", token), headers), Void.class).getStatusCode().value());
    }
}
