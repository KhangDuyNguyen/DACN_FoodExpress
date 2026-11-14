package vn.hutech.fooddelivery.order;

import java.util.Map;
import java.util.Set;

/**
 * Luat chuyen trang thai don (tap con cua enum orders.status trong V1):
 *
 *   PLACED -> PREPARING -> PICKED_UP -> DELIVERING -> DELIVERED
 *   PREPARING -> READY_FOR_PICKUP -> PICKED_UP cung duoc ho tro.
 *   PLACED, PREPARING -> CANCELLED (da huy)
 *
 * Moi vai tro chi duoc di nhung buoc ghi trong bang cua minh, moi buoc khac deu bi chan.
 * Chi la tra bang, khong doc database, nen unit test rieng duoc (OrderStatusFlowTest).
 */
public final class OrderStatusFlow {

    // Le Tan Binh An - MSSV 2380600006 - Tuan 5 [ADD]: Tach quyen giao hang cua tai xe khoi quyen chuan bi mon.
    public enum Actor { CUSTOMER, MERCHANT, DRIVER }

    // Le Tan Binh An - MSSV 2380600006 - Tuan 5 [MODIFY]:
    // Merchant duoc bao mon san sang, ke ca chua co tai xe; khong chuyen lui hay giao ho tai xe.
    private static final Map<String, Set<String>> MERCHANT_NEXT = Map.of(
            "PLACED", Set.of("PREPARING", "CANCELLED"),
            "PREPARING", Set.of("READY_FOR_PICKUP", "CANCELLED")
    );

    // Le Tan Binh An - MSSV 2380600006 - Tuan 5 [ADD]: Moi buoc giao bat buoc do tai xe duoc gan don thuc hien.
    private static final Map<String, Set<String>> DRIVER_NEXT = Map.of(
            "PREPARING", Set.of("PICKED_UP"),
            "READY_FOR_PICKUP", Set.of("PICKED_UP"),
            "PICKED_UP", Set.of("DELIVERING"),
            "DELIVERING", Set.of("DELIVERED")
    );

    /** Khach: chi huy duoc khi quan chua nhan don. */
    private static final Map<String, Set<String>> CUSTOMER_NEXT = Map.of(
            "PLACED", Set.of("CANCELLED")
    );

    private static final Map<String, String> LABELS = Map.of(
            "PLACED", "Chờ xác nhận",
            "PREPARING", "Đang chuẩn bị",
            // Le Tan Binh An - MSSV 2380600006 - Tuan 5 [ADD]: Nhan trang thai moi trong loi va lich su giao hang.
            "READY_FOR_PICKUP", "Sẵn sàng lấy món",
            "PICKED_UP", "Đã lấy món",
            "DELIVERING", "Đang giao",
            "DELIVERED", "Hoàn thành",
            "CANCELLED", "Đã hủy"
    );

    private OrderStatusFlow() {
    }

    public static boolean canChange(Actor actor, String fromStatus, String toStatus) {
        // Le Tan Binh An - MSSV 2380600006 - Tuan 5 [MODIFY]: Moi actor dung bang quyen rieng, DRIVER khong roi vao CUSTOMER.
        Map<String, Set<String>> table = switch (actor) {
            case MERCHANT -> MERCHANT_NEXT;
            case CUSTOMER -> CUSTOMER_NEXT;
            case DRIVER -> DRIVER_NEXT;
        };
        return table.getOrDefault(fromStatus, Set.of()).contains(toStatus);
    }

    /** Ten trang thai tieng Viet de dua vao thong bao loi. */
    public static String label(String status) {
        return LABELS.getOrDefault(status, status);
    }
}
