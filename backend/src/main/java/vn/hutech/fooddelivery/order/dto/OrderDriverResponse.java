package vn.hutech.fooddelivery.order.dto;

/** Tai xe da nhan don (doc tu bang drivers, users cua module An, chi SELECT). */
public record OrderDriverResponse(
        String name,
        String phone,
        String vehicleType,
        String licensePlate
) {
}
