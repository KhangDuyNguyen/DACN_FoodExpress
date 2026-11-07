package vn.hutech.fooddelivery.user.dto;

/** 1 dia chi trong so dia chi cua nguoi dung. */
public record AddressResponse(
        long id,
        String label,
        String receiverName,
        String receiverPhone,
        String addressLine,
        double lat,
        double lng,
        boolean isDefault
) {
}
