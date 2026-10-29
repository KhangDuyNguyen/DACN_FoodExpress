package vn.hutech.fooddelivery.promotion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.common.PageResponse;
import vn.hutech.fooddelivery.promotion.dto.AdminVoucherRequest;
import vn.hutech.fooddelivery.promotion.dto.MerchantRestaurantOption;
import vn.hutech.fooddelivery.promotion.dto.MerchantVoucherRequest;
import vn.hutech.fooddelivery.promotion.dto.VoucherResponse;
import vn.hutech.fooddelivery.promotion.dto.VoucherValidateRequest;
import vn.hutech.fooddelivery.promotion.dto.VoucherValidateResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service xử lý toàn bộ nghiệp vụ voucher:
 * - Kiểm tra voucher khả dụng và tính toán giảm giá 3 loại (PERCENT, FIXED, FREE_SHIP).
 * - Quản lý CRUD voucher toàn sàn (Admin).
 * - Quản lý CRUD voucher cửa hàng (Merchant) với phân quyền sở hữu chặt chẽ.
 * - Kiểm soát số lượt sử dụng và phòng chống race condition khi áp dụng voucher.
 *
 * Người phụ trách: Sầm Xuân Điệp.
 */
@Service
public class VoucherService {

    private final VoucherJdbcRepository repository;

    public VoucherService(VoucherJdbcRepository repository) {
        this.repository = repository;
    }

    /**
     * Lấy danh sách voucher khả dụng cho giỏ hàng của khách hàng.
     */
    public List<VoucherResponse> getAvailableVouchers(Long restaurantId, BigDecimal orderAmount, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<VoucherRow> rows = repository.findAvailableVouchers(now, restaurantId, orderAmount);

        // Lọc thêm theo giới hạn sử dụng của người dùng (per_user_limit)
        List<VoucherRow> eligibleRows = rows.stream()
                .filter(v -> {
                    if (userId == null) {
                        return true;
                    }
                    int userUsages = repository.countUserUsage(v.id(), userId);
                    return userUsages < v.perUserLimit();
                })
                .toList();

        // Lấy tên nhà hàng để map vào response
        Set<Long> restaurantIds = eligibleRows.stream()
                .map(VoucherRow::restaurantId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, String> restaurantNames = repository.getRestaurantNamesByIds(restaurantIds);

        return eligibleRows.stream()
                .map(row -> VoucherResponse.fromRow(row, row.restaurantId() != null ? restaurantNames.get(row.restaurantId()) : null))
                .toList();
    }

    /**
     * Xác thực voucher và tính toán mức giảm giá chuẩn xác tại server.
     * TUYỆT ĐỐI KHÔNG tăng lượt sử dụng ở bước này.
     */
    public VoucherValidateResponse validateVoucher(VoucherValidateRequest request, Long userId) {
        if (request.code() == null || request.code().trim().isEmpty()) {
            return VoucherValidateResponse.invalid("Mã voucher không được để trống");
        }

        VoucherRow voucher = repository.findByCode(request.code())
                .orElse(null);

        if (voucher == null) {
            return VoucherValidateResponse.invalid("Mã voucher không tồn tại");
        }

        // 1. Kiểm tra trạng thái
        VoucherStatus status = VoucherStatus.valueOf(voucher.status());
        if (status == VoucherStatus.EXPIRED) {
            return VoucherValidateResponse.invalid("Mã voucher đã hết hạn hoặc kết thúc");
        }
        if (status == VoucherStatus.PAUSED) {
            return VoucherValidateResponse.invalid("Chương trình khuyến mãi này đang tạm dừng");
        }

        // 2. Kiểm tra thời hạn
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.startAt())) {
            return VoucherValidateResponse.invalid("Chương trình khuyến mãi chưa bắt đầu");
        }
        if (now.isAfter(voucher.endAt())) {
            return VoucherValidateResponse.invalid("Mã voucher đã hết hạn sử dụng");
        }

        // 3. Kiểm tra phạm vi áp dụng (toàn sàn hoặc nhà hàng cụ thể)
        if (voucher.restaurantId() != null) {
            if (request.restaurantId() == null || !voucher.restaurantId().equals(request.restaurantId())) {
                return VoucherValidateResponse.invalid("Mã voucher này chỉ áp dụng cho nhà hàng chỉ định");
            }
        }

        // 4. Kiểm tra giá trị đơn hàng tối thiểu
        BigDecimal orderAmount = request.orderAmount() != null ? request.orderAmount() : BigDecimal.ZERO;
        if (orderAmount.compareTo(voucher.minOrder()) < 0) {
            return VoucherValidateResponse.invalid(String.format("Đơn hàng chưa đạt giá trị tối thiểu %,.0f đ", voucher.minOrder()));
        }

