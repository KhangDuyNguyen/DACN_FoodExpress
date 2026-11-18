package vn.hutech.fooddelivery.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cau hinh VNPay doc tu app.vnpay.* trong application.yml (gia tri that nam trong file .env, khong commit).
 * Chua dien tmnCode/hashSecret thi app van khoi dong binh thuong, chi rieng dat don VNPay bi bao loi.
 */
@ConfigurationProperties(prefix = "app.vnpay")
public record VnPayProperties(String tmnCode, String hashSecret, String payUrl, String apiUrl, String returnUrl) {

    /** Da dien VNPAY_TMN_CODE va VNPAY_HASH_SECRET chua. */
    public boolean isConfigured() {
        return tmnCode != null && !tmnCode.isBlank() && hashSecret != null && !hashSecret.isBlank();
    }

    /** Record tu sinh toString in ca khoa bi mat; viet lai de lo tay log doi tuong nay cung khong lo khoa. */
    @Override
    public String toString() {
        return "VnPayProperties[tmnCode=" + tmnCode + ", hashSecret=***, payUrl=" + payUrl
                + ", apiUrl=" + apiUrl + ", returnUrl=" + returnUrl + "]";
    }
}
