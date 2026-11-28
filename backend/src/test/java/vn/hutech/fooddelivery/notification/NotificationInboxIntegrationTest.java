package vn.hutech.fooddelivery.notification;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.security.JwtProperties;
import vn.hutech.fooddelivery.security.JwtService;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = {"spring.config.import=", "app.assignment.scheduling.enabled=false"})
@AutoConfigureMockMvc
class NotificationInboxIntegrationTest {
    private static final String URL = "/api/notifications";
    private static final String TEST_SIGNING_KEY = UUID.randomUUID().toString();
    private static final List<String> ROLES = List.of("CUSTOMER", "DRIVER", "MERCHANT", "ADMIN");
    private static final long CUSTOMER = 754001;
    private static final long DRIVER = 754002;
    private static final long MERCHANT = 754003;
    private static final long ADMIN = 754004;

    @Container @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withEnv("TZ", "Asia/Ho_Chi_Minh");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> TEST_SIGNING_KEY);
        registry.add("app.fcm.project-id", () -> "");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired NotificationInboxService inbox;

    @BeforeEach
    void fixture() {
        jdbc.update("DELETE FROM notifications WHERE user_id BETWEEN ? AND ?", CUSTOMER, ADMIN);
        for (int i = 0; i < ROLES.size(); i++) {
            jdbc.update("""
                    INSERT IGNORE INTO users (id, full_name, phone, password_hash, role)
                    VALUES (?, 'Inbox fixture', ?, 'fixture-only', ?)
                    """, CUSTOMER + i, "0987" + (CUSTOMER + i), ROLES.get(i));
        }
    }

    @Test
    void customerDriverAndMerchantOnlyReadTheirOwnRows() throws Exception {
        for (long id : List.of(CUSTOMER, DRIVER, MERCHANT)) {
            inbox.record(draft(id, "event:shared"));
        }
        for (long id : List.of(CUSTOMER, DRIVER, MERCHANT)) {
            mvc.perform(as(get(URL), id)).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].title").value("Fixture " + id))
                    .andExpect(jsonPath("$.items[0].eventType").value("ORDER_DELIVERED"))
                    .andExpect(jsonPath("$.items[0].data.orderId").value(11))
                    .andExpect(jsonPath("$.items[0].eventKey").doesNotHaveJsonPath())
                    .andExpect(jsonPath("$.unreadCount").value(1));
        }
    }

    @Test
    void guestInvalidAndExpiredJwtAreUnauthorizedAndAdminIsForbidden() throws Exception {
        long id = inbox.record(draft(CUSTOMER, "event:auth")).id();
        String patchUrl = URL + "/" + id + "/read";
        String expired = new JwtService(new JwtProperties(TEST_SIGNING_KEY, -5)).generateToken(CUSTOMER, "CUSTOMER");
        String wrongSignature = new JwtService(new JwtProperties(UUID.randomUUID().toString(), 5))
                .generateToken(CUSTOMER, "CUSTOMER");
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(patch(patchUrl)).andExpect(status().isUnauthorized());
        for (String token : List.of("not-a-jwt", expired, wrongSignature)) {
            mvc.perform(get(URL).header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
            mvc.perform(patch(patchUrl).header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(as(get(URL), ADMIN)).andExpect(status().isForbidden());
        mvc.perform(as(patch(patchUrl), ADMIN)).andExpect(status().isForbidden());
        assertEquals(1, inbox.list(CUSTOMER, 20, false, null).unreadCount());
    }

    @Test
    void emptyInboxHasEmptyItemsAndNoNextCursor() throws Exception {
        mvc.perform(as(get(URL), CUSTOMER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").hasJsonPath())
                .andExpect(jsonPath("$.nextCursor").isEmpty())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void usesDefaultTwentyAndMaximumFiftyWithFullInboxUnreadCount() throws Exception {
        for (int i = 0; i < 51; i++) {
            legacy(CUSTOMER, null);
        }
        mvc.perform(as(get(URL), CUSTOMER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andExpect(jsonPath("$.unreadCount").value(51));
        mvc.perform(as(get(URL).param("limit", "50"), CUSTOMER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(50))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andExpect(jsonPath("$.unreadCount").value(51));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "51", "abc", "", "1.5", "999999999999999"})
    void rejectsInvalidLimit(String limit) throws Exception {
        mvc.perform(as(get(URL).param("limit", limit), CUSTOMER)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsWrongFiltersUnknownParametersAndRepeatedParameters() throws Exception {
        for (var request : List.of(get(URL).param("unreadOnly", "yes"), get(URL).param("unreadOnly", ""),
                get(URL).param("userId", String.valueOf(DRIVER)), get(URL).param("limit", "1", "2"))) {
            mvc.perform(as(request, CUSTOMER)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void rejectsMalformedNoncanonicalAndOutOfRangeCursors() throws Exception {
        for (String cursor : List.of("", "not-base64!", cursor("2026-02-30T08:00:00:1"),
                cursor("2026-11-26T08:00:00:0"), cursor("2026-11-26T08:00:00:9223372036854775808"),
                cursor("0999-11-26T08:00:00:1"), cursor("2026-11-26T08:00:00:1") + "=",
                cursor("2026-11-26T08:00:00:1:2"))) {
            mvc.perform(as(get(URL).param("cursor", cursor), CUSTOMER)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void keysetOrdersTiesAndIgnoresNewerInsertUntilNextRefresh() throws Exception {
        long first = legacy(CUSTOMER, null);
        long second = legacy(CUSTOMER, null);
        long third = legacy(CUSTOMER, null);
        long fourth = legacy(CUSTOMER, null);
        JsonNode page1 = response(get(URL).param("limit", "2"), CUSTOMER);
        assertEquals(List.of(fourth, third), ids(page1));
        long newest = legacy(CUSTOMER, null);
        JsonNode page2 = response(get(URL).param("limit", "2").param("cursor", page1.get("nextCursor").asText()),
                CUSTOMER);
        assertEquals(List.of(second, first), ids(page2));
        assertTrue(page2.get("nextCursor").isNull());
        assertEquals(5, page2.get("unreadCount").asLong());
        assertEquals(List.of(newest, fourth), ids(response(get(URL).param("limit", "2"), CUSTOMER)));
        assertTrue(ids(response(get(URL).param("cursor", page1.get("nextCursor").asText()), DRIVER)).isEmpty());
    }

    @Test
    void legacyPayloadKeepsOnlyApprovedFieldsAndConvertsWallTime() throws Exception {
        assertEquals(420, jdbc.queryForObject("SELECT TIMESTAMPDIFF(MINUTE, UTC_TIMESTAMP(), NOW())", Integer.class));
        long legacy = legacy(CUSTOMER, null);
        long jsonLegacy = legacy(CUSTOMER, """
                {"orderId":21,"offerId":"13","unexpected":"internal","eventType":[1],"expiresAt":"bad"}
                """);
        long scalarLegacy = legacy(CUSTOMER, "42");
        var page = response(get(URL), CUSTOMER);
        assertEquals(List.of(scalarLegacy, jsonLegacy, legacy), ids(page));
        assertEquals("2026-11-26T08:00:00Z", page.at("/items/0/createdAt").asText());
        assertTrue(page.at("/items/0/data").isEmpty());
        assertTrue(page.at("/items/1/eventType").isNull());
        assertEquals(21, page.at("/items/1/data/orderId").asLong());
        assertEquals(1, page.at("/items/1/data").size());
        assertTrue(page.at("/items/2/eventType").isNull());
        assertTrue(page.at("/items/2/data").isEmpty());
    }

    @Test
    void patchIsIdempotentAndUpdatesGetAndUnreadFilter() throws Exception {
        long id = legacy(CUSTOMER, null);
        long unread = legacy(CUSTOMER, null);
        for (int i = 0; i < 3; i++) {
            mvc.perform(as(patch(URL + "/" + id + "/read"), CUSTOMER)).andExpect(status().isNoContent());
        }
        mvc.perform(as(get(URL), CUSTOMER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[1].isRead").value(true))
                .andExpect(jsonPath("$.unreadCount").value(1));
        var page = response(get(URL).param("unreadOnly", "true").param("limit", "1"), CUSTOMER);
        assertEquals(List.of(unread), ids(page));
        assertEquals(1, page.get("unreadCount").asLong());
        assertTrue(page.get("nextCursor").isNull());
    }

    @Test
    void patchNeverChangesAnotherInboxAndRejectsInvalidIds() throws Exception {
        long other = legacy(DRIVER, null);
        var foreign = mvc.perform(as(patch(URL + "/" + other + "/read"), CUSTOMER))
                .andExpect(status().isNotFound()).andReturn().getResponse();
        var missing = mvc.perform(as(patch(URL + "/" + Long.MAX_VALUE + "/read"), CUSTOMER))
                .andExpect(status().isNotFound()).andReturn().getResponse();
        assertEquals(json.readTree(missing.getContentAsString()).get("message"),
                json.readTree(foreign.getContentAsString()).get("message"));
        assertEquals(1, inbox.list(DRIVER, 20, false, null).unreadCount());
        for (String id : List.of("0", "-1", "bad", "9223372036854775808")) {
            mvc.perform(as(patch(URL + "/" + id + "/read"), CUSTOMER)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void internalRecordDedupeIsScopedAndNonduplicateSqlErrorsPropagate() {
        var initial = inbox.record(draft(CUSTOMER, "ORDER:created:11"));
        var replay = inbox.record(draft(CUSTOMER, "order:created:11"));
        var other = inbox.record(draft(DRIVER, "order:created:11"));
        assertTrue(initial.created());
        assertFalse(replay.created());
        assertEquals(initial.id(), replay.id());
        assertTrue(other.created());
        assertNotEquals(initial.id(), other.id());
        assertThrows(DataIntegrityViolationException.class, () -> inbox.record(draft(Long.MAX_VALUE, "event:fk")));
    }

    @Test
    void concurrentSameEventProducesOneWinnerAndTheSameIdForEveryCaller() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(6)) {
            var futures = new ArrayList<Future<NotificationInboxService.Recorded>>();
            for (int i = 0; i < 6; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return inbox.record(draft(CUSTOMER, "event:race"));
                }));
            }
            start.countDown();
            var results = new ArrayList<NotificationInboxService.Recorded>();
            for (var future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            assertEquals(1, results.stream().filter(NotificationInboxService.Recorded::created).count());
            assertEquals(1, results.stream().map(NotificationInboxService.Recorded::id).distinct().count());
            assertEquals(1, inbox.list(CUSTOMER, 20, false, null).items().size());
        }
    }

    @Test
    void internalRecordValidatesEverySchemaFieldAndDataBeforeInsert() {
        var type = NotificationInboxService.Type.ORDER;
        for (var invalid : List.of(
                new NotificationInboxService.Draft(0, type, "event", "EVENT", "title", "body", null),
                new NotificationInboxService.Draft(CUSTOMER, null, "event", "EVENT", "title", "body", null),
                new NotificationInboxService.Draft(CUSTOMER, type, "x".repeat(151), "EVENT", "title", "body", null),
                new NotificationInboxService.Draft(CUSTOMER, type, "event", "bad event", "title", "body", null),
                new NotificationInboxService.Draft(CUSTOMER, type, "event", "EVENT", "x".repeat(151), "body", null),
                new NotificationInboxService.Draft(CUSTOMER, type, "event", "EVENT", "title", "x".repeat(501), null),
                new NotificationInboxService.Draft(CUSTOMER, type, "event", "EVENT", "title", "body",
                        new NotificationInboxService.Data(0L, null, null, null)),
                new NotificationInboxService.Draft(CUSTOMER, type, "event", "EVENT", "title", "body",
                        new NotificationInboxService.Data(null, "invalid", null, null)))) {
            assertThrows(IllegalArgumentException.class, () -> inbox.record(invalid));
        }
        assertTrue(inbox.list(CUSTOMER, 20, false, null).items().isEmpty());
    }

    private long legacy(long userId, String payload) {
        jdbc.update("""
                INSERT INTO notifications (user_id, type, title, body, data_json, created_at)
                VALUES (?, 'ORDER', 'Legacy title', 'Legacy body', ?, '2026-11-26 15:00:00')
                """, userId, payload);
        return jdbc.queryForObject("SELECT MAX(id) FROM notifications WHERE user_id = ?", Long.class, userId);
    }

    private NotificationInboxService.Draft draft(long userId, String eventKey) {
        return new NotificationInboxService.Draft(userId, NotificationInboxService.Type.ORDER, eventKey,
                "ORDER_DELIVERED", "Fixture " + userId, "A notification",
                new NotificationInboxService.Data(11L, "FD261124575853", 20L, Instant.parse("2026-11-26T08:00:00Z")));
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, long userId) {
        return request.header("Authorization", "Bearer " + jwt.generateToken(userId, ROLES.get((int) (userId - CUSTOMER))));
    }

    private JsonNode response(MockHttpServletRequestBuilder request, long userId) throws Exception {
        return json.readTree(mvc.perform(as(request, userId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
    }

    private List<Long> ids(JsonNode page) {
        var ids = new ArrayList<Long>();
        page.get("items").forEach(item -> ids.add(item.get("id").asLong()));
        return ids;
    }

    private static String cursor(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.US_ASCII));
    }
}
