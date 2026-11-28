package vn.hutech.fooddelivery.tracking;

import org.springframework.web.socket.WebSocketHttpHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.driver.DriverRepository;
import vn.hutech.fooddelivery.order.OrderService;
import vn.hutech.fooddelivery.order.event.OrderPlacedEvent;
import vn.hutech.fooddelivery.security.JwtService;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

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

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @LocalServerPort
    int port;
    @Autowired
    NamedParameterJdbcTemplate jdbc;
    @Autowired
    JwtService jwt;
    @Autowired
    ObjectMapper json;
    @Autowired
    SimpleBrokerMessageHandler broker;
    @Autowired
    SimpMessagingTemplate messaging;
    @Autowired
    ApplicationEventPublisher events;
    @Autowired
    OrderService orders;
    @Autowired
    PlatformTransactionManager transactions;
    @Autowired
    TestRestTemplate rest;
    @MockitoSpyBean
    DriverRepository drivers;
    @MockitoSpyBean
    OrderTrackingLocationPublisher locationPublisher;

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

        // This fixture sends GPS directly to isolate the client SEND authorization check.
        messaging.convertAndSend(trackingTopic(), Map.of("lat", 10.78, "lng", 106.70));
        var received = locations.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        assertNotNull(received);
        assertEquals(10.78, json.readTree(received).path("lat").asDouble());
        assertNull(locations.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void committedGpsPointsReachOwnerInRequestOrderAndMatchHistory() throws Exception {
        var driver = assignDriver("DELIVERING");
        var subscriber = connect(jwt.generateToken(owner, "CUSTOMER"), false);
        var messages = subscribeAndWaitForBroker(subscriber, trackingTopic());
        var beforeCommit = new CountDownLatch(1);
        var allowCommit = new CountDownLatch(1);
        doAnswer(invocation -> {
            var updated = invocation.callRealMethod();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    beforeCommit.countDown();
                    try {
                        assertTrue(allowCommit.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                }
            });
            return updated;
        }).doCallRealMethod().when(drivers).updateLocation(eq(driver.id()), any(), any(), any());

        try (var worker = Executors.newSingleThreadExecutor()) {
            var first = worker.submit(() -> postLocation(driver, "10.78012344", "106.70056784"));
            try {
                assertTrue(beforeCommit.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
                assertTrue(savedFixes(driver.id()).isEmpty());
                assertNull(messages.poll(300, TimeUnit.MILLISECONDS), "GPS must not be sent before commit");
            } finally {
                allowCommit.countDown();
            }
            assertEquals(HttpStatus.CREATED, first.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).getStatusCode());
        }
        assertEquals(HttpStatus.CREATED, postLocation(driver, "10.78123456", "106.70123456").getStatusCode());

        var saved = savedFixes(driver.id());
        assertEquals(List.of(orderId, orderId), saved.stream().map(SavedFix::orderId).toList());
        assertEquals(List.of(new BigDecimal("10.7801234"), new BigDecimal("10.7812346")),
                saved.stream().map(fix -> fix.location().lat()).toList());
        assertEquals(List.of(new BigDecimal("106.7005678"), new BigDecimal("106.7012346")),
                saved.stream().map(fix -> fix.location().lng()).toList());
        assertEquals(saved.stream().map(SavedFix::location).toList(),
                List.of(receiveLocation(messages), receiveLocation(messages)));
        assertNull(messages.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void gpsWithoutDeliveringOrderUpdatesProfileWithoutBroadcast() throws Exception {
        var driver = assignDriver("PLACED");
        var subscriber = connect(jwt.generateToken(owner, "CUSTOMER"), false);
        var messages = subscribeAndWaitForBroker(subscriber, trackingTopic());

        assertEquals(HttpStatus.CREATED, postLocation(driver, "10.78", "106.70").getStatusCode());

        var saved = savedFixes(driver.id());
        assertEquals(1, saved.size());
        assertNull(saved.getFirst().orderId());
        assertEquals(new BigDecimal("10.7800000"), profileLatitude(driver.id()));
        assertNull(messages.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void writeFailureBeforeCommitRollsBackInsertedPointWithoutBroadcast() throws Exception {
        var driver = assignDriver("DELIVERING");
        var subscriber = connect(jwt.generateToken(owner, "CUSTOMER"), false);
        var messages = subscribeAndWaitForBroker(subscriber, trackingTopic());
        var failedAfterInsert = new AtomicBoolean();
        doAnswer(invocation -> {
            var updated = invocation.callRealMethod();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    // Use the same REQUIRES_NEW transaction, after INSERT and event registration.
                    assertEquals(orderId, savedFixes(driver.id()).getFirst().orderId());
                    failedAfterInsert.set(true);
                    jdbc.update("UPDATE drivers SET user_id = NULL WHERE id = :id", Map.of("id", driver.id()));
                }
            });
            return updated;
        }).when(drivers).updateLocation(eq(driver.id()), any(), any(), any());

        var response = postLocation(driver, "10.78", "106.70");

        assertTrue(failedAfterInsert.get());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(savedFixes(driver.id()).isEmpty());
        assertNull(profileLatitude(driver.id()));
        assertNull(messages.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void brokerFailureDoesNotChangeCommittedGpsResponse() {
        var driver = assignDriver("DELIVERING");
        doThrow(new MessageDeliveryException("Test broker failure"))
                .when(locationPublisher).publish(eq(orderId), any());

        assertEquals(HttpStatus.CREATED, postLocation(driver, "10.78", "106.70").getStatusCode());

        verify(locationPublisher).publish(eq(orderId), any());
        assertEquals(orderId, savedFixes(driver.id()).getFirst().orderId());
        assertEquals(new BigDecimal("10.7800000"), profileLatitude(driver.id()));
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
    @ValueSource(strings = { "/topic/**", "/topic/orders/**", "/topic/orders/*/location" })
    void broadSubscriptionIsRejectedEvenWithValidCustomerToken(String destination) throws Exception {
        assertSubscribeRejected(connect(jwt.generateToken(owner, "CUSTOMER"), false), destination);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void merchantReceivesNewOrderViaNativeWebSocketOrSockJs(boolean sockJs) throws Exception {
        var merchant = connect(jwt.generateToken(restaurantOwner(RESTAURANT), "MERCHANT"), sockJs);
        var messages = subscribeAndWaitForBroker(merchant, RESTAURANT_TOPIC);

        new TransactionTemplate(transactions).executeWithoutResult(
                transaction -> events.publishEvent(new OrderPlacedEvent(RESTAURANT, orders.getDetail(orderId))));

        var received = messages.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        assertNotNull(received);
        var order = json.readTree(received);
        assertEquals(orderId, order.path("id").asLong());
        assertEquals(RESTAURANT, order.path("restaurantId").asLong());
    }

    @Test
    void merchantReconnectAndAccountSwitchRemoveOldSubscriptions() throws Exception {
        long merchant = restaurantOwner(RESTAURANT);
        long otherRestaurant = jdbc.queryForObject("""
                SELECT MIN(r.id) FROM restaurants r JOIN merchants m ON m.id = r.merchant_id
                WHERE m.owner_user_id <> :owner
                """, Map.of("owner", merchant), Long.class);
        String otherTopic = "/topic/restaurants/" + otherRestaurant + "/orders";
        var first = connect(jwt.generateToken(merchant, "MERCHANT"), false);
        subscribeAndWaitForBroker(first, RESTAURANT_TOPIC);
        first.session().disconnect();
        await().atMost(TIMEOUT).until(() -> subscriptionsFor(RESTAURANT_TOPIC).isEmpty());

        var reconnected = connect(jwt.generateToken(merchant, "MERCHANT"), false);
        subscribeAndWaitForBroker(reconnected, RESTAURANT_TOPIC);
        reconnected.session().disconnect();
        await().atMost(TIMEOUT).until(() -> subscriptionsFor(RESTAURANT_TOPIC).isEmpty());

        var second = connect(jwt.generateToken(restaurantOwner(otherRestaurant), "MERCHANT"), false);
        subscribeAndWaitForBroker(second, otherTopic);
        assertTrue(subscriptionsFor(RESTAURANT_TOPIC).isEmpty());
    }

    @Test
    void oneMerchantSessionCanSubscribeToTwoOwnedRestaurants() throws Exception {
        long merchant = jdbc.queryForObject("""
                SELECT m.owner_user_id FROM restaurants r JOIN merchants m ON m.id = r.merchant_id
                GROUP BY m.owner_user_id HAVING COUNT(*) >= 2 ORDER BY m.owner_user_id LIMIT 1
                """, Map.of(), Long.class);
        var restaurants = jdbc.queryForList("""
                SELECT r.id FROM restaurants r JOIN merchants m ON m.id = r.merchant_id
                WHERE m.owner_user_id = :owner ORDER BY r.id LIMIT 2
                """, Map.of("owner", merchant), Long.class);
        var connection = connect(jwt.generateToken(merchant, "MERCHANT"), false);
        for (long restaurant : restaurants) {
            subscribeAndWaitForBroker(connection, "/topic/restaurants/" + restaurant + "/orders");
        }
    }

    private long restaurantOwner(long restaurantId) {
        return jdbc.queryForObject("""
                SELECT m.owner_user_id FROM restaurants r JOIN merchants m ON m.id = r.merchant_id
                WHERE r.id = :id
                """, Map.of("id", restaurantId), Long.class);
    }

    private Connection connect(String token, boolean sockJs) throws Exception {
        var transport = sockJs ? new SockJsClient(List.of(new RestTemplateXhrTransport()))
                : new StandardWebSocketClient();
        var client = new WebSocketStompClient(transport);
        client.setMessageConverter(new SimpleMessageConverter());
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
        var session = client.connectAsync(endpoint, (WebSocketHttpHeaders) null, headers, connected)
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

        // Wait for the simple broker to process SUBSCRIBE before publishing the
        // fixture.
        await().atMost(TIMEOUT).until(() -> subscriptionsFor(destination)
                .values().stream().anyMatch(ids -> ids.contains(subscriptionId)));
        return messages;
    }

    private org.springframework.util.MultiValueMap<String, String> subscriptionsFor(String destination) {
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setDestination(destination);
        return broker.getSubscriptionRegistry()
                .findSubscriptions(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()));
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

    private DriverFixture assignDriver(String status) {
        long userId = ORDER_IDS.incrementAndGet();
        long driverId = ORDER_IDS.incrementAndGet();
        jdbc.update("""
                INSERT INTO users (id, full_name, phone, password_hash, role)
                VALUES (:id, 'Tracking driver', :phone, 'test-only', 'DRIVER')
                """, Map.of("id", userId, "phone", "0900" + userId));
        jdbc.update("""
                INSERT INTO drivers (id, user_id, status) VALUES (:id, :user, 'APPROVED')
                """, Map.of("id", driverId, "user", userId));
        jdbc.update("UPDATE orders SET driver_id = :driver, status = :status WHERE id = :order",
                Map.of("driver", driverId, "status", status, "order", orderId));
        return new DriverFixture(driverId, jwt.generateToken(userId, "DRIVER"));
    }

    private ResponseEntity<Void> postLocation(DriverFixture driver, String lat, String lng) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(driver.token());
        var body = Map.of("lat", new BigDecimal(lat), "lng", new BigDecimal(lng));
        return rest.postForEntity("/api/driver/location", new HttpEntity<>(body, headers), Void.class);
    }

    private List<SavedFix> savedFixes(long driverId) {
        return jdbc.query("""
                SELECT order_id, lat, lng, recorded_at FROM driver_locations WHERE driver_id = :id ORDER BY id
                """, Map.of("id", driverId), (rs, row) -> new SavedFix(rs.getObject("order_id", Long.class),
                new OrderTrackingResponse.Location(rs.getBigDecimal("lat"), rs.getBigDecimal("lng"),
                        rs.getObject("recorded_at", LocalDateTime.class))));
    }

    private OrderTrackingResponse.Location receiveLocation(BlockingQueue<byte[]> messages) throws Exception {
        var payload = messages.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        assertNotNull(payload);
        return json.readValue(payload, OrderTrackingResponse.Location.class);
    }

    private BigDecimal profileLatitude(long driverId) {
        return jdbc.queryForObject("SELECT current_lat FROM drivers WHERE id = :id",
                Map.of("id", driverId), BigDecimal.class);
    }

    private long userId(String phone) {
        return jdbc.queryForObject("SELECT id FROM users WHERE phone = :phone", Map.of("phone", phone), Long.class);
    }

    private record Connection(StompSession session, CompletableFuture<StompHeaders> error) {
    }

    private record DriverFixture(long id, String token) {
    }

    private record SavedFix(Long orderId, OrderTrackingResponse.Location location) {
    }
}
