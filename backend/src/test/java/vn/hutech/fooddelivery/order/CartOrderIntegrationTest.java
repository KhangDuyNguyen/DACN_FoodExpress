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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test tich hop Module C (gio hang, dat hang) tren MySQL that trong Docker, dung du lieu seed:
 *   Com Nha Demo Quan 1 (don toi thieu 30.000d, "Com ga xoi mo" het hang), Quan 3, Bun Cha Demo;
 *   phi ship 15.000d cho 2 km dau (V20261107_02).
 * Cac test dung chung database nen moi test tu xoa gio truoc, va tra lai du lieu da sua trong finally.
 * Can Docker Desktop dang chay.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=cart_order_test_secret_dai_hon_32_ky_tu_abcdef",
        "app.admin.phone=",
        "app.admin.password="
})
@AutoConfigureMockMvc
@Testcontainers
class CartOrderIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    static final String Q1 = "Cơm Nhà Demo - Chi nhánh Quận 1";
    static final String Q3 = "Cơm Nhà Demo - Chi nhánh Quận 3";
    // Toa do chi nhanh Quan 1: giao ngay tai quan thi khoang cach 0 km, phi ship = 15.000d
    static final double Q1_LAT = 10.7812;
    static final double Q1_LNG = 106.7008;

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

    private long itemId(String restaurantName, String itemName) {
        return jdbc.queryForObject("""
                SELECT mi.id FROM menu_items mi
                JOIN menu_categories mc ON mc.id = mi.category_id
                JOIN restaurants r ON r.id = mc.restaurant_id
                WHERE r.name = :restaurantName AND mi.name = :itemName
                """, Map.of("restaurantName", restaurantName, "itemName", itemName), Long.class);
    }

    private ResultActions addItem(String token, long menuItemId, int quantity, String note, boolean replaceCart)
            throws Exception {
        String body = objectMapper.writeValueAsString(note == null
                ? Map.of("menuItemId", menuItemId, "quantity", quantity, "replaceCart", replaceCart)
                : Map.of("menuItemId", menuItemId, "quantity", quantity, "note", note, "replaceCart", replaceCart));
        return mockMvc.perform(post("/api/cart/items").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private void clearCart(String token) throws Exception {
        mockMvc.perform(delete("/api/cart").header("Authorization", token))
                .andExpect(status().isNoContent());
    }

    private ResultActions placeOrder(String token, double lat, double lng) throws Exception {
        String body = """
                {"receiverName":"Khách Demo","receiverPhone":"0911000001",
                 "deliveryAddress":"12 Nguyễn Thị Minh Khai, Quận 1","deliveryLat":%s,"deliveryLng":%s,
                 "note":"Gọi trước khi tới","paymentMethod":"COD"}
                """.formatted(lat, lng);
        return mockMvc.perform(post("/api/orders").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long countOrders() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM orders", Map.of(), Long.class);
    }

    private long countOrderItems() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM order_items", Map.of(), Long.class);
    }

    private long countHistory() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history", Map.of(), Long.class);
    }

    // ----- Gio hang -----

    @Test
    void cartAddMergeUpdateAndRemove() throws Exception {
        String token = login("0911000001");
        clearCart(token);
        long comSuon = itemId(Q1, "Cơm sườn nướng");

        addItem(token, comSuon, 2, null, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantName").value(Q1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].unitPrice").value(45000))
                .andExpect(jsonPath("$.items[0].lineTotal").value(90000))
                .andExpect(jsonPath("$.items[0].isAvailable").value(true))
                .andExpect(jsonPath("$.itemCount").value(2))
                .andExpect(jsonPath("$.subtotal").value(90000));

        // Cung mon, cung ghi chu (khong ghi chu) -> cong don vao dong cu
        addItem(token, comSuon, 1, null, false)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.subtotal").value(135000));

        // Ghi chu khac -> dong moi
        JsonNode cart = json(addItem(token, comSuon, 1, "Ít cơm", false)
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.itemCount").value(4)));
        long firstLine = cart.at("/items/0/id").asLong();
        long noteLine = cart.at("/items/1/id").asLong();

        mockMvc.perform(patch("/api/cart/items/{id}", noteLine).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2,\"note\":\"Nhiều cơm\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[1].quantity").value(2))
                .andExpect(jsonPath("$.items[1].note").value("Nhiều cơm"));

        mockMvc.perform(delete("/api/cart/items/{id}", firstLine).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));

        // Xoa mon cuoi cung -> gio bi xoa luon, tra ve gio trong
        mockMvc.perform(delete("/api/cart/items/{id}", noteLine).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(nullValue()))
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.itemCount").value(0));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM carts c JOIN users u ON u.id = c.user_id WHERE u.phone = '0911000001'
                """, Map.of(), Long.class));
    }

    @Test
    void cannotAddSoldOutItemOrItemOfClosedRestaurant() throws Exception {
        String token = login("0911000001");
        clearCart(token);

        addItem(token, itemId(Q1, "Cơm gà xối mỡ"), 1, null, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("đã hết")));

        addItem(token, 999999, 1, null, false)
                .andExpect(status().isNotFound());

        jdbc.update("UPDATE restaurants SET open_status = 'CLOSED' WHERE name = 'Bún Chả Demo'", Map.of());
        try {
            addItem(token, itemId("Bún Chả Demo", "Bún chả Hà Nội"), 1, null, false)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("đóng cửa")));
        } finally {
            jdbc.update("UPDATE restaurants SET open_status = 'OPEN' WHERE name = 'Bún Chả Demo'", Map.of());
        }
    }

    @Test
    void cartHoldsItemsOfOnlyOneRestaurant() throws Exception {
        String token = login("0911000001");
        clearCart(token);
        addItem(token, itemId(Q1, "Cơm sườn nướng"), 1, null, false).andExpect(status().isOk());

        long comSuonQ3 = itemId(Q3, "Cơm sườn nướng");
        addItem(token, comSuonQ3, 1, null, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Giỏ hàng đang có món của quán khác"));

        // Khach dong y thay gio
        addItem(token, comSuonQ3, 1, null, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantName").value(Q3))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].menuItemId").value(comSuonQ3));
    }

    @Test
    void customerCannotChangeAnotherCustomersCart() throws Exception {
        String owner = login("0911000001");
        String other = login("0911000002");
        clearCart(owner);
        long lineId = json(addItem(owner, itemId(Q1, "Cơm sườn nướng"), 1, null, false))
                .at("/items/0/id").asLong();

        mockMvc.perform(patch("/api/cart/items/{id}", lineId).header("Authorization", other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":5}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/cart/items/{id}", lineId).header("Authorization", other))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/cart").header("Authorization", owner))
                .andExpect(jsonPath("$.items[0].quantity").value(1));
    }

    // ----- Dat hang -----

    @Test
    void orderUsesPricesFromDatabaseAndClearsCart() throws Exception {
        String token = login("0911000001");
        clearCart(token);
        long caKho = itemId(Q1, "Cơm cá kho tộ");
        addItem(token, caKho, 2, null, false).andExpect(status().isOk());
        addItem(token, itemId(Q1, "Trà đá"), 1, null, false).andExpect(status().isOk());

        // Xem truoc: 2 x 45.000 + 5.000 = 95.000, giao tai quan -> phi 15.000
        mockMvc.perform(post("/api/orders/preview").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryLat\":%s,\"deliveryLng\":%s}".formatted(Q1_LAT, Q1_LNG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(95000))
                .andExpect(jsonPath("$.deliveryFee").value(15000))
                .andExpect(jsonPath("$.discountAmount").value(0))
                .andExpect(jsonPath("$.totalAmount").value(110000))
                .andExpect(jsonPath("$.distanceKm").value(0.0));

        // Quan doi gia SAU khi khach da them vao gio: don phai tinh theo gia moi trong database
        jdbc.update("UPDATE menu_items SET price = 47000 WHERE id = :id", Map.of("id", caKho));
        try {
            long ordersBefore = countOrders();
            JsonNode order = json(placeOrder(token, Q1_LAT, Q1_LNG)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PLACED"))
                    .andExpect(jsonPath("$.code").value(matchesPattern("FD\\d{12}")))
                    .andExpect(jsonPath("$.restaurant.name").value(Q1))
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.items[0].unitPrice").value(47000))
                    .andExpect(jsonPath("$.subtotal").value(99000))
                    .andExpect(jsonPath("$.deliveryFee").value(15000))
                    .andExpect(jsonPath("$.totalAmount").value(114000))
                    .andExpect(jsonPath("$.paymentMethod").value("COD"))
                    .andExpect(jsonPath("$.history.length()").value(1))
                    .andExpect(jsonPath("$.history[0].status").value("PLACED")));
            long orderId = order.get("id").asLong();

            assertEquals(ordersBefore + 1, countOrders());
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM order_items WHERE order_id = :id",
                    Map.of("id", orderId), Long.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id = :id",
                    Map.of("id", orderId), Long.class));

            // Gio da bi xoa, bam dat hang lan 2 -> 409, khong tao don thu 2
            mockMvc.perform(get("/api/cart").header("Authorization", token))
                    .andExpect(jsonPath("$.items.length()").value(0));
            placeOrder(token, Q1_LAT, Q1_LNG)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Giỏ hàng trống"));
            assertEquals(ordersBefore + 1, countOrders());
        } finally {
            jdbc.update("UPDATE menu_items SET price = 45000 WHERE id = :id", Map.of("id", caKho));
        }
    }

    @Test
    void itemSoldOutAtCheckoutIsRejectedAndNothingIsSaved() throws Exception {
        String token = login("0911000002");
        clearCart(token);
        long trung = itemId(Q3, "Trứng ốp la");
        addItem(token, itemId(Q3, "Cơm sườn nướng"), 1, null, false).andExpect(status().isOk());
        addItem(token, trung, 1, null, false).andExpect(status().isOk());

        // Mon het hang sau khi khach da them vao gio
        jdbc.update("UPDATE menu_items SET is_available = 0 WHERE id = :id", Map.of("id", trung));
        try {
            long ordersBefore = countOrders();
            placeOrder(token, Q1_LAT, Q1_LNG)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("Trứng ốp la")));
            assertEquals(ordersBefore, countOrders());

            // Gio con nguyen, mon het hang duoc danh dau de khach xoa
            mockMvc.perform(get("/api/cart").header("Authorization", token))
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.items[1].isAvailable").value(false));
        } finally {
            jdbc.update("UPDATE menu_items SET is_available = 1 WHERE id = :id", Map.of("id", trung));
        }
    }

    @Test
    void orderBelowMinimumIsRejected() throws Exception {
        String token = login("0911000002");
        clearCart(token);
        // 1 ly tra da 5.000d, quan yeu cau toi thieu 30.000d
        addItem(token, itemId(Q1, "Trà đá"), 1, null, false).andExpect(status().isOk());

        placeOrder(token, Q1_LAT, Q1_LNG)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Đơn tối thiểu của quán là 30.000đ"));
    }

    @Test
    void failureMidwayRollsBackTheWholeOrder() throws Exception {
        String token = login("0911000002");
        clearCart(token);
        // Ghi chu dac biet de buoc ghi order_items bi loi (xem rang buoc CHECK ben duoi)
        addItem(token, itemId(Q3, "Cơm sườn nướng"), 1, "gia lap loi", false).andExpect(status().isOk());

        long ordersBefore = countOrders();
        long itemsBefore = countOrderItems();
        long historyBefore = countHistory();

        // Gan tam rang buoc: order_items khong nhan ghi chu "gia lap loi" (NULL van qua).
        // Khi dat hang: INSERT orders thanh cong roi INSERT order_items moi loi -> phai rollback ca don.
        jdbc.update("""
                ALTER TABLE order_items ADD CONSTRAINT chk_test_gia_lap_loi
                CHECK (note IS NULL OR note <> 'gia lap loi')
                """, Map.of());
        try {
            placeOrder(token, Q1_LAT, Q1_LNG)
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Lỗi hệ thống, vui lòng thử lại sau"));
        } finally {
            jdbc.update("ALTER TABLE order_items DROP CHECK chk_test_gia_lap_loi", Map.of());
        }

        // Khong con dong nao cua don loi, gio van con nguyen
        assertEquals(ordersBefore, countOrders());
        assertEquals(itemsBefore, countOrderItems());
        assertEquals(historyBefore, countHistory());
        mockMvc.perform(get("/api/cart").header("Authorization", token))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].note").value("gia lap loi"));
    }

    @Test
    void invalidOrderRequestReturns400WithFieldErrors() throws Exception {
        String token = login("0911000002");
        String body = """
                {"receiverName":"","receiverPhone":"123","deliveryAddress":"Quận 1",
                 "deliveryLat":10.78,"deliveryLng":106.70,"paymentMethod":"VNPAY"}
                """;
        mockMvc.perform(post("/api/orders").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.receiverName").exists())
                .andExpect(jsonPath("$.fieldErrors.receiverPhone").exists())
                .andExpect(jsonPath("$.fieldErrors.paymentMethod").exists());
    }

    @Test
    void emptyCartAndFarAddressAreRejected() throws Exception {
        String token = login("0911000002");
        clearCart(token);
        mockMvc.perform(post("/api/orders/preview").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryLat\":%s,\"deliveryLng\":%s}".formatted(Q1_LAT, Q1_LNG)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Giỏ hàng trống"));

        // Dia chi o Ha Noi, cach quan hon 1.000 km
        addItem(token, itemId(Q1, "Cơm sườn nướng"), 1, null, false).andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/preview").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryLat\":21.0285,\"deliveryLng\":105.8542}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("quá xa")));
    }
}
