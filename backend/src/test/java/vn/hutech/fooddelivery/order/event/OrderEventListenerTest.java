package vn.hutech.fooddelivery.order.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import vn.hutech.fooddelivery.order.dto.MerchantOrderSummaryResponse;
import vn.hutech.fooddelivery.order.dto.OrderDetailResponse;
import vn.hutech.fooddelivery.order.dto.OrderItemResponse;
import vn.hutech.fooddelivery.order.dto.OrderRestaurantResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrderEventListenerTest {

    @Test
    @DisplayName("Broadcast đúng topic và payload MerchantOrderSummaryResponse khi có OrderPlacedEvent")
    void handleOrderPlaced_broadcastsToCorrectTopic() {
        AtomicReference<String> sentDestination = new AtomicReference<>();
        AtomicReference<Object> sentPayload = new AtomicReference<>();

        MessageChannel dummyChannel = (message, timeout) -> true;
        SimpMessagingTemplate fakeTemplate = new SimpMessagingTemplate(dummyChannel) {
            @Override
            public void convertAndSend(String destination, Object payload) {
                sentDestination.set(destination);
                sentPayload.set(payload);
            }
        };

        OrderEventListener listener = new OrderEventListener(fakeTemplate);

        List<OrderItemResponse> items = List.of(
                new OrderItemResponse(1L, "Cơm sườn nướng", BigDecimal.valueOf(50000), 2, "Không mỡ hành", BigDecimal.valueOf(100000))
        );

        OrderDetailResponse sampleOrder = new OrderDetailResponse(
                100L,
                "FD2611110001",
                "PLACED",
                "Khách Hàng Test",
                new OrderRestaurantResponse(15L, "Quán Cơm Tấm", "0912345678", "123 Đường Số 1"),
                "Người Nhận Test",
                "0912345678",
                "123 Đường Số 1",
                "Giao trước 12h",
                "COD",
                items,
                BigDecimal.valueOf(100000),
                BigDecimal.valueOf(15000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(115000),
                BigDecimal.valueOf(2.5),
                null,
                LocalDateTime.now(),
                null,
                null,
                null,
                List.of()
        );

        OrderPlacedEvent event = new OrderPlacedEvent(100L, 15L, sampleOrder);
        listener.handleOrderPlaced(event);

        assertEquals("/topic/restaurants/15/orders", sentDestination.get());
        MerchantOrderSummaryResponse payload = assertInstanceOf(MerchantOrderSummaryResponse.class, sentPayload.get());
        assertEquals(100L, payload.id());
        assertEquals("FD2611110001", payload.code());
        assertEquals(15L, payload.restaurantId());
        assertEquals("Quán Cơm Tấm", payload.restaurantName());
        assertEquals(2, payload.itemCount());
        assertEquals("Giao trước 12h", payload.note());
        assertEquals(1, payload.items().size());
    }

    @Test
    @DisplayName("Không broadcast nếu event hoặc order null")
    void handleOrderPlaced_ignoresNullOrder() {
        AtomicReference<String> sentDestination = new AtomicReference<>();

        MessageChannel dummyChannel = (message, timeout) -> true;
        SimpMessagingTemplate fakeTemplate = new SimpMessagingTemplate(dummyChannel) {
            @Override
            public void convertAndSend(String destination, Object payload) {
                sentDestination.set(destination);
            }
        };

        OrderEventListener listener = new OrderEventListener(fakeTemplate);
        listener.handleOrderPlaced(new OrderPlacedEvent(100L, 15L, null));

        assertNull(sentDestination.get());
    }
}
