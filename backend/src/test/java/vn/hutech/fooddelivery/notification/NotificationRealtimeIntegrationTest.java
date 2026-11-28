package vn.hutech.fooddelivery.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.order.OrderManagementService;
import vn.hutech.fooddelivery.order.OrderService;
import vn.hutech.fooddelivery.order.dto.ChangeOrderStatusRequest;
import vn.hutech.fooddelivery.order.dto.CreateOrderRequest;
import vn.hutech.fooddelivery.order.event.OrderPlacedEvent;
import vn.hutech.fooddelivery.security.JwtService;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.hutech.fooddelivery.notification.NotificationRealtimeChannel.*;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "app.assignment.scheduling.enabled=false", "app.admin.phone=", "app.admin.password="
})
class NotificationRealtimeIntegrationTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String CUSTOMER = "CUSTOMER";
    private static final String MERCHANT = "MERCHANT";

    @Container @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String key = UUID.randomUUID().toString();
        registry.add("app.jwt.secret", () -> key);
        registry.add("app.fcm.project-id", () -> "");
    }

    @LocalServerPort int port;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired SimpUserRegistry registry;
    @Autowired SimpleBrokerMessageHandler broker;
    @Autowired OrderService orders;
    @Autowired OrderManagementService management;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean NotificationInboxService inbox;
    @MockitoSpyBean NotificationRecipientRepository recipients;
    @MockitoSpyBean SimpMessagingTemplate messaging;

    private final List<WebSocketStompClient> clients = new ArrayList<>();
    private final List<StompSession> sessions = new ArrayList<>();
    private long customer;
    private long otherCustomer;
    private long merchant;
    private long otherMerchant;
    private long restaurant;
    private long menuItem;

    @BeforeEach
    void fixture() {
        customer = user("0911000001");
        otherCustomer = user("0911000002");
        otherMerchant = user("0922000002");
        restaurant = jdbc.queryForObject("SELECT id FROM restaurants WHERE name = ?", Long.class,
                "Cơm Nhà Demo - Chi nhánh Quận 1");
        merchant = jdbc.queryForObject("""
                SELECT m.owner_user_id FROM merchants m JOIN restaurants r ON r.merchant_id = m.id WHERE r.id=?
                """, Long.class, restaurant);
        menuItem = jdbc.queryForObject("""
                SELECT mi.id FROM menu_items mi JOIN menu_categories mc ON mc.id=mi.category_id
                WHERE mc.restaurant_id=? AND mi.name='Cơm sườn nướng'
                """, Long.class, restaurant);
        assertNotEquals(otherMerchant, merchant);
    }

    @AfterEach
    void disconnect() {
        try {
            sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
        } finally {
            clients.forEach(WebSocketStompClient::stop);
        }
    }

    @Test
    void nativeAndSockJsRouteToExactUsersAndAllSessionsOfTheSameUser() throws Exception {
        var a = connect(customer, CUSTOMER, false);
        var anotherSocket = connect(customer, CUSTOMER, true);
        var b = connect(otherCustomer, CUSTOMER, false);
        var owner = connect(merchant, MERCHANT, true);
        var otherOwner = connect(otherMerchant, MERCHANT, false);

        long orderId = place(customer);
        long customerId = expectSignal(a, customer, CUSTOMER, orderId, "ORDER_PLACED");
        assertEquals(customerId, expectSignal(anotherSocket, customer, CUSTOMER, orderId, "ORDER_PLACED"));
        long merchantId = expectSignal(owner, merchant, MERCHANT, orderId, "ORDER_PLACED");
        assertNotEquals(customerId, merchantId);
        assertTrue(list(customer, CUSTOMER).findValuesAsText("eventType").contains("ORDER_PLACED"));
        assertNoSignal(a, anotherSocket, b, owner, otherOwner);

        management.changeStatusByMerchant(merchant, orderId, new ChangeOrderStatusRequest("PREPARING", null));
        expectSignal(a, customer, CUSTOMER, orderId, "ORDER_PREPARING");
        expectSignal(anotherSocket, customer, CUSTOMER, orderId, "ORDER_PREPARING");
        assertNoSignal(b, owner, otherOwner);
        mvc.perform(as(patch("/api/notifications/{id}/read", merchantId), customer, CUSTOMER))
                .andExpect(status().isNotFound());
    }

    @Test
    void replayAnExistingBusinessEventDoesNotSendNewSignals() throws Exception {
        var a = connect(customer, CUSTOMER, false);
        var m = connect(merchant, MERCHANT, false);
        long orderId = place(customer);
        expectSignal(a, customer, CUSTOMER, orderId, "ORDER_PLACED");
        expectSignal(m, merchant, MERCHANT, orderId, "ORDER_PLACED");
        new TransactionTemplate(transactions).executeWithoutResult(tx ->
                events.publishEvent(new OrderPlacedEvent(orderId, restaurant, orders.getDetail(orderId))));
        assertEquals(2, notificationCount(orderId));
        assertNoSignal(a, m);
    }

    @Test
    void businessRollbackLeavesNoInboxOrSignal() throws Exception {
        var a = connect(customer, CUSTOMER, false);
        var m = connect(merchant, MERCHANT, false);
        addCart(customer);
        long orderId = new TransactionTemplate(transactions).execute(tx -> {
            long created = orders.createOrder(customer, request(), "127.0.0.1").order().id();
            tx.setRollbackOnly();
            return created;
        });
        assertEquals(0, orderCount(orderId));
        assertEquals(0, notificationCount(orderId));
        assertNoSignal(a, m);
    }

    @Test
    void inboxRollbackDiscardsSignalsEvenAfterFirstRecipientWasInserted() throws Exception {
        var a = connect(customer, CUSTOMER, false);
        var m = connect(merchant, MERCHANT, false);
        doThrow(new DataIntegrityViolationException("fixture second write error"))
                .when(inbox).record(argThat(draft -> draft.userId() == merchant));
        long orderId = place(customer);
        assertEquals(1, orderCount(orderId));
        assertEquals(0, notificationCount(orderId));
        assertNoSignal(a, m);
    }

    @Test
    void failedPublishLeavesInboxCommittedAndDoesNotBlockNextRecipient() throws Exception {
        var a = connect(customer, CUSTOMER, false);
        var m = connect(merchant, MERCHANT, false);
        doThrow(new MessageDeliveryException("fixture first publish error")).when(messaging)
                .convertAndSendToUser(eq(principalName(customer)), eq(QUEUE), any(NotificationRealtimePublisher.Signal.class));
        long orderId = place(customer);
        expectSignal(m, merchant, MERCHANT, orderId, "ORDER_PLACED");
        assertEquals(2, notificationCount(orderId));
        assertTrue(list(customer, CUSTOMER).get("items").valueStream().anyMatch(item ->
                item.at("/data/orderId").asLong() == orderId && "ORDER_PLACED".equals(item.get("eventType").asText())));
        assertNoSignal(a);
    }

    @Test
    void captureQueryErrorLeavesBusinessCommittedWithoutSignals() throws Exception {
        var a = connect(customer, CUSTOMER, false);
        var m = connect(merchant, MERCHANT, false);
        doThrow(new DataIntegrityViolationException("fixture capture query error"))
                .when(recipients).findOrder(anyLong(), eq(restaurant));
        long orderId = place(customer);
        assertEquals(1, orderCount(orderId));
        assertEquals(0, notificationCount(orderId));
        assertNoSignal(a, m);
    }

    @Test
    void offlineUsesRestAndReconnectOrAccountSwitchDoesNotReplayHistory() throws Exception {
        var first = connect(customer, CUSTOMER, false);
        first.session().disconnect();
        await().atMost(TIMEOUT).until(() -> registry.getUser(principalName(customer)) == null);
        long before = list(customer, CUSTOMER).get("unreadCount").asLong();
        long offlineOrder = place(customer);
        assertEquals(2, notificationCount(offlineOrder));
        assertEquals(before + 1, list(customer, CUSTOMER).get("unreadCount").asLong());

        var reconnected = connect(customer, CUSTOMER, false);
        assertNoSignal(first, reconnected);
        long liveOrder = place(customer);
        expectSignal(reconnected, customer, CUSTOMER, liveOrder, "ORDER_PLACED");
        reconnected.session().disconnect();
        await().atMost(TIMEOUT).until(() -> registry.getUser(principalName(customer)) == null);
        var switched = connect(otherCustomer, CUSTOMER, false);
        assertNoSignal(switched);
        long switchedOrder = place(otherCustomer);
        expectSignal(switched, otherCustomer, CUSTOMER, switchedOrder, "ORDER_PLACED");
        assertNoSignal(first, reconnected, switched);
    }

    private Connection connect(long id, String role, boolean sockJs) throws Exception {
        var transport = sockJs ? new SockJsClient(List.of(new RestTemplateXhrTransport()))
                : new StandardWebSocketClient();
        var client = new WebSocketStompClient(transport);
        clients.add(client);
        client.setMessageConverter(new SimpleMessageConverter());
        var credentials = new StompHeaders();
        credentials.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(id, role));
        String endpoint = (sockJs ? "http" : "ws") + "://127.0.0.1:" + port + "/ws";
        var session = client.connectAsync(endpoint, (WebSocketHttpHeaders) null, credentials, new StompSessionHandlerAdapter() {})
                .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        sessions.add(session);
        var messages = new LinkedBlockingQueue<byte[]>();
        var headers = new StompHeaders();
        String subId = UUID.randomUUID().toString();
        headers.setDestination(SUBSCRIPTION);
        headers.setId(subId);
        session.subscribe(headers, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { messages.add((byte[]) payload); }
        });
        await().atMost(TIMEOUT).until(() -> registered(id, subId));
        return new Connection(session, messages);
    }

    private boolean registered(long id, String subId) {
        var user = registry.getUser(principalName(id));
        if (user == null) return false;
        for (var session : user.getSessions()) {
            var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            headers.setDestination(QUEUE + "-user" + session.getId());
            var probe = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
            var ids = broker.getSubscriptionRegistry().findSubscriptions(probe).get(session.getId());
            if (ids != null && ids.contains(subId)) return true;
        }
        return false;
    }

    private long expectSignal(Connection connection, long userId, String role, long orderId, String event) throws Exception {
        byte[] bytes = connection.messages().poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        assertNotNull(bytes);
        JsonNode signal = json.readTree(bytes);
        assertEquals(2, signal.size());
        assertEquals(event, signal.path("eventType").asText());
        long id = signal.path("notificationId").asLong();
        assertTrue(id > 0);
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM notifications WHERE id=? AND user_id=? AND data_json->>'$.orderId'=?
                """, Integer.class, id, userId, Long.toString(orderId)));
        assertTrue(list(userId, role).get("items").valueStream().anyMatch(item -> item.get("id").asLong() == id));
        return id;
    }

    private void assertNoSignal(Connection... connections) throws InterruptedException {
        for (var connection : connections) assertNull(connection.messages().poll(200, TimeUnit.MILLISECONDS));
    }

    private long place(long user) throws Exception {
        addCart(user);
        return json.readTree(mvc.perform(as(post("/api/orders"), user, CUSTOMER)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray()).get("id").asLong();
    }

    private void addCart(long user) throws Exception {
        mvc.perform(as(delete("/api/cart"), user, CUSTOMER)).andExpect(status().isNoContent());
        mvc.perform(as(post("/api/cart/items"), user, CUSTOMER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"menuItemId\":" + menuItem + ",\"quantity\":1}")).andExpect(status().isOk());
    }

    private CreateOrderRequest request() {
        return new CreateOrderRequest("Fixture", "0900000000", "Fixture", 10.7756, 106.7004, null, null, "COD");
    }

    private JsonNode list(long user, String role) throws Exception {
        return json.readTree(mvc.perform(as(get("/api/notifications").param("limit", "50"), user, role))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    private long user(String phone) {
        return jdbc.queryForObject("SELECT id FROM users WHERE phone=?", Long.class, phone);
    }

    private int orderCount(long orderId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE id=?", Integer.class, orderId);
    }

    private int notificationCount(long orderId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE data_json->>'$.orderId'=?",
                Integer.class, Long.toString(orderId));
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, long id, String role) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(id, role));
    }

    private record Connection(StompSession session, BlockingQueue<byte[]> messages) {}
}
