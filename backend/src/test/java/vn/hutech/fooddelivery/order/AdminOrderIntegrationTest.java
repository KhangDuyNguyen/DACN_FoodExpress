package vn.hutech.fooddelivery.order;

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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin xem don toan san (Module K). Dung tai khoan admin demo co san trong seed.
 * Can Docker Desktop dang chay.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=admin_order_test_secret_dai_hon_32_ky_tu_abcd",
        "app.admin.phone=",
        "app.admin.password="
})
@AutoConfigureMockMvc
@Testcontainers
class AdminOrderIntegrationTest {

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
    void adminSeesEveryOrderAndFiltersThem() throws Exception {
        String admin = login(ADMIN_PHONE, ADMIN_PASSWORD);
        JsonNode order = placeCodOrder(login("0911000001", "Demo@12345"));
        long orderId = order.get("id").asLong();
        String code = order.get("code").asText();
        long restaurantId = order.get("restaurant").get("id").asLong();
        // Ngay dat theo dong ho cua DB (DB trong test chay gio UTC, khac gio may)
        LocalDate orderDate = jdbc.queryForObject("SELECT DATE(created_at) FROM orders WHERE id = :id",
                Map.of("id", orderId), LocalDate.class);

        // Moi nhat truoc, co du ten quan, ten va SDT khach
        mockMvc.perform(get("/api/admin/orders").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(orderId))
                .andExpect(jsonPath("$.items[0].customerPhone").value("0911000001"))
                .andExpect(jsonPath("$.items[0].restaurantName").value("Cơm Nhà Demo - Chi nhánh Quận 1"))
                .andExpect(jsonPath("$.items[0].paymentMethod").value("COD"));

        // Tim theo ma don (dau ma cung duoc) hoac SDT khach
        mockMvc.perform(get("/api/admin/orders").param("q", code).header("Authorization", admin))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].code").value(code));
        mockMvc.perform(get("/api/admin/orders").param("q", code.substring(0, 8)).header("Authorization", admin))
                .andExpect(jsonPath("$.items[*].id", hasItem((int) orderId)));
        mockMvc.perform(get("/api/admin/orders").param("q", "0911000001").header("Authorization", admin))
                .andExpect(jsonPath("$.items[*].id", hasItem((int) orderId)));

        // Loc theo trang thai, quan, cach thanh toan, ngay dat
        mockMvc.perform(get("/api/admin/orders").param("status", "PLACED").param("restaurantId", "" + restaurantId)
                        .param("paymentMethod", "COD").header("Authorization", admin))
                .andExpect(jsonPath("$.items[*].id", hasItem((int) orderId)));
        mockMvc.perform(get("/api/admin/orders").param("status", "DELIVERED").header("Authorization", admin))
                .andExpect(jsonPath("$.items[*].id", not(hasItem((int) orderId))));
        mockMvc.perform(get("/api/admin/orders").param("from", orderDate.toString()).param("to", orderDate.toString())
                        .header("Authorization", admin))
                .andExpect(jsonPath("$.items[*].id", hasItem((int) orderId)));
        mockMvc.perform(get("/api/admin/orders").param("from", orderDate.plusDays(1).toString())
                        .header("Authorization", admin))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void adminOpensAnyOrderDetail() throws Exception {
        String admin = login(ADMIN_PHONE, ADMIN_PASSWORD);
        long orderId = placeCodOrder(login("0911000002", "Demo@12345")).get("id").asLong();

        mockMvc.perform(get("/api/admin/orders/{id}", orderId).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.items[0].name").value("Cơm sườn nướng"))
                .andExpect(jsonPath("$.history[0].status").value("PLACED"));
        mockMvc.perform(get("/api/admin/orders/{id}", 999_999).header("Authorization", admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void badFiltersAndNonAdminAreRejected() throws Exception {
        String admin = login(ADMIN_PHONE, ADMIN_PASSWORD);
        mockMvc.perform(get("/api/admin/orders").param("status", "KHONG_CO").header("Authorization", admin))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/orders").param("from", "2026-13-40").header("Authorization", admin))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/orders").header("Authorization", login("0911000001", "Demo@12345")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/orders").header("Authorization", login("0922000001", "Demo@12345")))
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

    /** Gio con 1 phan Com suon nuong o chi nhanh Quan 1, dat COD, tra ve chi tiet don. */
    private JsonNode placeCodOrder(String token) throws Exception {
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
        String response = mockMvc.perform(post("/api/orders").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiverName":"Khách Test","receiverPhone":"0911000001",
                                 "deliveryAddress":"10 Lê Lợi, Quận 1","deliveryLat":10.7756,"deliveryLng":106.7004,
                                 "paymentMethod":"COD"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }
}
