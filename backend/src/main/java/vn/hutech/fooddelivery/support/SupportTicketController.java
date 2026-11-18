package vn.hutech.fooddelivery.support;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.common.PageResponse;
import vn.hutech.fooddelivery.security.AuthUser;
import vn.hutech.fooddelivery.support.dto.CreateTicketRequest;
import vn.hutech.fooddelivery.support.dto.SupportTicketResponse;

/**
 * Controller xử lý ticket hỗ trợ dành cho người dùng đăng nhập.
 */
@RestController
@RequestMapping("/api/support/tickets")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Support Tickets", description = "API tạo và quản lý ticket hỗ trợ của người dùng")
public class SupportTicketController {

    private final SupportTicketService supportTicketService;

    public SupportTicketController(SupportTicketService supportTicketService) {
        this.supportTicketService = supportTicketService;
    }

    @PostMapping
    @Operation(summary = "Người dùng đăng nhập tạo ticket hỗ trợ mới")
    public ResponseEntity<SupportTicketResponse> createTicket(
            @AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody CreateTicketRequest request
    ) {
        SupportTicketResponse created = supportTicketService.createTicket(user.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "Người dùng đăng nhập xem danh sách ticket của mình")
    public ResponseEntity<PageResponse<SupportTicketResponse>> getMyTickets(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<SupportTicketResponse> response = supportTicketService.getCustomerTickets(
                user.id(), status, page, size
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Người dùng xem chi tiết ticket của mình")
    public ResponseEntity<SupportTicketResponse> getMyTicketById(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUser user
    ) {
        SupportTicketResponse ticket = supportTicketService.getCustomerTicketById(id, user.id());
        return ResponseEntity.ok(ticket);
    }
}
