package vn.hutech.fooddelivery.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.common.PageResponse;
import vn.hutech.fooddelivery.support.dto.CreateTicketRequest;
import vn.hutech.fooddelivery.support.dto.SupportTicketResponse;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SupportTicketServiceTest {

    private FakeSupportTicketRepository repository;
    private SupportTicketService service;

    private static class FakeSupportTicketRepository extends SupportTicketRepository {
        final Map<Long, SupportTicketResponse> store = new HashMap<>();

        FakeSupportTicketRepository() {
            super(null);
        }

        @Override
        public SupportTicketResponse createTicket(long userId, CreateTicketRequest req) {
            long id = store.size() + 1;
            SupportTicketResponse resp = new SupportTicketResponse(
                    id, userId, "Nguyễn Văn A", "vana@example.com",
                    req.orderId(), req.subject(), req.description(), "OPEN",
                    LocalDateTime.now(), null
            );
            store.put(id, resp);
            return resp;
        }

        @Override
        public Optional<SupportTicketResponse> findById(long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public boolean existsById(long id) {
            return store.containsKey(id);
        }

        @Override
        public PageResponse<SupportTicketResponse> findCustomerTickets(long userId, String status, int page, int size) {
            List<SupportTicketResponse> list = store.values().stream()
                    .filter(t -> t.userId().equals(userId))
                    .filter(t -> status == null || t.status().equalsIgnoreCase(status))
                    .toList();
            return PageResponse.of(list, page, size, list.size());
        }

        @Override
        public PageResponse<SupportTicketResponse> findAdminTickets(String status, String keyword, int page, int size) {
            List<SupportTicketResponse> list = store.values().stream()
                    .filter(t -> status == null || t.status().equalsIgnoreCase(status))
                    .filter(t -> keyword == null || t.subject().contains(keyword) || (t.description() != null && t.description().contains(keyword)))
                    .toList();
            return PageResponse.of(list, page, size, list.size());
        }

        @Override
        public SupportTicketResponse updateStatus(long ticketId, String status) {
            SupportTicketResponse old = store.get(ticketId);
            if (old == null) {
                throw new NotFoundException("Ticket không tồn tại với ID: " + ticketId);
            }
            SupportTicketResponse updated = new SupportTicketResponse(
                    old.id(), old.userId(), old.userName(), old.userEmail(),
                    old.orderId(), old.subject(), old.description(), status,
                    old.createdAt(), "RESOLVED".equalsIgnoreCase(status) || "CLOSED".equalsIgnoreCase(status) ? LocalDateTime.now() : null
            );
            store.put(ticketId, updated);
            return updated;
        }
    }

    @BeforeEach
    void setUp() {
        repository = new FakeSupportTicketRepository();
        service = new SupportTicketService(repository);
    }

    @Test
    @DisplayName("Khách hàng tạo ticket thành công")
    void createTicket_success() {
        CreateTicketRequest req = new CreateTicketRequest("Đơn giao trễ", "Giao trễ hơn 30 phút", 101L);
        SupportTicketResponse result = service.createTicket(10L, req);

        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals("OPEN", result.status());
        assertEquals(10L, result.userId());
        assertEquals("Đơn giao trễ", result.subject());
    }

    @Test
    @DisplayName("Khách hàng lấy danh sách ticket của mình thành công")
    void getCustomerTickets_success() {
        service.createTicket(10L, new CreateTicketRequest("Ticket 1", "Mô tả", 101L));
        service.createTicket(20L, new CreateTicketRequest("Ticket 2 khác user", "Mô tả", 102L));

        PageResponse<SupportTicketResponse> result = service.getCustomerTickets(10L, "OPEN", 0, 10);

        assertNotNull(result);
        assertEquals(1, result.items().size());
        assertEquals(1L, result.totalItems());
        assertEquals(10L, result.items().get(0).userId());
    }

    @Test
    @DisplayName("Khách hàng xem ticket của mình thành công")
    void getCustomerTicketById_success() {
        SupportTicketResponse created = service.createTicket(10L, new CreateTicketRequest("Ticket 1", "Mô tả", 101L));

        SupportTicketResponse result = service.getCustomerTicketById(created.id(), 10L);

        assertNotNull(result);
        assertEquals(created.id(), result.id());
    }

    @Test
    @DisplayName("Khách hàng xem ticket của người khác ném lỗi ForbiddenException")
    void getCustomerTicketById_forbidden() {
        SupportTicketResponse created = service.createTicket(10L, new CreateTicketRequest("Ticket 1", "Mô tả", 101L));

        assertThrows(ForbiddenException.class, () -> service.getCustomerTicketById(created.id(), 999L));
    }

    @Test
    @DisplayName("Khách hàng xem ticket không tồn tại ném lỗi NotFoundException")
    void getCustomerTicketById_notFound() {
        assertThrows(NotFoundException.class, () -> service.getCustomerTicketById(99L, 10L));
    }

    @Test
    @DisplayName("Admin lấy danh sách ticket với filter và search thành công")
    void getAdminTickets_success() {
        service.createTicket(10L, new CreateTicketRequest("Đơn giao trễ", "Giao trễ", 101L));
        service.createTicket(20L, new CreateTicketRequest("Sai món", "Không đúng món", 102L));

        PageResponse<SupportTicketResponse> result = service.getAdminTickets("OPEN", "trễ", 0, 10);

        assertNotNull(result);
        assertEquals(1, result.items().size());
        assertEquals("Đơn giao trễ", result.items().get(0).subject());
    }

    @Test
    @DisplayName("Admin xem chi tiết ticket theo ID thành công")
    void getAdminTicketById_success() {
        SupportTicketResponse created = service.createTicket(10L, new CreateTicketRequest("Ticket 1", "Mô tả", null));

        SupportTicketResponse result = service.getAdminTicketById(created.id());

        assertNotNull(result);
        assertEquals(created.id(), result.id());
    }

    @Test
    @DisplayName("Admin cập nhật trạng thái ticket sang RESOLVED thành công")
    void updateTicketStatus_success() {
        SupportTicketResponse created = service.createTicket(10L, new CreateTicketRequest("Ticket 1", "Mô tả", null));

        SupportTicketResponse result = service.updateTicketStatus(created.id(), "RESOLVED");

        assertNotNull(result);
        assertEquals("RESOLVED", result.status());
        assertNotNull(result.resolvedAt());
    }

    @Test
    @DisplayName("Admin cập nhật ticket không tồn tại ném lỗi NotFoundException")
    void updateTicketStatus_notFound() {
        assertThrows(NotFoundException.class, () -> service.updateTicketStatus(999L, "RESOLVED"));
    }
}
