package vn.hutech.fooddelivery.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.admin.dto.AdminMerchantResponse;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.common.PageResponse;

import java.util.List;
import java.util.Set;

/**
 * Service xử lý nghiệp vụ quản trị và duyệt chủ quán (Merchants).
 * Người phụ trách: Sầm Xuân Điệp.
 */
@Service
public class AdminMerchantService {

    private static final Set<String> VALID_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");

    private final AdminMerchantRepository adminMerchantRepository;

    public AdminMerchantService(AdminMerchantRepository adminMerchantRepository) {
        this.adminMerchantRepository = adminMerchantRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminMerchantResponse> getMerchants(String keyword, String status, int page, int size) {
        int validPage = Math.max(page, 0);
        int validSize = (size <= 0 || size > 100) ? 10 : size;
        int offset = validPage * validSize;

        List<AdminMerchantResponse> items = adminMerchantRepository.findMerchants(keyword, status, offset, validSize);
        long totalItems = adminMerchantRepository.countMerchants(keyword, status);

        return PageResponse.of(items, validPage, validSize, totalItems);
    }

    @Transactional(readOnly = true)
    public AdminMerchantResponse getMerchantById(long id) {
        return adminMerchantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chủ quán với ID: " + id));
    }

    @Transactional
    public AdminMerchantResponse updateStatus(long id, String newStatus) {
        String normalizedStatus = newStatus != null ? newStatus.trim().toUpperCase() : "";
        if (!VALID_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ: " + newStatus + ". Chỉ chấp nhận PENDING, APPROVED, REJECTED.");
        }

        // Kiểm tra tồn tại
        adminMerchantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chủ quán với ID: " + id));

        int rows = adminMerchantRepository.updateStatus(id, normalizedStatus);
        if (rows == 0) {
            throw new NotFoundException("Không tìm thấy chủ quán với ID: " + id);
        }

        return getMerchantById(id);
    }
}

