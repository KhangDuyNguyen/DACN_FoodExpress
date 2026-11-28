package vn.hutech.fooddelivery.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.assignment.AssignmentService;
import vn.hutech.fooddelivery.order.OrderManagementService;
import vn.hutech.fooddelivery.order.OrderService;
import vn.hutech.fooddelivery.order.dto.ChangeOrderStatusRequest;
import vn.hutech.fooddelivery.order.dto.CreateOrderRequest;
import vn.hutech.fooddelivery.order.event.OrderPlacedEvent;
import vn.hutech.fooddelivery.security.JwtService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {"spring.config.import=", "app.fcm.source=INBOX",
        "app.assignment.scheduling.enabled=false", "app.admin.phone=", "app.admin.password=",
        "app.vnpay.tmn-code=FIXTURE", "app.vnpay.pay-url=https://example.invalid/vnpay",
        "app.vnpay.return-url=http://127.0.0.1/payment/return"})
class NotificationFcmIntegrationTest {
    private static final String VNPAY_FIXTURE_KEY = UUID.randomUUID().toString();
    private static final long DRIVER_ID = 781001;
    private static final long DRIVER_USER = 782001;
    private static final long ADMIN = 782002;
    private static final AtomicLong QUEUE_PROBES = new AtomicLong(Long.MAX_VALUE - 200);

    @Container @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        String jwt = UUID.randomUUID().toString();
        registry.add("app.jwt.secret", () -> jwt);
        registry.add("app.fcm.project-id", () -> "");
        registry.add("app.vnpay.hash-secret", () -> VNPAY_FIXTURE_KEY);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired OrderService orders;
    @Autowired OrderManagementService management;
    @Autowired AssignmentService assignments;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactions;
    @Autowired NotificationFcmPublisher publisher;
    @MockitoSpyBean DeviceTokenRepository tokens;
    @MockitoSpyBean NotificationInboxService inbox;
    @MockitoSpyBean SimpMessagingTemplate realtime;
    @MockitoBean FirebaseCustomerPush gateway;

    private long customer;
    private long other;
    private long merchant;
    private long restaurant;
    private long menuItem;

    @BeforeEach
    void fixture() throws Exception {
        customer = user("0911000001");
        other = user("0911000002");
        restaurant = jdbc.queryForObject("SELECT id FROM restaurants WHERE name=?",
                Long.class, "Cơm Nhà Demo - Chi nhánh Quận 1");
        merchant = jdbc.queryForObject("""
                SELECT m.owner_user_id FROM merchants m JOIN restaurants r ON r.merchant_id=m.id WHERE r.id=?
                """, Long.class, restaurant);
        menuItem = jdbc.queryForObject("""
                SELECT mi.id FROM menu_items mi JOIN menu_categories mc ON mi.category_id=mc.id
                WHERE mc.restaurant_id=? AND mi.name='Cơm sườn nướng'
                """, Long.class, restaurant);
        addUser(DRIVER_USER, "DRIVER");
        addUser(ADMIN, "ADMIN");
        jdbc.update("""
                INSERT INTO drivers(id,user_id,status) VALUES (?,?,'APPROVED')
                ON DUPLICATE KEY UPDATE is_online=0
                """, DRIVER_ID, DRIVER_USER);
        jdbc.update("DELETE FROM device_tokens WHERE user_id IN (?,?,?,?,?)", customer, other, merchant, DRIVER_USER, ADMIN);
        jdbc.update("DELETE FROM order_assignments WHERE driver_id=?", DRIVER_ID);
        jdbc.update("UPDATE orders SET driver_id=NULL WHERE driver_id=?", DRIVER_ID);
        when(gateway.enabled()).thenReturn(true);
        when(gateway.sendInbox(anyString(), anyLong(), anyString(), nullable(Long.class))).thenReturn(true);
    }

    @Test
    void codAndStatusUseOnlyInboxForEveryCustomerDeviceAndDoNotReplay() throws Exception {
        tokens.registerAndroid(customer, "fixture-first");
        tokens.registerAndroid(customer, "fixture-second");
        tokens.registerAndroid(merchant, "fixture-merchant");
        tokens.registerAndroid(ADMIN, "fixture-admin");
        long order = place("COD").get("id").asLong();
        long placed = notification(order, customer, "placed");
        drain();
        verify(gateway).sendInbox("fixture-first", placed, "ORDER_PLACED", null);
        verify(gateway).sendInbox("fixture-second", placed, "ORDER_PLACED", null);

        new TransactionTemplate(transactions).executeWithoutResult(tx ->
                events.publishEvent(new OrderPlacedEvent(order, restaurant, orders.getDetail(order))));
        management.changeStatusByMerchant(merchant, order, new ChangeOrderStatusRequest("PREPARING", null));
        long preparing = notification(order, customer, "preparing");
        drain();
        verify(gateway).sendInbox("fixture-first", preparing, "ORDER_PREPARING", null);
        verify(gateway).sendInbox("fixture-second", preparing, "ORDER_PREPARING", null);
        verify(gateway, times(4)).sendInbox(anyString(), anyLong(), anyString(), nullable(Long.class));
        verify(gateway, never()).sendOrderUpdate(anyString());
    }

