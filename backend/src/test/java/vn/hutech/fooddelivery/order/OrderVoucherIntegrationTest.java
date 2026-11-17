package vn.hutech.fooddelivery.order;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Module H (tuan 5): voucher o buoc thanh toan, dung VoucherService cua Diep.
 * Voucher test duoc them thang vao database cua test (container rieng), khong dung den database that.
 * Gio test: 1 x "Com suon nuong" 45.000d cua "Com Nha Demo - Chi nhanh Quan 1".
 * Can Docker Desktop dang chay.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=order_voucher_test_secret_dai_hon_32_ky_tu_ab",
        "app.admin.phone=",
        "app.admin.password="
})
@AutoConfigureMockMvc
@Testcontainers
class OrderVoucherIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    static final String RESTAURANT = "Cơm Nhà Demo - Chi nhánh Quận 1";
    static final String CUSTOMER = "0911000001";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    String token;
    long customerId;

    @BeforeEach
    void loginAndFillCart() throws Exception {
        token = login(CUSTOMER);
        customerId = jdbc.queryForObject("SELECT id FROM users WHERE phone = :phone",
                Map.of("phone", CUSTOMER), Long.class);
        fillCart();
    }

    @Test
    void previewShowsDiscountForEachVoucherType() throws Exception {
        insertVoucher("PCT50", "PERCENT", "50", "0", "20000", null, 5, null);
        insertVoucher("FIX10K", "FIXED", "10000", "0", null, null, 5, null);
        insertVoucher("SHIP0", "FREE_SHIP", "0", "0", null, null, 5, null);

        // Khong nhap ma: khong giam, khong co loi nhan voucher
        JsonNode plain = preview(null);
        assertMoney("0", plain, "discountAmount");
        assertNull(plain.get("voucherMessage").textValue());
        BigDecimal subtotal = plain.get("subtotal").decimalValue();
        BigDecimal fee = plain.get("deliveryFee").decimalValue();
        assertMoney("45000", plain, "subtotal");

        // PERCENT 50% cua 45.000 = 22.500 nhung bi tran max_discount 20.000
        JsonNode percent = preview("PCT50");
        assertMoney("20000", percent, "discountAmount");
        assertMoneyEquals(subtotal.add(fee).subtract(new BigDecimal("20000")), percent, "totalAmount");
        assertEquals("PCT50", percent.get("voucherCode").textValue());

        // FIXED giam dung 10.000
        assertMoney("10000", preview("FIX10K"), "discountAmount");

        // FREE_SHIP (discount_value 0 = mien het): giam dung bang phi giao, tong chi con tien mon
        JsonNode freeShip = preview("SHIP0");
        assertMoneyEquals(fee, freeShip, "discountAmount");
        assertMoneyEquals(subtotal, freeShip, "totalAmount");
    }

    @Test
    void unusableVoucherIsExplainedInPreviewAndRejectedWhenOrdering() throws Exception {
        long otherRestaurantId = jdbc.queryForObject(
                "SELECT id FROM restaurants WHERE name <> :name ORDER BY id LIMIT 1", Map.of("name", RESTAURANT),
                Long.class);
        insertVoucher("MIN100K", "FIXED", "10000", "100000", null, null, 5, null);
        insertVoucher("QUANKHAC", "FIXED", "10000", "0", null, null, 5, otherRestaurantId);

        // Xem truoc: van 200, khong giam, kem ly do cua VoucherService
        JsonNode unknown = preview("KHONGTONTAI");
        assertMoney("0", unknown, "discountAmount");
        assertNull(unknown.get("voucherCode").textValue());
        assertEquals("Mã voucher không tồn tại", unknown.get("voucherMessage").textValue());
        assertEquals(true, preview("MIN100K").get("voucherMessage").textValue().contains("tối thiểu"));
        assertEquals(true, preview("QUANKHAC").get("voucherMessage").textValue().contains("nhà hàng"));

        // Dat hang voi ma khong dung duoc: 409, khong tao don, gio con nguyen
        long ordersBefore = countOrdersOfCustomer();
        placeOrder("\"voucherCode\":\"KHONGTONTAI\"")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Mã voucher không tồn tại"));
        assertEquals(ordersBefore, countOrdersOfCustomer());
        assertEquals(1, countCartItems());
    }

    @Test
    void orderingWithVoucherSavesDiscountAndRecordsUsageOnce() throws Exception {
        long voucherId = insertVoucher("MOTLAN15K", "FIXED", "15000", "0", null, 100, 1, null);

        String body = placeOrder("\"voucherCode\":\"MOTLAN15K\"")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode order = objectMapper.readTree(body);
        long orderId = order.get("id").asLong();
        assertMoney("15000", order, "discountAmount");
        assertMoneyEquals(order.get("subtotal").decimalValue().add(order.get("deliveryFee").decimalValue())
                .subtract(new BigDecimal("15000")), order, "totalAmount");

        // Database: don luu so tien giam, voucher tang 1 luot, co dung 1 dong voucher_usages cho don nay
        assertEquals(0, new BigDecimal("15000").compareTo(jdbc.queryForObject(
                "SELECT discount_amount FROM orders WHERE id = :id", Map.of("id", orderId), BigDecimal.class)));
        assertEquals(1, usedCount(voucherId));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM voucher_usages
                WHERE voucher_id = :voucherId AND user_id = :userId AND order_id = :orderId
                """, Map.of("voucherId", voucherId, "userId", customerId, "orderId", orderId), Integer.class));

        // per_user_limit = 1: lan dat thu 2 bi tu choi, khong tang them luot
        fillCart();
        placeOrder("\"voucherCode\":\"MOTLAN15K\"")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("hết số lượt")));
        assertEquals(1, usedCount(voucherId));
    }

    @Test
    void clientCannotSendItsOwnDiscount() throws Exception {
        // Client gui them discountAmount/totalAmount gia: server bo qua, tu tinh tien
        String body = placeOrder("\"discountAmount\":999999,\"totalAmount\":1")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode order = objectMapper.readTree(body);
        assertMoney("0", order, "discountAmount");
        assertMoneyEquals(order.get("subtotal").decimalValue().add(order.get("deliveryFee").decimalValue()),
                order, "totalAmount");
    }

    // ----- Ham phu -----

    private String login(String phone) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\",\"password\":\"Demo@12345\"}".formatted(phone)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).get("token").asText();
    }

    /** Xoa gio roi them 1 phan "Com suon nuong" (45.000d). */
    private void fillCart() throws Exception {
        long menuItemId = jdbc.queryForObject("""
                SELECT mi.id FROM menu_items mi
                JOIN menu_categories mc ON mc.id = mi.category_id
                JOIN restaurants r ON r.id = mc.restaurant_id
                WHERE r.name = :restaurant AND mi.name = 'Cơm sườn nướng'
                """, Map.of("restaurant", RESTAURANT), Long.class);
        mockMvc.perform(delete("/api/cart").header("Authorization", token)).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/cart/items").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuItemId\":%d,\"quantity\":1}".formatted(menuItemId)))
                .andExpect(status().isOk());
    }

    private JsonNode preview(String voucherCode) throws Exception {
        String code = voucherCode == null ? "" : ",\"voucherCode\":\"%s\"".formatted(voucherCode);
        String body = mockMvc.perform(post("/api/orders/preview").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryLat\":10.7756,\"deliveryLng\":106.7004" + code + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    /** Dat hang COD tu gio hien tai, extraJson la cac truong them (vi du voucherCode). */
    private ResultActions placeOrder(String extraJson) throws Exception {
        return mockMvc.perform(post("/api/orders").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"receiverName":"Khách Test","receiverPhone":"0911000001",
                         "deliveryAddress":"10 Lê Lợi, Quận 1","deliveryLat":10.7756,"deliveryLng":106.7004,
                         "paymentMethod":"COD",%s}
                        """.formatted(extraJson)));
    }

    /** Them 1 voucher dang chay (bat dau hom qua, het han ngay mai). Tra ve id. */
    private long insertVoucher(String code, String type, String value, String minOrder, String maxDiscount,
                               Integer usageLimit, int perUserLimit, Long restaurantId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", code)
                .addValue("title", "Voucher test " + code)
                .addValue("type", type)
                .addValue("value", new BigDecimal(value))
                .addValue("minOrder", new BigDecimal(minOrder))
                .addValue("maxDiscount", maxDiscount == null ? null : new BigDecimal(maxDiscount))
                .addValue("startAt", LocalDateTime.now().minusDays(1))
                .addValue("endAt", LocalDateTime.now().plusDays(1))
                .addValue("usageLimit", usageLimit)
                .addValue("perUserLimit", perUserLimit)
                .addValue("restaurantId", restaurantId);
        jdbc.update("""
                INSERT INTO vouchers (code, title, discount_type, discount_value, min_order, max_discount,
                                      start_at, end_at, usage_limit, per_user_limit, restaurant_id, status)
                VALUES (:code, :title, :type, :value, :minOrder, :maxDiscount,
                        :startAt, :endAt, :usageLimit, :perUserLimit, :restaurantId, 'ACTIVE')
                """, params);
        return jdbc.queryForObject("SELECT id FROM vouchers WHERE code = :code", Map.of("code", code), Long.class);
    }

    private int usedCount(long voucherId) {
        return jdbc.queryForObject("SELECT used_count FROM vouchers WHERE id = :id", Map.of("id", voucherId),
                Integer.class);
    }

    private long countOrdersOfCustomer() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE customer_id = :id", Map.of("id", customerId),
                Long.class);
    }

    private int countCartItems() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM cart_items ci JOIN carts c ON c.id = ci.cart_id WHERE c.user_id = :id
                """, Map.of("id", customerId), Integer.class);
    }

    /** So sanh tien bang compareTo (khong phu thuoc so chu so le). */
    private static void assertMoney(String expected, JsonNode node, String field) {
        assertMoneyEquals(new BigDecimal(expected), node, field);
    }

    private static void assertMoneyEquals(BigDecimal expected, JsonNode node, String field) {
        BigDecimal actual = node.get(field).decimalValue();
        assertEquals(0, expected.compareTo(actual), field + ": mong doi " + expected + " nhung la " + actual);
    }
}
