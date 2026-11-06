package vn.hutech.fooddelivery.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.admin.dto.AdminDriverResponse;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.common.PageResponse;

import java.util.List;
import java.util.Set;

/**
 * Service xử lý nghiệp vụ quản trị và duyệt tài xế (Drivers).
 * Người phụ trách: Sầm Xuân Điệp.
 */
@Service
public class AdminDriverService {

    private static final Set<String> VALID_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");

    private final AdminDriverRepository adminDriverRepository;

    public AdminDriverService(AdminDriverRepository adminDriverRepository) {
        this.adminDriverRepository = adminDriverRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDriverResponse> getDrivers(String keyword, String status, int page, int size) {
        int validPage = Math.max(page, 0);
        int validSize = (size <= 0 || size > 100) ? 10 : size;
        int offset = validPage * validSize;

        List<AdminDriverResponse> items = adminDriverRepository.findDrivers(keyword, status, offset, validSize);
        long totalItems = adminDriverRepository.countDrivers(keyword, status);

        return PageResponse.of(items, validPage, validSize, totalItems);
    }

    @Transactional(readOnly = true)
    public AdminDriverResponse getDriverById(long id) {
        return adminDriverRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài xế với ID: " + id));
    }

    @Transactional
    public AdminDriverResponse updateStatus(long id, String newStatus) {
        String normalizedStatus = newStatus != null ? newStatus.trim().toUpperCase() : "";
        if (!VALID_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ: " + newStatus + ". Chỉ chấp nhận PENDING, APPROVED, REJECTED.");
        }

        adminDriverRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài xế với ID: " + id));

        int rows = adminDriverRepository.updateStatus(id, normalizedStatus);
        if (rows == 0) {
            throw new NotFoundException("Không tìm thấy tài xế với ID: " + id);
        }

        return getDriverById(id);
    }
}

