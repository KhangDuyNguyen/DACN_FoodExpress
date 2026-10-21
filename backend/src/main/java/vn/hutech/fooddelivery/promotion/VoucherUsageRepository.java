package vn.hutech.fooddelivery.promotion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository truy xuất lịch sử áp dụng voucher vào các đơn hàng.
 * Người phụ trách: Sầm Xuân Điệp.
 */
@Repository
public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, Long> {

    /**
     * Lấy toàn bộ lịch sử sử dụng của một voucher cụ thể.
     */
    List<VoucherUsage> findByVoucherId(Long voucherId);

    /**
     * Lấy danh sách voucher mà một khách hàng đã từng sử dụng.
     */
    List<VoucherUsage> findByUserId(Long userId);

    /**
     * Tìm thông tin voucher áp dụng cho một đơn hàng (Mỗi đơn tối đa 1 voucher).
     */
    Optional<VoucherUsage> findByOrderId(Long orderId);

    /**
     * Đếm số lần một người dùng đã sử dụng mã voucher này (đối chiếu với per_user_limit).
     */
    long countByVoucherIdAndUserId(Long voucherId, Long userId);
}
