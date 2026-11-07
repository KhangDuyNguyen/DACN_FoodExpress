package vn.hutech.fooddelivery.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.admin.dto.AdminRestaurantResponse;
import vn.hutech.fooddelivery.admin.dto.ApprovalStatusRequest;
import vn.hutech.fooddelivery.common.PageResponse;

/**
 * Controller quản trị và duyệt nhà hàng (Restaurants) dành cho Admin.
 * Người phụ trách: Sầm Xuân Điệp.
 */
@RestController
@RequestMapping("/api/admin/restaurants")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Restaurants", description = "Quản lý và duyệt nhà hàng dành cho Quản trị viên")
public class AdminRestaurantController {

    private final AdminRestaurantService adminRestaurantService;

    public AdminRestaurantController(AdminRestaurantService adminRestaurantService) {
        this.adminRestaurantService = adminRestaurantService;
    }

    @GetMapping
    @Operation(summary = "Xem danh sách nhà hàng kèm bộ lọc trạng thái và phân trang")
    public PageResponse<AdminRestaurantResponse> getRestaurants(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return adminRestaurantService.getRestaurants(keyword, status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết một nhà hàng theo ID")
    public AdminRestaurantResponse getRestaurantById(@PathVariable Long id) {
        return adminRestaurantService.getRestaurantById(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Admin cập nhật trạng thái duyệt nhà hàng (PENDING/APPROVED/REJECTED)")
    public AdminRestaurantResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ApprovalStatusRequest request
    ) {
        return adminRestaurantService.updateStatus(id, request.status());
    }
}

