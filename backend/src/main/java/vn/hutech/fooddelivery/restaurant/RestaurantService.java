package vn.hutech.fooddelivery.restaurant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.common.Haversine;
import vn.hutech.fooddelivery.restaurant.dto.RestaurantSummaryDTO;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service xử lý business logic cho nhà hàng (restaurants).
 */
@Service
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;

    public RestaurantService(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    /**
     * Tìm kiếm nhà hàng với các filter tùy chọn.
     * <p>
     * Nếu userLat và userLng được cung cấp, tính khoảng cách từ vị trí người dùng đến mỗi nhà hàng
     * bằng công thức Haversine và sắp xếp theo khoảng cách tăng dần (gần nhất lên đầu).
     * Nếu không có tọa độ, sắp xếp theo avgRating giảm dần (mặc định từ repository).
     * </p>
     * <p>
     * Lọc theo cuisineId: Nhà hàng có thể có nhiều loại món (qua bảng restaurant_cuisines).
     * Nếu cuisineId được cung cấp, chỉ trả về nhà hàng có loại món đó (filter ở repository với EXISTS).
     * </p>
     *
     * @param keyword   từ khóa tìm kiếm (nullable)
     * @param cuisineId ID loại món ăn (nullable)
     * @param userLat   vĩ độ người dùng (nullable)
     * @param userLng   kinh độ người dùng (nullable)
     * @return danh sách nhà hàng đã lọc và sắp xếp
     */
    @Transactional(readOnly = true)
    public List<RestaurantSummaryDTO> searchRestaurants(String keyword, Integer cuisineId,
                                                         Double userLat, Double userLng) {
        List<RestaurantSummaryDTO> restaurants = restaurantRepository.search(keyword, cuisineId);

        // Nếu có tọa độ người dùng, sắp xếp theo khoảng cách
        if (userLat != null && userLng != null) {
            // Tính khoảng cách cho mỗi nhà hàng và lưu vào map
            Map<Long, Double> distanceMap = new HashMap<>();
            for (RestaurantSummaryDTO restaurant : restaurants) {
                double distance = Haversine.distanceKm(userLat, userLng,
                        restaurant.lat(), restaurant.lng());
                distanceMap.put(restaurant.id(), distance);
            }

            // Sắp xếp danh sách theo khoảng cách tăng dần
            restaurants.sort(Comparator.comparingDouble(r -> distanceMap.get(r.id())));
        }

        return restaurants;
    }
}
