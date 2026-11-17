package vn.hutech.fooddelivery.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test tich hop so dia chi (Module G tuan 4) tren MySQL that trong Docker.
 * Test doc dung seed V20261108_01 (khach 0911000001 co 3 dia chi); test sua/xoa dung tai khoan dang ky moi
 * de khong lam doi du lieu seed. Can Docker Desktop dang chay.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=address_test_secret_dai_hon_32_ky_tu_abcdefgh",
        "app.admin.phone=",
        "app.admin.password="
})
@AutoConfigureMockMvc
@Testcontainers
class AddressIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    // ----- Ham tien ich -----

    private String login(String phone) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\",\"password\":\"Demo@12345\"}".formatted(phone)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("token").asText();
    }

    /** Dang ky 1 khach moi (chua co dia chi nao), tra ve token. */
    private String registerCustomer(String phone) throws Exception {
        String body = """
                {"fullName":"Khach So Dia Chi","phone":"%s","password":"matkhau123","role":"CUSTOMER"}
                """.formatted(phone);
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("token").asText();
    }

    private static String addressJson(String label, String addressLine, boolean isDefault) {
        return """
                {"label":"%s","receiverName":"Người Nhận","receiverPhone":"0912345678",
                 "addressLine":"%s","lat":10.7756,"lng":106.7004,"isDefault":%s}
                """.formatted(label, addressLine, isDefault);
    }

    private ResultActions create(String token, String json) throws Exception {
        return mockMvc.perform(post("/api/users/me/addresses").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long createId(String token, String json) throws Exception {
        JsonNode created = objectMapper.readTree(create(token, json)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return created.get("id").asLong();
    }

    // ----- Test -----

    @Test
    void seededAddressesAreListedDefaultFirst() throws Exception {
        mockMvc.perform(get("/api/users/me/addresses").header("Authorization", login("0911000001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].label").value("Nhà riêng"))
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[0].lat").value(10.7756))
                .andExpect(jsonPath("$[?(@.isDefault == true)]", hasSize(1)));
    }

    @Test
    void firstAddressBecomesDefaultAndOnlyOneDefaultAtATime() throws Exception {
        String token = registerCustomer("0955000001");

        // Dia chi dau tien: client gui isDefault=false van thanh mac dinh
        long first = createId(token, addressJson("Nhà", "1 Lê Lợi, Quận 1", false));
        long second = createId(token, addressJson("Công ty", "2 Lê Lợi, Quận 1", true));

        mockMvc.perform(get("/api/users/me/addresses").header("Authorization", token))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[1].id").value(first))
                .andExpect(jsonPath("$[1].isDefault").value(false));

        // Doi mac dinh ve dia chi dau
        mockMvc.perform(patch("/api/users/me/addresses/{id}/default", first).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));
        mockMvc.perform(get("/api/users/me/addresses").header("Authorization", token))
                .andExpect(jsonPath("$[0].id").value(first))
                .andExpect(jsonPath("$[?(@.isDefault == true)]", hasSize(1)));
    }

    @Test
    void updateChangesContentButFalseDoesNotRemoveDefault() throws Exception {
        String token = registerCustomer("0955000002");
        long id = createId(token, addressJson("Nhà", "1 Lê Lợi, Quận 1", false));

        mockMvc.perform(put("/api/users/me/addresses/{id}", id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("Nhà mới", "99 Hai Bà Trưng, Quận 1", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Nhà mới"))
                .andExpect(jsonPath("$.addressLine").value("99 Hai Bà Trưng, Quận 1"))
                // Dia chi duy nhat van la mac dinh
                .andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test
    void deletingDefaultPromotesNewestRemaining() throws Exception {
        String token = registerCustomer("0955000003");
        long first = createId(token, addressJson("Một", "1 Lê Lợi, Quận 1", false));
        long second = createId(token, addressJson("Hai", "2 Lê Lợi, Quận 1", false));
        long third = createId(token, addressJson("Ba", "3 Lê Lợi, Quận 1", false));

        // first dang la mac dinh -> xoa -> third (moi nhat) thanh mac dinh
        mockMvc.perform(delete("/api/users/me/addresses/{id}", first).header("Authorization", token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/users/me/addresses").header("Authorization", token))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(third))
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[1].id").value(second));

        mockMvc.perform(delete("/api/users/me/addresses/{id}", second).header("Authorization", token))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/users/me/addresses/{id}", third).header("Authorization", token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/users/me/addresses").header("Authorization", token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void cannotTouchAnotherUsersAddress() throws Exception {
        String owner = login("0911000001");
        long ownerAddressId = objectMapper.readTree(mockMvc.perform(get("/api/users/me/addresses")
                        .header("Authorization", owner))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
        String other = login("0911000002");

        mockMvc.perform(put("/api/users/me/addresses/{id}", ownerAddressId).header("Authorization", other)
                        .contentType(MediaType.APPLICATION_JSON).content(addressJson("Hack", "Hack", true)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/users/me/addresses/{id}/default", ownerAddressId).header("Authorization", other))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/users/me/addresses/{id}", ownerAddressId).header("Authorization", other))
                .andExpect(status().isNotFound());

        // Dia chi cua chu van con nguyen
        mockMvc.perform(get("/api/users/me/addresses").header("Authorization", owner))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].label").value("Nhà riêng"));
    }

    @Test
    void invalidAddressReturns400AndAnonymousReturns401() throws Exception {
        String token = registerCustomer("0955000004");
        create(token, """
                {"receiverName":"","receiverPhone":"123","addressLine":"1 Lê Lợi","lng":106.7}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.receiverName").exists())
                .andExpect(jsonPath("$.fieldErrors.receiverPhone").exists())
                .andExpect(jsonPath("$.fieldErrors.lat").value("Vui lòng chọn vị trí trên bản đồ"));
        create(token, """
                {"receiverName":"A","receiverPhone":"0912345678","addressLine":"1 Lê Lợi","lat":999,"lng":106.7}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.lat").value("Vĩ độ không hợp lệ"));

        mockMvc.perform(get("/api/users/me/addresses"))
                .andExpect(status().isUnauthorized());
    }
}
