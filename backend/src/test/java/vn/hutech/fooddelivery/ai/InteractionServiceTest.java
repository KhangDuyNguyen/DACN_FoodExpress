package vn.hutech.fooddelivery.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.hutech.fooddelivery.ai.dto.InteractionResponse;
import vn.hutech.fooddelivery.ai.dto.RecordInteractionRequest;
import vn.hutech.fooddelivery.common.BadRequestException;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.order.event.OrderPlacedEvent;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class InteractionServiceTest {

    private FakeInteractionRepository repository;
    private InteractionService service;

    private static class FakeInteractionRepository extends InteractionRepository {
        final Map<Long, Long> menuItemRestaurants = new HashMap<>();
        final Map<Long, OrderCheckRow> orders = new HashMap<>();
        final Map<Long, List<OrderItemQtyRow>> orderItems = new HashMap<>();
        final Map<Long, InteractionRow> store = new HashMap<>();
        final AtomicLong seq = new AtomicLong(1);

        FakeInteractionRepository() {
            super(null);
        }

        @Override
        public Optional<Long> findMenuItemRestaurantId(long menuItemId) {
            return Optional.ofNullable(menuItemRestaurants.get(menuItemId));
        }

        @Override
        public Optional<OrderCheckRow> findOrderForInteraction(long orderId) {
            return Optional.ofNullable(orders.get(orderId));
        }

        @Override
        public List<OrderItemQtyRow> findOrderItemQuantities(long orderId) {
            return orderItems.getOrDefault(orderId, List.of());
        }

        @Override
        public Optional<InteractionRow> findById(long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<InteractionRow> findByEventId(String eventId) {
            return store.values().stream()
                    .filter(r -> eventId.equals(r.eventId()))
                    .findFirst();
        }

        @Override
        public Optional<InteractionRow> findByOrderAndItemAndType(long orderId, long menuItemId, String type) {
            return store.values().stream()
                    .filter(r -> Long.valueOf(orderId).equals(r.orderId())
                            && r.menuItemId() == menuItemId
                            && type.equals(r.type()))
                    .findFirst();
        }

        @Override
        public long insert(long userId, long menuItemId, String type, int quantity, Long orderId, String eventId) {
            long id = seq.getAndIncrement();
            long restId = menuItemRestaurants.getOrDefault(menuItemId, 1L);
            store.put(id, new InteractionRow(
                    id, userId, menuItemId, restId, type, quantity, orderId, eventId, LocalDateTime.now()
            ));
            return id;
        }
    }

    @BeforeEach
    void setUp() {
        repository = new FakeInteractionRepository();
        repository.menuItemRestaurants.put(10L, 1L);
        repository.menuItemRestaurants.put(20L, 2L);
        repository.orders.put(100L, new InteractionRepository.OrderCheckRow(100L, 5L, 1L, "PLACED"));
        repository.orders.put(101L, new InteractionRepository.OrderCheckRow(101L, 5L, 1L, "CANCELLED"));
        repository.orders.put(102L, new InteractionRepository.OrderCheckRow(102L, 5L, 1L, "PENDING_PAYMENT"));
        repository.orders.put(103L, new InteractionRepository.OrderCheckRow(103L, 9L, 1L, "DELIVERED"));
        repository.orderItems.put(100L, List.of(
                new InteractionRepository.OrderItemQtyRow(10L, 2),
                new InteractionRepository.OrderItemQtyRow(20L, 1)
        ));
        service = new InteractionService(repository);
    }

    @Test
    @DisplayName("Ghi nhận VIEW, ADD_TO_CART, ORDER thành công và dùng userId từ JWT")
    void recordInteractions_success() {
        InteractionResponse viewRes = service.recordInteraction(
                5L, new RecordInteractionRequest(10L, "view", null, null, "evt-1"));
        assertEquals(5L, viewRes.userId());
        assertEquals("VIEW", viewRes.type());
        assertEquals(1, viewRes.quantity());
        assertFalse(viewRes.deduplicated());

        InteractionResponse cartRes = service.recordInteraction(
                5L, new RecordInteractionRequest(10L, "ADD_TO_CART", 3, null, null));
        assertEquals("ADD_TO_CART", cartRes.type());
        assertEquals(3, cartRes.quantity());

        InteractionResponse orderRes = service.recordInteraction(
                5L, new RecordInteractionRequest(10L, "ORDER", 2, 100L, null));
        assertEquals("ORDER", orderRes.type());
        assertEquals(100L, orderRes.orderId());
    }

    @Test
    @DisplayName("Chống ghi nhận trùng khi retry bằng eventId hoặc orderId")
    void recordInteraction_deduplicatesRetries() {
        InteractionResponse first = service.recordInteraction(
                5L, new RecordInteractionRequest(10L, "VIEW", 1, null, "evt-retry-1"));
        InteractionResponse retry = service.recordInteraction(
                5L, new RecordInteractionRequest(10L, "VIEW", 1, null, "evt-retry-1"));

        assertFalse(first.deduplicated());
        assertTrue(retry.deduplicated());
        assertEquals(first.id(), retry.id());
        assertEquals(1, repository.store.size());
    }

    @Test
    @DisplayName("Không cho phép ghi nhận ORDER cho đơn CANCELLED hoặc PENDING_PAYMENT")
    void recordInteraction_rejectsCancelledOrPendingOrder() {
        assertThrows(BadRequestException.class, () -> service.recordInteraction(
                5L, new RecordInteractionRequest(10L, "ORDER", 1, 101L, null)));
        assertThrows(BadRequestException.class, () -> service.recordInteraction(
                5L, new RecordInteractionRequest(10L, "ORDER", 1, 102L, null)));
    }

    @Test
    @DisplayName("Không cho phép ghi nhận interaction gắn với orderId của người dùng khác")
    void recordInteraction_rejectsOtherUsersOrder() {
        assertThrows(ForbiddenException.class, () -> service.recordInteraction(
                5L, new RecordInteractionRequest(10L, "ORDER", 1, 103L, null)));
    }

    @Test
    @DisplayName("Món ăn không tồn tại -> 404 NotFoundException, loại hành vi sai -> 400 BadRequestException")
    void recordInteraction_validatesItemAndType() {
        assertThrows(NotFoundException.class, () -> service.recordInteraction(
                5L, new RecordInteractionRequest(9999L, "VIEW", 1, null, null)));
        assertThrows(BadRequestException.class, () -> service.recordInteraction(
                5L, new RecordInteractionRequest(10L, "INVALID_TYPE", 1, null, null)));
    }

    @Test
    @DisplayName("OrderInteractionListener tự động ghi ORDER từ OrderPlacedEvent và không bao giờ ném lỗi")
    void orderListener_recordsItemsAndSwallowsErrors() {
        OrderInteractionListener listener = new OrderInteractionListener(service);
        listener.handleOrderPlaced(new OrderPlacedEvent(100L, 1L, null));
        assertEquals(2, repository.store.size());

        // Gọi lại lần 2 cho cùng orderId -> không tạo trùng
        listener.handleOrderPlaced(new OrderPlacedEvent(100L, 1L, null));
        assertEquals(2, repository.store.size());

        // Giả lập service bị lỗi -> listener nuốt lỗi, không phá luồng đặt hàng
        InteractionService failingService = new InteractionService(repository) {
            @Override
            public void recordOrderPlacedSafely(long orderId) {
                throw new RuntimeException("Simulated DB down");
            }
        };
        assertDoesNotThrow(() -> new OrderInteractionListener(failingService)
                .handleOrderPlaced(new OrderPlacedEvent(100L, 1L, null)));
    }
}

