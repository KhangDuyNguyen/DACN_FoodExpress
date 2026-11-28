package vn.hutech.fooddelivery.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.assignment.AssignmentOffer;
import vn.hutech.fooddelivery.assignment.AssignmentOfferEvent;
import vn.hutech.fooddelivery.assignment.AssignmentService;
import vn.hutech.fooddelivery.order.OrderManagementService;
import vn.hutech.fooddelivery.order.OrderService;
import vn.hutech.fooddelivery.order.dto.CreateOrderRequest;
import vn.hutech.fooddelivery.order.event.OrderPlacedEvent;
import vn.hutech.fooddelivery.order.event.OrderStatusChangedEvent;
import vn.hutech.fooddelivery.security.JwtService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = {"spring.config.import=", "app.assignment.scheduling.enabled=false"})
@AutoConfigureMockMvc
class NotificationBusinessEventsIntegrationTest {
    private static final long DRIVER = 771001;
    private static final long OTHER_DRIVER = 771002;
    private static final long DRIVER_USER = 772001;
    private static final long OTHER_DRIVER_USER = 772002;
    private static final long ADMIN = 772003;

    @Container @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4").withEnv("TZ", "Asia/Ho_Chi_Minh");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String key = UUID.randomUUID().toString();
        registry.add("app.jwt.secret", () -> key);
        registry.add("app.fcm.project-id", () -> "");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired AssignmentService assignments;
    @Autowired OrderService orders;
    @Autowired OrderManagementService management;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ApplicationEventPublisher events;
    @Autowired CommittedNotificationWriter writer;
    @Autowired OfferRollbackProbe rollbackProbe;
    @MockitoSpyBean NotificationInboxService inbox;

    private long customer;
    private long merchant;
    private long otherCustomer;
    private long otherMerchant;
    private long restaurant;
    private long menuItem;

    @BeforeEach
    void fixture() {
        rollbackProbe.fail = false;
        customer = user("0911000001");
        otherCustomer = user("0911000002");
        restaurant = jdbc.queryForObject("SELECT id FROM restaurants WHERE name = ?", Long.class,
                "Cơm Nhà Demo - Chi nhánh Quận 1");
        merchant = jdbc.queryForObject("""
                SELECT m.owner_user_id FROM merchants m JOIN restaurants r ON r.merchant_id = m.id WHERE r.id=?
                """, Long.class, restaurant);
        otherMerchant = user("0922000002");
        menuItem = jdbc.queryForObject("""
                SELECT mi.id FROM menu_items mi JOIN menu_categories mc ON mc.id = mi.category_id
                WHERE mc.restaurant_id = ? AND mi.name = 'Cơm sườn nướng'
                """, Long.class, restaurant);
        addUser(DRIVER_USER, "DRIVER");
        addUser(OTHER_DRIVER_USER, "DRIVER");
        addUser(ADMIN, "ADMIN");
        jdbc.update("""
                INSERT INTO drivers (id, user_id, status) VALUES (?, ?, 'APPROVED'), (?, ?, 'APPROVED')
                ON DUPLICATE KEY UPDATE is_online = 0
                """, DRIVER, DRIVER_USER, OTHER_DRIVER, OTHER_DRIVER_USER);
        jdbc.update("DELETE FROM order_assignments WHERE driver_id IN (?, ?)", DRIVER, OTHER_DRIVER);
    }

