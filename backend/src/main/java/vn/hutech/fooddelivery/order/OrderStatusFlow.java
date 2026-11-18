package vn.hutech.fooddelivery.order;

import java.util.Map;
import java.util.Set;

/**
 * Luat chuyen trang thai don (tap con cua enum orders.status trong V1). Tuan 4 them buoc
 * "mon da xong" (READY_FOR_PICKUP) va vai tro tai xe:
 *
 *   PLACED (cho xac nhan) -> PREPARING (dang chuan bi) -> READY_FOR_PICKUP (cho lay mon)
 *     -> DELIVERING (dang giao) -> DELIVERED (hoan thanh)
 *   PLACED, PREPARING -> CANCELLED (da huy)
 *
 * Moi vai tro chi duoc di nhung buoc ghi trong bang cua minh. Rieng 2 buoc giao hang
 * (sang DELIVERING va DELIVERED):
 *   - don DA CO tai xe nhan (orders.driver_id khac null): chi tai xe duoc bam;
 *   - don CHUA CO tai xe: chu quan bam thay, de van giao duoc khi khong co tai xe online.
 * Chi la tra bang, khong doc database, nen unit test rieng duoc (OrderStatusFlowTest).
 */
public final class OrderStatusFlow {

    public enum Actor { CUSTOMER, MERCHANT, DRIVER }

    /** Chu quan: nhan don, bao mon da xong, huy khi chua giao; 2 buoc giao chi khi don chua co tai xe. */
    private static final Map<String, Set<String>> MERCHANT_NEXT = Map.of(
            "PLACED", Set.of("PREPARING", "CANCELLED"),
            "PREPARING", Set.of("READY_FOR_PICKUP", "CANCELLED"),
            "READY_FOR_PICKUP", Set.of("DELIVERING"),
            "DELIVERING", Set.of("DELIVERED")
    );

    /** Tai xe da nhan don: lay mon roi di giao, giao xong. */
    private static final Map<String, Set<String>> DRIVER_NEXT = Map.of(
            "READY_FOR_PICKUP", Set.of("DELIVERING"),
            "DELIVERING", Set.of("DELIVERED")
    );

    /** Khach: chi huy duoc khi quan chua nhan don. */
    private static final Map<String, Set<String>> CUSTOMER_NEXT = Map.of(
            "PLACED", Set.of("CANCELLED")
    );

    private static final Map<String, String> LABELS = Map.of(
            "PENDING_PAYMENT", "Chờ thanh toán",
            "PLACED", "Chờ xác nhận",
            "PREPARING", "Đang chuẩn bị",
            "READY_FOR_PICKUP", "Chờ lấy món",
            "DELIVERING", "Đang giao",
            "DELIVERED", "Hoàn thành",
            "CANCELLED", "Đã hủy"
    );

    private OrderStatusFlow() {
    }

    /**
     * @param hasDriver don da co tai xe nhan chua (orders.driver_id khac null)
     */
    public static boolean canChange(Actor actor, String fromStatus, String toStatus, boolean hasDriver) {
        Map<String, Set<String>> table = switch (actor) {
            case MERCHANT -> MERCHANT_NEXT;
            case DRIVER -> DRIVER_NEXT;
            case CUSTOMER -> CUSTOMER_NEXT;
        };
        if (!table.getOrDefault(fromStatus, Set.of()).contains(toStatus)) {
            return false;
        }
        if (isDeliveryStep(toStatus)) {
            // Co tai xe thi viec giao la cua tai xe; chua co thi chu quan lam thay
            return actor == Actor.DRIVER ? hasDriver : !hasDriver;
        }
        return true;
    }

    /** Buoc giao hang: chuyen sang DELIVERING (da lay mon, dang giao) hoac DELIVERED (da giao). */
    public static boolean isDeliveryStep(String toStatus) {
        return "DELIVERING".equals(toStatus) || "DELIVERED".equals(toStatus);
    }

    /** Ten trang thai tieng Viet de dua vao thong bao loi. */
    public static String label(String status) {
        return LABELS.getOrDefault(status, status);
    }
}
