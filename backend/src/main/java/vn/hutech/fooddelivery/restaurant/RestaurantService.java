package vn.hutech.fooddelivery.restaurant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.common.Haversine;
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
     * Tìm kiếm nhà hàng với filter tùy chọn.
     * Nếu có lat/lng thì sort theo khoảng cách, không thì sort theo avg_rating.
     */
    @Transactional(readOnly = true)
    public List<RestaurantSummaryDTO> searchRestaurants(String keyword, Integer cuisineId,
                                                         Double userLat, Double userLng) {
        List<RestaurantSummaryDTO> restaurants = restaurantRepository.search(keyword, cuisineId);

        if (userLat != null && userLng != null) {
            Map<Long, Double> distanceMap = new HashMap<>();
            for (RestaurantSummaryDTO r : restaurants) {
                double dist = Haversine.distanceKm(userLat, userLng, r.lat(), r.lng());
                distanceMap.put(r.id(), dist);
            }
            restaurants.sort(Comparator.comparingDouble(r -> distanceMap.get(r.id())));
        }

        return restaurants;
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
}
