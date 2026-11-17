package vn.hutech.fooddelivery.order.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * Ket qua dat hang = chi tiet don + paymentUrl (link sang trang thanh toan VNPay; COD thi null).
 * {@code @JsonUnwrapped} dua cac truong cua don ra cung cap voi paymentUrl, nen JSON giong het truoc
 * va chi them 1 truong: web, app dang doc "id", "status"... khong phai sua.
 */
public record CreateOrderResponse(
        @JsonUnwrapped OrderDetailResponse order,
        String paymentUrl
) {
}
