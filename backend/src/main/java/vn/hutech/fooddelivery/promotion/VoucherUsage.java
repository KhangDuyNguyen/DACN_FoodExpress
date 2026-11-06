package vn.hutech.fooddelivery.promotion;

import java.time.LocalDateTime;

/**
 * Model biểu diễn lịch sử áp dụng voucher vào từng đơn hàng cụ thể của khách hàng.
 * Khớp hoàn toàn với schema bảng `voucher_usages` trong V1__init.sql.
 * Thuần Java POJO, không dùng JPA theo quy ước nhóm.
 *
 * Người phụ trách: Sầm Xuân Điệp.
 */
public class VoucherUsage {

    private Long id;
    private Long voucherId;
    private Long userId;
    private Long orderId;
    private LocalDateTime usedAt = LocalDateTime.now();

    public VoucherUsage() {
    }

    public VoucherUsage(Long voucherId, Long userId, Long orderId) {
        this.voucherId = voucherId;
        this.userId = userId;
        this.orderId = orderId;
        this.usedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVoucherId() {
        return voucherId;
    }

    public void setVoucherId(Long voucherId) {
        this.voucherId = voucherId;
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
