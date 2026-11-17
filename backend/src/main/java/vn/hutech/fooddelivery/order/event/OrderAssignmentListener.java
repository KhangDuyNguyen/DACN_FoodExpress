package vn.hutech.fooddelivery.order.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.hutech.fooddelivery.assignment.AssignmentService;

/**
 * Dat hang xong thi moi tai xe gan nhat NGAY (goi AssignmentService cua An), khong phai cho bo quet 30 giay.
 *
 * AFTER_COMMIT: chi chay khi transaction dat hang da commit; dat hang loi (rollback) thi khong moi ai.
 * Day cung la dieu kien An ghi o AssignmentService ("chi goi sau khi transaction tao Order da commit").
 * Loi o phan gan tai xe khong duoc lam hong viec dat hang (don da luu roi), nen chi ghi log:
 * bo quet dinh ky cua An se tu moi lai sau.
 */
@Component
public class OrderAssignmentListener {

    private static final Logger log = LoggerFactory.getLogger(OrderAssignmentListener.class);

    private final AssignmentService assignmentService;

    public OrderAssignmentListener(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        try {
            assignmentService.createOffer(event.orderId());
        } catch (RuntimeException ex) {
            log.warn("Chua moi duoc tai xe cho don {}, bo quet cua module assignment se thu lai", event.orderId(), ex);
        }
    }
}
