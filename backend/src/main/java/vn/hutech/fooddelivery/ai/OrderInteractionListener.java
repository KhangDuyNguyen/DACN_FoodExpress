package vn.hutech.fooddelivery.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.hutech.fooddelivery.order.event.OrderPlacedEvent;

/**
 * Lắng nghe OrderPlacedEvent sau khi đơn hàng commit thành công (COD đặt ngay hoặc VNPay trả tiền xong)
 * để tự động ghi nhận hành vi ORDER vào user_item_interactions mà không sửa code đặt hàng/thanh toán.
 */
@Component
public class OrderInteractionListener {

    private static final Logger log = LoggerFactory.getLogger(OrderInteractionListener.class);

    private final InteractionService interactionService;

    public OrderInteractionListener(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPlaced(OrderPlacedEvent event) {
        if (event == null || event.orderId() <= 0) {
            return;
        }
        try {
            interactionService.recordOrderPlacedSafely(event.orderId());
        } catch (Exception ex) {
            log.warn("Lỗi ghi nhận hành vi ORDER cho đơn {}: {}", event.orderId(), ex.getMessage());
        }
    }
}

