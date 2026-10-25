package vn.hutech.fooddelivery.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vn.hutech.fooddelivery.user.UserRepository;

/**
 * Chay 1 lan khi backend khoi dong: tao tai khoan admin dau tien tu ADMIN_PHONE / ADMIN_PASSWORD (.env).
 * Bo qua neu thieu bien hoac da co user ADMIN. Khong hardcode va khong log mat khau.
 */
@Component
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminPhone;
    private final String adminPassword;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                            @Value("${app.admin.phone:}") String adminPhone,
                            @Value("${app.admin.password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminPhone = adminPhone;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminPhone.isBlank() || adminPassword.isBlank()) {
            return;
        }
        if (userRepository.existsByRole("ADMIN")) {
            return;
        }
        if (userRepository.findByPhone(adminPhone.trim()).isPresent()) {
            log.warn("Khong tao admin: so dien thoai ADMIN_PHONE da duoc user khac dung");
            return;
        }
        userRepository.insert("Quản trị viên", adminPhone.trim(), null,
                passwordEncoder.encode(adminPassword), "ADMIN");
        log.info("Da tao tai khoan admin dau tien voi so dien thoai {}", adminPhone.trim());
    }
}
