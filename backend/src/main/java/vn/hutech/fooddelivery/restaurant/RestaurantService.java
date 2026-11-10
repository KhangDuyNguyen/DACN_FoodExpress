package vn.hutech.fooddelivery.restaurant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.common.PageResponse;
import vn.hutech.fooddelivery.restaurant.dto.*;

import java.util.*;

/**
 * Service xử lý business logic cho nhà hàng.
 */
@Service
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;

    public RestaurantService(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    /**
     * Tìm kiếm nhà hàng với filter tùy chọn, trả về một trang kết quả.
     * Có đủ lat và lng thì sort theo khoảng cách (gần trước), không thì sort theo avg_rating.
     * page bắt đầu từ 0; controller đã kiểm tra page >= 0 và 1 <= size <= 50.
     */
    @Transactional(readOnly = true)
    public PageResponse<RestaurantSummaryDTO> searchRestaurants(String keyword, Integer cuisineId,
                                                                Double userLat, Double userLng,
                                                                int page, int size) {
        // Ô tìm kiếm để trống hoặc toàn dấu cách thì coi như không lọc theo tên
        String cleanKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        // Thiếu một trong hai tọa độ thì không tính khoảng cách
        boolean hasLocation = userLat != null && userLng != null;
        // Ép kiểu long trước khi nhân để page rất lớn không bị tràn số int
        long offset = (long) page * size;

        List<RestaurantSummaryDTO> items = restaurantRepository.search(cleanKeyword, cuisineId,
                hasLocation ? userLat : null, hasLocation ? userLng : null, size, offset);
        long totalItems = restaurantRepository.countSearch(cleanKeyword, cuisineId);
        return PageResponse.of(items, page, size, totalItems);
    }

    /**
     * Lấy chi tiết nhà hàng theo id.
     * Throw NotFoundException nếu không tìm thấy hoặc không active.
     */
    @Transactional(readOnly = true)
    public RestaurantDetailDTO getRestaurantById(long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy quán với id: " + id));
    }

    /**
     * Lấy menu đầy đủ của nhà hàng (categories -> items -> option_groups -> options).
     * Throw NotFoundException nếu quán không tồn tại.
     */
    @Transactional(readOnly = true)
    public MenuDTO getMenuByRestaurantId(long restaurantId) {
        // Kiểm tra quán có tồn tại không
        restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy quán với id: " + restaurantId));

        List<MenuCategoryDTO> categories = restaurantRepository.findMenuByRestaurantId(restaurantId);
        return new MenuDTO(restaurantId, categories);
    }

    /**
     * Chi tiết một món. Món không có, không thuộc quán này, nhóm món bị tắt
     * hoặc quán chưa được duyệt (không phải APPROVED/ACTIVE) thì đều trả 404.
     */
    @Transactional(readOnly = true)
    public MenuItemDetailDTO getMenuItem(long restaurantId, long itemId) {
        return restaurantRepository.findMenuItem(restaurantId, itemId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy món trong quán này"));
    }

    /** Các quán/chi nhánh mà chủ quán (users.id = ownerUserId) sở hữu. Chưa có quán thì trả danh sách rỗng. */
    @Transactional(readOnly = true)
    public List<MerchantRestaurantDTO> getRestaurantsOfOwner(long ownerUserId) {
        return restaurantRepository.findByOwnerUserId(ownerUserId);
    }

    /**
     * Lấy chi tiết quán cho merchant (không lọc status - xem được quán PENDING/HIDDEN).
     * Kiểm tra ownership bằng JOIN merchants → users.
     */
    @Transactional(readOnly = true)
    public RestaurantDetailDTO getRestaurantByIdForMerchant(long restaurantId, long ownerUserId) {
        // Kiểm tra ownership trước (tránh lộ quán của merchant khác)
        long ownerCount = restaurantRepository.countByIdAndOwnerUserId(restaurantId, ownerUserId);
        if (ownerCount == 0) throw new NotFoundException("Không tìm thấy quán với id: " + restaurantId);
        return restaurantRepository.findByIdForMerchant(restaurantId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy quán với id: " + restaurantId));
    }

    }
