package vn.hutech.fooddelivery.driver;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.driver.earnings.DriverEarningsReadRepository;
import vn.hutech.fooddelivery.driver.order.DriverOrderRepository;
import vn.hutech.fooddelivery.security.JwtService;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Chi ghi fixture trong MySQL 8.4/Flyway dung rieng, khong dung database lam viec. */
@SpringBootTest(properties = {
        "spring.config.import=", "debug=false", "app.jwt.secret=0123456789abcdef0123456789abcdef",
        "app.admin.phone=", "app.admin.password=", "app.assignment.scheduling.enabled=false",
        "logging.level.org.springframework.jdbc=INFO",
        "logging.level.org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration=ERROR"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DriverReadIntegrationTest {
    private static final String DATABASE = "week5c_driver_read_test";
    private static final String EARNINGS = "/api/driver/earnings", HISTORY = "/api/driver/orders/history";
    private static final AtomicLong IDS = new AtomicLong(800000);
    private static final long START = Instant.parse("2026-11-10T17:00:00Z").getEpochSecond();
    @Container @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4").withDatabaseName(DATABASE);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JwtService jwt;
    @MockitoSpyBean DriverOrderRepository orders;
    @MockitoSpyBean DriverEarningsReadRepository earnings;
    Fixture f;

    @BeforeEach void createOnlyDisposableFixture() throws Exception {
        assertTrue(mysql.isRunning());
        try (var connection = dataSource.getConnection()) {
            assertEquals(DATABASE, connection.getCatalog());
            assertTrue(connection.getMetaData().getURL().contains(":" + mysql.getMappedPort(3306) + "/" + DATABASE));
        }
        long customer = user("CUSTOMER"), merchant = user("MERCHANT");
        long firstUser = user("DRIVER"), secondUser = user("DRIVER");
        long merchantId = IDS.incrementAndGet(), restaurant = IDS.incrementAndGet();
        jdbc.update("INSERT INTO merchants (id,owner_user_id,business_name) VALUES (:id,:owner,'Read Fixture')",
                Map.of("id", merchantId, "owner", merchant));
        jdbc.update("""
                INSERT INTO restaurants (id,merchant_id,name,address,phone,lat,lng)
                VALUES (:id,:merchant,'Read Restaurant','Private pickup','0280000000',10.77,106.70)
                """, Map.of("id", restaurant, "merchant", merchantId));
        f = new Fixture(customer, merchant, firstUser, secondUser, driver(firstUser), driver(secondUser), restaurant);
    }

    private long user(String role) {
        long id = IDS.incrementAndGet();
        jdbc.update("""
                INSERT INTO users (id,full_name,phone,password_hash,role,status)
                VALUES (:id,'Read Fixture',:phone,'unused',:role,'ACTIVE')
                """, Map.of("id", id, "phone", String.format("09%08d", id), "role", role));
        return id;
    }

    private long driver(long user) {
        long id = IDS.incrementAndGet();
        jdbc.update("INSERT INTO drivers (id,user_id,status,is_online) VALUES (:id,:user,'APPROVED',FALSE)",
                Map.of("id", id, "user", user));
        return id;
    }

    private long order(long driver, String status, Long deliveredEpoch) {
        long id = IDS.incrementAndGet();
        jdbc.update("""
                INSERT INTO orders (id,code,customer_id,restaurant_id,driver_id,status,receiver_name,receiver_phone,
                                    delivery_address,delivery_lat,delivery_lng,subtotal,delivery_fee,total_amount,delivered_at)
                VALUES (:id,:code,:customer,:restaurant,:driver,:status,'Private receiver','0911111111',
                        'Private delivery address',10.78,106.71,50000,15000,65000,FROM_UNIXTIME(:delivered))
                """, new MapSqlParameterSource().addValue("id", id).addValue("code", "W5C" + id)
                .addValue("customer", f.customer()).addValue("restaurant", f.restaurant()).addValue("driver", driver)
                .addValue("status", status).addValue("delivered", deliveredEpoch));
        return id;
    }

    private void earning(long driver, long order, int amount, int bonus, long createdEpoch) {
        jdbc.update("""
                INSERT INTO driver_earnings (driver_id,order_id,amount,bonus,created_at)
                VALUES (:driver,:order,:amount,:bonus,FROM_UNIXTIME(:created))
                """, Map.of("driver", driver, "order", order, "amount", amount, "bonus", bonus, "created", createdEpoch));
    }

    private ResultActions request(MockHttpServletRequestBuilder builder, long user) throws Exception {
        return mvc.perform(builder.header("Authorization", "Bearer " + jwt.generateToken(user, "DRIVER"))
                .accept(MediaType.APPLICATION_JSON));
    }

    private ResultActions income(String from, String to) throws Exception {
        var builder = get(EARNINGS);
        if (from != null) builder.param("from", from);
        if (to != null) builder.param("to", to);
        return request(builder, f.firstUser());
    }

    private JsonNode body(ResultActions response) throws Exception {
        return json.readTree(response.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test void storedAmountsAndBonusBelongOnlyToCurrentDriverAndAreNotRecalculated() throws Exception {
        long own = order(f.firstDriver(), "DELIVERED", START - 2 * 86400), other = order(f.secondDriver(), "DELIVERED", START);
        earning(f.firstDriver(), own, 123, 7, START);
        earning(f.secondDriver(), other, 99999, 999, START);
        jdbc.update("INSERT INTO delivery_fee_rules (name,base_fee,per_km_fee,is_active) VALUES ('5C changed rule',99999,99999,1)", Map.of());
        var result = body(income(null, null));
        assertNotEquals(f.firstUser(), f.firstDriver());
        assertEquals(123, result.path("baseAmount").asInt()); assertEquals(7, result.path("bonus").asInt());
        assertEquals(130, result.path("totalAmount").asInt()); assertEquals(1, result.path("items").size());
        assertEquals(own, result.path("items").get(0).path("orderId").asLong());
        assertEquals(130, result.path("items").get(0).path("totalAmount").asInt());
        var second = body(request(get(EARNINGS).param("driverId", Long.toString(f.firstDriver())), f.secondUser()));
        assertEquals(other, second.path("items").get(0).path("orderId").asLong());
    }

    @ParameterizedTest
    @CsvSource(value = {"2026-11-11|2026-11-11|2|6|2|8", "2026-11-11|NULL|3|14|3|17",
            "NULL|2026-11-11|3|7|3|10", "NULL|NULL|4|15|4|19"}, delimiter = '|', nullValues = "NULL")
    void vietnamMidnightAndEndOfDayAreFilteredByLedgerCreationDate(String from, String to, int rows, int base, int bonus, int total) throws Exception {
        long[] dates = {START - 1, START, START + 86399, START + 86400};
        int[] amounts = {1, 2, 4, 8};
        for (int i = 0; i < dates.length; i++) {
            long id = order(f.firstDriver(), "DELIVERED", START - 5 * 86400);
            earning(f.firstDriver(), id, amounts[i], 1, dates[i]);
        }
        var result = body(income(from, to));
        assertEquals(rows, result.path("items").size()); assertEquals(base, result.path("baseAmount").asInt());
        assertEquals(bonus, result.path("bonus").asInt()); assertEquals(total, result.path("totalAmount").asInt());
        if (from != null && to != null) {
            assertEquals("2026-11-11T23:59:59", result.path("items").get(0).path("createdAt").asText());
            assertEquals("2026-11-11T00:00:00", result.path("items").get(1).path("createdAt").asText());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"+00:00", "+07:00"})
    void dateFilteringAndDisplayUseVietnamTimeForEitherDatabaseSessionOffset(String zone) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            String previous = jdbc.queryForObject("SELECT @@session.time_zone", Map.of(), String.class);
            try {
                jdbc.update("SET SESSION time_zone = :zone", Map.of("zone", zone));
                long id = order(f.firstDriver(), "DELIVERED", START);
                earning(f.firstDriver(), id, 123, 7, START);
                var result = body(income("2026-11-11", "2026-11-11"));
                assertEquals(1, result.path("items").size());
                assertEquals("2026-11-11T00:00:00", result.path("items").get(0).path("createdAt").asText());
                var history = body(request(get(HISTORY), f.firstUser()));
                assertEquals("2026-11-11T00:00:00", history.path("items").get(0).path("deliveredAt").asText());
            } catch (Exception failure) {
                throw new AssertionError(failure);
            } finally {
                jdbc.update("SET SESSION time_zone = :zone", Map.of("zone", previous));
                tx.setRollbackOnly();
            }
        });
    }

    @Test void emptyEarningsAndHistoryAreSuccessfulAndDoNotBackfillLegacyOrder() throws Exception {
        long legacy = order(f.firstDriver(), "DELIVERED", START);
        var income = body(income(null, null));
        assertEquals(0, income.path("totalAmount").asInt()); assertEquals(0, income.path("items").size());
        var history = body(request(get(HISTORY), f.firstUser()));
        assertEquals(legacy, history.path("items").get(0).path("orderId").asLong());
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM driver_earnings WHERE order_id=:id", Map.of("id", legacy), Long.class));
        assertEquals(0, body(request(get(HISTORY), f.secondUser())).path("totalItems").asInt());
    }

    @ParameterizedTest @ValueSource(strings = {"PENDING_PAYMENT", "PLACED", "ACCEPTED", "PREPARING", "READY_FOR_PICKUP",
            "PICKED_UP", "DELIVERING", "CANCELLED", "REJECTED"})
    void historyExcludesEveryNonDeliveredStateAndOtherDrivers(String status) throws Exception {
        long own = order(f.firstDriver(), "DELIVERED", START);
        order(f.firstDriver(), status, START + 1);
        order(f.secondDriver(), "DELIVERED", START + 2);
        var history = body(request(get(HISTORY), f.firstUser()));
        assertEquals(1, history.path("totalItems").asInt());
        assertEquals(own, history.path("items").get(0).path("orderId").asLong());
    }

    @Test void historyHasStableTieBreakPaginationAndNoPrivateCustomerFieldsOrDuplicates() throws Exception {
        long first = order(f.firstDriver(), "DELIVERED", START), second = order(f.firstDriver(), "DELIVERED", START + 100),
                third = order(f.firstDriver(), "DELIVERED", START + 100);
        jdbc.update("INSERT INTO order_assignments (order_id,driver_id,status) VALUES (:order,:driver,'EXPIRED'),(:order,:driver,'REJECTED')",
                Map.of("order", third, "driver", f.firstDriver()));
        var page0 = body(request(get(HISTORY).param("size", "2"), f.firstUser()));
        var page1 = body(request(get(HISTORY).param("page", "1").param("size", "2"), f.firstUser()));
        var page2 = body(request(get(HISTORY).param("page", "2").param("size", "2"), f.firstUser()));
        assertEquals(3, page0.path("totalItems").asInt()); assertEquals(2, page0.path("totalPages").asInt());
        assertEquals(third, page0.path("items").get(0).path("orderId").asLong());
        assertEquals(second, page0.path("items").get(1).path("orderId").asLong());
        assertEquals(first, page1.path("items").get(0).path("orderId").asLong());
        assertEquals(0, page2.path("items").size());
        Set<String> fields = new HashSet<>(); page0.path("items").get(0).fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("orderId", "orderCode", "restaurantId", "restaurantName", "status", "deliveredAt"), fields);
    }

    @Test void historyPreservesLegacyNullDeliveryTimeAtTheEnd() throws Exception {
        long missingTime = order(f.firstDriver(), "DELIVERED", null), known = order(f.firstDriver(), "DELIVERED", START);
        var history = body(request(get(HISTORY), f.firstUser()));
        assertEquals(known, history.path("items").get(0).path("orderId").asLong());
        assertEquals(missingTime, history.path("items").get(1).path("orderId").asLong());
        assertTrue(history.path("items").get(1).path("deliveredAt").isNull());
    }

    @Test void earningsUseLedgerIdToBreakTimestampTiesAndReturnEachOrderOnce() throws Exception {
        long first = order(f.firstDriver(), "DELIVERED", START), second = order(f.firstDriver(), "DELIVERED", START);
        earning(f.firstDriver(), second, 2, 1, START);
        earning(f.firstDriver(), first, 4, 1, START);
        jdbc.update("INSERT INTO order_assignments (order_id,driver_id,status) VALUES (:order,:driver,'EXPIRED'),(:order,:driver,'REJECTED')",
                Map.of("order", first, "driver", f.firstDriver()));
        var result = body(income(null, null));
        assertEquals(2, result.path("items").size());
        assertEquals(first, result.path("items").get(0).path("orderId").asLong());
        assertEquals(second, result.path("items").get(1).path("orderId").asLong());
        assertEquals(8, result.path("totalAmount").asInt());
    }

    @ParameterizedTest @ValueSource(strings = {"", "2026-02-30", "2026-2-01", "2026-13-01", "not-a-date"})
    void invalidDatesAre400(String value) throws Exception {
        income(value, null).andExpect(status().isBadRequest());
        income(null, value).andExpect(status().isBadRequest());
    }

    @Test void invalidRangeAndDatabaseUnsupportedBoundsAre400() throws Exception {
        income("2026-11-12", "2026-11-11").andExpect(status().isBadRequest());
        income("0001-01-01", null).andExpect(status().isBadRequest());
        income(null, "9999-12-31").andExpect(status().isBadRequest());
    }

    @ParameterizedTest @CsvSource({"page,-1", "page,abc", "size,0", "size,51", "size,-1"})
    void invalidHistoryPaginationIs400(String key, String value) throws Exception {
        request(get(HISTORY).param(key, value), f.firstUser()).andExpect(status().isBadRequest());
    }

    @ParameterizedTest @ValueSource(strings = {EARNINGS, HISTORY})
    void missingDriverProfileIs404(String path) throws Exception {
        request(get(path), user("DRIVER")).andExpect(status().isNotFound());
    }

    @ParameterizedTest @ValueSource(strings = {"driver", "earnings", "count", "history"})
    void queryFailuresRemain500ApiErrorsInsteadOfEmptyData(String stage) throws Exception {
        if (stage.equals("driver")) doAnswer(call -> { failSql(); return call.callRealMethod(); }).when(orders).findDriverId(f.firstUser());
        if (stage.equals("earnings")) doAnswer(call -> { failSql(); return call.callRealMethod(); }).when(earnings).findEarnings(f.firstDriver(), null, null);
        if (stage.equals("count")) doAnswer(call -> { failSql(); return call.callRealMethod(); }).when(orders).countDeliveredHistory(f.firstDriver());
        if (stage.equals("history")) doAnswer(call -> { failSql(); return call.callRealMethod(); }).when(orders).findDeliveredHistory(f.firstDriver(), 10, 0);
        request(get(Set.of("count", "history").contains(stage) ? HISTORY : EARNINGS), f.firstUser())
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    private void failSql() {
        jdbc.queryForObject("SELECT missing_5c_column FROM drivers", Map.of(), Long.class);
    }

    @Test void repeatedGetsDoNotExecuteAnyBusinessDmlOrChangeStoredData() throws Exception {
        long id = order(f.firstDriver(), "DELIVERED", START); earning(f.firstDriver(), id, 123, 7, START);
        var beforeRows = jdbc.queryForList("SELECT * FROM driver_earnings WHERE order_id=:id", Map.of("id", id));
        var beforeOrder = jdbc.queryForMap("SELECT * FROM orders WHERE id=:id", Map.of("id", id));
        var counters = dmlCounters();
        for (int i = 0; i < 3; i++) {
            income("2026-11-11", "2026-11-11").andExpect(status().isOk());
            request(get(HISTORY), f.firstUser()).andExpect(status().isOk());
        }
        assertEquals(counters, dmlCounters());
        assertEquals(beforeRows, jdbc.queryForList("SELECT * FROM driver_earnings WHERE order_id=:id", Map.of("id", id)));
        assertEquals(beforeOrder, jdbc.queryForMap("SELECT * FROM orders WHERE id=:id", Map.of("id", id)));
    }

    private List<Map<String, Object>> dmlCounters() {
        return jdbc.queryForList("SHOW GLOBAL STATUS WHERE Variable_name IN ('Com_insert','Com_update','Com_delete','Com_replace')", Map.of());
    }

    @Test void readEndpointsRunInReadOnlyRepeatableReadTransactions() throws Exception {
        var earningsChecked = new AtomicBoolean(); var historyChecked = new AtomicBoolean();
        doAnswer(call -> { earningsChecked.set(readOnlySnapshot()); return call.callRealMethod(); }).when(earnings).findEarnings(f.firstDriver(), null, null);
        doAnswer(call -> { historyChecked.set(readOnlySnapshot()); return call.callRealMethod(); }).when(orders).countDeliveredHistory(f.firstDriver());
        income(null, null).andExpect(status().isOk()); request(get(HISTORY), f.firstUser()).andExpect(status().isOk());
        assertTrue(earningsChecked.get()); assertTrue(historyChecked.get());
    }

    private boolean readOnlySnapshot() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                && Integer.valueOf(Connection.TRANSACTION_REPEATABLE_READ).equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
    }

    @Test void concurrentCompletionCannotMakeHistoryCountAndItemsDisagree() throws Exception {
        order(f.firstDriver(), "DELIVERED", START);
        doAnswer(call -> {
            var count = call.callRealMethod();
            try (var executor = Executors.newSingleThreadExecutor()) {
                executor.submit(() -> order(f.firstDriver(), "DELIVERED", START + 100)).get(10, TimeUnit.SECONDS);
            }
            return count;
        }).when(orders).countDeliveredHistory(f.firstDriver());
        var result = body(request(get(HISTORY), f.firstUser()));
        assertEquals(1, result.path("totalItems").asInt()); assertEquals(1, result.path("items").size());
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE driver_id=:id", Map.of("id", f.firstDriver()), Long.class));
    }

    @Test void swaggerPublishesBothReadContractsWithoutIdentityInputs() throws Exception {
        var spec = json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        for (String path : List.of(EARNINGS, HISTORY)) {
            var operation = spec.path("paths").path(path).path("get");
            assertFalse(operation.isMissingNode()); assertFalse(operation.has("requestBody"));
            for (String code : List.of("200", "400", "401", "403", "404", "500")) assertTrue(operation.path("responses").has(code));
            for (var param : operation.path("parameters")) assertFalse(Set.of("userId", "driverId").contains(param.path("name").asText()));
        }
    }

    private record Fixture(long customer, long merchant, long firstUser, long secondUser,
                           long firstDriver, long secondDriver, long restaurant) {
    }
}
