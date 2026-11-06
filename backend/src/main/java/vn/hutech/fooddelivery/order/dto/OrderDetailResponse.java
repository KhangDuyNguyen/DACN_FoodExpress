package vn.hutech.fooddelivery.order.dto;

import vn.hutech.fooddelivery.order.OrderRow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Chi tiet 1 don hang: thong tin giao, cac mon, bang tien va lich su trang thai. */
public record OrderDetailResponse(
        long id,
        String code,
        String status,
        String customerName,
        OrderRestaurantResponse restaurant,
        String receiverName,
        String receiverPhone,
        String deliveryAddress,
        String note,
        String paymentMethod,
        List<OrderItemResponse> items,
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        BigDecimal distanceKm,
        String cancelReason,
        LocalDateTime createdAt,
        LocalDateTime acceptedAt,
        LocalDateTime deliveredAt,
        LocalDateTime cancelledAt,
        List<OrderHistoryResponse> history
) {

    public static OrderDetailResponse from(OrderRow row, List<OrderItemResponse> items,
                                           List<OrderHistoryResponse> history) {
        return new OrderDetailResponse(
                row.id(),
                row.code(),
                row.status(),
                row.customerName(),
                new OrderRestaurantResponse(row.restaurantId(), row.restaurantName(),
                        row.restaurantPhone(), row.restaurantAddress()),
                row.receiverName(),
                row.receiverPhone(),
                row.deliveryAddress(),
                row.note(),
                row.paymentMethod(),
                items,
                row.subtotal(),
                row.deliveryFee(),
                row.discountAmount(),
                row.totalAmount(),
                row.distanceKm(),
                row.cancelReason(),
                row.createdAt(),
                row.acceptedAt(),
                row.deliveredAt(),
                row.cancelledAt(),
                history
        );
    }
}
