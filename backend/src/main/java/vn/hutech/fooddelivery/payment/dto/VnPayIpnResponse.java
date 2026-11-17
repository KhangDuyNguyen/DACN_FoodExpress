package vn.hutech.fooddelivery.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Tra loi IPN dung dinh dang VNPay yeu cau, vi du {"RspCode":"00","Message":"Confirm Success"}.
 * Ma va noi dung lay theo tai lieu VNPay.
 */
public record VnPayIpnResponse(
        @JsonProperty("RspCode") String rspCode,
        @JsonProperty("Message") String message
) {
    public static final VnPayIpnResponse CONFIRMED = new VnPayIpnResponse("00", "Confirm Success");
    public static final VnPayIpnResponse ORDER_NOT_FOUND = new VnPayIpnResponse("01", "Order not found");
    public static final VnPayIpnResponse ALREADY_CONFIRMED = new VnPayIpnResponse("02", "Order already confirmed");
    public static final VnPayIpnResponse INVALID_AMOUNT = new VnPayIpnResponse("04", "invalid amount");
    public static final VnPayIpnResponse INVALID_SIGNATURE = new VnPayIpnResponse("97", "Invalid signature");
    /** Chu "Unknow" giu y nhu mau cua VNPay. */
    public static final VnPayIpnResponse UNKNOWN_ERROR = new VnPayIpnResponse("99", "Unknow error");
}