    @Test
    void codAndDeliveryCreateExactlyTheApprovedRecipientsAndRemainReadable() throws Exception {
        assertTrue(AopUtils.isAopProxy(writer));
        var transactionName = new AtomicReference<String>();
        doAnswer(call -> {
            transactionName.set(TransactionSynchronizationManager.getCurrentTransactionName());
            return call.callRealMethod();
        }).when(inbox).record(any());

        long orderId = placeCod();
        assertEquals(CommittedNotificationWriter.class.getName() + ".save", transactionName.get());
        assertRecipients(orderId, "ORDER_PLACED", customer, merchant);
        assertEquals(List.of("order:" + orderId + ":placed"), keys(orderId, "ORDER_PLACED"));
        online(DRIVER);
        AssignmentOffer offer = assignments.createOffer(orderId).orElseThrow();
        assertRecipients(orderId, "OFFER_CREATED", DRIVER_USER);
        assertEquals(List.of("offer:" + offer.id() + ":offered"), keys(orderId, "OFFER_CREATED"));
        var offerData = readInbox(DRIVER_USER, "DRIVER", orderId, "OFFER_CREATED").get("data");
        assertEquals(offer.id(), offerData.get("offerId").asLong());
        assertEquals(offer.expiresAt().atZone(ZoneId.systemDefault()).toInstant().toString(),
                offerData.get("expiresAt").asText());

        for (int i = 0; i < 2; i++) {
            mvc.perform(as(post("/api/driver/offers/{id}/accept", offer.id()), DRIVER_USER, "DRIVER"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
        }
        assertEquals("PLACED", orderStatus(orderId));
        assertRecipients(orderId, "OFFER_ACCEPTED", merchant);
        assertEquals(List.of("offer:" + offer.id() + ":accepted"), keys(orderId, "OFFER_ACCEPTED"));
        mvc.perform(as(post("/api/orders/{id}/cancel", orderId), customer, "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Fixture\"}"))
                .andExpect(status().isConflict());
        assertRecipients(orderId, "ORDER_CANCELLED");

        merchantStatus(orderId, "PREPARING");
        assertRecipients(orderId, "ORDER_PREPARING", customer);
        merchantStatus(orderId, "READY_FOR_PICKUP");
        assertRecipients(orderId, "ORDER_READY_FOR_PICKUP", customer, DRIVER_USER);
        management.changeStatusByDriver(DRIVER_USER, orderId, "DELIVERING");
        assertRecipients(orderId, "ORDER_DELIVERING", customer, merchant);
        management.changeStatusByDriver(DRIVER_USER, orderId, "DELIVERED");
        assertRecipients(orderId, "ORDER_DELIVERED", customer, merchant, DRIVER_USER);
        assertEquals("DELIVERED", orderStatus(orderId));

        long id = readInbox(customer, "CUSTOMER", orderId, "ORDER_DELIVERED").get("id").asLong();
        mvc.perform(as(patch("/api/notifications/{id}/read", id), otherCustomer, "CUSTOMER"))
                .andExpect(status().isNotFound());
        mvc.perform(as(patch("/api/notifications/{id}/read", id), customer, "CUSTOMER"))
                .andExpect(status().isNoContent());
        assertTrue(readInbox(customer, "CUSTOMER", orderId, "ORDER_DELIVERED").get("isRead").asBoolean());
        mvc.perform(as(get("/api/notifications"), ADMIN, "ADMIN")).andExpect(status().isForbidden());
        List<Long> actualUsers = jdbc.queryForList("""
                SELECT DISTINCT user_id FROM notifications WHERE data_json->>'$.orderId' = ?
                """, Long.class, Long.toString(orderId));
        assertFalse(actualUsers.contains(otherMerchant));
        assertFalse(actualUsers.contains(otherCustomer));
        assertFalse(actualUsers.contains(ADMIN));
        assertFalse(actualUsers.contains(OTHER_DRIVER_USER));
    }

    @Test
    void merchantSelfDeliveryHasNoDriverRecipient() throws Exception {
        long orderId = placeCod();
        for (String step : List.of("PREPARING", "READY_FOR_PICKUP", "DELIVERING", "DELIVERED")) {
            merchantStatus(orderId, step);
        }
        assertRecipients(orderId, "ORDER_READY_FOR_PICKUP", customer);
        assertRecipients(orderId, "ORDER_DELIVERING", customer, merchant);
        assertRecipients(orderId, "ORDER_DELIVERED", customer, merchant);
    }

    @Test
    void merchantRecipientIsCapturedWhenTheBusinessEventIsPublished() throws Exception {
        addCartItem();
        Long originalMerchant = jdbc.queryForObject("SELECT merchant_id FROM restaurants WHERE id = ?",
                Long.class, restaurant);
        Long replacementMerchant = jdbc.queryForObject("SELECT id FROM merchants WHERE owner_user_id = ?",
                Long.class, otherMerchant);
        try {
            Long orderId = new TransactionTemplate(transactions).execute(tx -> {
                long created = orders.createOrder(customer, codRequest(), "127.0.0.1").order().id();
                assertRecipients(created, "ORDER_PLACED");
                jdbc.update("UPDATE restaurants SET merchant_id = ? WHERE id = ?", replacementMerchant, restaurant);
                return created;
            });
            assertRecipients(orderId, "ORDER_PLACED", customer, merchant);
        } finally {
            jdbc.update("UPDATE restaurants SET merchant_id = ? WHERE id = ?", originalMerchant, restaurant);
        }
    }

    @Test
    void customerCancellationNotifiesOnlyMerchantAndTheEffectiveOfferDriver() throws Exception {
        long orderId = placeCod();
        online(OTHER_DRIVER);
        var rejected = assignments.createOffer(orderId).orElseThrow();
        mvc.perform(as(post("/api/driver/offers/{id}/reject", rejected.id()), OTHER_DRIVER_USER, "DRIVER"))
                .andExpect(status().isOk());
        assertRecipients(orderId, "ORDER_CANCELLED");
        online(DRIVER);
        var effective = assignments.createOffer(orderId).orElseThrow();
        assertNotEquals(rejected.id(), effective.id());
        assertRecipients(orderId, "OFFER_CREATED", OTHER_DRIVER_USER, DRIVER_USER);
        assertEquals(2, keys(orderId, "OFFER_CREATED").size());
        mvc.perform(as(post("/api/orders/{id}/cancel", orderId), customer, "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Fixture\"}"))
                .andExpect(status().isOk());
        assertRecipients(orderId, "ORDER_CANCELLED", merchant, DRIVER_USER);
        mvc.perform(as(post("/api/driver/offers/{id}/accept", effective.id()), DRIVER_USER, "DRIVER"))
                .andExpect(status().isConflict());
    }

    @Test
    void merchantCancellationNotifiesCustomerAndNoDriverWithExpiredOffer() throws Exception {
        long orderId = placeCod();
        online(DRIVER);
        var offered = assignments.createOffer(orderId).orElseThrow();
        jdbc.update("UPDATE order_assignments SET expires_at = ? WHERE id = ?",
                LocalDateTime.now().minusSeconds(1), offered.id());
        merchantStatus(orderId, "CANCELLED");
        assertRecipients(orderId, "ORDER_CANCELLED", customer);
    }

    @Test
    void replayAndNoTransactionDoNotCreateMoreInboxRows() throws Exception {
        long orderId = placeCod();
        var event = new OrderPlacedEvent(orderId, restaurant, orders.getDetail(orderId));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> events.publishEvent(event));
        assertRecipients(orderId, "ORDER_PLACED", customer, merchant);
        // Remove only the fixture inbox entries so a mistaken fallback cannot be hidden by deduplication.
        jdbc.update("DELETE FROM notifications WHERE event_key = ?", "order:" + orderId + ":placed");
        events.publishEvent(event);
        assertRecipients(orderId, "ORDER_PLACED");
        new TransactionTemplate(transactions).executeWithoutResult(tx -> events.publishEvent(
                new OrderStatusChangedEvent(orderId, restaurant, customer, "PLACED", "PLACED", customer)));
        assertRecipients(orderId, "ORDER_PLACED");
    }

    @Test
    void orderRollbackAfterRealCheckoutCreatesNoInbox() throws Exception {
        addCartItem();
        long orderId = new TransactionTemplate(transactions).execute(tx -> {
            long created = orders.createOrder(customer, codRequest(), "127.0.0.1").order().id();
            assertRecipients(created, "ORDER_PLACED");
            tx.setRollbackOnly();
            return created;
        });
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE id = ?", Integer.class, orderId));
        assertRecipients(orderId, "ORDER_PLACED");
    }

    @Test
    void offerRollbackAfterRealInsertCreatesNoInbox() throws Exception {
        long orderId = placeCod();
        online(DRIVER);
        rollbackProbe.fail = true;
        assertThrows(IllegalStateException.class, () -> assignments.createOffer(orderId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM order_assignments WHERE order_id = ?",
                Integer.class, orderId));
        assertRecipients(orderId, "OFFER_CREATED");
    }

    @Test
    void failureInNewInboxTransactionDoesNotUndoCheckoutOrChangeHttpStatus() throws Exception {
        doThrow(new DataIntegrityViolationException("fixture write failure")).when(inbox).record(any());
        long orderId = placeCod();
        assertEquals("PLACED", orderStatus(orderId));
        assertRecipients(orderId, "ORDER_PLACED");
    }

    private long placeCod() throws Exception {
        addCartItem();
        return json.readTree(mvc.perform(as(post("/api/orders"), customer, "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"receiverName":"Fixture","receiverPhone":"0900000000","deliveryAddress":"Fixture",
                                 "deliveryLat":10.7756,"deliveryLng":106.7004,"paymentMethod":"COD"}
                                """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray())
                .get("id").asLong();
    }

    private CreateOrderRequest codRequest() {
        return new CreateOrderRequest("Fixture", "0900000000", "Fixture", 10.7756, 106.7004, null, null, "COD");
    }

    private void addCartItem() throws Exception {
        mvc.perform(as(delete("/api/cart"), customer, "CUSTOMER")).andExpect(status().isNoContent());
        mvc.perform(as(post("/api/cart/items"), customer, "CUSTOMER").contentType(MediaType.APPLICATION_JSON)
                .content("{\"menuItemId\":" + menuItem + ",\"quantity\":1}")).andExpect(status().isOk());
    }

    private void merchantStatus(long id, String step) throws Exception {
        mvc.perform(as(patch("/api/merchant/orders/{id}/status", id), merchant, "MERCHANT")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + step + "\",\"reason\":\"Fixture\"}"))
                .andExpect(status().isOk());
    }

    private void assertRecipients(long orderId, String eventType, Long... expected) {
        var ids = jdbc.queryForList("""
                SELECT user_id FROM notifications WHERE data_json->>'$.orderId' = ?
                    AND data_json->>'$.eventType' = ? ORDER BY user_id
                """, Long.class, Long.toString(orderId), eventType);
        assertEquals(List.of(expected).stream().sorted().toList(), ids, eventType);
    }

    private List<String> keys(long orderId, String eventType) {
        return jdbc.queryForList("""
                SELECT DISTINCT event_key FROM notifications
                WHERE data_json->>'$.orderId' = ? AND data_json->>'$.eventType' = ?
                """, String.class, Long.toString(orderId), eventType);
    }

    private JsonNode readInbox(long user, String role, long orderId, String eventType) throws Exception {
        var response = mvc.perform(as(get("/api/notifications").param("limit", "50"), user, role))
                .andExpect(status().isOk()).andReturn().getResponse();
        for (JsonNode item : json.readTree(response.getContentAsByteArray()).get("items")) {
            if (eventType.equals(item.get("eventType").asText()) && item.at("/data/orderId").asLong() == orderId) {
                return item;
            }
        }
        throw new AssertionError("Missing " + eventType + " in inbox");
    }

    private long user(String phone) {
        return jdbc.queryForObject("SELECT id FROM users WHERE phone = ?", Long.class, phone);
    }

    private void addUser(long id, String role) {
        jdbc.update("""
                INSERT INTO users (id, full_name, phone, password_hash, role) VALUES (?, 'Fixture', ?, 'fixture-only', ?)
                ON DUPLICATE KEY UPDATE id = id
                """, id, "0990" + id, role);
    }

    private void online(long driverId) {
        jdbc.update("""
                UPDATE drivers SET is_online = 1, current_lat = 10.7756, current_lng = 106.7004, last_location_at = ?
                WHERE id = ?
                """, LocalDateTime.now().minusSeconds(1), driverId);
    }

    private String orderStatus(long orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, long user, String role) {
        return request.header("Authorization", "Bearer " + jwt.generateToken(user, role));
    }

    @TestConfiguration
    static class Probes {
        @Bean OfferRollbackProbe offerRollbackProbe() { return new OfferRollbackProbe(); }
    }

    static class OfferRollbackProbe {
        boolean fail;

        @Order(Ordered.LOWEST_PRECEDENCE)
        @EventListener
        public void offerCreated(AssignmentOfferEvent event) {
            if (fail && event.kind() == AssignmentOfferEvent.Kind.CREATED) {
                throw new IllegalStateException("fixture rollback after offer insert");
            }
        }
    }
}
