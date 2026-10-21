package vn.hutech.fooddelivery.promotion;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Thực thể biểu diễn thông tin Voucher khuyến mãi (toàn sàn hoặc của riêng từng quán).
 * Khớp hoàn toàn với schema bảng `vouchers` trong V1__init.sql.
 *
 * Người phụ trách: Sầm Xuân Điệp.
 */
@Entity
@Table(name = "vouchers", indexes = {
        @Index(name = "uq_vouchers_code", columnList = "code", unique = true),
        @Index(name = "idx_vouchers_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_vouchers_active", columnList = "status, start_at, end_at")
})
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 30, nullable = false, unique = true)
    private String code;

    @Column(name = "title", length = 150, nullable = false)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    /**
     * Giá trị giảm giá:
     * - Nếu PERCENT: lưu tỷ lệ % (ví dụ 10.00 = 10%)
     * - Nếu FIXED: lưu số tiền (ví dụ 20000.00 = 20.000đ)
     * - Nếu FREE_SHIP: lưu 0 hoặc mức tiền ship tối đa hỗ trợ
     */
    @Column(name = "discount_value", precision = 12, scale = 2, nullable = false)
    private BigDecimal discountValue = BigDecimal.ZERO;

    /**
     * Giá trị đơn hàng tối thiểu để được áp dụng voucher (VND, không có số thập phân).
     */
    @Column(name = "min_order", precision = 12, scale = 0, nullable = false)
    private BigDecimal minOrder = BigDecimal.ZERO;

    /**
     * Số tiền giảm tối đa (đặc biệt quan trọng khi discount_type = PERCENT).
     */
    @Column(name = "max_discount", precision = 12, scale = 0)
    private BigDecimal maxDiscount;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    /**
     * Tổng số lượt dùng tối đa toàn hệ thống (NULL = không giới hạn).
     */
    @Column(name = "usage_limit")
    private Integer usageLimit;

    /**
     * Số lượt mỗi tài khoản người dùng được áp dụng (mặc định 1).
     */
    @Column(name = "per_user_limit", nullable = false)
    private Integer perUserLimit = 1;

    /**
     * Số lượt thực tế đã được áp dụng vào các đơn hàng.
     */
    @Column(name = "used_count", nullable = false)
    private Integer usedCount = 0;

    /**
     * ID quán ăn phát hành voucher. NULL = Voucher áp dụng toàn sàn.
     */
    @Column(name = "restaurant_id")
    private Long restaurantId;

    /**
     * ID tài khoản người tạo (Admin hoặc Chủ quán).
     */
    @Column(name = "created_by")
    private Long createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VoucherStatus status = VoucherStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "voucher", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<VoucherUsage> usages = new ArrayList<>();

    public Voucher() {
    }

    public Voucher(String code, String title, DiscountType discountType, BigDecimal discountValue,
                   BigDecimal minOrder, LocalDateTime startAt, LocalDateTime endAt) {
        this.code = code;
        this.title = title;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrder = minOrder;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    // --- Getters & Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(DiscountType discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public BigDecimal getMinOrder() {
        return minOrder;
    }

    public void setMinOrder(BigDecimal minOrder) {
        this.minOrder = minOrder;
    }

    public BigDecimal getMaxDiscount() {
        return maxDiscount;
    }

    public void setMaxDiscount(BigDecimal maxDiscount) {
        this.maxDiscount = maxDiscount;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    public Integer getUsageLimit() {
        return usageLimit;
    }

    public void setUsageLimit(Integer usageLimit) {
        this.usageLimit = usageLimit;
    }

    public Integer getPerUserLimit() {
        return perUserLimit;
    }

    public void setPerUserLimit(Integer perUserLimit) {
        this.perUserLimit = perUserLimit;
    }

    public Integer getUsedCount() {
        return usedCount;
    }

    public void setUsedCount(Integer usedCount) {
        this.usedCount = usedCount;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public void setRestaurantId(Long restaurantId) {
        this.restaurantId = restaurantId;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public VoucherStatus getStatus() {
        return status;
    }

    public void setStatus(VoucherStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<VoucherUsage> getUsages() {
        return usages;
    }

    public void setUsages(List<VoucherUsage> usages) {
        this.usages = usages;
    }
}
