package vn.hutech.fooddelivery.restaurant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.restaurant.dto.CuisineDTO;

import java.util.List;

/**
 * Service xử lý business logic cho loại món ăn (cuisines).
 */
@Service
public class CuisineService {

    private final CuisineRepository cuisineRepository;

    public CuisineService(CuisineRepository cuisineRepository) {
        this.cuisineRepository = cuisineRepository;
    }

    /**
     * Lấy danh sách tất cả loại món ăn.
     */
    @Transactional(readOnly = true)
    public List<CuisineDTO> getAllCuisines() {
        return cuisineRepository.findAll();
    }
}