    @Test
    void signedVnPayIpnSuccessReplayAndFailureNotifyOnlyCustomer() throws Exception {
        tokens.registerAndroid(customer, "fixture-customer");
        tokens.registerAndroid(merchant, "fixture-merchant");
        var paid = place("VNPAY");
        verify(gateway, never()).sendInbox(anyString(), anyLong(), anyString(), nullable(Long.class));
        ipn(paid, "00", "00");
        ipn(paid, "00", "02");
        var failed = place("VNPAY");
        ipn(failed, "24", "00");
        drain();
        verify(gateway).sendInbox("fixture-customer", notification(paid.get("id").asLong(), customer, "placed"),
                "ORDER_PLACED", null);
        verify(gateway).sendInbox("fixture-customer", notification(failed.get("id").asLong(), customer, "cancelled"),
                "ORDER_CANCELLED", null);
        verify(gateway, times(2)).sendInbox(anyString(), anyLong(), anyString(), nullable(Long.class));
        verify(gateway, never()).sendOrderUpdate(anyString());
    }

    @Test
    void disabledFirebaseStillWritesInboxAndDispatchesStomp() throws Exception {
        when(gateway.enabled()).thenReturn(false);
        tokens.registerAndroid(customer, "fixture-customer");
        long order = place("COD").get("id").asLong();
        long id = notification(order, customer, "placed");
        verifyStomp(id);
        verify(gateway, never()).sendInbox(anyString(), anyLong(), anyString(), nullable(Long.class));
    }

