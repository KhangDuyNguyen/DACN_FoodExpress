package vn.hutech.fooddelivery.driver.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.assignment.AssignmentService;
import vn.hutech.fooddelivery.driver.earnings.DriverEarningsRepository;
import vn.hutech.fooddelivery.order.OrderRepository;
import vn.hutech.fooddelivery.security.JwtService;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** MySQL/Flyway that trong DB dung rieng; chi tao fixture ID >= 700000, khong dung DB lam viec. */
@SpringBootTest(properties = {
        "spring.config.import=", "app.jwt.secret=0123456789abcdef0123456789abcdef",
        "app.admin.phone=", "app.admin.password=", "app.assignment.scheduling.enabled=false",
        "logging.level.org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration=ERROR"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DriverDeliveryIntegrationTest {
    private static final String DATABASE = "week5b_delivery_test";
    private static final AtomicLong IDS = new AtomicLong(700000);
    @Container @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4").withDatabaseName(DATABASE);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired JwtService jwt;
    @Autowired AssignmentService assignment;
    @Autowired DriverDeliveryService deliveryService;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean DriverDeliveryRepository deliveries;
    @MockitoSpyBean OrderRepository orders;
    @MockitoSpyBean DriverEarningsRepository earnings;
    Fixture f;

    @BeforeEach void createIsolatedFixture() throws Exception {
        assertTrue(mysql.isRunning());
        try (var connection = dataSource.getConnection()) {
            assertEquals(DATABASE, connection.getCatalog());
            assertTrue(connection.getMetaData().getURL().contains(":" + mysql.getMappedPort(3306) + "/" + DATABASE));
        }
        // Khong xoa/sua seed Driver 8, Order 3 hay bat ky du lieu ngoai fixture cua test.
        long customer = user("CUSTOMER"), merchant = user("MERCHANT");
        long driverUser = user("DRIVER"), otherUser = user("DRIVER");
        long merchantId = IDS.incrementAndGet(), restaurant = IDS.incrementAndGet();
        jdbc.update("INSERT INTO merchants (id,owner_user_id,business_name) VALUES (:id,:owner,'Delivery Test')",
                Map.of("id", merchantId, "owner", merchant));
        jdbc.update("""
                INSERT INTO restaurants (id,merchant_id,name,address,phone,lat,lng)
                VALUES (:id,:merchant,'Delivery Fixture','Pickup','0280000000',10.77,106.70)
                """, Map.of("id", restaurant, "merchant", merchantId));
        long driver = driver(driverUser), otherDriver = driver(otherUser), order = IDS.incrementAndGet();
        jdbc.update("""
                INSERT INTO orders (id,code,customer_id,restaurant_id,driver_id,status,receiver_name,receiver_phone,
                                    delivery_address,delivery_lat,delivery_lng,subtotal,delivery_fee,total_amount)
                VALUES (:id,:code,:customer,:restaurant,:driver,'PREPARING','Receiver','0911111111',
                        'Delivery',10.78,106.71,50000,15001,65001)
                """, Map.of("id", order, "code", "W5B" + order, "customer", customer,
                "restaurant", restaurant, "driver", driver));
        long offer = IDS.incrementAndGet();
        var now = assignmentNow();
        jdbc.update("""
                INSERT INTO order_assignments (id,order_id,driver_id,status,offered_at,expires_at,responded_at)
                VALUES (:id,:order,:driver,'ACCEPTED',:offeredAt,:expiresAt,:respondedAt)
                """, Map.of("id", offer, "order", order, "driver", driver,
                "offeredAt", Timestamp.valueOf(now.minusMinutes(2)),
                "expiresAt", Timestamp.valueOf(now.minusSeconds(90)),
                "respondedAt", Timestamp.valueOf(now.minusSeconds(100))));
        f = new Fixture(customer, merchant, driverUser, otherUser, driver, otherDriver, restaurant, order, offer);
        orders.insertStatusHistory(order, "PREPARING", merchant, null);
    }

    private long user(String role) {
        long id = IDS.incrementAndGet();
        jdbc.update("""
                INSERT INTO users (id,full_name,phone,password_hash,role,status)
                VALUES (:id,'Delivery Fixture',:phone,'unused',:role,'ACTIVE')
                """, Map.of("id", id, "phone", String.format("09%08d", id), "role", role));
        return id;
    }

    private long driver(long userId) {
        long id = IDS.incrementAndGet();
        jdbc.update("""
                INSERT INTO drivers (id,user_id,status,is_online,current_lat,current_lng,last_location_at)
                VALUES (:id,:user,'APPROVED',TRUE,10.77,106.70,:recordedAt)
                """, Map.of("id", id, "user", userId, "recordedAt", Timestamp.valueOf(assignmentNow())));
        return id;
    }

    // An - Tuan 5 [MODIFY]: Cung mien gio JVM/JDBC voi Assignment; khong tron SQL NOW() cua DB UTC.
    private static LocalDateTime assignmentNow() {
        // DATETIME(0): cat nano de JDBC/MySQL khong lam tron offered_at/GPS thanh thoi diem tuong lai.
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private String auth(long userId, String role) { return "Bearer " + jwt.generateToken(userId, role); }

    private ResultActions patchStatus(long userId, String status) throws Exception {
        return mvc.perform(patch("/api/driver/orders/{id}/status", f.order())
                .header("Authorization", auth(userId, "DRIVER")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", status))));
    }

    private ResultActions merchant(String target) throws Exception {
        return mvc.perform(patch("/api/merchant/orders/{id}/status", f.order())
                .header("Authorization", auth(f.merchant(), "MERCHANT")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", target, "reason", "Fixture cancellation"))));
    }

    private ResultActions cancel() throws Exception {
        return mvc.perform(post("/api/orders/{id}/cancel", f.order()).header("Authorization", auth(f.customer(), "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Fixture cancellation\"}"));
    }

    private ResultActions accept() throws Exception {
        return mvc.perform(post("/api/driver/offers/{id}/accept", f.offer())
                .header("Authorization", auth(f.driverUser(), "DRIVER")));
    }

    private void setStatus(String status) {
        jdbc.update("UPDATE orders SET status=:status,delivered_at=CASE WHEN :status='DELIVERED' THEN NOW() ELSE NULL END WHERE id=:id",
                Map.of("status", status, "id", f.order()));
    }

    private Map<String, Object> orderRow() {
        return jdbc.queryForMap("SELECT * FROM orders WHERE id=:id", Map.of("id", f.order()));
    }

    private long count(String table) {
        String sql = switch (table) {
            case "order_status_history" -> "SELECT COUNT(*) FROM order_status_history WHERE order_id=:id";
            case "driver_earnings" -> "SELECT COUNT(*) FROM driver_earnings WHERE order_id=:id";
            default -> throw new IllegalArgumentException("Unknown fixture table");
        };
        return jdbc.queryForObject(sql, Map.of("id", f.order()), Long.class);
    }

    private void waitingOffer() {
        var now = assignmentNow();
        jdbc.update("UPDATE orders SET status='PLACED',driver_id=NULL WHERE id=:id", Map.of("id", f.order()));
        jdbc.update("""
                UPDATE order_assignments SET status='OFFERED',offered_at=:offeredAt,expires_at=:expiresAt,responded_at=NULL
                WHERE id=:id
                """, Map.of("id", f.offer(), "offeredAt", Timestamp.valueOf(now),
                "expiresAt", Timestamp.valueOf(now.plusSeconds(30))));
        jdbc.update("DELETE FROM order_status_history WHERE order_id=:id", Map.of("id", f.order()));
        orders.insertStatusHistory(f.order(), "PLACED", f.customer(), null);
    }

    @ParameterizedTest @ValueSource(strings = {"PREPARING", "READY_FOR_PICKUP"})
    void pickupAcceptsBothStatesAndDoesNotRecheckExpiredOfferOrGps(String from) throws Exception {
        setStatus(from);
        jdbc.update("UPDATE drivers SET is_online=FALSE,current_lat=NULL,last_location_at=NULL WHERE id=:id", Map.of("id", f.driver()));
        var before = orderRow();
        patchStatus(f.driverUser(), "PICKED_UP").andExpect(status().isOk());
        assertEquals(2, count("order_status_history"));
        assertEquals(f.driverUser(), jdbc.queryForObject("""
                SELECT changed_by FROM order_status_history WHERE order_id=:id AND status='PICKED_UP'
                """, Map.of("id", f.order()), Long.class));
        assertNotEquals(f.driver(), f.driverUser());
        assertTrue(jdbc.queryForObject("""
                SELECT TIMESTAMPDIFF(SECOND,changed_at,NOW()) BETWEEN 0 AND 30
                FROM order_status_history WHERE order_id=:id AND status='PICKED_UP'
                """, Map.of("id", f.order()), Boolean.class));
        assertEquals(before.get("delivery_fee"), orderRow().get("delivery_fee"));
        assertEquals(0, count("driver_earnings"));
    }

    @Test void acceptedThenPreparedDeliveryFlowKeepsCurrentHistoryAndFiltersCompatible() throws Exception {
        waitingOffer();
        accept().andExpect(status().isOk());
        assertEquals("PREPARING", orderRow().get("status"));
        merchant("PREPARING").andExpect(status().isOk());
        for (String next : List.of("PICKED_UP", "DELIVERING", "DELIVERED")) {
            patchStatus(f.driverUser(), next).andExpect(status().isOk());
            patchStatus(f.driverUser(), next).andExpect(status().isOk());
        }
        assertEquals(5, count("order_status_history")); // fixture + Accept preparation + three driver transitions
        assertEquals(1, count("driver_earnings"));
        assertNotNull(orderRow().get("delivered_at"));
        assertEquals(new BigDecimal("12001"), jdbc.queryForObject(
                "SELECT amount FROM driver_earnings WHERE order_id=:id", Map.of("id", f.order()), BigDecimal.class));
        assertEquals(BigDecimal.ZERO, jdbc.queryForObject(
                "SELECT bonus FROM driver_earnings WHERE order_id=:id", Map.of("id", f.order()), BigDecimal.class));
        mvc.perform(get("/api/driver/orders/current").header("Authorization", auth(f.driverUser(), "DRIVER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentOrder").isEmpty());
        mvc.perform(get("/api/orders/{id}", f.order()).header("Authorization", auth(f.customer(), "CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));
        mvc.perform(get("/api/merchant/orders").param("status", "PICKED_UP")
                        .header("Authorization", auth(f.merchant(), "MERCHANT")))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource({"PLACED,PICKED_UP", "PREPARING,DELIVERING", "PREPARING,DELIVERED", "PICKED_UP,DELIVERED",
            "DELIVERING,PICKED_UP", "CANCELLED,PICKED_UP", "REJECTED,DELIVERING"})
    void invalidStepsLeaveAllBusinessDataUnchanged(String from, String to) throws Exception {
        setStatus(from);
        var before = orderRow(); long history = count("order_status_history");
        patchStatus(f.driverUser(), to).andExpect(status().isConflict());
        assertEquals(before, orderRow());
        assertEquals(history, count("order_status_history"));
        assertEquals(0, count("driver_earnings"));
    }

    @ParameterizedTest @ValueSource(strings = {"OFFERED", "REJECTED", "EXPIRED", "CANCELLED", "wrongDriver", "duplicate"})
    void acceptedAssignmentMustBeUniqueAndBelongToAssignedDriver(String change) throws Exception {
        if (change.equals("wrongDriver")) {
            jdbc.update("UPDATE order_assignments SET driver_id=:driver WHERE id=:id", Map.of("driver", f.otherDriver(), "id", f.offer()));
        } else if (change.equals("duplicate")) {
            jdbc.update("INSERT INTO order_assignments (order_id,driver_id,status) VALUES (:order,:driver,'ACCEPTED')",
                    Map.of("order", f.order(), "driver", f.driver()));
        } else {
            jdbc.update("UPDATE order_assignments SET status=:status WHERE id=:id", Map.of("status", change, "id", f.offer()));
        }
        patchStatus(f.driverUser(), "PICKED_UP").andExpect(status().isConflict());
        assertEquals("PREPARING", orderRow().get("status"));
        assertEquals(1, count("order_status_history"));
    }

    @Test void otherDriverCannotWriteAndOldRequestsAfterProgressAreRejected() throws Exception {
        patchStatus(f.otherUser(), "PICKED_UP").andExpect(status().isForbidden());
        patchStatus(f.driverUser(), "PICKED_UP").andExpect(status().isOk());
        patchStatus(f.driverUser(), "DELIVERING").andExpect(status().isOk());
        var before = orderRow();
        patchStatus(f.driverUser(), "PICKED_UP").andExpect(status().isConflict());
        assertEquals(before, orderRow());
        assertEquals(3, count("order_status_history"));
    }

    @ParameterizedTest @ValueSource(strings = {"history", "earnings", "afterEarnings", "duplicateInsert"})
    void sqlFailureRollsBackOrderTimestampHistoryAndEarning(String stage) throws Exception {
        setStatus("DELIVERING");
        var before = orderRow(); long history = count("order_status_history");
        if (stage.equals("history")) {
            doAnswer(call -> { call.callRealMethod(); failSql(); return null; }).when(orders)
                    .insertStatusHistory(f.order(), "DELIVERED", f.driverUser(), null);
        } else {
            doAnswer(call -> {
                if (!stage.equals("earnings")) call.callRealMethod();
                if (stage.equals("duplicateInsert")) call.callRealMethod();
                else failSql();
                return null;
            }).when(earnings).insert(f.order(), f.driver(), new BigDecimal("12001"));
        }
        patchStatus(f.driverUser(), "DELIVERED").andExpect(status().is(stage.equals("duplicateInsert") ? 409 : 500));
        assertEquals(before, orderRow());
        assertEquals(history, count("order_status_history"));
        assertEquals(0, count("driver_earnings"));
    }

    private void failSql() {
        jdbc.queryForObject("SELECT missing_week5b_fixture_column FROM orders WHERE id=:id", Map.of("id", f.order()), Long.class);
    }

    @Test @WithMockUser(roles = "DRIVER")
    void callerRollbackIncludesDeliveryHistoryAndEarningWithoutRequiresNew() {
        setStatus("DELIVERING");
        var before = orderRow(); long history = count("order_status_history");
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            deliveryService.changeStatus(f.driverUser(), f.order(), "DELIVERED");
            assertEquals(1, count("driver_earnings"));
            transaction.setRollbackOnly();
        });
        assertEquals(before, orderRow());
        assertEquals(history, count("order_status_history"));
        assertEquals(0, count("driver_earnings"));
    }

    @Test void deliveredReplayKeepsMoneyTimestampAndHistoryExactlyAsStored() throws Exception {
        setStatus("DELIVERING");
        patchStatus(f.driverUser(), "DELIVERED").andExpect(status().isOk());
        var before = orderRow();
        var earning = jdbc.queryForMap("SELECT * FROM driver_earnings WHERE order_id=:id", Map.of("id", f.order()));
        var history = jdbc.queryForList("SELECT * FROM order_status_history WHERE order_id=:id ORDER BY id", Map.of("id", f.order()));
        jdbc.update("UPDATE orders SET delivery_fee=99999 WHERE id=:id", Map.of("id", f.order()));
        patchStatus(f.driverUser(), "DELIVERED").andExpect(status().isOk());
        assertEquals(earning, jdbc.queryForMap("SELECT * FROM driver_earnings WHERE order_id=:id", Map.of("id", f.order())));
        assertEquals(before.get("delivered_at"), orderRow().get("delivered_at"));
        assertEquals(history, jdbc.queryForList("SELECT * FROM order_status_history WHERE order_id=:id ORDER BY id", Map.of("id", f.order())));
    }

    @ParameterizedTest @ValueSource(strings = {"missingEarning", "wrongEarningDriver", "prematureEarning", "missingHistory"})
    void inconsistentExistingDataReturnsConflictWithoutRepair(String change) throws Exception {
        setStatus(change.equals("prematureEarning") ? "DELIVERING" : "DELIVERED");
        if (!change.equals("missingEarning")) {
            earnings.insert(f.order(), change.equals("wrongEarningDriver") ? f.otherDriver() : f.driver(), BigDecimal.ONE);
        }
        if (!change.equals("missingHistory")) orders.insertStatusHistory(f.order(), "DELIVERED", f.driverUser(), null);
        var before = orderRow(); long history = count("order_status_history"), money = count("driver_earnings");
        patchStatus(f.driverUser(), "DELIVERED").andExpect(status().isConflict());
        assertEquals(before, orderRow()); assertEquals(history, count("order_status_history"));
        assertEquals(money, count("driver_earnings"));
    }

    @Test void feeRuleChangesAndFreeShipDiscountDoNotReduceOriginalFeeEarning() throws Exception {
        setStatus("DELIVERING");
        jdbc.update("INSERT INTO delivery_fee_rules (name,base_fee,per_km_fee,is_active) VALUES ('Week5b fixture',99999,99999,1)", Map.of());
        jdbc.update("UPDATE orders SET discount_amount=delivery_fee,total_amount=subtotal WHERE id=:id", Map.of("id", f.order()));
        patchStatus(f.driverUser(), "DELIVERED").andExpect(status().isOk());
        assertEquals(new BigDecimal("12001"), jdbc.queryForObject(
                "SELECT amount FROM driver_earnings WHERE order_id=:id", Map.of("id", f.order()), BigDecimal.class));
        assertEquals(new BigDecimal("15001"), orderRow().get("delivery_fee"));
    }

    @Test void customerAndMerchantCannotCancelAcceptedOrderEvenWhenPlaced() throws Exception {
        setStatus("PLACED");
        var before = orderRow();
        cancel().andExpect(status().isConflict()); merchant("CANCELLED").andExpect(status().isConflict());
        assertEquals(before, orderRow());
        assertEquals("ACCEPTED", jdbc.queryForObject("SELECT status FROM order_assignments WHERE id=:id", Map.of("id", f.offer()), String.class));
        assertEquals(1, count("order_status_history"));
    }

    @Test void merchantCannotBypassDriverSteps() throws Exception {
        merchant("DELIVERING").andExpect(status().isConflict());
        setStatus("DELIVERING");
        merchant("DELIVERED").andExpect(status().isConflict());
        assertEquals(0, count("driver_earnings"));
    }

    @Test void preparingBeforeDriverAcceptRemainsAssignableUnderWeek5Contract() throws Exception {
        waitingOffer();
        merchant("PREPARING").andExpect(status().isOk());
        accept().andExpect(status().isOk());
        assertTrue(assignment.createOffer(f.order()).isEmpty());
        assertEquals(f.driver(), ((Number) orderRow().get("driver_id")).longValue());
        assertEquals("PREPARING", orderRow().get("status"));
    }

    @Test void twoConcurrentDeliveredRequestsProduceOneHistoryAndOneEarning() throws Exception {
        setStatus("DELIVERING");
        var acquired = new CountDownLatch(1); var secondEntered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var first = new AtomicBoolean(true); Set<Long> connections = ConcurrentHashMap.newKeySet();
        doAnswer(call -> {
            connections.add(jdbc.queryForObject("SELECT CONNECTION_ID()", Map.of(), Long.class));
            boolean hold = first.compareAndSet(true, false);
            if (!hold) secondEntered.countDown();
            Object value = call.callRealMethod();
            if (hold) { acquired.countDown(); assertTrue(release.await(15, TimeUnit.SECONDS)); }
            return value;
        }).when(deliveries).lockOrder(f.order());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> patchStatus(f.driverUser(), "DELIVERED").andReturn().getResponse().getStatus());
            assertTrue(acquired.await(10, TimeUnit.SECONDS));
            var b = executor.submit(() -> patchStatus(f.driverUser(), "DELIVERED").andReturn().getResponse().getStatus());
            try { assertTrue(secondEntered.await(10, TimeUnit.SECONDS)); }
            finally { release.countDown(); }
            assertEquals(200, a.get(15, TimeUnit.SECONDS)); assertEquals(200, b.get(15, TimeUnit.SECONDS));
            assertEquals(2, connections.size());
        } finally { release.countDown(); }
        assertEquals(1, count("driver_earnings"));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id=:id AND status='DELIVERED'",
                Map.of("id", f.order()), Long.class));
    }

    @Test void concurrentPickupAndMerchantCancelCannotLeaveCancelledAcceptedDelivery() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var pickup = executor.submit(() -> { assertTrue(start.await(10, TimeUnit.SECONDS)); return patchStatus(f.driverUser(), "PICKED_UP").andReturn().getResponse().getStatus(); });
            var cancel = executor.submit(() -> { assertTrue(start.await(10, TimeUnit.SECONDS)); return merchant("CANCELLED").andReturn().getResponse().getStatus(); });
            start.countDown();
            assertEquals(200, pickup.get(15, TimeUnit.SECONDS)); assertEquals(409, cancel.get(15, TimeUnit.SECONDS));
        }
        assertEquals("PICKED_UP", orderRow().get("status"));
    }

    @Test void concurrentAcceptAndCustomerCancelAreSerializedByOrderLock() throws Exception {
        waitingOffer(); var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var accept = executor.submit(() -> { assertTrue(start.await(10, TimeUnit.SECONDS)); return accept().andReturn().getResponse().getStatus(); });
            var cancel = executor.submit(() -> { assertTrue(start.await(10, TimeUnit.SECONDS)); return cancel().andReturn().getResponse().getStatus(); });
            start.countDown();
            int a = accept.get(15, TimeUnit.SECONDS), c = cancel.get(15, TimeUnit.SECONDS);
            assertTrue((a == 200 && c == 409) || (a == 409 && c == 200), "accept=" + a + ", cancel=" + c);
            String offerStatus = jdbc.queryForObject("SELECT status FROM order_assignments WHERE id=:id", Map.of("id", f.offer()), String.class);
            if (a == 200) { assertEquals("PREPARING", orderRow().get("status")); assertEquals("ACCEPTED", offerStatus); }
            else { assertEquals("CANCELLED", orderRow().get("status")); assertNotEquals("ACCEPTED", offerStatus); }
        }
    }

    @Test void deliveryWaitsForUncommittedCancellationAndRechecksCurrentStatus() throws Exception {
        var entered = new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); return call.callRealMethod(); }).when(deliveries).lockOrder(f.order());
        try (var connection = dataSource.getConnection(); var executor = Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.prepareStatement("UPDATE orders SET status='CANCELLED' WHERE id=?")) {
                    statement.setLong(1, f.order()); assertEquals(1, statement.executeUpdate());
                }
                var result = executor.submit(() -> patchStatus(f.driverUser(), "PICKED_UP").andReturn().getResponse().getStatus());
                assertTrue(entered.await(10, TimeUnit.SECONDS)); connection.commit();
                assertEquals(409, result.get(15, TimeUnit.SECONDS));
            } finally { connection.rollback(); }
        }
        assertEquals(1, count("order_status_history")); assertEquals(0, count("driver_earnings"));
    }

    @Test void lockTimeoutReturns503AndDoesNotCommitPartialDelivery() throws Exception {
        var entered = new CountDownLatch(1);
        doAnswer(call -> {
            jdbc.getJdbcTemplate().execute("SET SESSION innodb_lock_wait_timeout=1");
            entered.countDown();
            try { return call.callRealMethod(); }
            finally { jdbc.getJdbcTemplate().execute("SET SESSION innodb_lock_wait_timeout=50"); }
        }).when(deliveries).lockOrder(f.order());
        var before = orderRow();
        try (var connection = dataSource.getConnection(); var executor = Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.prepareStatement("SELECT id FROM orders WHERE id=? FOR UPDATE")) {
                    statement.setLong(1, f.order()); statement.executeQuery().close();
                }
                var result = executor.submit(() -> patchStatus(f.driverUser(), "PICKED_UP").andReturn().getResponse().getStatus());
                assertTrue(entered.await(10, TimeUnit.SECONDS)); assertEquals(503, result.get(10, TimeUnit.SECONDS));
            } finally { connection.rollback(); }
        }
        assertEquals(before, orderRow()); assertEquals(1, count("order_status_history"));
        patchStatus(f.driverUser(), "PICKED_UP").andExpect(status().isOk());
    }

    @Test void swaggerDescribesDeliveryTargetsAndFailureCodes() throws Exception {
        JsonNode spec = json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var operation = spec.path("paths").path("/api/driver/orders/{id}/status").path("patch");
        for (String code : List.of("200", "400", "401", "403", "404", "409", "500", "503")) {
            assertTrue(operation.path("responses").has(code), code);
        }
        assertTrue(operation.has("requestBody"));
    }

    private record Fixture(long customer, long merchant, long driverUser, long otherUser, long driver,
                           long otherDriver, long restaurant, long order, long offer) {
    }
}
