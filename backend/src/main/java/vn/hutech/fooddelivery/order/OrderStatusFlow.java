package vn.hutech.fooddelivery.order;

import java.util.Map;
import java.util.Set;

/**
 * Luat chuyen trang thai don tuan 3 (tap con cua enum orders.status trong V1):
 *
 *   PLACED (cho xac nhan) -> PREPARING (dang chuan bi) -> DELIVERING (dang giao) -> DELIVERED (hoan thanh)
 *   PLACED, PREPARING -> CANCELLED (da huy)
 *
 * Moi vai tro chi duoc di nhung buoc ghi trong bang cua minh, moi buoc khac deu bi chan.
 * Chi la tra bang, khong doc database, nen unit test rieng duoc (OrderStatusFlowTest).
 */
public final class OrderStatusFlow {

    public enum Actor { CUSTOMER, MERCHANT }

    /** Chu quan: nhan don, bat dau giao, hoan thanh (tam thoi thay tai xe), huy khi chua giao. */
    private static final Map<String, Set<String>> MERCHANT_NEXT = Map.of(
            "PLACED", Set.of("PREPARING", "CANCELLED"),
            "PREPARING", Set.of("DELIVERING", "CANCELLED"),
            // TODO(khang): khi An noi luong tai xe vao don thi buoc nay chuyen cho tai xe
            "DELIVERING", Set.of("DELIVERED")
    );

    /** Khach: chi huy duoc khi quan chua nhan don. */
    private static final Map<String, Set<String>> CUSTOMER_NEXT = Map.of(
            "PLACED", Set.of("CANCELLED")
    );

    private static final Map<String, String> LABELS = Map.of(
            "PLACED", "Chờ xác nhận",
            "PREPARING", "Đang chuẩn bị",
            "DELIVERING", "Đang giao",
            "DELIVERED", "Hoàn thành",
            "CANCELLED", "Đã hủy"
    );

    private OrderStatusFlow() {
    }

    public static boolean canChange(Actor actor, String fromStatus, String toStatus) {
        Map<String, Set<String>> table = actor == Actor.MERCHANT ? MERCHANT_NEXT : CUSTOMER_NEXT;
        return table.getOrDefault(fromStatus, Set.of()).contains(toStatus);
    }

    /** Ten trang thai tieng Viet de dua vao thong bao loi. */
    public static String label(String status) {
        return LABELS.getOrDefault(status, status);
    }
}
