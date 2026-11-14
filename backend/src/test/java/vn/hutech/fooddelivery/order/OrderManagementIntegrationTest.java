package vn.hutech.fooddelivery.order;

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

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test tich hop Module D (quan ly don) tren MySQL that trong Docker.
 * Tai khoan seed: khach 0911000001, 0911000002; chu quan 0922000001 (Com Nha Demo Quan 1, Quan 3),
 * 0922000002 (Bun Cha Demo). Moi test tu dat don moi qua API nen khong phu thuoc test khac.
 * Can Docker Desktop dang chay.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=order_management_test_secret_dai_hon_32_ky_tu",
        "app.admin.phone=",
        "app.admin.password="
})
@AutoConfigureMockMvc
@Testcontainers
class OrderManagementIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    static final String Q1 = "Cơm Nhà Demo - Chi nhánh Quận 1";
    static final String Q3 = "Cơm Nhà Demo - Chi nhánh Quận 3";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    // ----- Ham tien ich -----

    private String login(String phone) throws Exception {
        String body = """
                {"identifier":"%s","password":"Demo@12345"}
                """.formatted(phone);
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("token").asText();
    }

    private long restaurantId(String name) {
        return jdbc.queryForObject("SELECT id FROM restaurants WHERE name = :name", Map.of("name", name), Long.class);
    }

    /** Khach dat 1 don gom 1 mon cua quan restaurantName, tra ve id don (trang thai PLACED). */
    private long placeOrder(String customerToken, String restaurantName, String itemName) throws Exception {
        long menuItemId = jdbc.queryForObject("""
                SELECT mi.id FROM menu_items mi
                JOIN menu_categories mc ON mc.id = mi.category_id
                JOIN restaurants r ON r.id = mc.restaurant_id
                WHERE r.name = :restaurantName AND mi.name = :itemName
                """, Map.of("restaurantName", restaurantName, "itemName", itemName), Long.class);
        mockMvc.perform(delete("/api/cart").header("Authorization", customerToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/cart/items").header("Authorization", customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuItemId\":%d,\"quantity\":1}".formatted(menuItemId)))
                .andExpect(status().isOk());
        String body = """
                {"receiverName":"Khách Test","receiverPhone":"0911000001",
                 "deliveryAddress":"10 Lê Lợi, Quận 1","deliveryLat":10.7756,"deliveryLng":106.7004,
                 "paymentMethod":"COD"}
                """;
        String response = mockMvc.perform(post("/api/orders").header("Authorization", customerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private ResultActions changeStatus(String merchantToken, long orderId, String json) throws Exception {
        return mockMvc.perform(patch("/api/merchant/orders/{id}/status", orderId)
                .header("Authorization", merchantToken)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions cancelByCustomer(String customerToken, long orderId, String json) throws Exception {
        return mockMvc.perform(post("/api/orders/{id}/cancel", orderId)
                .header("Authorization", customerToken)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ----- Luong trang thai -----

    @Test
    // Le Tan Binh An - MSSV 2380600006 - Tuan 5 [MODIFY]: Merchant nhan mon nhung khong con giao/hoan thanh thay tai xe.
    void merchantPreparesOrderButCannotPerformDriverSteps() throws Exception {
        String customer = login("0911000001");
        String merchant = login("0922000001");
        long orderId = placeOrder(customer, Q1, "Cơm sườn nướng");

        changeStatus(merchant, orderId, "{\"status\":\"PREPARING\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.acceptedAt").value(notNullValue()));
        changeStatus(merchant, orderId, "{\"status\":\"DELIVERING\"}")
                .andExpect(status().isConflict());
        changeStatus(merchant, orderId, "{\"status\":\"DELIVERED\"}")
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/orders/{id}", orderId).header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[0].status").value("PLACED"))
                .andExpect(jsonPath("$.history[1].status").value("PREPARING"));

        // Lich su ghi dung nguoi doi: buoc nhan don do chu quan 0922000001 bam
        assertEquals("0922000001", jdbc.queryForObject("""
                SELECT u.phone FROM order_status_history h JOIN users u ON u.id = h.changed_by
                WHERE h.order_id = :orderId AND h.status = 'PREPARING'
                """, Map.of("orderId", orderId), String.class));

        // Le Tan Binh An - MSSV 2380600006 - Tuan 5 [MODIFY]: Trang thai chi tien tiep khi tai xe thuc hien luong giao.
        mockMvc.perform(get("/api/orders/{id}", orderId).header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"));
    }

    @Test
    void skippingOrGoingBackIsRejected() throws Exception {
        String merchant = login("0922000001");
        long orderId = placeOrder(login("0911000001"), Q1, "Cơm sườn nướng");

        changeStatus(merchant, orderId, "{\"status\":\"DELIVERED\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Không thể chuyển đơn từ \"Chờ xác nhận\" sang \"Hoàn thành\""));
        changeStatus(merchant, orderId, "{\"status\":\"DELIVERING\"}")
                .andExpect(status().isConflict());

        changeStatus(merchant, orderId, "{\"status\":\"PREPARING\"}").andExpect(status().isOk());
        // Le Tan Binh An - MSSV 2380600006 - Tuan 5 [MODIFY]:
        // Xac nhan PREPARING lap lai la no-op, lich su van chi co hai dong.
        changeStatus(merchant, orderId, "{\"status\":\"PREPARING\"}")
                .andExpect(status().isOk());

        // Trang thai ngoai danh sach cho phep -> 400
        changeStatus(merchant, orderId, "{\"status\":\"PICKED_UP\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id = :id",
                Map.of("id", orderId), Long.class));
    }

    @Test
    void merchantCancelNeedsReason() throws Exception {
        String merchant = login("0922000001");
        long orderId = placeOrder(login("0911000001"), Q3, "Cơm sườn nướng");

        changeStatus(merchant, orderId, "{\"status\":\"CANCELLED\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reasonGivenWhenCancelled").value("Hủy đơn phải ghi lý do"));

        changeStatus(merchant, orderId, "{\"status\":\"CANCELLED\",\"reason\":\"Hết món\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Hết món"))
                .andExpect(jsonPath("$.cancelledAt").value(notNullValue()))
                .andExpect(jsonPath("$.history[1].note").value("Hết món"));
    }

    @Test
    void customerCancelsOnlyBeforeRestaurantAccepts() throws Exception {
        String customer = login("0911000001");
        String merchant = login("0922000001");

        long placed = placeOrder(customer, Q1, "Cơm sườn nướng");
        cancelByCustomer(customer, placed, "{\"reason\":\"\"}")
                .andExpect(status().isBadRequest());
        cancelByCustomer(customer, placed, "{\"reason\":\"Đặt nhầm món\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Đặt nhầm món"));
        // Da huy thi khong huy lai, chu quan cung khong nhan duoc nua
        cancelByCustomer(customer, placed, "{\"reason\":\"Huy lan 2\"}")
                .andExpect(status().isConflict());
        changeStatus(merchant, placed, "{\"status\":\"PREPARING\"}")
                .andExpect(status().isConflict());

        long accepted = placeOrder(customer, Q1, "Cơm sườn nướng");
        changeStatus(merchant, accepted, "{\"status\":\"PREPARING\"}").andExpect(status().isOk());
        cancelByCustomer(customer, accepted, "{\"reason\":\"Đổi ý\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Không thể chuyển đơn từ \"Đang chuẩn bị\" sang \"Đã hủy\""));
    }

    // ----- Quyen so huu -----

    @Test
    void customerCannotSeeOrCancelAnotherCustomersOrder() throws Exception {
        long orderId = placeOrder(login("0911000001"), Q1, "Cơm sườn nướng");
        String otherCustomer = login("0911000002");

        mockMvc.perform(get("/api/orders/{id}", orderId).header("Authorization", otherCustomer))
                .andExpect(status().isForbidden());
        cancelByCustomer(otherCustomer, orderId, "{\"reason\":\"Pha don\"}")
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/orders/{id}", 999999).header("Authorization", otherCustomer))
                .andExpect(status().isNotFound());
    }

    @Test
    void merchantCannotSeeOrChangeOrderOfAnotherOwner() throws Exception {
        long orderId = placeOrder(login("0911000001"), Q1, "Cơm sườn nướng");
        // Chu quan Bun Cha Demo khong so huu chi nhanh Quan 1 cua Com Nha Demo
        String otherMerchant = login("0922000002");

        mockMvc.perform(get("/api/merchant/orders/{id}", orderId).header("Authorization", otherMerchant))
                .andExpect(status().isForbidden());
        changeStatus(otherMerchant, orderId, "{\"status\":\"PREPARING\"}")
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/merchant/orders").param("restaurantId", String.valueOf(restaurantId(Q1)))
                        .header("Authorization", otherMerchant))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/merchant/orders").header("Authorization", otherMerchant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == " + orderId + ")]").isEmpty());

        // Don van con o trang thai cho xac nhan
        assertEquals("PLACED", jdbc.queryForObject("SELECT status FROM orders WHERE id = :id",
                Map.of("id", orderId), String.class));
    }

    // ----- Danh sach -----

    @Test
    void customerListIsNewestFirstAndFiltersByStatus() throws Exception {
        String customer = login("0911000002");
        long older = placeOrder(customer, Q3, "Cơm sườn nướng");
        long newer = placeOrder(customer, Q3, "Cơm sườn nướng");
        cancelByCustomer(customer, older, "{\"reason\":\"Đặt trùng\"}").andExpect(status().isOk());

        mockMvc.perform(get("/api/orders").param("size", "50").header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(newer))
                .andExpect(jsonPath("$.items[0].restaurantName").value(Q3))
                .andExpect(jsonPath("$.items[0].itemCount").value(1))
                .andExpect(jsonPath("$.items[0].totalAmount").value(notNullValue()));

        mockMvc.perform(get("/api/orders").param("status", "CANCELLED").header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].status", everyItem(is("CANCELLED"))))
                .andExpect(jsonPath("$.items[?(@.id == " + older + ")]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.id == " + newer + ")]").isEmpty());

        mockMvc.perform(get("/api/orders").param("status", "ABC").header("Authorization", customer))
                .andExpect(status().isBadRequest());
    }

    @Test
    void merchantListFiltersByBranchAndStatus() throws Exception {
        String merchant = login("0922000001");
        long q3Order = placeOrder(login("0911000001"), Q3, "Cơm sườn nướng");
        long q3 = restaurantId(Q3);

        mockMvc.perform(get("/api/merchant/orders")
                        .param("restaurantId", String.valueOf(q3)).param("status", "PLACED")
                        .header("Authorization", merchant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].restaurantId", everyItem(is((int) q3))))
                .andExpect(jsonPath("$.items[*].status", everyItem(is("PLACED"))))
                .andExpect(jsonPath("$.items[?(@.id == " + q3Order + ")].receiverName").value("Khách Test"));

        // Khong loc chi nhanh: thay don cua ca 2 chi nhanh minh so huu
        mockMvc.perform(get("/api/merchant/orders").param("size", "50").header("Authorization", merchant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == " + q3Order + ")]").isNotEmpty());

        // Chu chuoi 12 quan (0901234567) khong thay don cua Com Nha Demo
        mockMvc.perform(get("/api/merchant/orders").param("size", "50").header("Authorization", login("0901234567")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == " + q3Order + ")]").isEmpty());
    }
}
