package vn.hutech.fooddelivery.common;

import java.util.List;

/**
 * Ket qua dang trang dung chung cho cac API danh sach.
 * page bat dau tu 0.
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
        return new PageResponse<>(items, page, size, totalItems, totalPages);
    }
}
