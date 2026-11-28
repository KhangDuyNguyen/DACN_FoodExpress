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
class TrackingStompAuthorizationLifecycleTest {
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
    @MockitoSpyBean
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
        assertTrue(mysql.isRunning() && mysql.getMappedPort(3306) != 3306);
        try (var connection = jdbc.getJdbcTemplate().getDataSource().getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith(mysql.getJdbcUrl().split("[?]")[0]));
        } catch (java.sql.SQLException exception) { throw new IllegalStateException("Unsafe fixture datasource", exception); }
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

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "gps-expiry", "gps-lock", "gps-owner", "merchant-expiry", "merchant-lock", "merchant-owner",
            "personal-expiry", "personal-lock", "personal-role"})
    void syntheticPostRevocationReceipt(String scenario) throws Exception {
        verifyReceipt(scenario,false);
    }

    private void verifyReceipt(String scenario, boolean sockJs) throws Exception {
        assertTrue(port > 0 && port != 8080 && port != 18083);
        assertTrue(mysql.isRunning());
        assertTrue(mysql.getMappedPort(3306) != 3306);
        try (var c = jdbc.getJdbcTemplate().getDataSource().getConnection()) {
            assertTrue(c.getMetaData().getURL().startsWith(mysql.getJdbcUrl().split("[?]")[0]));
        }
        boolean merchant = scenario.startsWith("merchant");
        boolean personal = scenario.startsWith("personal");
        long subject = merchant ? restaurantOwner(RESTAURANT) : owner;
        String role = merchant ? "MERCHANT" : "CUSTOMER";
        String topic = merchant ? RESTAURANT_TOPIC : personal ? "/user/queue/notifications" : trackingTopic();
        long controlRestaurant = jdbc.queryForObject("""
                SELECT MIN(r.id) FROM restaurants r JOIN merchants m ON m.id=r.merchant_id
                WHERE m.owner_user_id <> :subject
                """, Map.of("subject", subject), Long.class);
        String controlTopic = "/topic/restaurants/" + controlRestaurant + "/orders";
        var control = connect(jwt.generateToken(restaurantOwner(controlRestaurant), "MERCHANT"), false);
        var controlMessages = subscribeAndWaitForBroker(control, controlTopic);
        var expires = java.time.Instant.now().plusSeconds(scenario.endsWith("expiry") ? 6 : 120);
        var token = io.jsonwebtoken.Jwts.builder().subject(Long.toString(subject)).claim("role", role)
                .expiration(java.util.Date.from(expires))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "tracking_websocket_integration_secret_at_least_32_characters".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();
        var target = connect(token, sockJs);
        BlockingQueue<byte[]> targetMessages;
        if (personal) {
            targetMessages = new LinkedBlockingQueue<>();
            target.session().subscribe(topic, collect(targetMessages));
            final var inbox = targetMessages;
            await().atMost(TIMEOUT).until(() -> {
                messaging.convertAndSendToUser("user:" + subject, "/queue/notifications", Map.of("fixture", "warmup"));
                return inbox.poll(100, TimeUnit.MILLISECONDS) != null;
            });
        } else targetMessages = subscribeAndWaitForBroker(target, topic);
        String oldStatus=jdbc.queryForObject("SELECT status FROM users WHERE id=:id",Map.of("id",subject),String.class);
        long oldMerchant=jdbc.queryForObject("SELECT merchant_id FROM restaurants WHERE id=:id",Map.of("id",RESTAURANT),Long.class);
        try {
            if (scenario.endsWith("expiry")) {
                await().atMost(Duration.ofSeconds(8)).until(() -> java.time.Instant.now().isAfter(expires.plusSeconds(1)));
            } else if (scenario.endsWith("lock")) {
                jdbc.update("UPDATE users SET status='LOCKED' WHERE id=:id",Map.of("id",subject));
            } else if (scenario.equals("gps-owner")) {
                long other=userId("0911000002");
                jdbc.update("UPDATE orders SET customer_id=:other WHERE id=:id",Map.of("other",other,"id",orderId));
            } else if (scenario.equals("merchant-owner")) {
                long replacement=jdbc.queryForObject("SELECT merchant_id FROM restaurants WHERE id=:id",Map.of("id",controlRestaurant),Long.class);
                jdbc.update("UPDATE restaurants SET merchant_id=:merchant WHERE id=:id",Map.of("merchant",replacement,"id",RESTAURANT));
            } else {
                jdbc.update("UPDATE users SET role='DRIVER' WHERE id=:id",Map.of("id",subject));
            }
            targetMessages.clear();
            byte[] marker=json.writeValueAsBytes(Map.of("fixture","prompt25c-synthetic-only","case",scenario));
            if (personal) messaging.convertAndSendToUser("user:"+subject,"/queue/notifications",marker);
            else messaging.convertAndSend(topic,marker);
            messaging.convertAndSend(controlTopic,marker);
            boolean received = java.util.Arrays.equals(marker, targetMessages.poll(800,TimeUnit.MILLISECONDS));
        assertEquals(!Boolean.getBoolean("p25d.secure"), received, "Unexpected post-revocation receipt");
            assertTrue(java.util.Arrays.equals(marker, controlMessages.poll(3,TimeUnit.SECONDS)), "Valid control did not receive fixture");
            var h=new HttpHeaders(); h.setBearerAuth(token);
            var response=rest.exchange(personal ? "/api/notifications?limit=1" : merchant ? "/api/merchant/orders/"+orderId : "/api/orders/"+orderId+"/tracking",
                    org.springframework.http.HttpMethod.GET,new HttpEntity<>(h),String.class);
            System.out.println("P25D_RESULT case="+scenario+" syntheticReceived="+received+" validControlReceived=true connected="+
                    target.session().isConnected()+" restStatus="+response.getStatusCode().value());
            if (!scenario.equals("personal-role")) assertTrue(response.getStatusCode().is4xxClientError());
            if (target.session().isConnected()) target.session().disconnect();
            if (!personal) await().atMost(TIMEOUT).until(() -> !subscriptionsFor(topic).containsKey(target.session().getSessionId()));
        } finally {
            jdbc.update("UPDATE users SET status=:status,role=:role WHERE id=:id",Map.of("status",oldStatus,"role",role,"id",subject));
            jdbc.update("UPDATE orders SET customer_id=:owner WHERE id=:id",Map.of("owner",owner,"id",orderId));
            jdbc.update("UPDATE restaurants SET merchant_id=:merchant WHERE id=:id",Map.of("merchant",oldMerchant,"id",RESTAURANT));
        }
    }

    @Test
    void syntheticMerchantOwnershipRestControl() throws Exception {
        syntheticPostRevocationReceipt("merchant-owner");
    }

    @Autowired org.springframework.context.ApplicationContext benchmarkContext;
    @MockitoSpyBean
    vn.hutech.fooddelivery.user.UserRepository permissionUsers;
    @MockitoSpyBean
    OrderTrackingRepository permissionOrders;

    @Test
    void benchmarkSyntheticSubscribers() throws Exception {
        var pool=(com.zaxxer.hikari.HikariDataSource) jdbc.getJdbcTemplate().getDataSource();
        final var waits=java.util.Collections.synchronizedList(new ArrayList<Long>());
        final var acquired=new java.util.concurrent.atomic.AtomicLong();
        final var acquiredNanos=new java.util.concurrent.atomic.AtomicLong();
        pool.setMetricsTrackerFactory((name, stats) -> new com.zaxxer.hikari.metrics.IMetricsTracker() {
            @Override public void recordConnectionAcquiredNanos(long elapsed) {
                acquired.incrementAndGet(); acquiredNanos.addAndGet(elapsed); waits.add(elapsed);
            }
        });
        int max=Integer.getInteger("p25d.maxSubscribers",10);
        for(int count : new int[]{1,10,100}) {
            if(count>max) continue;
            var connections=new ArrayList<Connection>();
            var samples=java.util.Collections.synchronizedList(new ArrayList<Long>());
            var sequence=new AtomicLong();
            for(int index=0;index<count;index++) {
                var connection=connect(jwt.generateToken(owner,"CUSTOMER"),false);
                connections.add(connection);
                String id=UUID.randomUUID().toString();
                var h=new StompHeaders(); h.setId(id); h.setDestination(trackingTopic());
                connection.session().subscribe(h,new StompFrameHandler(){
                    public Type getPayloadType(StompHeaders headers){return byte[].class;}
                    public void handleFrame(StompHeaders headers,Object payload){
                        try {
                            long sent=json.readTree((byte[])payload).path("sentNs").asLong();
                            if(sent>0){samples.add(System.nanoTime()-sent);sequence.incrementAndGet();}
                        }catch(Exception exception){throw new IllegalStateException(exception);}
                    }
                });
                await().atMost(TIMEOUT).until(() -> subscriptionsFor(trackingTopic()).values().stream().anyMatch(ids -> ids.contains(id)));
            }
            for(int warm=0;warm<3;warm++) {
                long expected=(warm+1L)*count;
                messaging.convertAndSend(trackingTopic(),Map.of("sentNs",System.nanoTime()));
                await().atMost(TIMEOUT).until(() -> sequence.get()>=expected);
            }
            samples.clear();sequence.set(0);
            org.mockito.Mockito.clearInvocations(jwt,permissionUsers,permissionOrders);
            acquired.set(0);acquiredNanos.set(0); waits.clear();
            var executor=(org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor) benchmarkContext.getBean("clientOutboundChannelExecutor");
            int queuePeak=0; int activePeak=0;
            int poolPending=0;
            for(int wave=0;wave<20;wave++) {
                long expected=(wave+1L)*count;
                messaging.convertAndSend(trackingTopic(),Map.of("sentNs",System.nanoTime()));
                queuePeak=Math.max(queuePeak,executor.getThreadPoolExecutor().getQueue().size());
                activePeak=Math.max(activePeak,executor.getActiveCount());
                poolPending=Math.max(poolPending,pool.getHikariPoolMXBean().getThreadsAwaitingConnection());
                await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(5)).until(() -> sequence.get()>=expected);
            }
            var ordered=samples.stream().sorted().toList();
            long jwtCalls=org.mockito.Mockito.mockingDetails(jwt).getInvocations().stream().filter(i -> i.getMethod().getName().equals("parse")).count();
            long reads=org.mockito.Mockito.mockingDetails(permissionUsers).getInvocations().stream().filter(i -> i.getMethod().getName().equals("findById")).count()
                    +org.mockito.Mockito.mockingDetails(permissionOrders).getInvocations().stream().filter(i -> i.getMethod().getName().equals("isOwnedBy")).count();
            long heap=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
            System.out.println("P25D_PERF secure="+Boolean.getBoolean("p25d.secure")+" subscribers="+count+" received="+ordered.size()+
                " p95Ms="+(ordered.get((int)Math.ceil(ordered.size()*.95)-1)/1_000_000.0)+" jwt="+jwtCalls+" permissionReads="+reads+
                " poolAcquireCount="+acquired.get()+" poolAcquireMeanMs="+(acquired.get()==0?0:acquiredNanos.get()/1_000_000.0/acquired.get())+
                " sampledPoolPending="+poolPending+" heapBytes="+heap+" sampledQueuePeak="+queuePeak+" sampledActivePeak="+activePeak+
                " poolAcquireP95Ms="+(waits.isEmpty()?0:waits.stream().sorted().toList().get((int)Math.ceil(waits.size()*.95)-1)/1_000_000.0));
            connections.forEach(c -> {if(c.session().isConnected())c.session().disconnect();});
            await().atMost(TIMEOUT).until(() -> subscriptionsFor(trackingTopic()).isEmpty());
            await().atMost(TIMEOUT).until(() -> benchmarkContext.getBean(org.springframework.messaging.simp.user.SimpUserRegistry.class).getUserCount()==0);
            if(Boolean.getBoolean("p25d.secure")) {
                Object state=benchmarkContext.getBean("stompAuthorizationSessions");
                Object life=benchmarkContext.getBean("stompAuthorizationLifecycle");
                await().atMost(TIMEOUT).until(() -> (int)state.getClass().getMethod("size").invoke(state)==0
                        && (int)life.getClass().getMethod("transportCount").invoke(life)==0);
                System.out.println("P25D_STATE_CLEANUP sessions="+state.getClass().getMethod("size").invoke(state)+
                    " subscriptions="+state.getClass().getMethod("subscriptionCount").invoke(state)+
                    " transports="+life.getClass().getMethod("transportCount").invoke(life));
            }
            System.out.println("P25D_CLEANUP subscribers="+count+" brokerEmpty=true userRegistryEmpty=true heapAfterBytes="+
                    (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void sameDestinationFanoutAndUnsubscribe(boolean sockJs) throws Exception {
        var first=connect(jwt.generateToken(owner,"CUSTOMER"),sockJs);
        var second=connect(jwt.generateToken(owner,"CUSTOMER"),sockJs);
        var a=subscribeAndWaitForBroker(first,trackingTopic());
        var b=subscribeAndWaitForBroker(second,trackingTopic());
        messaging.convertAndSend(trackingTopic(),Map.of("fixture","fanout"));
        assertNotNull(a.poll(3,TimeUnit.SECONDS));assertNotNull(b.poll(3,TimeUnit.SECONDS));
        first.session().disconnect();
        await().atMost(TIMEOUT).until(() -> subscriptionsFor(trackingTopic()).size()==1);
        messaging.convertAndSend(trackingTopic(),Map.of("fixture","still-valid"));
        assertNotNull(b.poll(3,TimeUnit.SECONDS));
        System.out.println("P25D_FANOUT sockJs="+sockJs+" validSubscribers=2 remainingReceives=true");
    }

    @ParameterizedTest
    @ValueSource(strings={"LOCKED","DELETED","DRIVER"})
    void newSubscriptionAfterPermissionLoss(String change) throws Exception {
        var connection=connect(jwt.generateToken(owner,"CUSTOMER"),false);
        try {
            if("DRIVER".equals(change)) jdbc.update("UPDATE users SET role='DRIVER' WHERE id=:id",Map.of("id",owner));
            else jdbc.update("UPDATE users SET status=:status WHERE id=:id",Map.of("status",change,"id",owner));
            assertSubscribeRejected(connection,trackingTopic());
            System.out.println("P25D_NEW_SUB change="+change+" rejected=true");
        } finally {jdbc.update("UPDATE users SET role='CUSTOMER',status='ACTIVE' WHERE id=:id",Map.of("id",owner));}
    }


    @Autowired StompAuthorizationSessions authorizationSessions;
    @Autowired StompAuthorizationLifecycle authorizationLifecycle;
    @Autowired org.springframework.messaging.simp.user.SimpUserRegistry userRegistry;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("clientOutboundChannel")
    org.springframework.messaging.support.ExecutorSubscribableChannel outboundChannel;

    private record ObservedSubscription(Connection connection, String serverId, String subscriptionId, BlockingQueue<byte[]> received, StompSession.Subscription subscription) { }

    private ObservedSubscription observedGps(boolean sockJs) throws Exception {
        var connection=connect(jwt.generateToken(owner,"CUSTOMER"),sockJs);
        String id=UUID.randomUUID().toString();
        var headers=new StompHeaders();headers.setId(id);headers.setDestination(trackingTopic());
        var received=new LinkedBlockingQueue<byte[]>();
        var subscription = connection.session().subscribe(headers,collect(received));
        await().atMost(TIMEOUT).until(() -> subscriptionsFor(trackingTopic()).values().stream().anyMatch(ids -> ids.contains(id)));
        String serverId=subscriptionsFor(trackingTopic()).entrySet().stream().filter(entry -> entry.getValue().contains(id)).findFirst().orElseThrow().getKey();
        return new ObservedSubscription(connection,serverId,id,received,subscription);
    }

    @ParameterizedTest
    @ValueSource(strings={"timeout","database-down","missing-state"})
    void permissionFailuresDropActualReceipt(String failure) throws Exception {
        var observed=observedGps(false);
        try {
            if(failure.equals("missing-state")) authorizationSessions.disconnected(observed.serverId());
            else if(failure.equals("timeout")) org.mockito.Mockito.doThrow(new org.springframework.dao.QueryTimeoutException("fixture timeout"))
                    .when(permissionUsers).findById(owner);
            else org.mockito.Mockito.doThrow(new org.springframework.jdbc.CannotGetJdbcConnectionException("fixture unavailable"))
                    .when(permissionUsers).findById(owner);
            messaging.convertAndSend(trackingTopic(),Map.of("fixture","permission-fault"));
            assertNull(observed.received().poll(800,TimeUnit.MILLISECONDS));
            await().atMost(TIMEOUT).until(() -> authorizationSessions.get(observed.serverId())==null
                    && !subscriptionsFor(trackingTopic()).containsKey(observed.serverId()));
            System.out.println("P25D_FAULT kind="+failure+" receipt=false cleanup=true simulatedRepositoryFault=true");
        } finally {org.mockito.Mockito.doCallRealMethod().when(permissionUsers).findById(owner);}
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void controlledOutboundRevokeRace(boolean revokeAfterGuard) throws Exception {
        var observed=observedGps(false);
        var entered=new CountDownLatch(1);
        var release=new CountDownLatch(1);
        var barrier=new org.springframework.messaging.support.ExecutorChannelInterceptor(){
            @Override public org.springframework.messaging.Message<?> beforeHandle(org.springframework.messaging.Message<?> message,
                    org.springframework.messaging.MessageChannel channel,org.springframework.messaging.MessageHandler handler){
                if(SimpMessageType.MESSAGE==SimpMessageHeaderAccessor.getMessageType(message.getHeaders())
                        && observed.serverId().equals(SimpMessageHeaderAccessor.getSessionId(message.getHeaders()))){
                    entered.countDown();
                    try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Fixture release timed out");}
                    catch(InterruptedException exception){Thread.currentThread().interrupt();return null;}
                }
                return message;
            }
        };
        int guard=0;
        for(int i=0;i<outboundChannel.getInterceptors().size();i++)
            if(outboundChannel.getInterceptors().get(i) instanceof TrackingStompOutboundInterceptor)guard=i;
        var savedInterceptors=new ArrayList<>(outboundChannel.getInterceptors());
        var arranged=new ArrayList<>(savedInterceptors);
        arranged.add(revokeAfterGuard?guard+1:0,barrier);
        outboundChannel.setInterceptors(arranged);
        try{
            messaging.convertAndSend(trackingTopic(),Map.of("fixture","controlled-race"));
            assertTrue(entered.await(3,TimeUnit.SECONDS));
            jdbc.update("UPDATE users SET status='LOCKED' WHERE id=:id",Map.of("id",owner));
            release.countDown();
            boolean receipt=observed.received().poll(800,TimeUnit.MILLISECONDS)!=null;
            if(!revokeAfterGuard)assertTrue(!receipt);
            System.out.println("P25D_RACE revokeAfterGuard="+revokeAfterGuard+" receipt="+receipt+" controlledLatch=true");
        }finally{
            release.countDown();outboundChannel.setInterceptors(savedInterceptors);
            jdbc.update("UPDATE users SET status='ACTIVE' WHERE id=:id",Map.of("id",owner));
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"gps-lock","merchant-owner","personal-lock"})
    void sockJsRevocationReceipt(String scenario) throws Exception { verifyReceipt(scenario,true); }

    @Test
    void refreshReconnectAndUnsubscribeCleanup() throws Exception {
        var first=observedGps(false);
        first.subscription().unsubscribe();
        await().atMost(TIMEOUT).until(() -> authorizationSessions.get(first.serverId()).destinations.isEmpty()
                && !subscriptionsFor(trackingTopic()).containsKey(first.serverId()));
        first.connection().session().disconnect();
        await().atMost(TIMEOUT).until(() -> authorizationSessions.get(first.serverId())==null);
        var refreshed=observedGps(false);
        messaging.convertAndSend(trackingTopic(),Map.of("fixture","refreshed-session"));
        assertNotNull(refreshed.received().poll(3,TimeUnit.SECONDS));
        assertTrue(!first.serverId().equals(refreshed.serverId()));
        authorizationSessions.disconnected(first.serverId());
        assertNotNull(authorizationSessions.get(refreshed.serverId()));
        System.out.println("P25D_REFRESH newSessionReceives=true staleCleanupSafe=true unsubscribedRemoved=true");
    }

    @Test
    void malformedProtectedEnvelopeAndProtocolFrames() throws Exception {
        var observed=observedGps(false);
        var guard=new TrackingStompOutboundInterceptor(authorizationSessions,authorizationLifecycle);
        for(var type: new SimpMessageType[]{SimpMessageType.HEARTBEAT,SimpMessageType.CONNECT_ACK,SimpMessageType.DISCONNECT_ACK}){
            var h=SimpMessageHeaderAccessor.create(type);
            var message=MessageBuilder.createMessage(new byte[0],h.getMessageHeaders());
            assertNotNull(guard.beforeHandle(message,outboundChannel,ignored -> {}));
        }
        var h=SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        h.setDestination("/topic/unrelated/example");
        assertNotNull(guard.beforeHandle(MessageBuilder.createMessage(new byte[0],h.getMessageHeaders()),outboundChannel,ignored -> {}));
        h=SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        h.setDestination("/topic/orders/999999/location");h.setSessionId(observed.serverId());h.setSubscriptionId(observed.subscriptionId());
        assertNull(guard.beforeHandle(MessageBuilder.createMessage(new byte[0],h.getMessageHeaders()),outboundChannel,ignored -> {}));
        messaging.convertAndSend(trackingTopic(),Map.of("fixture","after-invalid-envelope"));
        assertNull(observed.received().poll(800,TimeUnit.MILLISECONDS));
        System.out.println("P25D_ENVELOPE invalidProtectedDropped=true protocolUnchanged=true outsideScopeAllowed=true");
    }

    @Test
    void deletedIdleSessionIsReapedAndTransportCloses() throws Exception {
        var observed=observedGps(false);
        try{
            jdbc.update("UPDATE users SET status='DELETED' WHERE id=:id",Map.of("id",owner));
            authorizationLifecycle.reap();
            await().atMost(TIMEOUT).until(() -> authorizationSessions.get(observed.serverId())==null
                    && !subscriptionsFor(trackingTopic()).containsKey(observed.serverId()));
            System.out.println("P25D_REAPER idleDeletedCleanup=true assignmentSchedulerOff=true");
        }finally{jdbc.update("UPDATE users SET status='ACTIVE' WHERE id=:id",Map.of("id",owner));}
    }

    @Test
    void queuedDeliveryRevalidatesAfterRevocationCommit() throws Exception {
        var observed=observedGps(false);
        var executor=(org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor)outboundChannel.getExecutor();
        // Populate lazy core workers before occupying the pool; poolSize may initially be one.
        executor.getThreadPoolExecutor().prestartAllCoreThreads();
        await().atMost(TIMEOUT).until(() -> executor.getActiveCount()==0
                && executor.getThreadPoolExecutor().getQueue().isEmpty());
        int workers=executor.getPoolSize();
        var occupied=new CountDownLatch(workers);
        var release=new CountDownLatch(1);
        for(int index=0;index<workers;index++) executor.execute(() -> {
            occupied.countDown();
            try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException exception){Thread.currentThread().interrupt();}
        });
        try{
            assertTrue(occupied.await(3,TimeUnit.SECONDS));
            messaging.convertAndSend(trackingTopic(),Map.of("fixture","queued-before-guard"));
            await().atMost(Duration.ofSeconds(2)).until(() -> executor.getThreadPoolExecutor().getQueue().size()>0);
            jdbc.update("UPDATE users SET status='LOCKED' WHERE id=:id",Map.of("id",owner));
            release.countDown();
            assertNull(observed.received().poll(800,TimeUnit.MILLISECONDS));
            System.out.println("P25D_EXECUTOR_QUEUE queuedBeforeGuard=true revokeCommitted=true receipt=false");
        }finally{release.countDown();jdbc.update("UPDATE users SET status='ACTIVE' WHERE id=:id",Map.of("id",owner));}
    }

    @Test
    void actualPersonalPublisherHonorsAccountAndRole() throws Exception {
        var connection=connect(jwt.generateToken(owner,"CUSTOMER"),false);
        var received=new LinkedBlockingQueue<byte[]>();
        connection.session().subscribe("/user/queue/notifications",collect(received));
        await().atMost(TIMEOUT).until(() -> {
            messaging.convertAndSendToUser("user:"+owner,"/queue/notifications",Map.of("fixture","warmup"));
            return received.poll(100,TimeUnit.MILLISECONDS)!=null;
        });
        var publisherClass=Class.forName("vn.hutech.fooddelivery.notification.NotificationRealtimePublisher");
        var savedClass=Class.forName("vn.hutech.fooddelivery.notification.CommittedNotificationWriter$SavedNotification");
        var ctor=savedClass.getDeclaredConstructor(long.class,long.class,String.class);ctor.setAccessible(true);
        var saved=ctor.newInstance(owner,990001L,"ORDER_DELIVERED");
        var publish=publisherClass.getDeclaredMethod("publish",savedClass);publish.setAccessible(true);
        var publisher=benchmarkContext.getBean(publisherClass);
        received.clear();publish.invoke(publisher,saved);
        byte[] signal=received.poll(3,TimeUnit.SECONDS);assertNotNull(signal);
        assertEquals(990001,json.readTree(signal).path("notificationId").asLong());
        var spy=org.mockito.Mockito.spy(messaging);
        // Verify the publisher's own suppression at the producer boundary, independently of outbound drops.
        var templateField=publisherClass.getDeclaredField("messaging");templateField.setAccessible(true);
        Object original=templateField.get(publisher);templateField.set(publisher,spy);
        try {
            for(String value:new String[]{"LOCKED","DRIVER"}) {
                if(value.equals("LOCKED"))jdbc.update("UPDATE users SET status='LOCKED' WHERE id=:id",Map.of("id",owner));
                else jdbc.update("UPDATE users SET status='ACTIVE',role='DRIVER' WHERE id=:id",Map.of("id",owner));
                org.mockito.Mockito.clearInvocations(spy);
                publish.invoke(publisher,saved);
                assertTrue(org.mockito.Mockito.mockingDetails(spy).getInvocations().stream().noneMatch(i -> i.getMethod().getName().equals("convertAndSendToUser")));
            }
        }finally{templateField.set(publisher,original);jdbc.update("UPDATE users SET status='ACTIVE',role='CUSTOMER' WHERE id=:id",Map.of("id",owner));}
        System.out.println("P25D_PRODUCTION_PUBLISHER activeSignalReceived=true lockedAndWrongRoleSuppressedAtProducer=true syntheticSavedNotification=true");
    }

    @Test
    void expiredTokenCannotAddNewSubscription() {
        var expiredJwt=new JwtService(new vn.hutech.fooddelivery.security.JwtProperties(
                "tracking_websocket_integration_secret_at_least_32_characters",-1));
        var account=permissionUsers.findById(owner).orElseThrow();
        var mockUsers=org.mockito.Mockito.mock(vn.hutech.fooddelivery.user.UserRepository.class);
        var mockOrders=org.mockito.Mockito.mock(OrderTrackingRepository.class);
        var mockMerchant=org.mockito.Mockito.mock(vn.hutech.fooddelivery.order.OrderRepository.class);
        var state=new StompAuthorizationSessions(jwt,mockUsers,new OrderTrackingSubscriptionAuthorization(mockOrders),
                new MerchantOrderSubscriptionAuthorization(mockMerchant,mockUsers),
                org.mockito.Mockito.mock(PlatformTransactionManager.class));
        var authentication=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                new vn.hutech.fooddelivery.security.AuthUser(owner,account.role()),null,List.of());
        state.connected("fixture-expired-session",expiredJwt.generateToken(owner,"CUSTOMER"),authentication);
        assertNull(state.subscribe("fixture-expired-session","new-subscription",trackingTopic()));
        org.mockito.Mockito.verifyNoInteractions(mockUsers,mockOrders,mockMerchant);
        System.out.println("P25D_SUPPLEMENT expiredNewSubscribeDenied=true");
    }

    @Test
    void boundedRoundRobinAndStateGeneration() {
        var account=permissionUsers.findById(owner).orElseThrow();
        var fakeJwt=org.mockito.Mockito.mock(JwtService.class);
        var fakeUsers=org.mockito.Mockito.mock(vn.hutech.fooddelivery.user.UserRepository.class);
        var fakeOrders=org.mockito.Mockito.mock(OrderTrackingRepository.class);
        var fakeMerchant=org.mockito.Mockito.mock(vn.hutech.fooddelivery.order.OrderRepository.class);
        var identity=new vn.hutech.fooddelivery.security.AuthUser(owner,"CUSTOMER");
        org.mockito.Mockito.when(fakeJwt.parse(org.mockito.ArgumentMatchers.anyString())).thenReturn(identity);
        org.mockito.Mockito.when(fakeUsers.findById(owner)).thenReturn(java.util.Optional.of(account));
        org.mockito.Mockito.when(fakeOrders.isOwnedBy(orderId,owner)).thenReturn(true);
        var registry=new StompAuthorizationSessions(fakeJwt,fakeUsers,new OrderTrackingSubscriptionAuthorization(fakeOrders),
                new MerchantOrderSubscriptionAuthorization(fakeMerchant,fakeUsers),org.mockito.Mockito.mock(PlatformTransactionManager.class));
        var life=new StompAuthorizationLifecycle(registry);
        var authentication=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(identity,null,List.of());
        try {
            for(int index=0;index<130;index++)registry.connected("round-"+index,"fixture-"+index,authentication);
            var checked=new java.util.HashSet<String>();
            for(int tick=0;tick<8 && checked.size()<130;tick++) {
                org.mockito.Mockito.clearInvocations(fakeJwt);
                life.reap();
                var checks=org.mockito.Mockito.mockingDetails(fakeJwt).getInvocations().stream()
                        .filter(call -> call.getMethod().getName().equals("parse")).toList();
                assertTrue(checks.size()<=64);
                checks.forEach(call -> checked.add((String)call.getArgument(0)));
            }
            assertEquals(130,checked.size());
            var old=registry.get("round-0");registry.disconnected(old);
            registry.connected("round-0","replacement",authentication);
            var replacement=registry.get("round-0");registry.disconnected(old);
            org.junit.jupiter.api.Assertions.assertSame(replacement,registry.get("round-0"));
            for(int index=0;index<64;index++)assertNotNull(registry.subscribe("round-0","sub-"+index,trackingTopic()));
            assertNull(registry.subscribe("round-0","overflow-sub",trackingTopic()));
            for(int index=130;index<StompAuthorizationSessions.MAX_SESSIONS;index++)registry.connected("cap-"+index,"fixture",authentication);
            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    () -> registry.connected("overflow-session","fixture",authentication));
            assertEquals(StompAuthorizationSessions.MAX_SESSIONS,registry.size());
            System.out.println("P25D_SUPPLEMENT roundRobinCovered=130 maxChecksPerTick=64 capSessions=2048 capSubscriptions=64 staleGenerationSafe=true fakePermissionDependencies=true");
        }finally{life.stop();}
    }

    @Test
    void idleExpiryClosesOnlyExpiredSocketAndFreshTokenReceives() throws Exception {
        var fresh=observedGps(false);
        var expires=java.time.Instant.now().plusSeconds(2);
        var token=io.jsonwebtoken.Jwts.builder().subject(Long.toString(owner)).claim("role","CUSTOMER")
                .expiration(java.util.Date.from(expires)).signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "tracking_websocket_integration_secret_at_least_32_characters".getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact();
        var old=connect(token,false);
        String subscription=UUID.randomUUID().toString();
        var headers=new StompHeaders();headers.setDestination(trackingTopic());headers.setId(subscription);
        var received=new LinkedBlockingQueue<byte[]>();old.session().subscribe(headers,collect(received));
        await().atMost(TIMEOUT).until(() -> subscriptionsFor(trackingTopic()).values().stream().anyMatch(ids -> ids.contains(subscription)));
        String serverId=subscriptionsFor(trackingTopic()).entrySet().stream().filter(entry -> entry.getValue().contains(subscription)).findFirst().orElseThrow().getKey();
        await().atMost(Duration.ofSeconds(12)).until(() -> authorizationSessions.get(serverId)==null
                && !subscriptionsFor(trackingTopic()).containsKey(serverId) && !old.session().isConnected());
        messaging.convertAndSend(trackingTopic(),Map.of("fixture","valid-after-idle-expiry"));
        assertNotNull(fresh.received().poll(3,TimeUnit.SECONDS));assertNull(received.poll(300,TimeUnit.MILLISECONDS));
        System.out.println("P25D_SUPPLEMENT automaticIdleExpiryCleanup=true validSameDestinationReceives=true expiredReceipt=false distinctJwtLifetimes=true");
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
