package vn.hutech.fooddelivery.assignment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.driver.order.DriverDeliveryService;
import vn.hutech.fooddelivery.order.OrderManagementService;
import vn.hutech.fooddelivery.order.OrderRepository;
import vn.hutech.fooddelivery.order.dto.ChangeOrderStatusRequest;
import vn.hutech.fooddelivery.security.JwtService;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MySQL 8.4/Flyway rieng; moi ghi fixture chi sau khi xac minh catalog va cong container. */
@SpringBootTest(properties = {
        "spring.config.import=", "app.jwt.secret=week5br_test_secret_at_least_thirty_two_chars",
        "app.admin.phone=", "app.admin.password=", "app.assignment.scheduling.enabled=false",
        "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout=5",
        "logging.level.org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration=ERROR"
})
@AutoConfigureMockMvc
@WithMockUser(roles = "DRIVER")
@Testcontainers(disabledWithoutDocker = true)
class OrderAssignmentIntegrationTest {
    private static final String DATABASE = "week5br_assignment_test";
    private static final AtomicLong IDS = new AtomicLong(810000);
    @Container @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4").withDatabaseName(DATABASE);
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired AssignmentService assignments;
    @Autowired AssignmentRecoveryService recovery;
    @Autowired AssignmentOfferLifecycleService lifecycle;
    @Autowired DriverOfferDecisionService decisions;
    @Autowired DriverOfferService driverOffers;
    @Autowired DriverDeliveryService delivery;
    @Autowired OrderManagementService merchant;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoSpyBean OrderRepository orders;
    @MockitoSpyBean AssignmentOfferRepository parents;
    Fixture f;

    @BeforeEach void isolatedFixtureOnly() throws Exception {
        assertTrue(mysql.isRunning());
        try (var connection = dataSource.getConnection()) {
            assertEquals(DATABASE, connection.getCatalog());
            assertTrue(connection.getMetaData().getURL().contains(":" + mysql.getMappedPort(3306) + "/" + DATABASE));
        }
        jdbc.update("UPDATE orders SET status='CANCELLED' WHERE id>=810000", Map.of());
        jdbc.update("UPDATE order_assignments SET status='CANCELLED' WHERE order_id>=810000 AND status='OFFERED'", Map.of());
        jdbc.update("UPDATE drivers SET is_online=FALSE WHERE id>=810000", Map.of());
        long customer = user("CUSTOMER"), owner = user("MERCHANT"), driverUser = user("DRIVER"), otherUser = user("DRIVER");
        long merchantId = IDS.incrementAndGet(), restaurant = IDS.incrementAndGet();
        jdbc.update("INSERT INTO merchants (id,owner_user_id,business_name) VALUES (:id,:owner,'5BR Fixture')",
                Map.of("id", merchantId, "owner", owner));
        jdbc.update("""
                INSERT INTO restaurants (id,merchant_id,name,address,lat,lng)
                VALUES (:id,:merchant,'5BR Restaurant','Fixture pickup',10.77,106.70)
                """, Map.of("id", restaurant, "merchant", merchantId));
        long driver = driver(driverUser, 10.77), otherDriver = driver(otherUser, 10.78);
        long order = IDS.incrementAndGet();
        jdbc.update("""
                INSERT INTO orders (id,code,customer_id,restaurant_id,status,receiver_name,receiver_phone,
                                    delivery_address,delivery_lat,delivery_lng,subtotal,delivery_fee,total_amount)
                VALUES (:id,:code,:customer,:restaurant,'PLACED','Fixture','0900000000',
                        'Fixture delivery',10.78,106.71,50000,15001,65001)
                """, Map.of("id", order, "code", "W5BR" + order, "customer", customer, "restaurant", restaurant));
        orders.insertStatusHistory(order, "PLACED", customer, null);
        f = new Fixture(order, customer, owner, driverUser, driver, otherUser, otherDriver);
    }