        // 5. Kiểm tra tổng số lượt sử dụng toàn hệ thống
        if (voucher.usageLimit() != null && voucher.usedCount() >= voucher.usageLimit()) {
            return VoucherValidateResponse.invalid("Mã voucher đã hết lượt sử dụng trên hệ thống");
        }

        // 6. Kiểm tra giới hạn số lượt mỗi người dùng
        if (userId != null) {
            int userUsages = repository.countUserUsage(voucher.id(), userId);
            if (userUsages >= voucher.perUserLimit()) {
                return VoucherValidateResponse.invalid(String.format("Bạn đã dùng hết số lượt cho phép (%d lượt) của mã này", voucher.perUserLimit()));
            }
        }

        // 7. Tính toán mức giảm giá theo 3 loại: PERCENT, FIXED, FREE_SHIP
        DiscountType discountType = DiscountType.valueOf(voucher.discountType());
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal finalOrderAmount = orderAmount;
        BigDecimal effectiveShippingFee = request.getEffectiveShippingFee();
        BigDecimal finalShippingFee = effectiveShippingFee;

        switch (discountType) {
            case PERCENT -> {
                // Giảm % theo giá trị đơn hàng
                BigDecimal percent = voucher.discountValue();
                BigDecimal rawDiscount = orderAmount.multiply(percent).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
                if (voucher.maxDiscount() != null && rawDiscount.compareTo(voucher.maxDiscount()) > 0) {
                    rawDiscount = voucher.maxDiscount();
                }
                if (rawDiscount.compareTo(orderAmount) > 0) {
                    rawDiscount = orderAmount;
                }
                discountAmount = rawDiscount;
                finalOrderAmount = orderAmount.subtract(discountAmount);
            }
            case FIXED -> {
                // Giảm số tiền cố định
                BigDecimal fixedDiscount = voucher.discountValue();
                if (fixedDiscount.compareTo(orderAmount) > 0) {
                    fixedDiscount = orderAmount;
                }
                discountAmount = fixedDiscount;
                finalOrderAmount = orderAmount.subtract(discountAmount);
            }
            case FREE_SHIP -> {
                // Miễn phí vận chuyển (toàn phần hoặc tối đa maxDiscount / discountValue)
                BigDecimal maxShipDiscount = effectiveShippingFee;
                if (voucher.discountValue() != null && voucher.discountValue().compareTo(BigDecimal.ZERO) > 0) {
                    maxShipDiscount = maxShipDiscount.min(voucher.discountValue());
                }
                if (voucher.maxDiscount() != null && maxShipDiscount.compareTo(voucher.maxDiscount()) > 0) {
                    maxShipDiscount = voucher.maxDiscount();
                }
                discountAmount = maxShipDiscount;
                finalShippingFee = effectiveShippingFee.subtract(discountAmount);
            }
        }

