package vn.hutech.fooddelivery.support;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.common.PageResponse;
import vn.hutech.fooddelivery.support.dto.SupportTicketResponse;
import vn.hutech.fooddelivery.support.dto.UpdateTicketStatusRequest;

/**
 * Controller quản lý ticket hỗ trợ dành cho Admin.
 */
@RestController
@RequestMapping("/api/admin/tickets")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Support Tickets", description = "Quản lý và giải quyết ticket hỗ trợ dành cho Quản trị viên")
public class AdminTicketController {

    private final SupportTicketService supportTicketService;

    public AdminTicketController(SupportTicketService supportTicketService) {
        this.supportTicketService = supportTicketService;
    }

    @GetMapping
    @Operation(summary = "Admin xem danh sách tất cả các ticket hỗ trợ kèm bộ lọc và phân trang")
    public ResponseEntity<PageResponse<SupportTicketResponse>> getTickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<SupportTicketResponse> response = supportTicketService.getAdminTickets(
                status, keyword, page, size
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Admin xem chi tiết một ticket hỗ trợ theo ID")
    public ResponseEntity<SupportTicketResponse> getTicketById(@PathVariable Long id) {
        SupportTicketResponse ticket = supportTicketService.getAdminTicketById(id);
        return ResponseEntity.ok(ticket);
    }

    @PatchMapping(value = {"/{id}", "/{id}/status"})
    @Operation(summary = "Admin cập nhật trạng thái ticket hỗ trợ (OPEN, IN_PROGRESS, RESOLVED, CLOSED)")
    public ResponseEntity<SupportTicketResponse> updateTicketStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTicketStatusRequest request
    ) {
        SupportTicketResponse updated = supportTicketService.updateTicketStatus(id, request.status());
        return ResponseEntity.ok(updated);
    }
}
