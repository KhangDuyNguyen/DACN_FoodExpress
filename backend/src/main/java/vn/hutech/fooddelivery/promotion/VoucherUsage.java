package vn.hutech.fooddelivery.promotion;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Thực thể biểu diễn lịch sử áp dụng voucher vào từng đơn hàng cụ thể của khách hàng.
 * Khớp hoàn toàn với schema bảng `voucher_usages` trong V1__init.sql.
 *
 * Người phụ trách: Sầm Xuân Điệp.
 */
@Entity
@Table(name = "voucher_usages", indexes = {
        @Index(name = "uq_voucher_usages_order", columnList = "order_id", unique = true),
        @Index(name = "idx_voucher_usages_voucher_user", columnList = "voucher_id, user_id"),
        @Index(name = "idx_voucher_usages_user", columnList = "user_id")
})
public class VoucherUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voucher_id", nullable = false, foreignKey = @ForeignKey(name = "fk_vu_voucher"))
    private Voucher voucher;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Mã đơn hàng được áp dụng mã giảm giá (Mỗi đơn chỉ được dùng tối đa 1 voucher).
     */
    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "used_at", nullable = false, updatable = false)
    private LocalDateTime usedAt = LocalDateTime.now();

    public VoucherUsage() {
    }

    public VoucherUsage(Voucher voucher, Long userId, Long orderId) {
        this.voucher = voucher;
        this.userId = userId;
        this.orderId = orderId;
        this.usedAt = LocalDateTime.now();
    }

    // --- Getters & Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Voucher getVoucher() {
        return voucher;
    }

    public void setVoucher(Voucher voucher) {
        this.voucher = voucher;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }
}
