package vn.hutech.fooddelivery.driver;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.driver.dto.DriverProfileResponse;
import vn.hutech.fooddelivery.driver.dto.UpdateDriverProfileRequest;
import vn.hutech.fooddelivery.driver.dto.DriverLocationRequest;
import vn.hutech.fooddelivery.driver.dto.DriverLocationResponse;
import vn.hutech.fooddelivery.driver.dto.DriverStatusRequest;
import vn.hutech.fooddelivery.driver.dto.DriverStatusResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class DriverService {

    private final DriverRepository driverRepository;
    private final DriverLocationRepository driverLocationRepository;
    private final Clock clock;

    @Autowired
    public DriverService(DriverRepository driverRepository, DriverLocationRepository driverLocationRepository) {
        this(driverRepository, driverLocationRepository, Clock.systemDefaultZone());
    }

    DriverService(DriverRepository driverRepository, DriverLocationRepository driverLocationRepository, Clock clock) {
        this.driverRepository = driverRepository;
        this.driverLocationRepository = driverLocationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DriverProfileResponse getProfile(long userId) {
        return driverRepository.findProfileByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ tài xế"));
    }

    @Transactional
    public DriverProfileResponse updateProfile(long userId, UpdateDriverProfileRequest request) {
        DriverProfileResponse profile = getProfile(userId);
        if (!"PENDING".equals(profile.status())) {
            throw new ForbiddenException("Chỉ tài xế PENDING được cập nhật thông tin phương tiện");
        }
        int updated = driverRepository.updatePendingProfile(userId, request.vehicleType(), request.licensePlate());
        if (updated == 0) {
            throw new ConflictException("Hồ sơ đã thay đổi trong lúc cập nhật. Vui lòng tải lại hồ sơ");
        }
        return getProfile(userId);
    }

    @Transactional
    public DriverLocationResponse recordLocation(long userId, DriverLocationRequest request) {
        // Khoa cung mot driver de hai request vi tri khong ghi history va snapshot xen ke nhau.
        long driverId = driverRepository.findIdByUserIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ tài xế"));
        var lat = request.lat().setScale(7, RoundingMode.HALF_UP);
        var lng = request.lng().setScale(7, RoundingMode.HALF_UP);
        // drivers.last_location_at la DATETIME(0); dung cung thoi diem cho ca hai bang.
        var recordedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        driverLocationRepository.insert(driverId, lat, lng, recordedAt);
        if (driverRepository.updateLocation(driverId, lat, lng, recordedAt) != 1) {
            throw new NotFoundException("Không tìm thấy hồ sơ tài xế");
        }
        return new DriverLocationResponse(lat, lng, recordedAt);
    }

    @Transactional
    public DriverStatusResponse updateStatus(long userId, DriverStatusRequest request) {
        // Giu khoa den khi commit: Admin/location request khong chen thay doi giua kiem tra va UPDATE.
        var driver = driverRepository.findStatusByUserIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ tài xế"));
        boolean online = request.isOnline();
        if (online) {
            if (!"APPROVED".equals(driver.approvalStatus())) {
                throw new ForbiddenException("Chỉ tài xế APPROVED được bật nhận đơn");
            }
            if (!validCoordinate(driver.lat(), 90) || !validCoordinate(driver.lng(), 180)
                    || driver.lastLocationAt() == null) {
                throw new ConflictException("Chưa có vị trí hợp lệ. Vui lòng gửi vị trí trước khi bật nhận đơn");
            }
            // Cung gio Backend va do chinh xac giay nhu POST Location/DATETIME(0).
            var now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
            if (driver.lastLocationAt().isAfter(now)) {
                throw new ConflictException("Thời gian vị trí nằm trong tương lai. Vui lòng gửi lại vị trí");
            }
            if (driver.lastLocationAt().isBefore(now.minusSeconds(60))) {
                throw new ConflictException("Vị trí đã cũ quá 60 giây. Vui lòng gửi vị trí mới");
            }
        }
        if (driver.isOnline() == online) {
            return new DriverStatusResponse(driver.isOnline());
        }
        if (driverRepository.updateOnline(driver.id(), online) != 1) {
            throw new ConflictException("Không thể cập nhật trạng thái. Vui lòng tải lại hồ sơ");
        }
        return new DriverStatusResponse(online);
    }

    private static boolean validCoordinate(BigDecimal value, int limit) {
        return value != null && value.abs().compareTo(BigDecimal.valueOf(limit)) <= 0;
    }
}