        return new VoucherValidateResponse(
                true,
                "Áp dụng mã giảm giá thành công",
                voucher.id(),
                voucher.code(),
                voucher.title(),
                discountType,
                discountAmount,
                finalOrderAmount,
                finalShippingFee,
                voucher.minOrder(),
                voucher.maxDiscount()
        );
    }

    /**
     * Ghi nhận sử dụng voucher thực tế khi khách hàng hoàn tất tạo đơn hàng (được gọi từ OrderService).
     * Áp dụng Transaction và Atomic increment để chống race condition.
     */
    @Transactional
    public void recordVoucherUsage(Long voucherId, Long userId, Long orderId) {
        int updated = repository.incrementUsedCount(voucherId);
        if (updated == 0) {
            throw new ConflictException("Voucher đã hết lượt sử dụng hoặc không còn hiệu lực");
        }
        repository.recordUsage(voucherId, userId, orderId, LocalDateTime.now());
    }

    // -------------------------------------------------------------
    // ADMIN VOUCHER MANAGEMENT
    // -------------------------------------------------------------

    public PageResponse<VoucherResponse> getAdminVouchers(String keyword, String discountType, String status, int page, int size) {
        int offset = page * size;
        List<VoucherRow> rows = repository.findForAdmin(keyword, discountType, status, offset, size);
        long total = repository.countForAdmin(keyword, discountType, status);

        Set<Long> restaurantIds = rows.stream()
                .map(VoucherRow::restaurantId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, String> restaurantNames = repository.getRestaurantNamesByIds(restaurantIds);

        List<VoucherResponse> items = rows.stream()
                .map(row -> VoucherResponse.fromRow(row, row.restaurantId() != null ? restaurantNames.get(row.restaurantId()) : null))
                .toList();

        return PageResponse.of(items, page, size, total);
    }

    public VoucherResponse getAdminVoucherById(Long id) {
        VoucherRow row = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy voucher với id: " + id));
        String restaurantName = row.restaurantId() != null
                ? repository.getRestaurantNamesByIds(Set.of(row.restaurantId())).get(row.restaurantId())
                : null;
        return VoucherResponse.fromRow(row, restaurantName);
    }

    @Transactional
    public VoucherResponse createAdminVoucher(AdminVoucherRequest req, Long adminUserId) {
        validateVoucherRequestDates(req.startAt(), req.endAt());

        if (repository.existsByCode(req.code(), null)) {
            throw new ConflictException("Mã voucher '" + req.code() + "' đã tồn tại trên hệ thống");
        }

        if (req.discountType() == DiscountType.PERCENT) {
            if (req.discountValue().compareTo(BigDecimal.ZERO) <= 0 || req.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new ConflictException("Tỷ lệ giảm giá phần trăm phải nằm trong khoảng (0, 100]");
            }
        }

        Voucher voucher = new Voucher();
        voucher.setCode(req.code().toUpperCase().trim());
        voucher.setTitle(req.title().trim());
        voucher.setDescription(req.description());
        voucher.setDiscountType(req.discountType());
        voucher.setDiscountValue(req.discountValue());
        voucher.setMinOrder(req.minOrder());
        voucher.setMaxDiscount(req.maxDiscount());
        voucher.setStartAt(req.startAt());
        voucher.setEndAt(req.endAt());
        voucher.setUsageLimit(req.usageLimit());
        voucher.setPerUserLimit(req.perUserLimit() != null ? req.perUserLimit() : 1);
        voucher.setRestaurantId(req.restaurantId()); // NULL nếu là voucher toàn sàn
        voucher.setCreatedBy(adminUserId);
        voucher.setStatus(req.status() != null ? req.status() : VoucherStatus.ACTIVE);
        voucher.setCreatedAt(LocalDateTime.now());

        Long id = repository.create(voucher);
        return getAdminVoucherById(id);
    }

    @Transactional
    public VoucherResponse updateAdminVoucher(Long id, AdminVoucherRequest req) {
        VoucherRow existing = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy voucher với id: " + id));

        validateVoucherRequestDates(req.startAt(), req.endAt());

        if (repository.existsByCode(req.code(), id)) {
            throw new ConflictException("Mã voucher '" + req.code() + "' đã được sử dụng bởi voucher khác");
        }

        if (req.discountType() == DiscountType.PERCENT) {
            if (req.discountValue().compareTo(BigDecimal.ZERO) <= 0 || req.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new ConflictException("Tỷ lệ giảm giá phần trăm phải nằm trong khoảng (0, 100]");
            }
        }

        Voucher voucher = new Voucher();
        voucher.setId(id);
        voucher.setTitle(req.title().trim());
        voucher.setDescription(req.description());
        voucher.setDiscountType(req.discountType());
        voucher.setDiscountValue(req.discountValue());
        voucher.setMinOrder(req.minOrder());
        voucher.setMaxDiscount(req.maxDiscount());
        voucher.setStartAt(req.startAt());
        voucher.setEndAt(req.endAt());
        voucher.setUsageLimit(req.usageLimit());
        voucher.setPerUserLimit(req.perUserLimit() != null ? req.perUserLimit() : existing.perUserLimit());
        voucher.setStatus(req.status() != null ? req.status() : VoucherStatus.valueOf(existing.status()));

        repository.update(voucher);
        return getAdminVoucherById(id);
    }

    @Transactional
    public void updateAdminVoucherStatus(Long id, VoucherStatus status) {
        if (!repository.findById(id).isPresent()) {
            throw new NotFoundException("Không tìm thấy voucher với id: " + id);
        }
        repository.updateStatus(id, status);
    }

    // -------------------------------------------------------------
    // MERCHANT VOUCHER MANAGEMENT
    // -------------------------------------------------------------

    public List<MerchantRestaurantOption> getMerchantRestaurants(Long merchantUserId) {
        return repository.findRestaurantsByOwnerUserId(merchantUserId);
    }

    public PageResponse<VoucherResponse> getMerchantVouchers(Long merchantUserId, String keyword, String status, int page, int size) {
        List<Long> restaurantIds = repository.findRestaurantIdsByOwnerUserId(merchantUserId);
        if (restaurantIds.isEmpty()) {
            return PageResponse.of(Collections.emptyList(), page, size, 0);
        }

        int offset = page * size;
        List<VoucherRow> rows = repository.findForMerchant(restaurantIds, keyword, status, offset, size);
        long total = repository.countForMerchant(restaurantIds, keyword, status);

        Map<Long, String> restaurantNames = repository.getRestaurantNamesByIds(restaurantIds);

        List<VoucherResponse> items = rows.stream()
                .map(row -> VoucherResponse.fromRow(row, restaurantNames.get(row.restaurantId())))
                .toList();

        return PageResponse.of(items, page, size, total);
    }

    @Transactional
    public VoucherResponse createMerchantVoucher(MerchantVoucherRequest req, Long merchantUserId) {
        if (!repository.isRestaurantOwnedByUser(req.restaurantId(), merchantUserId)) {
            throw new ForbiddenException("Bạn không có quyền tạo voucher cho nhà hàng này");
        }

        validateVoucherRequestDates(req.startAt(), req.endAt());

        if (repository.existsByCode(req.code(), null)) {
            throw new ConflictException("Mã voucher '" + req.code() + "' đã tồn tại");
        }

        if (req.discountType() == DiscountType.PERCENT) {
            if (req.discountValue().compareTo(BigDecimal.ZERO) <= 0 || req.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new ConflictException("Tỷ lệ giảm giá phần trăm phải nằm trong khoảng (0, 100]");
            }
        }

        Voucher voucher = new Voucher();
        voucher.setCode(req.code().toUpperCase().trim());
        voucher.setTitle(req.title().trim());
        voucher.setDescription(req.description());
        voucher.setDiscountType(req.discountType());
        voucher.setDiscountValue(req.discountValue());
        voucher.setMinOrder(req.minOrder());
        voucher.setMaxDiscount(req.maxDiscount());
        voucher.setStartAt(req.startAt());
        voucher.setEndAt(req.endAt());
        voucher.setUsageLimit(req.usageLimit());
        voucher.setPerUserLimit(req.perUserLimit() != null ? req.perUserLimit() : 1);
        voucher.setRestaurantId(req.restaurantId()); // BẮT BUỘC gắn với quán của merchant
        voucher.setCreatedBy(merchantUserId);
        voucher.setStatus(req.status() != null ? req.status() : VoucherStatus.ACTIVE);
        voucher.setCreatedAt(LocalDateTime.now());

        Long id = repository.create(voucher);
        return getAdminVoucherById(id);
    }

    @Transactional
    public VoucherResponse updateMerchantVoucher(Long id, MerchantVoucherRequest req, Long merchantUserId) {
        VoucherRow existing = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy voucher với id: " + id));

        // Kiểm tra quyền sở hữu với quán hiện tại của voucher
        if (existing.restaurantId() == null || !repository.isRestaurantOwnedByUser(existing.restaurantId(), merchantUserId)) {
            throw new ForbiddenException("Bạn không có quyền chỉnh sửa voucher này");
        }

        // Nếu chuyển sang quán khác, phải kiểm tra quán mới cũng thuộc sở hữu của merchant
        if (!repository.isRestaurantOwnedByUser(req.restaurantId(), merchantUserId)) {
            throw new ForbiddenException("Bạn không có quyền gán voucher cho nhà hàng này");
        }

        validateVoucherRequestDates(req.startAt(), req.endAt());

        if (repository.existsByCode(req.code(), id)) {
            throw new ConflictException("Mã voucher '" + req.code() + "' đã được sử dụng");
        }

        if (req.discountType() == DiscountType.PERCENT) {
            if (req.discountValue().compareTo(BigDecimal.ZERO) <= 0 || req.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new ConflictException("Tỷ lệ giảm giá phần trăm phải nằm trong khoảng (0, 100]");
            }
        }

        Voucher voucher = new Voucher();
        voucher.setId(id);
        voucher.setTitle(req.title().trim());
        voucher.setDescription(req.description());
        voucher.setDiscountType(req.discountType());
        voucher.setDiscountValue(req.discountValue());
        voucher.setMinOrder(req.minOrder());
        voucher.setMaxDiscount(req.maxDiscount());
        voucher.setStartAt(req.startAt());
        voucher.setEndAt(req.endAt());
        voucher.setUsageLimit(req.usageLimit());
        voucher.setPerUserLimit(req.perUserLimit() != null ? req.perUserLimit() : existing.perUserLimit());
        voucher.setStatus(req.status() != null ? req.status() : VoucherStatus.valueOf(existing.status()));

        repository.update(voucher);
        return getAdminVoucherById(id);
    }

    @Transactional
    public void updateMerchantVoucherStatus(Long id, VoucherStatus status, Long merchantUserId) {
        VoucherRow existing = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy voucher với id: " + id));

        if (existing.restaurantId() == null || !repository.isRestaurantOwnedByUser(existing.restaurantId(), merchantUserId)) {
            throw new ForbiddenException("Bạn không có quyền thay đổi trạng thái voucher này");
        }

        repository.updateStatus(id, status);
    }

    private void validateVoucherRequestDates(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null) {
            throw new ConflictException("Thời gian bắt đầu và kết thúc không được để trống");
        }
        if (!endAt.isAfter(startAt)) {
            throw new ConflictException("Thời gian kết thúc phải sau thời gian bắt đầu");
        }
    }
}

