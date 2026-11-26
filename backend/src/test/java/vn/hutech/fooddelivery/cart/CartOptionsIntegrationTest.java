package vn.hutech.fooddelivery.cart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test tich hop Module M (tuy chon mon trong gio va don) tren MySQL that trong Docker.
 * Seed chua co tuy chon nao, nen moi test tu tao 1 mon "Trà sữa" moi o Com Nha Demo Quan 1 (don toi thieu 30.000d):
 *   gia 30.000d; nhom "Size" bat buoc chon 1 (Vừa +0, Lớn +10.000);
 *   nhom "Topping" khong bat buoc, toi da 2 (Trân châu +5.000, Thạch +7.000, Pudding +8.000).
 * Can Docker Desktop dang chay.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=cart_options_test_secret_dai_hon_32_ky_tu_abcdef",
        "app.admin.phone=",
        "app.admin.password="
})
@AutoConfigureMockMvc
@Testcontainers
class CartOptionsIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    static final String Q1 = "Cơm Nhà Demo - Chi nhánh Quận 1";
    // Giao ngay tai quan Quan 1: 0 km, phi ship 15.000d
    static final double Q1_LAT = 10.7812;
    static final double Q1_LNG = 106.7008;
    static final String CUSTOMER = "0911000001";
    static final String MERCHANT = "0922000001";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    /** id cua mon tra sua vua tao va 5 tuy chon cua no. */
    record TraSua(long itemId, long vua, long lon, long tranChau, long thach, long pudding) {
    }

    // ----- Ham tien ich -----

    private TraSua createTraSua() {
        long categoryId = jdbc.queryForObject("""
                SELECT mi.category_id FROM menu_items mi
                JOIN menu_categories mc ON mc.id = mi.category_id
                JOIN restaurants r ON r.id = mc.restaurant_id
                WHERE r.name = :restaurantName AND mi.name = 'Cơm sườn nướng'
                """, Map.of("restaurantName", Q1), Long.class);
        long itemId = insert("INSERT INTO menu_items (category_id, name, price) VALUES (:categoryId, 'Trà sữa', 30000)",
                Map.of("categoryId", categoryId));
        long size = insert("""
                INSERT INTO option_groups (menu_item_id, name, is_required, min_select, max_select, sort_order)
                VALUES (:itemId, 'Size', 1, 1, 1, 1)
                """, Map.of("itemId", itemId));
        long topping = insert("""
                INSERT INTO option_groups (menu_item_id, name, is_required, min_select, max_select, sort_order)
                VALUES (:itemId, 'Topping', 0, 0, 2, 2)
                """, Map.of("itemId", itemId));
        return new TraSua(itemId,
                insertOption(size, "Vừa", 0), insertOption(size, "Lớn", 10000),
                insertOption(topping, "Trân châu", 5000), insertOption(topping, "Thạch", 7000),
                insertOption(topping, "Pudding", 8000));
    }

    private long insertOption(long groupId, String name, long extraPrice) {
        return insert("INSERT INTO options (group_id, name, extra_price) VALUES (:groupId, :name, :extraPrice)",
                Map.of("groupId", groupId, "name", name, "extraPrice", extraPrice));
    }

    private long insert(String sql, Map<String, ?> params) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, new MapSqlParameterSource(params), keyHolder, new String[]{"id"});
        return keyHolder.getKey().longValue();
    }

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

    private ResultActions addItem(String token, long menuItemId, int quantity, Long... optionIds) throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("menuItemId", menuItemId, "quantity", quantity, "optionIds", List.of(optionIds)));
        return mockMvc.perform(post("/api/cart/items").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void clearCart(String token) throws Exception {
        mockMvc.perform(delete("/api/cart").header("Authorization", token))
                .andExpect(status().isNoContent());
    }

    private ResultActions placeOrder(String token) throws Exception {
        String body = """
                {"receiverName":"Khách Demo","receiverPhone":"0911000001",
                 "deliveryAddress":"12 Nguyễn Thị Minh Khai, Quận 1","deliveryLat":%s,"deliveryLng":%s,
                 "paymentMethod":"COD"}
                """.formatted(Q1_LAT, Q1_LNG);
        return mockMvc.perform(post("/api/orders").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private long countOrders() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM orders", Map.of(), Long.class);
    }

    // ----- Gio hang -----

    @Test
    void optionPricesAreAddedToUnitPriceFromDatabase() throws Exception {
        String token = login(CUSTOMER);
        clearCart(token);
        TraSua tra = createTraSua();

        // 30.000 + 10.000 (Lớn) + 5.000 (Trân châu) = 45.000 moi ly
        addItem(token, tra.itemId(), 2, tra.lon(), tra.tranChau())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].unitPrice").value(45000))
                .andExpect(jsonPath("$.items[0].lineTotal").value(90000))
                .andExpect(jsonPath("$.items[0].options.length()").value(2))
                .andExpect(jsonPath("$.items[0].options[0].name").value("Lớn"))
                .andExpect(jsonPath("$.items[0].options[0].extraPrice").value(10000))
                .andExpect(jsonPath("$.items[0].options[1].name").value("Trân châu"))
                .andExpect(jsonPath("$.subtotal").value(90000));

        // Quan doi gia tuy chon sau khi khach da them: gio tinh theo gia moi (gio khong luu gia)
        jdbc.update("UPDATE options SET extra_price = 12000 WHERE id = :id", Map.of("id", tra.lon()));
        mockMvc.perform(get("/api/cart").header("Authorization", token))
                .andExpect(jsonPath("$.items[0].unitPrice").value(47000))
                .andExpect(jsonPath("$.subtotal").value(94000));
    }

    @Test
    void invalidOptionChoicesAreRejectedWith400() throws Exception {
        String token = login(CUSTOMER);
        clearCart(token);
        TraSua tra = createTraSua();
        TraSua other = createTraSua();
        jdbc.update("UPDATE options SET is_available = 0 WHERE id = :id", Map.of("id", tra.pudding()));

        addItem(token, tra.itemId(), 1, tra.tranChau())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vui lòng chọn ít nhất 1 mục ở nhóm \"Size\""));
        addItem(token, tra.itemId(), 1, tra.vua(), tra.lon())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Nhóm \"Size\" chọn tối đa 1 mục"));
        addItem(token, tra.itemId(), 1, tra.vua(), other.tranChau())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("không thuộc món này")));
        addItem(token, tra.itemId(), 1, tra.vua(), tra.pudding())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tùy chọn \"Pudding\" đã hết"));

        // Khong co gi duoc them vao gio
        mockMvc.perform(get("/api/cart").header("Authorization", token))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void sameOptionsMergeAndDifferentOptionsMakeANewLine() throws Exception {
        String token = login(CUSTOMER);
        clearCart(token);
        TraSua tra = createTraSua();

        addItem(token, tra.itemId(), 1, tra.vua()).andExpect(status().isOk());
        addItem(token, tra.itemId(), 2, tra.vua())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3));

        addItem(token, tra.itemId(), 1, tra.lon())
                .andExpect(jsonPath("$.items.length()").value(2));
        addItem(token, tra.itemId(), 1, tra.vua(), tra.thach())
                .andExpect(jsonPath("$.items.length()").value(3));
        // Cung bo tuy chon, chi khac thu tu gui len -> van cong don vao dong do
        addItem(token, tra.itemId(), 1, tra.thach(), tra.vua())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[2].quantity").value(2))
                .andExpect(jsonPath("$.itemCount").value(6));
    }

    // ----- Dat hang -----

    @Test
    void orderKeepsOptionNamesAndPricesAsOrdered() throws Exception {
        String token = login(CUSTOMER);
        clearCart(token);
        TraSua tra = createTraSua();
        // 30.000 + 10.000 (Lớn) + 7.000 (Thạch) = 47.000, du don toi thieu 30.000
        addItem(token, tra.itemId(), 1, tra.lon(), tra.thach()).andExpect(status().isOk());

        long orderId = json(placeOrder(token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].unitPrice").value(47000))
                .andExpect(jsonPath("$.items[0].options.length()").value(2))
                .andExpect(jsonPath("$.items[0].options[0].name").value("Lớn"))
                .andExpect(jsonPath("$.items[0].options[0].extraPrice").value(10000))
                .andExpect(jsonPath("$.items[0].options[1].name").value("Thạch"))
                .andExpect(jsonPath("$.subtotal").value(47000)))
                .get("id").asLong();
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM order_item_options oio
                JOIN order_items oi ON oi.id = oio.order_item_id
                WHERE oi.order_id = :orderId
                """, Map.of("orderId", orderId), Long.class));

        // Quan doi ten, doi gia tuy chon SAU khi dat: don cu van giu ten va gia luc dat
        jdbc.update("UPDATE options SET name = 'Size L', extra_price = 15000 WHERE id = :id", Map.of("id", tra.lon()));
        mockMvc.perform(get("/api/orders/{id}", orderId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].options[0].name").value("Lớn"))
                .andExpect(jsonPath("$.items[0].options[0].extraPrice").value(10000));

        // Quan thay tuy chon cua mon ngay trong danh sach don (de lam dung size, topping)
        mockMvc.perform(get("/api/merchant/orders").header("Authorization", login(MERCHANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == %s)].items[0].options[1].name", orderId).value("Thạch"));
    }

    @Test
    void soldOutOptionMarksLineUnavailableAndBlocksOrder() throws Exception {
        String token = login(CUSTOMER);
        clearCart(token);
        TraSua tra = createTraSua();
        addItem(token, tra.itemId(), 1, tra.lon()).andExpect(status().isOk());

        // Tuy chon het SAU khi khach da them vao gio
        jdbc.update("UPDATE options SET is_available = 0 WHERE id = :id", Map.of("id", tra.lon()));
        mockMvc.perform(get("/api/cart").header("Authorization", token))
                .andExpect(jsonPath("$.items[0].isAvailable").value(false))
                .andExpect(jsonPath("$.items[0].options[0].isAvailable").value(false));

        long ordersBefore = countOrders();
        placeOrder(token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Lớn")));
        assertEquals(ordersBefore, countOrders());
    }

    @Test
    void deletedRequiredOptionBlocksOrder() throws Exception {
        String token = login(CUSTOMER);
        clearCart(token);
        TraSua tra = createTraSua();
        addItem(token, tra.itemId(), 1, tra.lon(), tra.tranChau()).andExpect(status().isOk());

        // Quan xoa han tuy chon "Lớn": dong cart_item_options tu xoa theo (ON DELETE CASCADE),
        // mon trong gio khong con Size nao -> dat hang phai bao khach chon lai
        jdbc.update("DELETE FROM options WHERE id = :id", Map.of("id", tra.lon()));
        long ordersBefore = countOrders();
        placeOrder(token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Size")));
        assertEquals(ordersBefore, countOrders());
    }
}
