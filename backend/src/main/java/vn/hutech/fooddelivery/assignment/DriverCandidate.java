package vn.hutech.fooddelivery.assignment;

import java.time.LocalDateTime;

/**
 * Snapshot dau vao; hai co activeOrder/pendingOffer do ben goi cung cap, khong suy ra tu Order.
 * lastLocationAt va now cua selector phai cung mui gio Backend.
 */
public record DriverCandidate(
        long driverId,
        String status,
        boolean isOnline,
        Double lat,
        Double lng,
        LocalDateTime lastLocationAt,
        boolean hasActiveOrder,
        boolean hasPendingOffer
) {
}
