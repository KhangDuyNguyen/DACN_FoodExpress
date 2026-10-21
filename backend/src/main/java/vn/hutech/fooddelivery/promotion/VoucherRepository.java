package vn.hutech.fooddelivery.promotion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository truy xuất dữ liệu Voucher sử dụng Spring Data JPA.
 * Người phụ trách: Sầm Xuân Điệp.
 */
@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    /**
     * Tìm voucher theo mã code duy nhất (ví dụ: WELCOME2026).
     */
    Optional<Voucher> findByCode(String code);

    /**
     * Kiểm tra sự tồn tại của mã code voucher.
     */
    boolean existsByCode(String code);

    /**
     * Lấy danh sách voucher thuộc về một quán ăn cụ thể.
     */
    List<Voucher> findByRestaurantId(Long restaurantId);

    /**
     * Lấy danh sách voucher áp dụng toàn sàn (restaurant_id is NULL).
     */
    List<Voucher> findByRestaurantIdIsNull();

    /**
     * Lấy danh sách voucher theo trạng thái hoạt động (ACTIVE, PAUSED, EXPIRED).
     */
    List<Voucher> findByStatus(VoucherStatus status);
}
