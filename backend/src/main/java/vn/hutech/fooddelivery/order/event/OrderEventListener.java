package vn.hutech.fooddelivery.order.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.hutech.fooddelivery.order.dto.MerchantOrderSummaryResponse;
import vn.hutech.fooddelivery.order.dto.OrderDetailResponse;
import vn.hutech.fooddelivery.order.dto.OrderItemResponse;

import java.util.List;

/**
 * Lắng nghe sự kiện tạo đơn hàng sau khi transaction đã commit thành công
 * và broadcast thông tin đơn hàng mới qua WebSocket STOMP tới chủ quán.
 * Chỉ gửi dữ liệu cần thiết để hiển thị đơn cho cửa hàng, không phát tán thông tin nhạy cảm.
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final SimpMessagingTemplate messagingTemplate;

    public OrderEventListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPlaced(OrderPlacedEvent event) {
        if (event == null || event.order() == null) {
            return;
        }
        OrderDetailResponse order = event.order();
        List<OrderItemResponse> items = order.items() != null ? order.items() : List.of();
        int itemCount = items.stream().mapToInt(OrderItemResponse::quantity).sum();
        String restaurantName = order.restaurant() != null ? order.restaurant().name() : "";

        MerchantOrderSummaryResponse payload = new MerchantOrderSummaryResponse(
                order.id(),
                order.code(),
                event.restaurantId(),
                restaurantName,
                order.status(),
                order.totalAmount(),
                itemCount,
                order.receiverName(),
                order.receiverPhone(),
                order.deliveryAddress(),
                order.note(),
                order.paymentMethod(),
                items,
                order.createdAt()
        );

        String destination = "/topic/restaurants/" + event.restaurantId() + "/orders";
        log.info("Broadcasting OrderPlacedEvent for order #{} to topic {}", event.orderId(), destination);
        messagingTemplate.convertAndSend(destination, payload);
    }
}
