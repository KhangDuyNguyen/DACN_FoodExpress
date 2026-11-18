package vn.hutech.fooddelivery.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.order.OrderRepository;
import vn.hutech.fooddelivery.order.OrderRow;
import vn.hutech.fooddelivery.order.dto.OrderDetailResponse;
import vn.hutech.fooddelivery.order.event.OrderPlacedEvent;
import vn.hutech.fooddelivery.order.event.OrderStatusChangedEvent;

/**
 * Ghi ket qua thanh toan vao database trong MOT transaction: bang payments va trang thai don + lich su.
 * Dung chung cho IPN va cho lan hoi lai VNPay (querydr) khi khach quay ve.
 *
 * Tach thanh class rieng vi VnPayService goi ham nay sau khi goi HTTP sang VNPay: goi 1 ham @Transactional
 * ngay trong cung class thi Spring KHONG mo transaction (khong di qua proxy), con goi sang bean khac thi co.
 */
@Service
public class PaymentResultService {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultService.class);

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher events;

    public PaymentResultService(PaymentRepository paymentRepository, OrderRepository orderRepository,
                                ApplicationEventPublisher events) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.events = events;
    }

    /**
     * @param success true = VNPay xac nhan da tru tien
     * @param code    ma ket qua VNPay (vnp_ResponseCode), ghi vao ly do huy khi that bai
     * @param payload du lieu VNPay tra ve (JSON), luu de doi soat
     * @return false neu giao dich da duoc xu ly truoc do (khong lam gi them)
     */
    @Transactional
    public boolean apply(PaymentRow payment, boolean success, String code, String payload) {
        // 1. Chi xu ly 1 lan: UPDATE ... WHERE status = 'PENDING' khop 0 dong nghia la da xu ly roi
        if (paymentRepository.updateResult(payment.id(), success ? "SUCCESS" : "FAILED", payload) == 0) {
            return false;
        }
        OrderRow order = orderRepository.findById(payment.orderId())
                .orElseThrow(() -> new IllegalStateException("Khong tim thay don " + payment.orderId()));
        if (success) {
            placePaidOrder(order);
        } else {
            cancelUnpaidOrder(order, "Thanh toán VNPay không thành công (mã " + code + ")");
        }
        return true;
    }

    /** Da tra tien: don sang "Cho xac nhan", roi bao quan va moi tai xe giong het don COD vua dat. */
    private void placePaidOrder(OrderRow order) {
        if (orderRepository.updateStatus(order.id(), "PENDING_PAYMENT", "PLACED", null) == 0) {
            // Tien da ve nhung don khong con cho thanh toan nua: phai hoan tien cho khach.
            // TODO(khang): tao yeu cau hoan tien (bang refunds) cung phan hoan tien VNPay o tuan 6-7
            log.warn("Don {} da duoc VNPay xac nhan thanh toan nhung dang o trang thai {}, can hoan tien",
                    order.id(), order.status());
            return;
        }
        orderRepository.insertStatusHistory(order.id(), "PLACED", order.customerId(), "Đã thanh toán VNPay");
        events.publishEvent(new OrderStatusChangedEvent(order.id(), order.restaurantId(), order.customerId(),
                "PENDING_PAYMENT", "PLACED", order.customerId()));
        // Ben nghe dung AFTER_COMMIT: chi chay khi transaction nay commit (giong luc dat don COD)
        OrderRow placed = orderRepository.findById(order.id()).orElseThrow();
        OrderDetailResponse detail = OrderDetailResponse.from(placed, orderRepository.findItems(order.id()),
                orderRepository.findHistory(order.id()));
        events.publishEvent(new OrderPlacedEvent(order.id(), order.restaurantId(), detail));
    }

    /**
     * Thanh toan that bai: huy don dang cho thanh toan (quan chua tung thay don nay).
     * TODO(khang): tra lai luot voucher cua don khi Diep co ham tra luot (ke hoach tuan 5, cau 3)
     */
    private void cancelUnpaidOrder(OrderRow order, String reason) {
        if (orderRepository.updateStatus(order.id(), "PENDING_PAYMENT", "CANCELLED", reason) == 0) {
            return;
        }
        orderRepository.insertStatusHistory(order.id(), "CANCELLED", order.customerId(), reason);
        events.publishEvent(new OrderStatusChangedEvent(order.id(), order.restaurantId(), order.customerId(),
                "PENDING_PAYMENT", "CANCELLED", order.customerId()));
    }
}