    @ParameterizedTest @ValueSource(strings = {"PLACED", "PREPARING", "READY_FOR_PICKUP"})
    void offerGetAcceptAndReplayPreservePreparationProgress(String initial) {
        setStatus(initial);
        var before = row();
        long preparationHistory = history("PREPARING");
        var offer = assignments.createOffer(f.order()).orElseThrow();
        assertEquals(f.driver(), offer.driverId());
        assertEquals(1, driverOffers.getOffers(f.driverUser()).offers().size());
        assertTrue(lifecycle.reconcileOffer(offer.id()).isEmpty());
        var response = decisions.accept(f.driverUser(), offer.id());
        assertEquals("ACCEPTED", response.status());
        assertEquals(f.driver(), ((Number) row().get("driver_id")).longValue());
        assertEquals(initial.equals("PLACED") ? "PREPARING" : initial, row().get("status"));
        assertEquals(preparationHistory + (initial.equals("PLACED") ? 1 : 0), history("PREPARING"));
        if (initial.equals("PLACED")) {
            assertNotNull(row().get("accepted_at"));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id=:id AND status='PREPARING' AND changed_by=:actor",
                    Map.of("id", f.order(), "actor", f.driverUser()), Long.class));
        } else assertEquals(before.get("accepted_at"), row().get("accepted_at"));
        var after = row(); long allHistory = history(null);
        assertEquals(response, decisions.accept(f.driverUser(), offer.id()));
        assertEquals(after, row()); assertEquals(allHistory, history(null));
        assertEquals(1, count("SELECT COUNT(*) FROM order_assignments WHERE order_id=:id AND status='ACCEPTED'"));
    }

    @ParameterizedTest @ValueSource(strings = {"PREPARING", "READY_FOR_PICKUP"})
    void expiryAndRecoveryStillFindNextDriverDuringPreparation(String initial) {
        setStatus(initial);
        var first = assignments.createOffer(f.order()).orElseThrow();
        jdbc.update("UPDATE order_assignments SET expires_at=:past WHERE id=:id",
                Map.of("past", Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)), "id", first.id()));
        var report = recovery.expireAndReassign();
        assertEquals(0, report.errors()); assertEquals(1, report.expired()); assertEquals(1, report.created());
        assertEquals(initial, row().get("status")); assertNull(row().get("driver_id"));
        assertEquals(f.otherDriver(), jdbc.queryForObject("SELECT driver_id FROM order_assignments WHERE order_id=:id AND status='OFFERED'",
                Map.of("id", f.order()), Long.class));
        assertEquals(0, recovery.recoverWaitingOrders().created());
    }

    @ParameterizedTest @ValueSource(strings = {"PREPARING", "READY_FOR_PICKUP"})
    void waitingWithoutCandidatesKeepsOrderUntilDriverBecomesAvailable(String initial) {
        setStatus(initial);
        jdbc.update("UPDATE drivers SET is_online=FALSE WHERE id IN (:a,:b)", Map.of("a", f.driver(), "b", f.otherDriver()));
        assertEquals(0, recovery.recoverWaitingOrders().created());
        assertEquals(initial, row().get("status")); assertNull(row().get("driver_id"));
        assertEquals(0, count("SELECT COUNT(*) FROM order_assignments WHERE order_id=:id"));
        jdbc.update("UPDATE drivers SET is_online=TRUE,last_location_at=:now WHERE id=:id",
                Map.of("id", f.driver(), "now", Timestamp.valueOf(LocalDateTime.now().withNano(0))));
        assertEquals(1, recovery.recoverWaitingOrders().created());
        assertEquals(0, recovery.recoverWaitingOrders().created());
    }

    @Test void merchantConfirmationAfterAcceptIsIdempotentButCannotMoveReadyBackwards() throws Exception {
        var offer = assignments.createOffer(f.order()).orElseThrow();
        decisions.accept(f.driverUser(), offer.id());
        var before = row(); long beforeHistory = history(null);
        mvc.perform(patch("/api/merchant/orders/{id}/status", f.order())
                .header("Authorization", "Bearer " + jwt.generateToken(f.owner(), "MERCHANT"))
                .contentType("application/json").content("{\"status\":\"PREPARING\"}"))
                .andExpect(status().isOk());
        assertEquals(before, row()); assertEquals(beforeHistory, history(null));
        assertThrows(vn.hutech.fooddelivery.common.ForbiddenException.class,
                () -> merchant.changeStatusByMerchant(f.customer(), f.order(), new ChangeOrderStatusRequest("PREPARING", null)));
        merchant.changeStatusByMerchant(f.owner(), f.order(), new ChangeOrderStatusRequest("READY_FOR_PICKUP", null));
        assertThrows(ConflictException.class,
                () -> merchant.changeStatusByMerchant(f.owner(), f.order(), new ChangeOrderStatusRequest("PREPARING", null)));
        assertEquals("READY_FOR_PICKUP", row().get("status")); assertEquals(1, history("PREPARING"));
        assertEquals(1, history("READY_FOR_PICKUP"));
    }

    @ParameterizedTest @ValueSource(strings = {"PREPARING", "READY_FOR_PICKUP"})
    void rejectedDriverRemainsExcludedAndRecoveryCanRepairCommittedReject(String initial) {
        setStatus(initial);
        var first = assignments.createOffer(f.order()).orElseThrow();
        decisions.reject(f.driverUser(), first.id()); // Mo phong crash sau commit, truoc reassign.
        var report = recovery.recoverWaitingOrders();
        assertEquals(0, report.errors()); assertEquals(1, report.created());
        assertEquals(f.otherDriver(), jdbc.queryForObject("SELECT driver_id FROM order_assignments WHERE order_id=:id AND status='OFFERED'",
                Map.of("id", f.order()), Long.class));
        assertEquals(initial, row().get("status"));
    }

    @ParameterizedTest @ValueSource(strings = {"cas", "history"})
    void sqlFailureRollsBackOfferDriverStatusTimestampsAndHistory(String failure) {
        var offer = assignments.createOffer(f.order()).orElseThrow();
        var before = row(); long beforeHistory = history(null);
        var beforeOffer = jdbc.queryForMap("SELECT * FROM order_assignments WHERE id=:id", Map.of("id", offer.id()));
        if (failure.equals("cas")) {
            doAnswer(call -> { call.callRealMethod(); throw new DataIntegrityViolationException("fixture CAS failure"); })
                    .when(orders).updateStatus(f.order(), "PLACED", "PREPARING", null);
        } else {
            doAnswer(call -> { call.callRealMethod(); throw new DataIntegrityViolationException("fixture history failure"); })
                    .when(orders).insertStatusHistory(f.order(), "PREPARING", f.driverUser(), null);
        }
        assertThrows(DataIntegrityViolationException.class, () -> decisions.accept(f.driverUser(), offer.id()));
        assertEquals(before, row()); assertEquals(beforeHistory, history(null));
        assertEquals(beforeOffer, jdbc.queryForMap("SELECT * FROM order_assignments WHERE id=:id", Map.of("id", offer.id())));
    }

    @ParameterizedTest @ValueSource(strings = {"PREPARING", "READY_FOR_PICKUP"})
    void pickupAndDeliveryKeepEightyPercentAndSingleEarning(String initial) {
        setStatus(initial); var offer = assignments.createOffer(f.order()).orElseThrow();
        decisions.accept(f.driverUser(), offer.id());
        assertThrows(ConflictException.class, () -> delivery.changeStatus(f.driverUser(), f.order(), "DELIVERED"));
        delivery.changeStatus(f.driverUser(), f.order(), "PICKED_UP");
        delivery.changeStatus(f.driverUser(), f.order(), "DELIVERING");
        delivery.changeStatus(f.driverUser(), f.order(), "DELIVERED");
        delivery.changeStatus(f.driverUser(), f.order(), "DELIVERED");
        assertEquals(new BigDecimal("12001"), jdbc.queryForObject("SELECT amount FROM driver_earnings WHERE order_id=:id",
                Map.of("id", f.order()), BigDecimal.class));
        assertEquals(1, count("SELECT COUNT(*) FROM driver_earnings WHERE order_id=:id"));
        assertEquals(1, history("DELIVERED"));
    }

    @ParameterizedTest @ValueSource(strings = {"PREPARING", "READY_FOR_PICKUP"})
    void merchantHttpCanPrepareBeforeAssignmentThenDriverHttpAcceptsWithoutRegression(String target) throws Exception {
        merchantHttp(f.owner(), "MERCHANT", "PREPARING").andExpect(status().isOk());
        if (target.equals("READY_FOR_PICKUP")) merchantHttp(f.owner(), "MERCHANT", target).andExpect(status().isOk());
        assertEquals(target, row().get("status")); assertNull(row().get("driver_id"));
        assertEquals(1, history("PREPARING"));
        var offer = assignments.createOffer(f.order()).orElseThrow();
        mvc.perform(post("/api/driver/offers/{id}/accept", offer.id())
                .header("Authorization", "Bearer " + jwt.generateToken(f.driverUser(), "DRIVER")))
                .andExpect(status().isOk());
        assertEquals(target, row().get("status")); assertEquals(f.driver(), ((Number) row().get("driver_id")).longValue());
        assertEquals(1, history("PREPARING")); assertEquals(target.equals("READY_FOR_PICKUP") ? 1 : 0, history("READY_FOR_PICKUP"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id=:id AND status='PREPARING' AND changed_by=:actor",
                Map.of("id", f.order(), "actor", f.owner()), Long.class));
    }

    @ParameterizedTest @CsvSource({"PLACED,READY_FOR_PICKUP", "READY_FOR_PICKUP,PREPARING", "PICKED_UP,PREPARING", "DELIVERING,PREPARING", "DELIVERED,PREPARING"})
    void merchantHttpCannotSkipPreparationOrMoveBackwards(String from, String target) throws Exception {
        setStatus(from); var before = row(); long beforeHistory = history(null);
        merchantHttp(f.owner(), "MERCHANT", target).andExpect(status().isConflict());
        assertEquals(before, row()); assertEquals(beforeHistory, history(null));
    }

    @Test void readyForPickupValidationStillRequiresAuthenticatedMerchantAndRestaurantOwnership() throws Exception {
        setStatus("PREPARING"); var before = row(); long beforeHistory = history(null);
        long otherMerchant = user("MERCHANT");
        merchantHttp(otherMerchant, "MERCHANT", "READY_FOR_PICKUP").andExpect(status().isForbidden());
        merchantHttp(f.customer(), "CUSTOMER", "READY_FOR_PICKUP").andExpect(status().isForbidden());
        merchantHttp(f.driverUser(), "DRIVER", "READY_FOR_PICKUP").andExpect(status().isForbidden());
        // Xoa @WithMockUser cua test cho request rieng nay; khong doi Security production.
        mvc.perform(patch("/api/merchant/orders/{id}/status", f.order())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())
                .contentType("application/json").content("{\"status\":\"READY_FOR_PICKUP\"}"))
                .andExpect(status().isUnauthorized());
        merchantHttp(f.owner(), "MERCHANT", "NOT_A_STATUS").andExpect(status().isBadRequest());
        assertEquals(before, row()); assertEquals(beforeHistory, history(null));
    }

    @ParameterizedTest @CsvSource({"true,PREPARING", "false,PREPARING", "true,READY_FOR_PICKUP", "false,READY_FOR_PICKUP"})
    void merchantAndAcceptSerializeOnOrderWithoutRegression(boolean merchantFirst, String target) throws Exception {
        var offer = assignments.createOffer(f.order()).orElseThrow();
        var locked = new CountDownLatch(1); var otherEntered = new CountDownLatch(1); var release = new CountDownLatch(1);
        Set<Long> connections = ConcurrentHashMap.newKeySet(); Set<String> isolation = ConcurrentHashMap.newKeySet();
        if (merchantFirst) {
            doAnswer(call -> { Object result = call.callRealMethod(); observe(connections, isolation); locked.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS)); return result; }).when(orders).lockOrder(f.order());
            doAnswer(call -> { observe(connections, isolation); otherEntered.countDown(); return call.callRealMethod(); }).when(parents).lockOrder(f.order());
        } else {
            doAnswer(call -> { Object result = call.callRealMethod(); observe(connections, isolation); locked.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS)); return result; }).when(parents).lockOrder(f.order());
            doAnswer(call -> { observe(connections, isolation); otherEntered.countDown(); return call.callRealMethod(); }).when(orders).lockOrder(f.order());
        }
        java.util.concurrent.Callable<Object> accept = () -> decisions.accept(f.driverUser(), offer.id());
        java.util.concurrent.Callable<Object> prepare = () -> {
            merchant.changeStatusByMerchant(f.owner(), f.order(), new ChangeOrderStatusRequest("PREPARING", null));
            if (target.equals("READY_FOR_PICKUP")) merchant.changeStatusByMerchant(f.owner(), f.order(), new ChangeOrderStatusRequest(target, null));
            return row().get("status");
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(merchantFirst ? prepare : accept);
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            var second = executor.submit(merchantFirst ? accept : prepare);
            try { assertTrue(otherEntered.await(10, TimeUnit.SECONDS)); } finally { release.countDown(); }
            first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
        } finally { release.countDown(); }
        assertEquals(target, row().get("status")); assertEquals(f.driver(), ((Number) row().get("driver_id")).longValue());
        assertEquals(1, history("PREPARING")); assertEquals(target.equals("READY_FOR_PICKUP") ? 1 : 0, history("READY_FOR_PICKUP"));
        assertEquals(2, connections.size()); assertTrue(isolation.contains("READ-COMMITTED"));
        System.out.println("5BR_MYSQL_ORDER_LOCK merchantFirst=" + merchantFirst + " target=" + target + " isolation=" + isolation + " connections=" + connections);
    }

    @ParameterizedTest @ValueSource(strings = {"PREPARING", "READY_FOR_PICKUP"})
    void twoDriversCompeteOnlyOneAcceptsDuringPreparation(String initial) throws Exception {
        setStatus(initial); var first = assignments.createOffer(f.order()).orElseThrow();
        long otherOffer = IDS.incrementAndGet();
        jdbc.update("""
                INSERT INTO order_assignments (id,order_id,driver_id,status,offered_at,expires_at)
                VALUES (:id,:order,:driver,'OFFERED',:now,:expires)
                """, Map.of("id", otherOffer, "order", f.order(), "driver", f.otherDriver(),
                        "now", Timestamp.valueOf(LocalDateTime.now()), "expires", Timestamp.valueOf(LocalDateTime.now().plusSeconds(30))));
        var entered = new CountDownLatch(2); var release = new CountDownLatch(1);
        Set<Long> connections = ConcurrentHashMap.newKeySet(); Set<String> isolation = ConcurrentHashMap.newKeySet();
        doAnswer(call -> { observe(connections, isolation); entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS));
            return call.callRealMethod(); }).when(parents).lockOrder(f.order());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> acceptResult(f.driverUser(), first.id()));
            var b = executor.submit(() -> acceptResult(f.otherUser(), otherOffer));
            try { assertTrue(entered.await(10, TimeUnit.SECONDS)); } finally { release.countDown(); }
            assertEquals(List.of("ACCEPTED", "CONFLICT"), java.util.stream.Stream.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)).sorted().toList());
        } finally { release.countDown(); }
        assertEquals(2, connections.size()); assertEquals(Set.of("READ-COMMITTED"), isolation);
        assertEquals(initial, row().get("status")); assertEquals(1, count("SELECT COUNT(*) FROM order_assignments WHERE order_id=:id AND status='ACCEPTED'"));
        System.out.println("5BR_MYSQL_TWO_DRIVERS status=" + initial + " connections=" + connections + " isolation=" + isolation);
    }

    private void observe(Set<Long> connections, Set<String> isolation) {
        connections.add(jdbc.queryForObject("SELECT CONNECTION_ID()", Map.of(), Long.class));
        isolation.add(jdbc.queryForObject("SELECT @@transaction_isolation", Map.of(), String.class));
    }
    private org.springframework.test.web.servlet.ResultActions merchantHttp(long user, String role, String target) throws Exception {
        return mvc.perform(patch("/api/merchant/orders/{id}/status", f.order())
                .header("Authorization", "Bearer " + jwt.generateToken(user, role))
                .contentType("application/json").content("{\"status\":\"" + target + "\"}"));
    }
    private String acceptResult(long user, long offer) {
        try { return decisions.accept(user, offer).status(); } catch (ConflictException expected) { return "CONFLICT"; }
    }
    private long user(String role) {
        long id = IDS.incrementAndGet();
        jdbc.update("INSERT INTO users (id,full_name,phone,email,password_hash,role,status) VALUES (:id,'5BR Fixture',:phone,:email,'not-a-login-hash',:role,'ACTIVE')",
                Map.of("id", id, "phone", "09" + id, "email", "5br-" + id + "@example.test", "role", role)); return id;
    }
    private long driver(long user, double lat) {
        long id = IDS.incrementAndGet();
        jdbc.update("INSERT INTO drivers (id,user_id,status,is_online,current_lat,current_lng,last_location_at) VALUES (:id,:user,'APPROVED',TRUE,:lat,106.70,:now)",
                Map.of("id", id, "user", user, "lat", lat, "now", Timestamp.valueOf(LocalDateTime.now().withNano(0)))); return id;
    }
    private void setStatus(String status) {
        if (!status.equals("PLACED")) {
            assertEquals(1, orders.updateStatus(f.order(), "PLACED", status, null));
            orders.insertStatusHistory(f.order(), status, f.owner(), null);
        }
    }
    private Map<String, Object> row() { return jdbc.queryForMap("SELECT * FROM orders WHERE id=:id", Map.of("id", f.order())); }
    private long history(String status) {
        return status == null ? count("SELECT COUNT(*) FROM order_status_history WHERE order_id=:id")
                : jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id=:id AND status=:status", Map.of("id", f.order(), "status", status), Long.class);
    }
    private long count(String sql) { return jdbc.queryForObject(sql, Map.of("id", f.order()), Long.class); }
    private record Fixture(long order, long customer, long owner, long driverUser, long driver, long otherUser, long otherDriver) {}
}
