package vn.hutech.fooddelivery.cart;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Unit test luat chon tuy chon, khong can database. */
class OptionRulesTest {

    // Tra sua: nhom "Size" bat buoc chon dung 1; nhom "Topping" khong bat buoc, toi da 2 ("Pudding" da het)
    private static final List<MenuOptionInfo> TRA_SUA = List.of(
            new MenuOptionInfo(1, "Vừa", true, 10, "Size", true, 1, 1),
            new MenuOptionInfo(2, "Lớn", true, 10, "Size", true, 1, 1),
            new MenuOptionInfo(3, "Trân châu", true, 20, "Topping", false, 0, 2),
            new MenuOptionInfo(4, "Thạch", true, 20, "Topping", false, 0, 2),
            new MenuOptionInfo(5, "Pudding", false, 20, "Topping", false, 0, 2));

    private static Optional<String> check(Long... chosenIds) {
        return OptionRules.findProblem(TRA_SUA, List.of(chosenIds));
    }

    @Test
    void validChoicePasses() {
        assertEquals(Optional.empty(), check(2L, 3L, 4L));
    }

    @Test
    void optionalGroupMayBeSkipped() {
        assertEquals(Optional.empty(), check(1L));
    }

    @Test
    void requiredGroupMustBeChosen() {
        assertEquals(Optional.of("Vui lòng chọn ít nhất 1 mục ở nhóm \"Size\""), check(3L));
        assertEquals(Optional.of("Vui lòng chọn ít nhất 1 mục ở nhóm \"Size\""), check());
    }

    @Test
    void requiredGroupUsesMinSelectWhenLargerThanOne() {
        List<MenuOptionInfo> combo = List.of(
                new MenuOptionInfo(1, "Gà", true, 10, "Món phụ", true, 2, 3),
                new MenuOptionInfo(2, "Khoai", true, 10, "Món phụ", true, 2, 3),
                new MenuOptionInfo(3, "Salad", true, 10, "Món phụ", true, 2, 3));

        assertEquals(Optional.of("Vui lòng chọn ít nhất 2 mục ở nhóm \"Món phụ\""),
                OptionRules.findProblem(combo, List.of(1L)));
        assertEquals(Optional.empty(), OptionRules.findProblem(combo, List.of(1L, 3L)));
    }

    @Test
    void cannotChooseMoreThanMaxSelect() {
        assertEquals(Optional.of("Nhóm \"Size\" chọn tối đa 1 mục"), check(1L, 2L));
    }

    @Test
    void optionOfAnotherItemIsRejected() {
        assertEquals(Optional.of("Tùy chọn #99 không thuộc món này"), check(1L, 99L));
        // Mon khong co tuy chon nao thi chi nhan danh sach rong
        assertEquals(Optional.empty(), OptionRules.findProblem(List.of(), List.of()));
        assertEquals(Optional.of("Tùy chọn #1 không thuộc món này"), OptionRules.findProblem(List.of(), List.of(1L)));
    }

    @Test
    void soldOutOptionIsRejected() {
        assertEquals(Optional.of("Tùy chọn \"Pudding\" đã hết"), check(1L, 5L));
    }

    @Test
    void sameOptionTwiceIsRejected() {
        assertEquals(Optional.of("Mỗi tùy chọn chỉ chọn 1 lần"), check(1L, 3L, 3L));
    }
}
