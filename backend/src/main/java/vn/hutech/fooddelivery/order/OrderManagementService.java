package vn.hutech.fooddelivery.order;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.common.PageResponse;
import vn.hutech.fooddelivery.order.OrderStatusFlow.Actor;
import vn.hutech.fooddelivery.order.dto.ChangeOrderStatusRequest;
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
        long offset = (long) page * size;
        return PageResponse.of(
                orderRepository.findSummariesByCustomer(customerId, status, size, offset),
                page, size,
                orderRepository.countByCustomer(customerId, status));
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getCustomerOrder(long customerId, long orderId) {
        checkCustomerOwns(findOrder(orderId), customerId);
        return orderService.getDetail(orderId);
    }

    @Transactional
    public OrderDetailResponse cancelByCustomer(long customerId, long orderId, String reason) {
        // Le Tan Binh An - MSSV 2380600006 - Tuan 5 [MODIFY]: Order lock phai co truoc khi kiem tra huy/assignment, tranh race Accept.
        var order = findOrderForUpdate(orderId);
        checkCustomerOwns(order.customerId(), customerId);
        changeStatus(order, Actor.CUSTOMER, "CANCELLED", customerId, reason.trim());
        return orderService.getDetail(orderId);
    }

    // ----- Chu quan -----

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listMerchantOrders(long ownerUserId, Long restaurantId, String status,
                                                                 int page, int size) {
        // Loc theo 1 quan cu the thi quan do phai la cua minh
        if (restaurantId != null && !orderRepository.isRestaurantOwnedBy(restaurantId, ownerUserId)) {
            throw new ForbiddenException("Bạn không phải chủ quán này");
        }
        long offset = (long) page * size;
        return PageResponse.of(
                orderRepository.findSummariesByOwner(ownerUserId, restaurantId, status, size, offset),
                page, size,
                orderRepository.countByOwner(ownerUserId, restaurantId, status));
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getMerchantOrder(long ownerUserId, long orderId) {
        checkMerchantOwns(findOrder(orderId), ownerUserId);
        return orderService.getDetail(orderId);
    }

    @Transactional
    public OrderDetailResponse changeStatusByMerchant(long ownerUserId, long orderId, ChangeOrderStatusRequest req) {
        // Le Tan Binh An - MSSV 2380600006 - Tuan 5 [MODIFY]: Cung khoa Order voi Driver Delivery va Assignment Accept.
        var order = findOrderForUpdate(orderId);
        checkMerchantOwns(order.restaurantId(), ownerUserId);
        // Le Tan Binh An - MSSV 2380600006 - Tuan 5 [ADD]:
        // Accept da chuyen PREPARING thi merchant xac nhan lai la no-op, khong ghi trung history.
        if ("PREPARING".equals(order.status()) && "PREPARING".equals(req.status())) {
            return orderService.getDetail(orderId);
        }
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
    // Le Tan Binh An - MSSV 2380600006 - Tuan 5 [MODIFY]: Snapshot khoa thay cho doc thuong khi ghi trang thai.
    private void changeStatus(OrderRepository.LockedOrder order, Actor actor, String toStatus, long changedBy, String reason) {
        if (!OrderStatusFlow.canChange(actor, order.status(), toStatus)) {
            throw new ConflictException("Không thể chuyển đơn từ \"" + OrderStatusFlow.label(order.status())
                    + "\" sang \"" + OrderStatusFlow.label(toStatus) + "\"");
        }
        // Le Tan Binh An - MSSV 2380600006 - Tuan 5 [ADD]: Chan ca khach/merchant huy sau ACCEPTED; chua co nghiep vu huy/tai gan.
        if ("CANCELLED".equals(toStatus) && orderRepository.hasAcceptedAssignmentForUpdate(order.id())) {
            throw new ConflictException("Tài xế đã nhận giao đơn, chưa thể hủy đơn hàng");
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

    // Le Tan Binh An - MSSV 2380600006 - Tuan 5 [ADD]: Khong doi transaction, giu khoa den khi status/history cung commit.
    private OrderRepository.LockedOrder findOrderForUpdate(long orderId) {
        return orderRepository.lockOrder(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng"));
    }

    private static void checkCustomerOwns(OrderRow order, long customerId) {
        checkCustomerOwns(order.customerId(), customerId);
    }

    // Le Tan Binh An - MSSV 2380600006 - Tuan 5 [ADD]: Dung chung kiem tra so huu cho snapshot doc va snapshot khoa.
    private static void checkCustomerOwns(long orderCustomerId, long customerId) {
        if (orderCustomerId != customerId) {
            throw new ForbiddenException("Bạn không có quyền xem đơn hàng này");
        }
    }

    private void checkMerchantOwns(OrderRow order, long ownerUserId) {
        checkMerchantOwns(order.restaurantId(), ownerUserId);
    }

    // Le Tan Binh An - MSSV 2380600006 - Tuan 5 [ADD]: Giu quyen so huu quan cu cho ca luong ghi co khoa.
    private void checkMerchantOwns(long restaurantId, long ownerUserId) {
        if (!orderRepository.isRestaurantOwnedBy(restaurantId, ownerUserId)) {
            throw new ForbiddenException("Đơn này không thuộc quán của bạn");
        }
    }
}
