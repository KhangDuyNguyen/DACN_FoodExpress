package vn.hutech.fooddelivery.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.order.OrderService;
import vn.hutech.fooddelivery.order.event.OrderPlacedEvent;
import vn.hutech.fooddelivery.security.JwtService;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=",
        "app.jwt.secret=tracking_websocket_integration_secret_at_least_32_characters",
        "app.admin.phone=", "app.admin.password=", "app.assignment.scheduling.enabled=false"
})
@Testcontainers
class TrackingStompWebSocketIntegrationTest {
    private static final AtomicLong ORDER_IDS = new AtomicLong(630000);
    private static final long RESTAURANT = 1;
    private static final String RESTAURANT_TOPIC = "/topic/restaurants/" + RESTAURANT + "/orders";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Container @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @LocalServerPort int port;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper json;
    @Autowired SimpleBrokerMessageHandler broker;
    @Autowired SimpMessagingTemplate messaging;
    @Autowired ApplicationEventPublisher events;
    @Autowired OrderService orders;
    @Autowired PlatformTransactionManager transactions;

    private final List<WebSocketStompClient> clients = new ArrayList<>();
    private final List<StompSession> sessions = new ArrayList<>();
    private long owner;
    private long orderId;

    @BeforeEach
    void createOrderFixture() {
        owner = userId("0911000001");
        orderId = ORDER_IDS.incrementAndGet();
        jdbc.update("""
                INSERT INTO orders (id, code, customer_id, restaurant_id, status, receiver_name, receiver_phone,
                                    delivery_address, delivery_lat, delivery_lng, subtotal, total_amount)
                VALUES (:id, :code, :owner, :restaurant, 'PLACED', 'Tracking test', '0900000000',
                        'Test address', 10.76, 106.66, 50000, 50000)
                """, Map.of("id", orderId, "code", "STOMP-" + orderId, "owner", owner, "restaurant", RESTAURANT));
    }

    @AfterEach
    void disconnectClients() {
        try {
            sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
        } finally {
            clients.forEach(WebSocketStompClient::stop);
        }
    }

    @Test
    void ownerReceivesSimulatedLocationAndClientSendIsRejected() throws Exception {
        var subscriber = connect(jwt.generateToken(owner, "CUSTOMER"), false);
        var locations = subscribeAndWaitForBroker(subscriber, trackingTopic());
        var sender = connect(jwt.generateToken(owner, "CUSTOMER"), false);

        sender.session().send(trackingTopic(), json.writeValueAsBytes(Map.of("lat", 0, "lng", 0)));
        awaitRejection(sender);

        // Only the test sends a server-side GPS message. No application publisher is enabled.
        messaging.convertAndSend(trackingTopic(), Map.of("lat", 10.78, "lng", 106.70));
        var received = locations.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        assertNotNull(received);
        assertEquals(10.78, json.readTree(received).path("lat").asDouble());
        assertNull(locations.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void anotherCustomerCannotSubscribeToTracking() throws Exception {
        long otherCustomer = userId("0911000002");
        assertSubscribeRejected(connect(jwt.generateToken(otherCustomer, "CUSTOMER"), false), trackingTopic());
    }

    @Test
    void nativeWebSocketConnectsWithoutJwtButCannotSubscribeToTracking() throws Exception {
        var anonymous = connect(null, false);
        assertSubscribeRejected(anonymous, trackingTopic());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/**", "/topic/orders/**", "/topic/orders/*/location"})
    void broadSubscriptionIsRejectedEvenWithValidCustomerToken(String destination) throws Exception {
        assertSubscribeRejected(connect(jwt.generateToken(owner, "CUSTOMER"), false), destination);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void merchantReceivesNewOrderViaNativeWebSocketOrSockJs(boolean sockJs) throws Exception {
        // Native mirrors the merchant web client. SockJS also exercises the existing no-JWT connection path.
        var merchant = connect(sockJs ? null : jwt.generateToken(userId("0901234567"), "MERCHANT"), sockJs);
        var messages = subscribeAndWaitForBroker(merchant, RESTAURANT_TOPIC);

        new TransactionTemplate(transactions).executeWithoutResult(transaction ->
                events.publishEvent(new OrderPlacedEvent(RESTAURANT, orders.getDetail(orderId))));

        var received = messages.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        assertNotNull(received);
        var order = json.readTree(received);
        assertEquals(orderId, order.path("id").asLong());
        assertEquals(RESTAURANT, order.path("restaurantId").asLong());
    }

    private Connection connect(String token, boolean sockJs) throws Exception {
        var transport = sockJs ? new SockJsClient(List.of(new RestTemplateXhrTransport())) : new StandardWebSocketClient();
        var client = new WebSocketStompClient(transport);
        client.setMessageConverter(new ByteArrayMessageConverter());
        clients.add(client);
        var headers = new StompHeaders();
        if (token != null) {
            headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        var error = new CompletableFuture<StompHeaders>();
        var connected = new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders frameHeaders) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders frameHeaders, Object payload) {
                error.complete(frameHeaders);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                error.completeExceptionally(exception);
            }
        };
        String endpoint = (sockJs ? "http" : "ws") + "://127.0.0.1:" + port + "/ws";
        var session = client.connectAsync(endpoint, null, headers, connected)
                .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        sessions.add(session);
        return new Connection(session, error);
    }

    private BlockingQueue<byte[]> subscribeAndWaitForBroker(Connection connection, String destination) {
        var messages = new LinkedBlockingQueue<byte[]>();
        String subscriptionId = UUID.randomUUID().toString();
        var headers = new StompHeaders();
        headers.setDestination(destination);
        headers.setId(subscriptionId);
        connection.session().subscribe(headers, collect(messages));

        // Wait for the simple broker to process SUBSCRIBE before publishing the fixture.
        var probeHeaders = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        probeHeaders.setDestination(destination);
        var probe = MessageBuilder.createMessage(new byte[0], probeHeaders.getMessageHeaders());
        await().atMost(TIMEOUT).until(() -> broker.getSubscriptionRegistry().findSubscriptions(probe)
                .values().stream().anyMatch(ids -> ids.contains(subscriptionId)));
        return messages;
    }

    private void assertSubscribeRejected(Connection connection, String destination) throws Exception {
        connection.session().subscribe(destination, collect(new LinkedBlockingQueue<>()));
        awaitRejection(connection);
    }

    private void awaitRejection(Connection connection) throws Exception {
        connection.error().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        // The default STOMP handler closes the session after writing ERROR.
        await().atMost(TIMEOUT).until(() -> !connection.session().isConnected());
    }

    private StompFrameHandler collect(BlockingQueue<byte[]> messages) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add((byte[]) payload);
            }
        };
    }

    private String trackingTopic() {
        return "/topic/orders/" + orderId + "/location";
    }

    private long userId(String phone) {
        return jdbc.queryForObject("SELECT id FROM users WHERE phone = :phone", Map.of("phone", phone), Long.class);
    }

    private record Connection(StompSession session, CompletableFuture<StompHeaders> error) {
    }
}