    @Test
    void rollbacksAndNonexistentNotificationNeverSend() throws Exception {
        tokens.registerAndroid(customer, "fixture-customer");
        addCart();
        long rolledBack = new TransactionTemplate(transactions).execute(tx -> {
            long id = orders.createOrder(customer, request("COD"), "127.0.0.1").order().id();
            tx.setRollbackOnly();
            return id;
        });
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE id=?", Integer.class, rolledBack));
        doThrow(new DataIntegrityViolationException("fixture second write failure"))
                .when(inbox).record(argThat(draft -> draft.userId() == merchant));
        long order = place("COD").get("id").asLong();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE event_key=?",
                Integer.class, "order:" + order + ":placed"));
        publisher.enqueue(new CommittedNotificationWriter.SavedNotification(customer, Long.MAX_VALUE, "ORDER_PLACED"));
        drain();
        verify(gateway, never()).sendInbox(anyString(), anyLong(), anyString(), nullable(Long.class));
    }

    @Test
    void deviceErrorsAreIndependentAndOnlyUnregisteredBindingIsRemoved() throws Exception {
        tokens.registerAndroid(customer, "fixture-network");
        tokens.registerAndroid(customer, "fixture-unregistered");
        tokens.registerAndroid(customer, "fixture-valid");
        doThrow(new IOException("fixture transport failure")).when(gateway)
                .sendInbox(eq("fixture-network"), anyLong(), anyString(), nullable(Long.class));
        when(gateway.sendInbox(eq("fixture-unregistered"), anyLong(), anyString(), nullable(Long.class))).thenReturn(false);
        long order = place("COD").get("id").asLong();
        long id = notification(order, customer, "placed");
        drain();
        verify(gateway).sendInbox("fixture-valid", id, "ORDER_PLACED", null);
        assertEquals(List.of("fixture-network", "fixture-valid"), tokens.findActiveAndroidTokens(customer));
        assertTrue(list(customer).path("items").valueStream().anyMatch(node -> node.path("id").asLong() == id));
        verifyStomp(id);
    }

    @Test
    void rechecksOwnerAfterTokenEnumerationAndDoesNotDeleteReboundToken() throws Exception {
        tokens.registerAndroid(customer, "fixture-moved-before-send");
        tokens.registerAndroid(customer, "fixture-moved-during-send");
        doAnswer(call -> {
            Object candidates = call.callRealMethod();
            tokens.registerAndroid(other, "fixture-moved-before-send");
            return candidates;
        }).when(tokens).findActiveAndroidTokens(customer);
        when(gateway.sendInbox(eq("fixture-moved-during-send"), anyLong(), anyString(), nullable(Long.class)))
                .thenAnswer(call -> {
                    tokens.registerAndroid(other, "fixture-moved-during-send");
                    return false;
                });
        long order = place("COD").get("id").asLong();
        drain();
        verify(gateway, never()).sendInbox(eq("fixture-moved-before-send"), anyLong(), anyString(), nullable(Long.class));
        verify(gateway).sendInbox("fixture-moved-during-send", notification(order, customer, "placed"),
                "ORDER_PLACED", null);
        assertEquals(List.of("fixture-moved-before-send", "fixture-moved-during-send"),
                tokens.findActiveAndroidTokens(other));
    }

    @Test
    void saturatedQueueDoesNotDelayOrFailCheckout() throws Exception {
        tokens.registerAndroid(customer, "fixture-customer");
        var sending = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(gateway.sendInbox(eq("fixture-customer"), anyLong(), anyString(), nullable(Long.class))).thenAnswer(call -> {
            sending.countDown();
            assertTrue(release.await(15, TimeUnit.SECONDS));
            return true;
        });
        try {
            place("COD");
            assertTrue(sending.await(5, TimeUnit.SECONDS));
            // The first checkout's Merchant notice already occupies one queued slot.
            for (int i = 0; i < 126; i++) {
                publisher.enqueue(new CommittedNotificationWriter.SavedNotification(QUEUE_PROBES.decrementAndGet(),
                        Long.MAX_VALUE, "ORDER_PLACED"));
            }
            long marker = QUEUE_PROBES.decrementAndGet();
            publisher.enqueue(new CommittedNotificationWriter.SavedNotification(marker, Long.MAX_VALUE, "ORDER_PLACED"));
            long second = place("COD").get("id").asLong();
            long id = notification(second, customer, "placed");
            release.countDown();
            verify(tokens, timeout(10000)).findActiveAndroidTokens(marker);
            drain();
            verify(gateway, never()).sendInbox("fixture-customer", id, "ORDER_PLACED", null);
            verifyStomp(id);
        } finally {
            release.countDown();
        }
    }

    @Test
    void actionableOfferUsesRemainingTtlAndAcceptReplayDoesNotSendAgain() throws Exception {
        tokens.registerAndroid(DRIVER_USER, "fixture-driver");
        tokens.registerAndroid(other, "fixture-other");
        long order = place("COD").get("id").asLong();
        online();
        var offer = assignments.createOffer(order).orElseThrow();
        long id = offerNotification(offer.id());
        drain();
        verify(gateway).sendInbox(eq("fixture-driver"), eq(id), eq("OFFER_CREATED"),
                longThat(ttl -> ttl > 0 && ttl <= 30_000));
        for (int i = 0; i < 2; i++) {
            mvc.perform(as(post("/api/driver/offers/{id}/accept", offer.id()), DRIVER_USER, "DRIVER"))
                    .andExpect(status().isOk());
        }
        drain();
        verify(gateway, times(1)).sendInbox(anyString(), anyLong(), anyString(), nullable(Long.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXPIRED", "ACCEPTED", "REJECTED", "CANCELLED", "ORDER_PREPARING", "WRONG_DRIVER"})
    void dropsOffersThatStoppedBeingActionableBeforeWorkerSend(String condition) throws Exception {
        long order = place("COD").get("id").asLong();
        online();
        var offer = assignments.createOffer(order).orElseThrow();
        long id = offerNotification(offer.id());
        drain();
        switch (condition) {
            case "EXPIRED" -> jdbc.update("UPDATE order_assignments SET expires_at=? WHERE id=?",
                    LocalDateTime.now().minusSeconds(1), offer.id());
            case "ORDER_PREPARING" -> jdbc.update("UPDATE orders SET status='PREPARING' WHERE id=?", order);
            case "WRONG_DRIVER" -> jdbc.update("UPDATE drivers SET user_id=? WHERE id=?", ADMIN, DRIVER_ID);
            default -> jdbc.update("UPDATE order_assignments SET status=? WHERE id=?", condition, offer.id());
        }
        try {
            tokens.registerAndroid(DRIVER_USER, "fixture-driver");
            publisher.enqueue(new CommittedNotificationWriter.SavedNotification(DRIVER_USER, id, "OFFER_CREATED"));
            drain();
            verify(gateway, never()).sendInbox(anyString(), anyLong(), anyString(), nullable(Long.class));
        } finally {
            if ("WRONG_DRIVER".equals(condition)) {
                jdbc.update("UPDATE drivers SET user_id=? WHERE id=?", DRIVER_USER, DRIVER_ID);
            }
        }
    }

    @Test
    void customerCancellationPushesOnlyToDriverWithAnEffectiveOffer() throws Exception {
        long order = place("COD").get("id").asLong();
        online();
        assignments.createOffer(order).orElseThrow();
        drain();
        tokens.registerAndroid(DRIVER_USER, "fixture-driver");
        tokens.registerAndroid(customer, "fixture-customer");
        tokens.registerAndroid(merchant, "fixture-merchant");
        management.cancelByCustomer(customer, order, "Fixture");
        drain();
        verify(gateway).sendInbox("fixture-driver", notification(order, DRIVER_USER, "cancelled"), "ORDER_CANCELLED", null);
        verify(gateway, times(1)).sendInbox(anyString(), anyLong(), anyString(), nullable(Long.class));
    }

    @Test
    void inboxMessageIsDataOnlyAndUsesTheAndroidTtl() {
        var message = FirebaseCustomerPush.inboxMessage("fixture-token", 105L, "OFFER_CREATED", 1200L);
        assertEquals(Map.of("notificationId", "105", "eventType", "OFFER_CREATED"),
                ReflectionTestUtils.getField(message, "data"));
        assertNull(ReflectionTestUtils.getField(message, "notification"));
        assertNotNull(ReflectionTestUtils.getField(message, "androidConfig"));
    }

    private JsonNode place(String method) throws Exception {
        addCart();
        return json.readTree(mvc.perform(as(post("/api/orders"), customer, "CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request(method))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
    }

    private void addCart() throws Exception {
        mvc.perform(as(delete("/api/cart"), customer, "CUSTOMER")).andExpect(status().isNoContent());
        mvc.perform(as(post("/api/cart/items"), customer, "CUSTOMER").contentType(MediaType.APPLICATION_JSON)
                .content("{\"menuItemId\":" + menuItem + ",\"quantity\":1}")).andExpect(status().isOk());
    }

    private CreateOrderRequest request(String method) {
        return new CreateOrderRequest("Fixture", "0900000000", "Fixture", 10.7756, 106.7004, null, null, method);
    }

    private void ipn(JsonNode order, String paymentCode, String expected) throws Exception {
        var params = new TreeMap<String, String>();
        params.put("vnp_TmnCode", "FIXTURE");
        params.put("vnp_TxnRef", order.path("code").asText());
        params.put("vnp_Amount", new BigDecimal(order.path("totalAmount").asText()).movePointRight(2).toBigInteger().toString());
        params.put("vnp_ResponseCode", paymentCode);
        params.put("vnp_TransactionStatus", paymentCode);
        String query = params.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(VNPAY_FIXTURE_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        params.put("vnp_SecureHash", HexFormat.of().formatHex(mac.doFinal(query.getBytes(StandardCharsets.UTF_8))));
        var request = get("/api/payments/vnpay/ipn");
        params.forEach(request::param);
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value(expected));
    }

    private void drain() {
        long marker = QUEUE_PROBES.decrementAndGet();
        publisher.enqueue(new CommittedNotificationWriter.SavedNotification(marker, Long.MAX_VALUE, "ORDER_PLACED"));
        verify(tokens, timeout(10000)).findActiveAndroidTokens(marker);
    }

    private void verifyStomp(long id) {
        verify(realtime).convertAndSendToUser(eq("user:" + customer), eq("/queue/notifications"),
                argThat(signal -> signal instanceof NotificationRealtimePublisher.Signal value
                        && value.notificationId() == id));
    }

    private long notification(long order, long recipient, String key) {
        return jdbc.queryForObject("SELECT id FROM notifications WHERE user_id=? AND event_key=?",
                Long.class, recipient, "order:" + order + ":" + key);
    }

    private long offerNotification(long offerId) {
        return jdbc.queryForObject("SELECT id FROM notifications WHERE user_id=? AND event_key=?",
                Long.class, DRIVER_USER, "offer:" + offerId + ":offered");
    }

    private JsonNode list(long id) throws Exception {
        return json.readTree(mvc.perform(as(get("/api/notifications").param("limit", "50"), id, "CUSTOMER"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    private void online() {
        jdbc.update("""
                UPDATE drivers SET is_online=1,current_lat=10.7756,current_lng=106.7004,last_location_at=?
                WHERE id=?
                """, LocalDateTime.now().minusSeconds(1), DRIVER_ID);
    }

    private long user(String phone) {
        return jdbc.queryForObject("SELECT id FROM users WHERE phone=?", Long.class, phone);
    }

    private void addUser(long id, String role) {
        jdbc.update("""
                INSERT INTO users (id,full_name,phone,password_hash,role) VALUES (?, 'Fixture', ?, 'fixture-only', ?)
                ON DUPLICATE KEY UPDATE id=id
                """, id, "0990" + id, role);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, long id, String role) {
        return request.header("Authorization", "Bearer " + jwt.generateToken(id, role));
    }
}
