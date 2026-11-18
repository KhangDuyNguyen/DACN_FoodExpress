package vn.hutech.fooddelivery.support;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.common.PageResponse;
import vn.hutech.fooddelivery.support.dto.CreateTicketRequest;
import vn.hutech.fooddelivery.support.dto.SupportTicketResponse;

/**
 * Service xử lý nghiệp vụ cho Support Tickets (hỗ trợ khách hàng và quản trị ticket).
 */
@Service
public class SupportTicketService {

    private final SupportTicketRepository repository;

    public SupportTicketService(SupportTicketRepository repository) {
        this.repository = repository;
    }

    /**
     * Khách hàng tạo ticket hỗ trợ mới.
     */
    @Transactional
    public SupportTicketResponse createTicket(long userId, CreateTicketRequest request) {
        return repository.createTicket(userId, request);
    }

    /**
     * Khách hàng lấy danh sách ticket của mình (hỗ trợ lọc status, phân trang).
     */
    @Transactional(readOnly = true)
    public PageResponse<SupportTicketResponse> getCustomerTickets(long userId, String status, int page, int size) {
        return repository.findCustomerTickets(userId, status, page, size);
    }

    /**
     * Khách hàng xem chi tiết ticket của mình theo ID.
     */
    @Transactional(readOnly = true)
    public SupportTicketResponse getCustomerTicketById(long ticketId, long userId) {
        SupportTicketResponse ticket = repository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket hỗ trợ không tồn tại với ID: " + ticketId));

        if (ticket.userId() != userId) {
            throw new ForbiddenException("Bạn không có quyền truy cập ticket hỗ trợ của người dùng khác");
        }

        return ticket;
    }

    /**
     * Admin xem danh sách tất cả các ticket hỗ trợ (lọc status, tìm kiếm keyword, phân trang).
     */
    @Transactional(readOnly = true)
    public PageResponse<SupportTicketResponse> getAdminTickets(String status, String keyword, int page, int size) {
        return repository.findAdminTickets(status, keyword, page, size);
    }

    /**
     * Admin xem chi tiết ticket hỗ trợ theo ID.
     */
    @Transactional(readOnly = true)
    public SupportTicketResponse getAdminTicketById(long ticketId) {
        return repository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket hỗ trợ không tồn tại với ID: " + ticketId));
    }

    /**
     * Admin cập nhật trạng thái ticket (OPEN, IN_PROGRESS, RESOLVED, CLOSED).
     */
    @Transactional
    public SupportTicketResponse updateTicketStatus(long ticketId, String status) {
        return repository.updateStatus(ticketId, status);
    }
}
