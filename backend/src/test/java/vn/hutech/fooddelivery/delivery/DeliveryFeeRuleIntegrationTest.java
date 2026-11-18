package vn.hutech.fooddelivery.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin quan ly quy tac phi giao (Module K). Dat hang dung quy tac DANG BAT MOI NHAT.
 * Dung tai khoan admin demo co san trong seed. Can Docker Desktop dang chay.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=delivery_fee_test_secret_dai_hon_32_ky_tu_abcd",
        "app.admin.phone=",
        "app.admin.password="
})
@AutoConfigureMockMvc
@Testcontainers
class DeliveryFeeRuleIntegrationTest {

    static final String ADMIN_PHONE = "0900000001";
    static final String ADMIN_PASSWORD = "Demo@12345";

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @Test
    void newRuleBecomesAppliedAndCheckoutUsesIt() throws Exception {
        String admin = login(ADMIN_PHONE, ADMIN_PASSWORD);

        long ruleId = createRule(admin, """
                {"name":"Phí thử 20k","baseFee":20000,"baseDistanceKm":2,"perKmFee":5000}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Phí thử 20k"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.applied").value(true))
                .andReturn().getResponse().getContentAsString().transform(this::idFrom);

        // Moi nhat truoc; chi 1 quy tac duoc danh dau "dang ap dung"
        String list = mockMvc.perform(get("/api/admin/delivery-fee-rules").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ruleId))
                .andExpect(jsonPath("$[0].applied").value(true))
                .andReturn().getResponse().getContentAsString();
        int appliedCount = 0;
        for (JsonNode rule : objectMapper.readTree(list)) {
            if (rule.get("applied").asBoolean()) {
                appliedCount++;
            }
        }
        assertEquals(1, appliedCount);

        // Khach xem truoc don o gan quan (0,62 km, trong 2 km co ban) -> phi giao = phi co ban moi
        String customer = login("0911000001", "Demo@12345");
        fillCart(customer);
        mockMvc.perform(post("/api/orders/preview").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryLat\":10.7756,\"deliveryLng\":106.7004}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryFee").value(20000));
    }

    @Test
    void lastActiveRuleCannotBeTurnedOff() throws Exception {
        String admin = login(ADMIN_PHONE, ADMIN_PASSWORD);
        long keep = createRule(admin, """
                {"name":"Giữ lại","baseFee":18000,"baseDistanceKm":3,"perKmFee":4000}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(this::idFrom);

        // Tat het cac quy tac dang bat khac
        String list = mockMvc.perform(get("/api/admin/delivery-fee-rules").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString();
        Long another = null;
        for (JsonNode rule : objectMapper.readTree(list)) {
            long id = rule.get("id").asLong();
            if (id != keep) {
                another = id;
                if (rule.get("active").asBoolean()) {
                    setActive(admin, id, false).andExpect(status().isOk());
                }
            }
        }

        // Quy tac bat cuoi cung khong tat duoc: dat hang can 1 quy tac
        setActive(admin, keep, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Phải còn ít nhất 1 quy tắc phí giao đang bật"));

        // Bat lai 1 quy tac khac thi tat duoc
        setActive(admin, another, true).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        setActive(admin, keep, false).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void invalidRuleAndNonAdminAreRejected() throws Exception {
        String admin = login(ADMIN_PHONE, ADMIN_PASSWORD);
        createRule(admin, "{\"name\":\"\",\"baseFee\":-1,\"baseDistanceKm\":60,\"perKmFee\":1500.5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.baseFee").exists())
                .andExpect(jsonPath("$.fieldErrors.baseDistanceKm").exists())
                .andExpect(jsonPath("$.fieldErrors.perKmFee").exists());
        setActive(admin, 999_999L, false).andExpect(status().isNotFound());

        String customer = login("0911000001", "Demo@12345");
        mockMvc.perform(get("/api/admin/delivery-fee-rules").header("Authorization", customer))
                .andExpect(status().isForbidden());
    }

    // ----- Ham tien ich -----

    private String login(String phone, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(phone, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("token").asText();
    }

    private ResultActions createRule(String token, String body) throws Exception {
        return mockMvc.perform(post("/api/admin/delivery-fee-rules").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions setActive(String token, long ruleId, boolean active) throws Exception {
        return mockMvc.perform(patch("/api/admin/delivery-fee-rules/{id}/active", ruleId)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":%s}".formatted(active)));
    }

    private long idFrom(String json) {
        try {
            return objectMapper.readTree(json).get("id").asLong();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void fillCart(String token) throws Exception {
        long menuItemId = jdbc.queryForObject("""
                SELECT mi.id FROM menu_items mi
                JOIN menu_categories mc ON mc.id = mi.category_id
                JOIN restaurants r ON r.id = mc.restaurant_id
                WHERE r.name = 'Cơm Nhà Demo - Chi nhánh Quận 1' AND mi.name = 'Cơm sườn nướng'
                """, Map.of(), Long.class);
        mockMvc.perform(delete("/api/cart").header("Authorization", token)).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/cart/items").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuItemId\":%d,\"quantity\":1}".formatted(menuItemId)))
                .andExpect(status().isOk());
    }
}
