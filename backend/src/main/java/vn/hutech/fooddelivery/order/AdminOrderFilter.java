package vn.hutech.fooddelivery.order;

import java.time.LocalDate;

/**
 * Bo loc danh sach don toan san cho admin. Truong null = khong loc theo tieu chi do.
 *
 * @param keyword  dau ma don (FD2611...) hoac dau SDT khach
 * @param fromDate ngay dat tu (tinh ca ngay nay)
 * @param toDate   ngay dat den (tinh ca ngay nay)
 */
public record AdminOrderFilter(
        String status,
        Long restaurantId,
        String paymentMethod,
        String keyword,
        LocalDate fromDate,
        LocalDate toDate
) {
}
