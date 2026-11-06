package vn.hutech.fooddelivery.promotion.dto;

/**
 * Lựa chọn quán ăn cho Merchant khi tạo voucher trong dropdown.
 * Người phụ trách: Sầm Xuân Điệp.
 */
public record MerchantRestaurantOption(
        Long id,
        String name,
        String address
) {
}

