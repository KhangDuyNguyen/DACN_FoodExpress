package vn.hutech.fooddelivery.restaurant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.hutech.fooddelivery.restaurant.dto.*;
import vn.hutech.fooddelivery.security.AuthUser;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Dashboard merchant: quản lý quán, menu, tùy chọn.
 * Tất cả endpoint đều yêu cầu role MERCHANT (SecurityConfig).
 */
@RestController
@Tag(name = "Merchant - Quản lý Menu", description = "CRUD quán, nhóm món, món, tùy chọn")
public class MerchantMenuController {

    private final MerchantMenuService service;

    public MerchantMenuController(MerchantMenuService service) {
        this.service = service;
    }

    // ── Cập nhật thông tin quán ──────────────────────────────────────────────

    @PutMapping("/api/merchant/restaurants/{id}")
    @Operation(summary = "Cập nhật thông tin quán")
    public ResponseEntity<Void> updateRestaurant(
            @PathVariable long id,
            @Valid @RequestBody UpdateRestaurantRequest req,
            @AuthenticationPrincipal AuthUser user) {
        service.updateRestaurant(id, user.id(), req);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/merchant/restaurants/{id}/hours")
    @Operation(summary = "Cập nhật giờ mở cửa")
    public ResponseEntity<Void> updateHours(
            @PathVariable long id,
            @Valid @RequestBody UpdateHoursRequest req,
            @AuthenticationPrincipal AuthUser user) {
        service.updateHours(id, user.id(), req);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/merchant/restaurants/{id}/open-status")
    @Operation(summary = "Mở hoặc đóng quán")
    public ResponseEntity<Void> updateOpenStatus(
            @PathVariable long id,
            @Valid @RequestBody OpenStatusRequest req,
            @AuthenticationPrincipal AuthUser user) {
        service.updateOpenStatus(id, user.id(), req.openStatus());
        return ResponseEntity.noContent().build();
    }

    // ── CRUD categories ──────────────────────────────────────────────────────

    @GetMapping("/api/merchant/restaurants/{id}/categories")
    @Operation(summary = "Danh sách nhóm món của quán")
    public ResponseEntity<List<MenuCategoryDTO>> getCategories(
            @PathVariable long id,
            @AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(service.getCategories(id, user.id()));
    }

    @PostMapping("/api/merchant/restaurants/{id}/categories")
    @Operation(summary = "Thêm nhóm món mới")
    public ResponseEntity<Map<String, Long>> createCategory(
            @PathVariable long id,
            @Valid @RequestBody CategoryRequest req,
            @AuthenticationPrincipal AuthUser user) {
        long newId = service.createCategory(id, user.id(), req);
        return ResponseEntity.created(URI.create("/api/merchant/restaurants/" + id + "/categories/" + newId))
                .body(Map.of("id", newId));
    }

    @PutMapping("/api/merchant/restaurants/{id}/categories/{categoryId}")
    @Operation(summary = "Sửa nhóm món")
    public ResponseEntity<Void> updateCategory(
            @PathVariable long id,
            @PathVariable long categoryId,
            @Valid @RequestBody CategoryRequest req,
            @AuthenticationPrincipal AuthUser user) {
        service.updateCategory(id, categoryId, user.id(), req);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/merchant/restaurants/{id}/categories/{categoryId}")
    @Operation(summary = "Xóa nhóm món (xóa luôn các món trong nhóm)")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable long id,
            @PathVariable long categoryId,
            @AuthenticationPrincipal AuthUser user) {
        service.deleteCategory(id, categoryId, user.id());
        return ResponseEntity.noContent().build();
    }

    // ── CRUD menu items ──────────────────────────────────────────────────────

    @GetMapping("/api/merchant/restaurants/{id}/items")
    @Operation(summary = "Danh sách tất cả món của quán (dùng cho quản lý)")
    public ResponseEntity<List<Map<String, Object>>> getItems(
            @PathVariable long id,
            @AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(service.getItems(id, user.id()));
    }

    @PostMapping("/api/merchant/restaurants/{id}/items")
    @Operation(summary = "Thêm món ăn mới")
    public ResponseEntity<Map<String, Long>> createItem(
            @PathVariable long id,
            @Valid @RequestBody MenuItemRequest req,
            @AuthenticationPrincipal AuthUser user) {
        long newId = service.createItem(id, user.id(), req);
        return ResponseEntity.created(URI.create("/api/merchant/restaurants/" + id + "/items/" + newId))
                .body(Map.of("id", newId));
    }

    @PutMapping("/api/merchant/restaurants/{id}/items/{itemId}")
    @Operation(summary = "Sửa thông tin món ăn")
    public ResponseEntity<Void> updateItem(
            @PathVariable long id,
            @PathVariable long itemId,
            @Valid @RequestBody MenuItemRequest req,
            @AuthenticationPrincipal AuthUser user) {
        service.updateItem(id, itemId, user.id(), req);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/merchant/restaurants/{id}/items/{itemId}")
    @Operation(summary = "Xóa món ăn")
    public ResponseEntity<Void> deleteItem(
            @PathVariable long id,
            @PathVariable long itemId,
            @AuthenticationPrincipal AuthUser user) {
        service.deleteItem(id, itemId, user.id());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/merchant/items/{itemId}/availability")
    @Operation(summary = "Bật/tắt còn món (không cần biết restaurant id)")
    public ResponseEntity<Void> patchAvailability(
            @PathVariable long itemId,
            @RequestBody AvailabilityRequest req,
            @AuthenticationPrincipal AuthUser user) {
        // Tìm restaurantId từ itemId để kiểm tra quyền
        service.patchAvailabilityByItem(itemId, user.id(), req.isAvailable());
        return ResponseEntity.noContent().build();
    }

    // ── CRUD option groups ───────────────────────────────────────────────────

    @GetMapping("/api/merchant/restaurants/{id}/items/{itemId}/option-groups")
    @Operation(summary = "Danh sách nhóm tùy chọn của món")
    public ResponseEntity<List<OptionGroupDTO>> getOptionGroups(
            @PathVariable long id,
            @PathVariable long itemId,
            @AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(service.getOptionGroups(id, itemId, user.id()));
    }

    @PostMapping("/api/merchant/restaurants/{id}/items/{itemId}/option-groups")
    @Operation(summary = "Thêm nhóm tùy chọn cho món")
    public ResponseEntity<Map<String, Long>> createOptionGroup(
            @PathVariable long id,
            @PathVariable long itemId,
            @Valid @RequestBody OptionGroupRequest req,
            @AuthenticationPrincipal AuthUser user) {
        long newId = service.createOptionGroup(id, itemId, user.id(), req);
        return ResponseEntity.status(201).body(Map.of("id", newId));
    }

    @PutMapping("/api/merchant/restaurants/{id}/items/{itemId}/option-groups/{groupId}")
    @Operation(summary = "Sửa nhóm tùy chọn")
    public ResponseEntity<Void> updateOptionGroup(
            @PathVariable long id,
            @PathVariable long itemId,
            @PathVariable long groupId,
            @Valid @RequestBody OptionGroupRequest req,
            @AuthenticationPrincipal AuthUser user) {
        service.updateOptionGroup(id, itemId, groupId, user.id(), req);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/merchant/restaurants/{id}/items/{itemId}/option-groups/{groupId}")
    @Operation(summary = "Xóa nhóm tùy chọn (xóa luôn các tùy chọn bên trong)")
    public ResponseEntity<Void> deleteOptionGroup(
            @PathVariable long id,
            @PathVariable long itemId,
            @PathVariable long groupId,
            @AuthenticationPrincipal AuthUser user) {
        service.deleteOptionGroup(id, itemId, groupId, user.id());
        return ResponseEntity.noContent().build();
    }

    // ── CRUD options ─────────────────────────────────────────────────────────

    @PostMapping("/api/merchant/restaurants/{id}/items/{itemId}/option-groups/{groupId}/options")
    @Operation(summary = "Thêm tùy chọn vào nhóm")
    public ResponseEntity<Map<String, Long>> createOption(
            @PathVariable long id,
            @PathVariable long itemId,
            @PathVariable long groupId,
            @Valid @RequestBody OptionRequest req,
            @AuthenticationPrincipal AuthUser user) {
        long newId = service.createOption(id, itemId, groupId, user.id(), req);
        return ResponseEntity.status(201).body(Map.of("id", newId));
    }

    @PutMapping("/api/merchant/restaurants/{id}/items/{itemId}/option-groups/{groupId}/options/{optionId}")
    @Operation(summary = "Sửa tùy chọn")
    public ResponseEntity<Void> updateOption(
            @PathVariable long id,
            @PathVariable long itemId,
            @PathVariable long groupId,
            @PathVariable long optionId,
            @Valid @RequestBody OptionRequest req,
            @AuthenticationPrincipal AuthUser user) {
        service.updateOption(id, itemId, groupId, optionId, user.id(), req);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/merchant/restaurants/{id}/items/{itemId}/option-groups/{groupId}/options/{optionId}")
    @Operation(summary = "Xóa tùy chọn")
    public ResponseEntity<Void> deleteOption(
            @PathVariable long id,
            @PathVariable long itemId,
            @PathVariable long groupId,
            @PathVariable long optionId,
            @AuthenticationPrincipal AuthUser user) {
        service.deleteOption(id, itemId, groupId, optionId, user.id());
        return ResponseEntity.noContent().build();
    }
}
