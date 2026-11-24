package vn.hutech.fooddelivery.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test dau-cuoi Module E: dat hang -> moi tai xe ngay (code gan tai xe THAT cua An) -> tai xe nhan
 * -> quan nau xong -> tai xe giao. Tai xe demo 0933000001 duoc tao dong drivers trong test
 * (da duyet, dang online, GPS vua cap nhat, dung gan quan). Can Docker Desktop dang chay.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=driver_flow_test_secret_dai_hon_32_ky_tu_abcd",
        "app.admin.phone=",
        "app.admin.password="
})
@AutoConfigureMockMvc
@Testcontainers
class DriverFlowIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    static final String Q1 = "Cơm Nhà Demo - Chi nhánh Quận 1";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    private String login(String phone) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\",\"password\":\"Demo@12345\"}".formatted(phone)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("token").asText();
    }

    /**
     * Tai xe demo san sang nhan don: dong drivers da duyet, online, GPS vua gui (trong 60 giay), gan chi nhanh Quan 1.
     * Thoi gian ghi bang Timestamp.valueOf(LocalDateTime.now()) giong code cua An de 2 ben cung mui gio.
     * Cac test dung chung 1 tai xe: don/loi moi con treo tu test truoc lam tai xe "dang ban",
     * nen huy het truoc moi test (bo quet tu dong cua An mac dinh tat trong test).
     */
    @BeforeEach
    void driverIsOnlineNearRestaurant() {
        jdbc.update("UPDATE order_assignments SET status = 'CANCELLED' WHERE status = 'OFFERED'", Map.of());
        jdbc.update("""
                UPDATE orders SET status = 'CANCELLED'
                WHERE status NOT IN ('DELIVERED', 'CANCELLED', 'REJECTED')
                """, Map.of());
        MapSqlParameterSource params = new MapSqlParameterSource()
                // Lui 2 giay va bo phan le: cot DATETIME(0) lam tron giay, tranh bi tron len thanh "tuong lai"
                .addValue("now", Timestamp.valueOf(LocalDateTime.now().minusSeconds(2).truncatedTo(ChronoUnit.SECONDS)));
        jdbc.update("""
                INSERT INTO drivers (user_id, vehicle_type, license_plate, status, is_online,
                                     current_lat, current_lng, last_location_at)
                VALUES ((SELECT id FROM users WHERE phone = '0933000001'), 'MOTORBIKE', '59A1-00001', 'APPROVED', 1,
                        10.7800, 106.7000, :now)
                ON DUPLICATE KEY UPDATE status = 'APPROVED', is_online = 1,
                                        current_lat = 10.7800, current_lng = 106.7000, last_location_at = :now
                """, params);
    }

    /** Khach dat 1 phan Com suon o chi nhanh Quan 1, tra ve id don. */
    private long placeOrder(String customerToken) throws Exception {
        long menuItemId = jdbc.queryForObject("""
                SELECT mi.id FROM menu_items mi
                JOIN menu_categories mc ON mc.id = mi.category_id
                JOIN restaurants r ON r.id = mc.restaurant_id
                WHERE r.name = :restaurantName AND mi.name = 'Cơm sườn nướng'
                """, Map.of("restaurantName", Q1), Long.class);
        mockMvc.perform(delete("/api/cart").header("Authorization", customerToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/cart/items").header("Authorization", customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuItemId\":%d,\"quantity\":1}".formatted(menuItemId)))
                .andExpect(status().isOk());
        String response = mockMvc.perform(post("/api/orders").header("Authorization", customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiverName":"Khách Test","receiverPhone":"0911000001",
                                 "deliveryAddress":"10 Lê Lợi, Quận 1","deliveryLat":10.7756,"deliveryLng":106.7004,
                                 "paymentMethod":"COD"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long offerIdOf(long orderId) {
        return jdbc.queryForObject("""
                SELECT oa.id FROM order_assignments oa
                JOIN drivers d ON d.id = oa.driver_id
                JOIN users u ON u.id = d.user_id
                WHERE oa.order_id = :orderId AND oa.status = 'OFFERED' AND u.phone = '0933000001'
                """, Map.of("orderId", orderId), Long.class);
    }

    private ResultActions merchantChanges(String token, long orderId, String status) throws Exception {
        return mockMvc.perform(patch("/api/merchant/orders/{id}/status", orderId).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"%s\"}".formatted(status)));
    }

    private ResultActions driverChanges(String token, long orderId, String status) throws Exception {
        return mockMvc.perform(patch("/api/driver/orders/{id}/status", orderId).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"%s\"}".formatted(status)));
    }

    @Test
    void placingOrderInvitesNearestOnlineDriverRightAway() throws Exception {
        long orderId = placeOrder(login("0911000001"));

        // Ngay khi API dat hang tra ve da co loi moi (khong phai cho bo quet 30 giay cua An)
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_assignments WHERE order_id = :orderId AND status = 'OFFERED'",
                Map.of("orderId", orderId), Long.class));
        offerIdOf(orderId);
    }

    @Test
    void assignedDriverDeliversAndMerchantCannotDoDeliverySteps() throws Exception {
        String customer = login("0911000001");
        String merchant = login("0922000001");
        String driver = login("0933000001");
        long orderId = placeOrder(customer);

        // Tai xe nhan loi moi qua API cua An -> orders.driver_id duoc gan
        mockMvc.perform(post("/api/driver/offers/{id}/accept", offerIdOf(orderId)).header("Authorization", driver))
                .andExpect(status().isOk());

        merchantChanges(merchant, orderId, "PREPARING").andExpect(status().isOk());
        merchantChanges(merchant, orderId, "READY_FOR_PICKUP")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driver.name").value("Tài Xế Demo"))
                .andExpect(jsonPath("$.driver.licensePlate").value("59A1-00001"));

        // Da co tai xe: chu quan khong bam buoc giao nua
        merchantChanges(merchant, orderId, "DELIVERING")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Đơn đã có tài xế nhận, bước giao hàng do tài xế cập nhật"));

        driverChanges(driver, orderId, "DELIVERING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERING"));
        driverChanges(driver, orderId, "DELIVERED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.history.length()").value(5));

        // Lich su ghi dung tai xe la nguoi bao da giao
        assertEquals("0933000001", jdbc.queryForObject("""
                SELECT u.phone FROM order_status_history h JOIN users u ON u.id = h.changed_by
                WHERE h.order_id = :orderId AND h.status = 'DELIVERED'
                """, Map.of("orderId", orderId), String.class));

        // Khach thay ten tai xe tren don
        mockMvc.perform(get("/api/orders/{id}", orderId).header("Authorization", customer))
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.driver.name").value("Tài Xế Demo"));
    }

    @Test
    void otherDriverCannotUpdateOrderAndDriverCannotSkipKitchen() throws Exception {
        String merchant = login("0922000001");
        String driver = login("0933000001");
        long orderId = placeOrder(login("0911000001"));
        mockMvc.perform(post("/api/driver/offers/{id}/accept", offerIdOf(orderId)).header("Authorization", driver))
                .andExpect(status().isOk());

        // Accept does not prepare the order. The driver must wait in both kitchen states.
        assertEquals("PLACED", jdbc.queryForObject("SELECT status FROM orders WHERE id = :id",
                Map.of("id", orderId), String.class));
        driverChanges(driver, orderId, "DELIVERING").andExpect(status().isConflict());
        merchantChanges(merchant, orderId, "PREPARING").andExpect(status().isOk());
        driverChanges(driver, orderId, "DELIVERING")
                .andExpect(status().isConflict());

        // Tai xe khac (dang ky moi, co dong drivers) khong dung vao don nay duoc
        String otherDriverBody = """
                {"fullName":"Tai Xe Khac","phone":"0966000001","password":"matkhau123","role":"DRIVER",
                 "vehicleType":"MOTORBIKE","licensePlate":"59B2-99999"}
                """;
        String registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(otherDriverBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String otherDriver = "Bearer " + objectMapper.readTree(registered).get("token").asText();
        merchantChanges(merchant, orderId, "READY_FOR_PICKUP").andExpect(status().isOk());
        driverChanges(otherDriver, orderId, "DELIVERING")
                .andExpect(status().isForbidden());

        // Sai trang thai cho tai xe -> 400
        driverChanges(driver, orderId, "PICKED_UP").andExpect(status().isBadRequest());
        driverChanges(driver, orderId, "PREPARING")
                .andExpect(status().isBadRequest());

        // Tai xe dung van giao tiep duoc, xong de giai phong cho test khac
        driverChanges(driver, orderId, "DELIVERING").andExpect(status().isOk());
        driverChanges(driver, orderId, "DELIVERED").andExpect(status().isOk());
    }

    /** Quy tac lay tu PR #18 cua An: tai xe da nhan don thi khach va quan khong huy duoc nua. */
    @Test
    void nobodyCanCancelOnceDriverAccepted() throws Exception {
        String customer = login("0911000001");
        String merchant = login("0922000001");
        String driver = login("0933000001");
        long orderId = placeOrder(customer);
        mockMvc.perform(post("/api/driver/offers/{id}/accept", offerIdOf(orderId)).header("Authorization", driver))
                .andExpect(status().isOk());

        // Don con "Cho xac nhan" (luat van cho khach huy) nhung da co tai xe nhan -> 409
        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Đổi ý\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Tài xế đã nhận giao đơn, chưa thể hủy đơn hàng"));

        // Quan cung khong huy duoc, ke ca luc dang chuan bi
        merchantChanges(merchant, orderId, "PREPARING").andExpect(status().isOk());
        mockMvc.perform(patch("/api/merchant/orders/{id}/status", orderId).header("Authorization", merchant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\",\"reason\":\"Hết món\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Tài xế đã nhận giao đơn, chưa thể hủy đơn hàng"));
        assertEquals("PREPARING", jdbc.queryForObject(
                "SELECT status FROM orders WHERE id = :id", Map.of("id", orderId), String.class));

        // Don van giao tiep binh thuong (xong de giai phong tai xe cho test khac)
        merchantChanges(merchant, orderId, "READY_FOR_PICKUP").andExpect(status().isOk());
        driverChanges(driver, orderId, "DELIVERING").andExpect(status().isOk());
        driverChanges(driver, orderId, "DELIVERED").andExpect(status().isOk());
    }

    @Test
    void customerCanStillCancelWhileDriverIsOnlyInvited() throws Exception {
        String customer = login("0911000001");
        long orderId = placeOrder(customer);
        offerIdOf(orderId); // da co loi moi (OFFERED) nhung chua tai xe nao nhan

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Đổi ý\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void failedOrderInvitesNobody() throws Exception {
        String customer = login("0911000002");
        long offersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM order_assignments", Map.of(), Long.class);

        // 1 ly tra da 5.000d, duoi don toi thieu 30.000d -> 409, khong co don, khong moi ai
        long traDa = jdbc.queryForObject("""
                SELECT mi.id FROM menu_items mi JOIN menu_categories mc ON mc.id = mi.category_id
                JOIN restaurants r ON r.id = mc.restaurant_id
                WHERE r.name = :restaurantName AND mi.name = 'Trà đá'
                """, Map.of("restaurantName", Q1), Long.class);
        mockMvc.perform(delete("/api/cart").header("Authorization", customer)).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/cart/items").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"menuItemId\":%d,\"quantity\":1}".formatted(traDa)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiverName":"Khách Test","receiverPhone":"0911000002",
                                 "deliveryAddress":"10 Lê Lợi, Quận 1","deliveryLat":10.7756,"deliveryLng":106.7004,
                                 "paymentMethod":"COD"}
                                """))
                .andExpect(status().isConflict());

        assertEquals(offersBefore, jdbc.queryForObject("SELECT COUNT(*) FROM order_assignments", Map.of(), Long.class));
    }
}
