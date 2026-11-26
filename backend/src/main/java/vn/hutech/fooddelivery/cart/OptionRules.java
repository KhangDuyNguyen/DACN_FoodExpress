package vn.hutech.fooddelivery.cart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Luat chon tuy chon (size, topping...) cua 1 mon. Chi tinh toan, khong doc database,
 * nen test rieng duoc bang unit test (OptionRulesTest).
 * Dung o 2 cho: luc them vao gio (sai -> 400) va luc dat hang (menu co the da doi sau khi them -> 409).
 */
public final class OptionRules {

    private OptionRules() {
    }

    /**
     * Kiem tra bo tuy chon khach chon cho 1 mon.
     *
     * @param itemOptions moi tuy chon cua mon kem luat nhom, doc tu database
     * @param chosenIds   id cac tuy chon khach chon
     * @return ly do khong hop le (tieng Viet), hoac rong neu hop le
     */
    public static Optional<String> findProblem(List<MenuOptionInfo> itemOptions, List<Long> chosenIds) {
        Set<Long> chosen = new HashSet<>(chosenIds);
        if (chosen.size() != chosenIds.size()) {
            return Optional.of("Mỗi tùy chọn chỉ chọn 1 lần");
        }

        // 1. Tuy chon phai thuoc dung mon nay va con ban
        Map<Long, MenuOptionInfo> optionById = new HashMap<>();
        for (MenuOptionInfo option : itemOptions) {
            optionById.put(option.id(), option);
        }
        for (Long id : chosenIds) {
            MenuOptionInfo option = optionById.get(id);
            if (option == null) {
                return Optional.of("Tùy chọn #" + id + " không thuộc món này");
            }
            if (!option.available()) {
                return Optional.of("Tùy chọn \"" + option.name() + "\" đã hết");
            }
        }

        // 2. Gom tuy chon theo nhom, dem so muc da chon trong moi nhom roi so voi luat cua nhom
        Map<Long, List<MenuOptionInfo>> groups = new LinkedHashMap<>();
        for (MenuOptionInfo option : itemOptions) {
            groups.computeIfAbsent(option.groupId(), k -> new ArrayList<>()).add(option);
        }
        for (List<MenuOptionInfo> group : groups.values()) {
            MenuOptionInfo rule = group.get(0); // cac tuy chon cung nhom co chung luat nhom
            long count = group.stream().filter(option -> chosen.contains(option.id())).count();
            // Nhom bat buoc: chon it nhat 1 (hoac min_select neu lon hon). Nhom khong bat buoc: bo qua duoc.
            int min = rule.groupRequired() ? Math.max(rule.minSelect(), 1) : 0;
            if (count < min) {
                return Optional.of("Vui lòng chọn ít nhất " + min + " mục ở nhóm \"" + rule.groupName() + "\"");
            }
            if (count > rule.maxSelect()) {
                return Optional.of("Nhóm \"" + rule.groupName() + "\" chọn tối đa " + rule.maxSelect() + " mục");
            }
        }
        return Optional.empty();
    }
}
