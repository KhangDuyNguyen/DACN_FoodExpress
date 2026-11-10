package vn.hutech.fooddelivery.restaurant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.common.ForbiddenException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.restaurant.dto.*;

import java.util.List;
import java.util.Map;

/**
 * Business logic cho merchant: quản lý quán, menu, tùy chọn.
 */
@Service
public class MerchantMenuService {

    private final MerchantMenuRepository repo;

    public MerchantMenuService(MerchantMenuRepository repo) {
        this.repo = repo;
    }

    // ── Helpers kiểm tra quyền ───────────────────────────────────────────────

    private void requireOwner(long restaurantId, long ownerUserId) {
        if (!repo.isOwner(restaurantId, ownerUserId))
            throw new ForbiddenException("Bạn không có quyền thao tác với quán này");
    }

    // ── Cập nhật thông tin quán ──────────────────────────────────────────────

    @Transactional
    public void updateRestaurant(long restaurantId, long ownerUserId, UpdateRestaurantRequest req) {
        requireOwner(restaurantId, ownerUserId);
        repo.updateRestaurant(restaurantId, req);
    }

    @Transactional
    public void updateHours(long restaurantId, long ownerUserId, UpdateHoursRequest req) {
        requireOwner(restaurantId, ownerUserId);
        repo.updateHours(restaurantId, req.hours());
    }

    @Transactional
    public void updateOpenStatus(long restaurantId, long ownerUserId, String openStatus) {
        requireOwner(restaurantId, ownerUserId);
        repo.updateOpenStatus(restaurantId, openStatus);
    }

    // ── CRUD categories ──────────────────────────────────────────────────────

    public List<MenuCategoryDTO> getCategories(long restaurantId, long ownerUserId) {
        requireOwner(restaurantId, ownerUserId);
        return repo.findCategoriesByRestaurantId(restaurantId);
    }

    @Transactional
    public long createCategory(long restaurantId, long ownerUserId, CategoryRequest req) {
        requireOwner(restaurantId, ownerUserId);
        return repo.createCategory(restaurantId, req);
    }

    @Transactional
    public void updateCategory(long restaurantId, long categoryId, long ownerUserId, CategoryRequest req) {
        requireOwner(restaurantId, ownerUserId);
        if (!repo.isCategoryOfRestaurant(categoryId, restaurantId))
            throw new NotFoundException("Không tìm thấy nhóm món này");
        repo.updateCategory(categoryId, req);
    }

    @Transactional
    public void deleteCategory(long restaurantId, long categoryId, long ownerUserId) {
        requireOwner(restaurantId, ownerUserId);
        if (!repo.isCategoryOfRestaurant(categoryId, restaurantId))
            throw new NotFoundException("Không tìm thấy nhóm món này");
        repo.deleteCategory(categoryId);
    }

    // ── CRUD menu items ──────────────────────────────────────────────────────

    public List<Map<String, Object>> getItems(long restaurantId, long ownerUserId) {
        requireOwner(restaurantId, ownerUserId);
        return repo.findItemsByRestaurantId(restaurantId);
    }

    @Transactional
    public long createItem(long restaurantId, long ownerUserId, MenuItemRequest req) {
        requireOwner(restaurantId, ownerUserId);
        // Kiểm tra category thuộc quán
        if (!repo.isCategoryOfRestaurant(req.categoryId(), restaurantId))
            throw new NotFoundException("Nhóm món không thuộc quán này");
        return repo.createItem(req);
    }

    @Transactional
    public void updateItem(long restaurantId, long itemId, long ownerUserId, MenuItemRequest req) {
        requireOwner(restaurantId, ownerUserId);
        if (!repo.isItemOfRestaurant(itemId, restaurantId))
            throw new NotFoundException("Không tìm thấy món này trong quán");
        if (!repo.isCategoryOfRestaurant(req.categoryId(), restaurantId))
            throw new NotFoundException("Nhóm món không thuộc quán này");
        repo.updateItem(itemId, req);
    }

    @Transactional
    public void deleteItem(long restaurantId, long itemId, long ownerUserId) {
        requireOwner(restaurantId, ownerUserId);
        if (!repo.isItemOfRestaurant(itemId, restaurantId))
            throw new NotFoundException("Không tìm thấy món này trong quán");
        repo.deleteItem(itemId);
    }

