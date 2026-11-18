package vn.hutech.fooddelivery.user;

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

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ho so khach (Module J): sua ho ten, email va doi mat khau cua chinh minh.
 * Moi test tu dang ky tai khoan moi de khong lam doi mat khau cua tai khoan seed ma test khac dang dung.
 * Can Docker Desktop dang chay.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=user_profile_test_secret_dai_hon_32_ky_tu_abc",
        "app.admin.phone=",
        "app.admin.password="
})
@AutoConfigureMockMvc
@Testcontainers
class UserProfileIntegrationTest {

    private static final String PASSWORD = "MatKhauCu123";
    /** Moi lan dang ky lay 1 so dien thoai moi: 0977000001, 0977000002... */
    private static final AtomicInteger NEXT_PHONE = new AtomicInteger(1);

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void customerUpdatesOwnNameAndEmail() throws Exception {
        String token = registerCustomer();

        // Ho ten duoc cat khoang trang; email cat khoang trang va doi ve chu thuong (giong luc dang ky)
        updateProfile(token, "{\"fullName\":\"  Nguyễn Văn Mới  \",\"email\":\"  MoiMoi@Example.COM \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn Mới"))
                .andExpect(jsonPath("$.email").value("moimoi@example.com"));
        mockMvc.perform(get("/api/users/me").header("Authorization", token))
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn Mới"))
                .andExpect(jsonPath("$.email").value("moimoi@example.com"));

        // Email khong bat buoc: de trong la bo email
        updateProfile(token, "{\"fullName\":\"Nguyễn Văn Mới\",\"email\":\"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(nullValue()));
    }

    @Test
    void emailOfAnotherAccountIsRejected() throws Exception {
        String first = registerCustomer();
        String second = registerCustomer();
        updateProfile(first, "{\"fullName\":\"Người Một\",\"email\":\"trung@example.com\"}")
                .andExpect(status().isOk());

        // Email da thuoc tai khoan khac -> 409 (so sanh khong phan biet hoa thuong)
        updateProfile(second, "{\"fullName\":\"Người Hai\",\"email\":\"TRUNG@example.com\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email đã được đăng ký"));

        // Giu nguyen email cua chinh minh thi van luu duoc
        updateProfile(first, "{\"fullName\":\"Người Một Đổi Tên\",\"email\":\"trung@example.com\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Người Một Đổi Tên"));
    }

    @Test
    void invalidProfileIsRejectedWithFieldErrors() throws Exception {
        String token = registerCustomer();
        updateProfile(token, "{\"fullName\":\"   \",\"email\":\"khong-phai-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void changingPasswordNeedsTheCurrentPassword() throws Exception {
        String phone = nextPhone();
        String token = register(phone);

        changePassword(token, "{\"currentPassword\":\"SaiMatKhau1\",\"newPassword\":\"MatKhauMoi456\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mật khẩu hiện tại không đúng"));
        changePassword(token, "{\"currentPassword\":\"%s\",\"newPassword\":\"ngan\"}".formatted(PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
        changePassword(token, "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(PASSWORD, PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mật khẩu mới phải khác mật khẩu hiện tại"));

        changePassword(token, "{\"currentPassword\":\"%s\",\"newPassword\":\"MatKhauMoi456\"}".formatted(PASSWORD))
                .andExpect(status().isNoContent());

        // Mat khau moi dang nhap duoc, mat khau cu thi khong
        login(phone, "MatKhauMoi456").andExpect(status().isOk());
        login(phone, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void profileApisNeedLogin() throws Exception {
        mockMvc.perform(put("/api/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ai Đó\",\"email\":\"\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/users/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"a\",\"newPassword\":\"MatKhauMoi456\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ----- Ham tien ich -----

    private static String nextPhone() {
        return "0977%06d".formatted(NEXT_PHONE.getAndIncrement());
    }

    private String registerCustomer() throws Exception {
        return register(nextPhone());
    }

    /** Dang ky khach moi, tra ve "Bearer <token>". */
    private String register(String phone) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Khách Hồ Sơ","phone":"%s","password":"%s","role":"CUSTOMER"}
                                """.formatted(phone, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("token").asText();
    }

    private ResultActions login(String phone, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(phone, password)));
    }

    private ResultActions updateProfile(String token, String body) throws Exception {
        return mockMvc.perform(put("/api/users/me").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions changePassword(String token, String body) throws Exception {
        return mockMvc.perform(put("/api/users/me/password").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
