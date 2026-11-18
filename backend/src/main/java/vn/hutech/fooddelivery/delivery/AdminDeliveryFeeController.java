package vn.hutech.fooddelivery.delivery;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.delivery.dto.ChangeRuleActiveRequest;
import vn.hutech.fooddelivery.delivery.dto.CreateDeliveryFeeRuleRequest;
import vn.hutech.fooddelivery.delivery.dto.DeliveryFeeRuleResponse;

import java.util.List;

/** Admin quan ly quy tac phi giao. /api/admin/** chi ADMIN goi duoc (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/delivery-fee-rules")
@Tag(name = "Admin - Phí giao hàng", description = "Quy tắc tính phí ship; đặt hàng dùng quy tắc đang bật mới nhất")
public class AdminDeliveryFeeController {

    private final DeliveryFeeRuleService service;

    public AdminDeliveryFeeController(DeliveryFeeRuleService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Danh sách quy tắc phí giao, mới nhất trước; applied = quy tắc đang áp dụng khi đặt hàng")
    public ResponseEntity<List<DeliveryFeeRuleResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PostMapping
    @Operation(summary = "Tạo quy tắc phí giao mới (đang bật); quy tắc mới nhất đang bật sẽ được áp dụng")
    public ResponseEntity<DeliveryFeeRuleResponse> create(@Valid @RequestBody CreateDeliveryFeeRuleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PatchMapping("/{id}/active")
    @Operation(summary = "Bật hoặc tắt 1 quy tắc; không tắt được quy tắc đang bật cuối cùng")
    public ResponseEntity<DeliveryFeeRuleResponse> changeActive(@PathVariable long id,
                                                                @Valid @RequestBody ChangeRuleActiveRequest req) {
        return ResponseEntity.ok(service.changeActive(id, req.active()));
    }
}
