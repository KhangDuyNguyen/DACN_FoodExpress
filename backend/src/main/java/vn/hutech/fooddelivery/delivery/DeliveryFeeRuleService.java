package vn.hutech.fooddelivery.delivery;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.common.ConflictException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.delivery.dto.CreateDeliveryFeeRuleRequest;
import vn.hutech.fooddelivery.delivery.dto.DeliveryFeeRuleResponse;

import java.util.List;

/**
 * Admin quan ly quy tac phi giao. Dat hang luon dung quy tac DANG BAT MOI NHAT (id lon nhat),
 * nen tao quy tac moi = doi phi ship tu luc do; don cu khong doi vi tien da luu trong don.
 */
@Service
public class DeliveryFeeRuleService {

    private final DeliveryFeeRuleRepository repository;

    public DeliveryFeeRuleService(DeliveryFeeRuleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DeliveryFeeRuleResponse> list() {
        List<DeliveryFeeRuleRow> rules = repository.findAll(); // moi nhat truoc
        // Quy tac dang ap dung = quy tac dang bat dau tien trong danh sach (moi nhat)
        Long appliedId = rules.stream().filter(DeliveryFeeRuleRow::active).map(DeliveryFeeRuleRow::id)
                .findFirst().orElse(null);
        return rules.stream().map(rule -> toResponse(rule, appliedId)).toList();
    }

    /** Tao quy tac moi (dang bat). Vi moi nhat nen no thanh quy tac dang ap dung. */
    @Transactional
    public DeliveryFeeRuleResponse create(CreateDeliveryFeeRuleRequest req) {
        long id = repository.insert(req.name().trim(), req.baseFee(), req.baseDistanceKm(), req.perKmFee());
        return findResponse(id);
    }

    /** Bat / tat 1 quy tac. Khong tat duoc quy tac dang bat cuoi cung: dat hang can it nhat 1 quy tac. */
    @Transactional
    public DeliveryFeeRuleResponse changeActive(long id, boolean active) {
        DeliveryFeeRuleRow rule = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy quy tắc phí giao"));
        if (!active && rule.active()) {
            // Khoa cac quy tac dang bat roi moi dem, de 2 admin tat cung luc khong lam het quy tac
            List<Long> activeIds = repository.lockActiveIds();
            if (activeIds.size() <= 1) {
                throw new ConflictException("Phải còn ít nhất 1 quy tắc phí giao đang bật");
            }
        }
        repository.updateActive(id, active);
        return findResponse(id);
    }

    private DeliveryFeeRuleResponse findResponse(long id) {
        return list().stream().filter(rule -> rule.id() == id).findFirst()
                .orElseThrow(() -> new NotFoundException("Không tìm thấy quy tắc phí giao"));
    }

    private static DeliveryFeeRuleResponse toResponse(DeliveryFeeRuleRow rule, Long appliedId) {
        return new DeliveryFeeRuleResponse(rule.id(), rule.name(), rule.baseFee(), rule.baseDistanceKm(),
                rule.perKmFee(), rule.active(), appliedId != null && appliedId == rule.id(), rule.createdAt());
    }
}
