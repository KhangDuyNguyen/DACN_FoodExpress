package vn.hutech.fooddelivery.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.admin.dto.AdminRestaurantResponse;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.common.PageResponse;

import java.util.List;
import java.util.Set;

/**
 * Service xử lý nghiệp vụ quản trị và duyệt nhà hàng (Restaurants).
 * Người phụ trách: Sầm Xuân Điệp.
 */
@Service
public class AdminRestaurantService {

    private static final Set<String> VALID_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");

    private final AdminRestaurantRepository adminRestaurantRepository;

    public AdminRestaurantService(AdminRestaurantRepository adminRestaurantRepository) {
        this.adminRestaurantRepository = adminRestaurantRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminRestaurantResponse> getRestaurants(String keyword, String status, int page, int size) {
        int validPage = Math.max(page, 0);
        int validSize = (size <= 0 || size > 100) ? 10 : size;
        int offset = validPage * validSize;

        List<AdminRestaurantResponse> items = adminRestaurantRepository.findRestaurants(keyword, status, offset, validSize);
        long totalItems = adminRestaurantRepository.countRestaurants(keyword, status);

        return PageResponse.of(items, validPage, validSize, totalItems);
    }

    @Transactional(readOnly = true)
    public AdminRestaurantResponse getRestaurantById(long id) {
        return adminRestaurantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhà hàng với ID: " + id));
    }

    @Transactional
    public AdminRestaurantResponse updateStatus(long id, String newStatus) {
        String normalizedStatus = newStatus != null ? newStatus.trim().toUpperCase() : "";
        if (!VALID_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ: " + newStatus + ". Chỉ chấp nhận PENDING, APPROVED, REJECTED.");
        }

        AdminRestaurantResponse current = adminRestaurantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhà hàng với ID: " + id));

        // Nghiệp vụ quan trọng: Không được duyệt nhà hàng khi chủ quán chưa được duyệt APPROVED
        if ("APPROVED".equals(normalizedStatus)) {
            if (!"APPROVED".equalsIgnoreCase(current.merchantStatus())) {
                throw new ConflictException("Không thể duyệt nhà hàng khi chủ quán chưa được phê duyệt (trạng thái chủ quán hiện tại: " + current.merchantStatus() + ")");
            }
        }

        int rows = adminRestaurantRepository.updateStatus(id, normalizedStatus);
        if (rows == 0) {
            throw new NotFoundException("Không tìm thấy nhà hàng với ID: " + id);
        }

        return getRestaurantById(id);
    }
}

