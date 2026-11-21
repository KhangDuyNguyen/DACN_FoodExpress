package vn.hutech.fooddelivery.tracking;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.common.NotFoundException;

@Service
public class OrderTrackingService {
    private final OrderTrackingRepository trackingRepository;

    public OrderTrackingService(OrderTrackingRepository trackingRepository) {
        this.trackingRepository = trackingRepository;
    }

    @Transactional(readOnly = true)
    public OrderTrackingResponse getTracking(long customerId, long orderId) {
        var order = trackingRepository.findTracking(orderId, customerId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng"));
        if (order.customerId() != customerId) {
            throw new ForbiddenException("Bạn không có quyền xem đơn hàng này");
        }
        return order.response();
    }
}
