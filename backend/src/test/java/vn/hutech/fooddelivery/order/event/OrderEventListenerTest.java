package vn.hutech.fooddelivery.order.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import vn.hutech.fooddelivery.order.dto.OrderDetailResponse;
import vn.hutech.fooddelivery.order.dto.OrderRestaurantResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderEventListenerTest {

    @Test
    @DisplayName("Broadcast đúng topic và payload khi có OrderPlacedEvent")
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

        OrderDetailResponse sampleOrder = new OrderDetailResponse(
                100L,
                "ORD-20261111-0001",
                "PLACED",
                "Khách Hàng Test",
                new OrderRestaurantResponse(15L, "Quán Cơm Tấm", "0912345678", "123 Đường Số 1"),
                "Người Nhận Test",
                "0912345678",
                "123 Đường Số 1",
                "Giao trước 12h",
                "COD",
                List.of(),
                BigDecimal.valueOf(50000),
                BigDecimal.valueOf(15000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(65000),
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
        assertEquals(sampleOrder, sentPayload.get());
    }
}
