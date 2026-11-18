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

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin quan ly nguoi dung (Module K). Dung tai khoan admin demo co san trong seed
 * (V20261026_01__seed_tai_khoan_demo.sql). Can Docker Desktop dang chay.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=admin_user_test_secret_dai_hon_32_ky_tu_abcd",
        "app.admin.phone=",
        "app.admin.password="
})
@AutoConfigureMockMvc
@Testcontainers
class AdminUserIntegrationTest {

    static final String ADMIN_PHONE = "0900000001";
    static final String ADMIN_PASSWORD = "Demo@12345";

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void adminFiltersUsersByRoleStatusAndKeyword() throws Exception {
        String admin = login(ADMIN_PHONE, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/admin/users").param("role", "CUSTOMER").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].role", everyItem(is("CUSTOMER"))))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());

        // Tim theo so dien thoai (mot phan cung duoc)
        mockMvc.perform(get("/api/admin/users").param("q", "0911000001").header("Authorization", admin))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].phone").value("0911000001"))
                .andExpect(jsonPath("$.items[0].fullName").value("Khách Demo Một"));

        mockMvc.perform(get("/api/admin/users").param("role", "ADMIN").header("Authorization", admin))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].phone").value(ADMIN_PHONE));

        mockMvc.perform(get("/api/admin/users").param("role", "KHONG_CO").header("Authorization", admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lockingBlocksLoginAndCurrentTokenUntilUnlocked() throws Exception {
        String admin = login(ADMIN_PHONE, ADMIN_PASSWORD);
        String customerToken = register("0977100001");
        long customerId = idOf("0977100001", admin);

        changeStatus(admin, customerId, "LOCKED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));

        // Dang nhap lai bi tu choi; token dang dung cung het tac dung ngay (JwtAuthFilter doc lai trang thai)
        loginRequest("0977100001", "MatKhau123")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản đã bị khóa"));
        mockMvc.perform(get("/api/users/me").header("Authorization", customerToken))
                .andExpect(status().isUnauthorized());

        changeStatus(admin, customerId, "ACTIVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        loginRequest("0977100001", "MatKhau123").andExpect(status().isOk());
    }

    @Test
    void adminAccountsCannotBeLockedAndBadStatusIsRejected() throws Exception {
        String admin = login(ADMIN_PHONE, ADMIN_PASSWORD);
        long adminId = idOf(ADMIN_PHONE, admin);

        changeStatus(admin, adminId, "LOCKED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Không khóa được tài khoản quản trị"));
        changeStatus(admin, idOf("0911000002", admin), "DELETED")
                .andExpect(status().isBadRequest());
        changeStatus(admin, 999_999L, "LOCKED")
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminCannotUseAdminUserApis() throws Exception {
        String customer = login("0911000001", "Demo@12345");
        mockMvc.perform(get("/api/admin/users").header("Authorization", customer))
                .andExpect(status().isForbidden());
        changeStatus(customer, 1L, "LOCKED").andExpect(status().isForbidden());
    }

    // ----- Ham tien ich -----

    private ResultActions loginRequest(String phone, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(phone, password)));
    }

    private String login(String phone, String password) throws Exception {
        String response = loginRequest(phone, password).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("token").asText();
    }

    private String register(String phone) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Khách Bị Khóa","phone":"%s","password":"MatKhau123","role":"CUSTOMER"}
                                """.formatted(phone)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("token").asText();
    }

    /** id cua tai khoan theo SDT, lay qua chinh API tim kiem cua admin. */
    private long idOf(String phone, String adminToken) throws Exception {
        String response = mockMvc.perform(get("/api/admin/users").param("q", phone)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode items = objectMapper.readTree(response).get("items");
        return items.get(0).get("id").asLong();
    }

    private ResultActions changeStatus(String token, long userId, String status) throws Exception {
        return mockMvc.perform(patch("/api/admin/users/{id}/status", userId).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"%s\"}".formatted(status)));
    }
}
