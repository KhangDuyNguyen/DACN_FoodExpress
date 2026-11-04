package vn.hutech.fooddelivery.driver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.driver.dto.DriverProfileResponse;
import vn.hutech.fooddelivery.driver.dto.UpdateDriverProfileRequest;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Chi tao du lieu trong MySQL container rieng; khong ghi vao database cua nhom. */
@JdbcTest(properties = {
        "spring.config.import=",
        "app.jwt.secret=driver_repository_test_secret_at_least_32_chars"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DriverRepository.class, DriverLocationRepository.class, DriverService.class})
@Testcontainers(disabledWithoutDocker = true)
class DriverRepositoryTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    DriverRepository driverRepository;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @Autowired
    DriverService driverService;

    @Autowired
    DataSource dataSource;

    @Test
    void joinsByUserIdRatherThanDriverIdAndDoesNotReturnAnotherDriver() {
        insertUser(42, "0911111111");
        insertUser(43, "0922222222");
        jdbc.update("""
                INSERT INTO drivers (id, user_id, vehicle_type, license_plate, status, is_online)
                VALUES (7, 42, 'MOTORBIKE', '59A1-12345', 'APPROVED', 1),
                       (42, 43, 'CAR', '51A-99999', 'PENDING', 0)
                """, Map.of());

        assertEquals(new DriverProfileResponse("Nguyen Van An", "0911111111", null,
                "MOTORBIKE", "59A1-12345", "APPROVED", true),
                driverRepository.findProfileByUserId(42).orElseThrow());
    }

    @Test
    void readsPendingBicycleWithNullLicensePlateAndOfflineStatus() {
        insertUser(42, "0911111111");
        jdbc.update("INSERT INTO drivers (user_id, vehicle_type) VALUES (:userId, 'BICYCLE')",
                Map.of("userId", 42));

        assertEquals(new DriverProfileResponse("Nguyen Van An", "0911111111", null,
                "BICYCLE", null, "PENDING", false),
                driverRepository.findProfileByUserId(42).orElseThrow());
    }

    @Test
    void userWithoutDriverRowHasNoProfile() {
        insertUser(42, "0911111111");

        assertTrue(driverRepository.findProfileByUserId(42).isEmpty());
    }

    @Test
    void pendingUpdateAndSubsequentGetPreserveStatusOnlineAndOtherDriver() {
        insertUser(42, "0911111111");
        insertUser(43, "0922222222");
        jdbc.update("""
                INSERT INTO drivers (user_id, vehicle_type, license_plate, status, is_online)
                VALUES (42, 'MOTORBIKE', '59A1-12345', 'PENDING', 1),
                       (43, 'CAR', '51A-99999', 'PENDING', 0)
                """, Map.of());
        var otherBefore = driverRepository.findProfileByUserId(43).orElseThrow();

        var updated = driverService.updateProfile(42, new UpdateDriverProfileRequest("BICYCLE", null));

        assertEquals(new DriverProfileResponse("Nguyen Van An", "0911111111", null,
                "BICYCLE", null, "PENDING", true), updated);
        assertEquals(updated, driverService.getProfile(42));
        assertEquals(otherBefore, driverRepository.findProfileByUserId(43).orElseThrow());
    }

    @Test
    void repeatedIdenticalPendingUpdateIsSuccessful() {
        insertUser(42, "0911111111");
        jdbc.update("INSERT INTO drivers (user_id, vehicle_type) VALUES (42, 'BICYCLE')", Map.of());

        var request = new UpdateDriverProfileRequest("BICYCLE", null);
        assertEquals(driverService.updateProfile(42, request), driverService.updateProfile(42, request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "REJECTED", "SUSPENDED"})
    void updateSqlRejectsNonPendingDriverEvenWithoutServiceCheck(String status) {
        insertUser(42, "0911111111");
        jdbc.update("""
                INSERT INTO drivers (user_id, vehicle_type, license_plate, status)
                VALUES (42, 'MOTORBIKE', '59A1-12345', :status)
                """, Map.of("status", status));
        var before = driverRepository.findProfileByUserId(42).orElseThrow();

        assertEquals(0, driverRepository.updatePendingProfile(42, "CAR", "51A-12345"));
        assertEquals(before, driverRepository.findProfileByUserId(42).orElseThrow());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void updateWaitingForConcurrentApprovalCannotOverwriteApprovedVehicle() throws Exception {
        // Du lieu commit chi nam trong container test; dung ID rieng vi test nay co hai transaction.
        insertUser(93, "0933333333");
        jdbc.update("""
                INSERT INTO drivers (user_id, vehicle_type, license_plate)
                VALUES (93, 'MOTORBIKE', '59A1-12345')
                """, Map.of());
        assertEquals("PENDING", driverRepository.findProfileByUserId(93).orElseThrow().status());

        try (var adminConnection = dataSource.getConnection();
             var executor = Executors.newSingleThreadExecutor()) {
            adminConnection.setAutoCommit(false);
            try {
                try (var statement = adminConnection.prepareStatement(
                        "UPDATE drivers SET status = 'APPROVED' WHERE user_id = ?")) {
                    statement.setLong(1, 93);
                    assertEquals(1, statement.executeUpdate());
                }

                var started = new CountDownLatch(1);
                var update = executor.submit(() -> {
                    started.countDown();
                    return driverRepository.updatePendingProfile(93, "CAR", "51A-12345");
                });
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> update.get(200, TimeUnit.MILLISECONDS));
                adminConnection.commit();

                assertEquals(0, update.get(5, TimeUnit.SECONDS));
                var after = driverRepository.findProfileByUserId(93).orElseThrow();
                assertEquals("APPROVED", after.status());
                assertEquals("MOTORBIKE", after.vehicleType());
                assertEquals("59A1-12345", after.licensePlate());
            } finally {
                adminConnection.rollback();
            }
        }
    }

    private void insertUser(long id, String phone) {
        jdbc.update("""
                INSERT INTO users (id, full_name, phone, password_hash, role)
                VALUES (:id, 'Nguyen Van An', :phone, 'test-only-hash', 'DRIVER')
                """, Map.of("id", id, "phone", phone));
    }
}