    @Transactional
    public void patchAvailability(long restaurantId, long itemId, long ownerUserId, boolean isAvailable) {
        requireOwner(restaurantId, ownerUserId);
        if (!repo.isItemOfRestaurant(itemId, restaurantId))
            throw new NotFoundException("Không tìm thấy món này trong quán");
        repo.patchAvailability(itemId, isAvailable);
    }

    // ── CRUD option groups ───────────────────────────────────────────────────

    public List<OptionGroupDTO> getOptionGroups(long restaurantId, long itemId, long ownerUserId) {
        requireOwner(restaurantId, ownerUserId);
        if (!repo.isItemOfRestaurant(itemId, restaurantId))
            throw new NotFoundException("Không tìm thấy món này trong quán");
        return repo.findOptionGroupsByItemId(itemId);
    }

    @Transactional
    public long createOptionGroup(long restaurantId, long itemId, long ownerUserId, OptionGroupRequest req) {
        requireOwner(restaurantId, ownerUserId);
        if (!repo.isItemOfRestaurant(itemId, restaurantId))
            throw new NotFoundException("Không tìm thấy món này trong quán");
        return repo.createOptionGroup(itemId, req);
    }

    @Transactional
    public void updateOptionGroup(long restaurantId, long itemId, long groupId, long ownerUserId, OptionGroupRequest req) {
        requireOwner(restaurantId, ownerUserId);
        if (!repo.isGroupOfItem(groupId, itemId))
            throw new NotFoundException("Không tìm thấy nhóm tùy chọn này");
        repo.updateOptionGroup(groupId, req);
    }

    @Transactional
    public void deleteOptionGroup(long restaurantId, long itemId, long groupId, long ownerUserId) {
        requireOwner(restaurantId, ownerUserId);
        if (!repo.isGroupOfItem(groupId, itemId))
            throw new NotFoundException("Không tìm thấy nhóm tùy chọn này");
        repo.deleteOptionGroup(groupId);
    }

    // ── CRUD options ─────────────────────────────────────────────────────────

    @Transactional
    public long createOption(long restaurantId, long itemId, long groupId, long ownerUserId, OptionRequest req) {
        requireOwner(restaurantId, ownerUserId);
        if (!repo.isGroupOfItem(groupId, itemId))
            throw new NotFoundException("Không tìm thấy nhóm tùy chọn này");
        return repo.createOption(groupId, req);
    }

    @Transactional
    public void updateOption(long restaurantId, long itemId, long groupId, long optionId, long ownerUserId, OptionRequest req) {
        requireOwner(restaurantId, ownerUserId);
        if (!repo.isOptionOfGroup(optionId, groupId))
            throw new NotFoundException("Không tìm thấy tùy chọn này");
        repo.updateOption(optionId, req);
    }

    @Transactional
    public void deleteOption(long restaurantId, long itemId, long groupId, long optionId, long ownerUserId) {
        requireOwner(restaurantId, ownerUserId);
        if (!repo.isOptionOfGroup(optionId, groupId))
            throw new NotFoundException("Không tìm thấy tùy chọn này");
        repo.deleteOption(optionId);
    }

    /** Bật/tắt món không cần biết restaurantId (controller PATCH /api/merchant/items/{id}/availability). */
    @Transactional
    public void patchAvailabilityByItem(long itemId, long ownerUserId, boolean isAvailable) {
        long restaurantId = repo.findRestaurantIdByItemId(itemId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy món"));
        requireOwner(restaurantId, ownerUserId);
        repo.patchAvailability(itemId, isAvailable);
    }


    // ── Tạo chi nhánh mới ────────────────────────────────────────────────────

    @Transactional
    public long createRestaurant(long ownerUserId, CreateRestaurantRequest req) {
        long merchantId = repo.findMerchantIdByUserId(ownerUserId)
                .orElseThrow(() -> new vn.hutech.fooddelivery.common.NotFoundException(
                        "Tài khoản chưa có hồ sơ merchant. Liên hệ admin để được duyệt."));
        return repo.createRestaurant(merchantId, req);
    }

}
