package vn.hutech.fooddelivery.restaurant;

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

import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test tich hop Module B (quan, menu) tren MySQL that trong Docker, dung du lieu seed:
 *   V2 (12 quan cua chu 0901234567) + V20261107_01 (Com Nha Demo 2 chi nhanh cua 0922000001, Bun Cha Demo cua 0922000002).
 * Can Docker Desktop dang chay.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=restaurant_test_secret_dai_hon_32_ky_tu_abcdef",
        "app.admin.phone=",
        "app.admin.password="
})
@AutoConfigureMockMvc
@Testcontainers
class RestaurantApiIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    static final String COM_NHA_Q1 = "Cơm Nhà Demo - Chi nhánh Quận 1";
    static final String COM_NHA_Q3 = "Cơm Nhà Demo - Chi nhánh Quận 3";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    private long restaurantId(String name) {
        return jdbc.queryForObject("SELECT id FROM restaurants WHERE name = :name",
                Map.of("name", name), Long.class);
    }

    private String loginAndGetToken(String phone) throws Exception {
        String body = """
                {"identifier":"%s","password":"Demo@12345"}
                """.formatted(phone);
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    // ----- B1: danh sach co phan trang -----

    @Test
    void listIsPagedOverAllActiveRestaurants() throws Exception {
        // 12 quan V2 + 3 quan moi = 15 quan ACTIVE -> 3 trang, moi trang 5 quan
        mockMvc.perform(get("/api/restaurants").param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalItems").value(15))
                .andExpect(jsonPath("$.totalPages").value(3));

        mockMvc.perform(get("/api/restaurants").param("page", "3").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalItems").value(15));
    }

    @Test
    void withoutLocationSortsByRatingAndDistanceIsNull() throws Exception {
        // Diem cao nhat 4.7 co 2 quan (id 5 va 12), bang diem thi id nho truoc
        mockMvc.perform(get("/api/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Gogi House Nguyễn Huệ"))
                .andExpect(jsonPath("$.items[0].distanceKm").value(nullValue()));
    }

    @Test
    void keywordAndCuisineFilters() throws Exception {
        mockMvc.perform(get("/api/restaurants").param("keyword", "Cơm Nhà"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2));

        // Chuoi toan dau cach coi nhu khong loc
        mockMvc.perform(get("/api/restaurants").param("keyword", "   "))
                .andExpect(jsonPath("$.totalItems").value(15));

        // Mon Viet (id 1): quan 1, 7, 10, 12 trong V2 + 3 quan moi
        mockMvc.perform(get("/api/restaurants").param("cuisineId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(7));
    }

    @Test
    void withLocationNearestRestaurantComesFirst() throws Exception {
        // Dung ngay toa do chi nhanh Quan 1 -> quan nay dung dau, cach 0 km
        mockMvc.perform(get("/api/restaurants").param("lat", "10.7812").param("lng", "106.7008"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value(COM_NHA_Q1))
                .andExpect(jsonPath("$.items[0].distanceKm").value(0.0))
                .andExpect(jsonPath("$.items[1].distanceKm").isNumber());
    }

    @Test
    void invalidPagingOrLocationReturns400() throws Exception {
        mockMvc.perform(get("/api/restaurants").param("size", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(get("/api/restaurants").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/restaurants").param("lat", "999").param("lng", "106.7"))
                .andExpect(status().isBadRequest());
    }

    // ----- B2, B3, B4: chi tiet quan, menu, chi tiet mon -----

    @Test
    void detailShowsMerchantAndOtherBranches() throws Exception {
        mockMvc.perform(get("/api/restaurants/{id}", restaurantId(COM_NHA_Q1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchant.businessName").value("Cơm Nhà Demo"))
                .andExpect(jsonPath("$.otherBranches.length()").value(1))
                .andExpect(jsonPath("$.otherBranches[0].name").value(COM_NHA_Q3))
                .andExpect(jsonPath("$.hours.length()").value(7));

        // Quan duy nhat cua chu quan thi khong co chi nhanh khac
        mockMvc.perform(get("/api/restaurants/{id}", restaurantId("Bún Chả Demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otherBranches.length()").value(0));

        mockMvc.perform(get("/api/restaurants/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void menuIsGroupedByCategoryAndShowsSoldOutItem() throws Exception {
        mockMvc.perform(get("/api/restaurants/{id}/menu", restaurantId(COM_NHA_Q1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(3))
                .andExpect(jsonPath("$.categories[0].name").value("Cơm"))
                .andExpect(jsonPath("$.categories[0].items[1].name").value("Cơm gà xối mỡ"))
                .andExpect(jsonPath("$.categories[0].items[1].isAvailable").value(false));
    }

    @Test
    void menuItemDetailMustBelongToRestaurant() throws Exception {
        long q1 = restaurantId(COM_NHA_Q1);
        long q3 = restaurantId(COM_NHA_Q3);
        Long itemId = jdbc.queryForObject("""
                SELECT mi.id FROM menu_items mi JOIN menu_categories mc ON mc.id = mi.category_id
                WHERE mc.restaurant_id = :restaurantId AND mi.name = 'Cơm bò lúc lắc'
                """, Map.of("restaurantId", q3), Long.class);

        // Gia lay theo dung chi nhanh: Quan 3 ban 69.000d
        mockMvc.perform(get("/api/restaurants/{r}/menu-items/{i}", q3, itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(q3))
                .andExpect(jsonPath("$.categoryName").value("Cơm"))
                .andExpect(jsonPath("$.price").value(69000))
                .andExpect(jsonPath("$.isAvailable").value(true));

        // Cung id mon nhung hoi qua quan khac -> 404
        mockMvc.perform(get("/api/restaurants/{r}/menu-items/{i}", q1, itemId))
                .andExpect(status().isNotFound());
    }

    // ----- Seed va B5: quan cua chu quan -----

    @Test
    void seedHasAtLeastFiveRestaurantsWithTenItems() {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT mc.restaurant_id
                    FROM menu_items mi JOIN menu_categories mc ON mc.id = mi.category_id
                    GROUP BY mc.restaurant_id
                    HAVING COUNT(*) >= 10
                ) t
                """, Map.of(), Long.class);
        assertTrue(count != null && count >= 5, "Can it nhat 5 quan co tu 10 mon, dang co " + count);
    }

    @Test
    void merchantSeesOnlyOwnRestaurants() throws Exception {
        mockMvc.perform(get("/api/merchant/restaurants")
                        .header("Authorization", "Bearer " + loginAndGetToken("0922000001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value(COM_NHA_Q1))
                .andExpect(jsonPath("$[1].name").value(COM_NHA_Q3));

        mockMvc.perform(get("/api/merchant/restaurants")
                        .header("Authorization", "Bearer " + loginAndGetToken("0901234567")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12));

        mockMvc.perform(get("/api/merchant/restaurants")
                        .header("Authorization", "Bearer " + loginAndGetToken("0911000001")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/merchant/restaurants"))
                .andExpect(status().isUnauthorized());
    }
}
