package vn.hutech.fooddelivery.order.dto;

/** Quan cua don hang, hien tren trang chi tiet don. */
public record OrderRestaurantResponse(
        long id,
        String name,
        String phone,
        String address
) {
}
