package vn.hutech.fooddelivery.order;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.common.PageResponse;
import vn.hutech.fooddelivery.order.OrderStatusFlow.Actor;
import vn.hutech.fooddelivery.order.dto.ChangeOrderStatusRequest;
import vn.hutech.fooddelivery.order.dto.MerchantOrderSummaryResponse;
import vn.hutech.fooddelivery.order.dto.OrderDetailResponse;
import vn.hutech.fooddelivery.order.dto.OrderSummaryResponse;

/**
 * Quan ly don sau khi dat: khach xem/huy don cua minh, chu quan xem va doi trang thai don cua quan minh.
 * Quyen SO HUU kiem tra o day (SecurityConfig chi kiem tra VAI TRO):
 *   khach chi dung duoc don co customer_id = minh, chu quan chi dung duoc don cua quan minh so huu.
 */
@Service
public class OrderManagementService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public OrderManagementService(OrderRepository orderRepository, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    // ----- Khach hang -----

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listCustomerOrders(long customerId, String status, int page, int size) {
        String normalizedStatus = normalizeStatus(status);
        long offset = (long) page * size;
        return PageResponse.of(
                orderRepository.findSummariesByCustomer(customerId, normalizedStatus, size, offset),
                page, size,
                orderRepository.countByCustomer(customerId, normalizedStatus));
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getCustomerOrder(long customerId, long orderId) {
        checkCustomerOwns(findOrder(orderId), customerId);
        return orderService.getDetail(orderId);
    }

    @Transactional
    public OrderDetailResponse cancelByCustomer(long customerId, long orderId, String reason) {
        OrderRow order = findOrder(orderId);
        checkCustomerOwns(order, customerId);
        changeStatus(order, Actor.CUSTOMER, "CANCELLED", customerId, reason.trim());
        return orderService.getDetail(orderId);
    }

    // ----- Chu quan -----

    @Transactional(readOnly = true)
    public PageResponse<MerchantOrderSummaryResponse> listMerchantOrders(long ownerUserId, Long restaurantId, String status,
                                                                         int page, int size) {
        // Kiem tra nha hang ton tai va thuoc quyen so huu cua chu quan dang dang nhap
        if (restaurantId != null) {
            if (!orderRepository.restaurantExists(restaurantId)) {
                throw new NotFoundException("Không tìm thấy nhà hàng #" + restaurantId);
            }
            if (!orderRepository.isRestaurantOwnedBy(restaurantId, ownerUserId)) {
                throw new ForbiddenException("Bạn không phải chủ quán này");
            }
        }
        String normalizedStatus = normalizeStatus(status);
        long offset = (long) page * size;
        return PageResponse.of(
                orderRepository.findMerchantSummariesByOwner(ownerUserId, restaurantId, normalizedStatus, size, offset),
                page, size,
                orderRepository.countByOwner(ownerUserId, restaurantId, normalizedStatus));
    }

    private static String normalizeStatus(String status) {
        return (status == null || status.isBlank()) ? null : status.trim();
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getMerchantOrder(long ownerUserId, long orderId) {
        checkMerchantOwns(findOrder(orderId), ownerUserId);
        return orderService.getDetail(orderId);
    }

    @Transactional
    public OrderDetailResponse changeStatusByMerchant(long ownerUserId, long orderId, ChangeOrderStatusRequest req) {
        OrderRow order = findOrder(orderId);
        checkMerchantOwns(order, ownerUserId);
        // Chi luu ly do khi huy don
        String reason = "CANCELLED".equals(req.status()) ? req.reason().trim() : null;
        changeStatus(order, Actor.MERCHANT, req.status(), ownerUserId, reason);
        return orderService.getDetail(orderId);
    }

    // ----- Dung chung -----

    /**
     * Doi trang thai trong cung transaction voi ham goi:
     *   1. Luat chuyen (OrderStatusFlow) khong cho phep -> 409.
     *   2. UPDATE ... WHERE status = trang thai vua doc; 0 dong nghia la co nguoi vua doi truoc -> 409.
     *   3. Ghi them 1 dong lich su (ai doi, luc nao, ly do).
     */
    private void changeStatus(OrderRow order, Actor actor, String toStatus, long changedBy, String reason) {
        if (!OrderStatusFlow.canChange(actor, order.status(), toStatus)) {
            throw new ConflictException("Không thể chuyển đơn từ \"" + OrderStatusFlow.label(order.status())
                    + "\" sang \"" + OrderStatusFlow.label(toStatus) + "\"");
        }
        int updated = orderRepository.updateStatus(order.id(), order.status(), toStatus, reason);
        if (updated == 0) {
            throw new ConflictException("Đơn vừa được cập nhật, vui lòng tải lại trang");
        }
        orderRepository.insertStatusHistory(order.id(), toStatus, changedBy, reason);
    }

    private OrderRow findOrder(long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng"));
    }

    private static void checkCustomerOwns(OrderRow order, long customerId) {
        if (order.customerId() != customerId) {
            throw new ForbiddenException("Bạn không có quyền xem đơn hàng này");
        }
    }

    private void checkMerchantOwns(OrderRow order, long ownerUserId) {
        if (!orderRepository.isRestaurantOwnedBy(order.restaurantId(), ownerUserId)) {
            throw new ForbiddenException("Đơn này không thuộc quán của bạn");
        }
    }
}
