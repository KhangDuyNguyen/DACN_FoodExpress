package vn.hutech.fooddelivery.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.ai.InteractionRepository.InteractionRow;
import vn.hutech.fooddelivery.ai.InteractionRepository.OrderCheckRow;
import vn.hutech.fooddelivery.ai.InteractionRepository.OrderItemQtyRow;
import vn.hutech.fooddelivery.ai.dto.InteractionResponse;
import vn.hutech.fooddelivery.ai.dto.RecordInteractionRequest;
import vn.hutech.fooddelivery.common.BadRequestException;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.common.NotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Nghiệp vụ ghi nhận hành vi người dùng (xem món, thêm giỏ, đặt món, yêu thích).
 */
@Service
public class InteractionService {

    private static final Logger log = LoggerFactory.getLogger(InteractionService.class);
    private static final Set<String> INVALID_ORDER_STATUSES = Set.of("PENDING_PAYMENT", "CANCELLED", "REJECTED");

    private final InteractionRepository interactionRepository;

    public InteractionService(InteractionRepository interactionRepository) {
        this.interactionRepository = interactionRepository;
    }

    @Transactional
    public InteractionResponse recordInteraction(long userId, RecordInteractionRequest req) {
        InteractionType type = InteractionType.fromString(req.type());
        long restaurantId = interactionRepository.findMenuItemRestaurantId(req.menuItemId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy món ăn"));

        int quantity = req.quantity() != null ? req.quantity() : 1;
        String eventId = cleanEventId(req.eventId());
        Long orderId = req.orderId();

        if (orderId != null) {
            OrderCheckRow order = interactionRepository.findOrderForInteraction(orderId)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng"));
            if (order.customerId() != userId) {
                throw new ForbiddenException("Đơn hàng không thuộc về tài khoản đang đăng nhập");
            }
            if (type == InteractionType.ORDER && INVALID_ORDER_STATUSES.contains(order.status())) {
                throw new BadRequestException("Không thể ghi nhận đặt món cho đơn chưa thanh toán hoặc đã hủy");
            }
        }

        // Chống ghi nhận trùng khi retry bằng eventId
        if (eventId != null) {
            Optional<InteractionRow> existingByEvent = interactionRepository.findByEventId(eventId);
            if (existingByEvent.isPresent()) {
                InteractionRow row = existingByEvent.get();
                if (row.userId() != userId) {
                    throw new ForbiddenException("eventId đã tồn tại cho tài khoản khác");
                }
                return toResponse(row, true);
            }
        }

        // Chống ghi nhận trùng sự kiện ORDER cho cùng 1 đơn hàng & món ăn
        if (type == InteractionType.ORDER && orderId != null) {
            Optional<InteractionRow> existingByOrder =
                    interactionRepository.findByOrderAndItemAndType(orderId, req.menuItemId(), type.name());
            if (existingByOrder.isPresent()) {
                return toResponse(existingByOrder.get(), true);
            }
        }

        try {
            long insertedId = interactionRepository.insert(
                    userId,
                    req.menuItemId(),
                    type.name(),
                    quantity,
                    orderId,
                    eventId
            );
            InteractionRow created = interactionRepository.findById(insertedId)
                    .orElseGet(() -> new InteractionRow(
                            insertedId, userId, req.menuItemId(), restaurantId,
                            type.name(), quantity, orderId, eventId, null
                    ));
            return toResponse(created, false);
        } catch (DuplicateKeyException ex) {
            // Race condition khi 2 request retry đến cùng lúc
            if (eventId != null) {
                Optional<InteractionRow> dupEvent = interactionRepository.findByEventId(eventId);
                if (dupEvent.isPresent() && dupEvent.get().userId() == userId) {
                    return toResponse(dupEvent.get(), true);
                }
            }
            if (type == InteractionType.ORDER && orderId != null) {
                Optional<InteractionRow> dupOrder =
                        interactionRepository.findByOrderAndItemAndType(orderId, req.menuItemId(), type.name());
                if (dupOrder.isPresent()) {
                    return toResponse(dupOrder.get(), true);
                }
            }
            throw ex;
        }
    }

    /**
     * Tự động ghi nhận hành vi ORDER sau khi đơn hàng được tạo/thanh toán thành công.
     * Chạy trong transaction mới (REQUIRES_NEW) sau AFTER_COMMIT để không bao giờ ảnh hưởng đơn hàng.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOrderPlacedSafely(long orderId) {
        Optional<OrderCheckRow> orderOpt = interactionRepository.findOrderForInteraction(orderId);
        if (orderOpt.isEmpty()) {
            return;
        }
        OrderCheckRow order = orderOpt.get();
        if (INVALID_ORDER_STATUSES.contains(order.status())) {
            return;
        }
        List<OrderItemQtyRow> items = interactionRepository.findOrderItemQuantities(orderId);
        for (OrderItemQtyRow item : items) {
            try {
                if (interactionRepository.findByOrderAndItemAndType(orderId, item.menuItemId(), InteractionType.ORDER.name()).isPresent()) {
                    continue;
                }
                String eventId = "order-" + orderId + "-item-" + item.menuItemId();
                interactionRepository.insert(
                        order.customerId(),
                        item.menuItemId(),
                        InteractionType.ORDER.name(),
                        Math.max(1, item.quantity()),
                        orderId,
                        eventId
                );
            } catch (DuplicateKeyException dup) {
                // Đã ghi nhận trước đó -> bỏ qua an toàn
            } catch (Exception ex) {
                log.warn("Không thể ghi nhận interaction ORDER cho orderId={}, menuItemId={}: {}",
                        orderId, item.menuItemId(), ex.getMessage());
            }
        }
    }

    private static String cleanEventId(String raw) {
        return (raw == null || raw.isBlank()) ? null : raw.trim();
    }

    private static InteractionResponse toResponse(InteractionRow row, boolean deduplicated) {
        return new InteractionResponse(
                row.id(),
                row.userId(),
                row.menuItemId(),
                row.restaurantId(),
                row.type(),
                row.quantity(),
                row.orderId(),
                row.eventId(),
                deduplicated,
                row.createdAt()
        );
    }
}

