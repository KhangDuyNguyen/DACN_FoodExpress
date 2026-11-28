package vn.hutech.fooddelivery.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.hutech.fooddelivery.assignment.AssignmentService;
import vn.hutech.fooddelivery.notification.NotificationRealtimeChannel;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thanh toan VNPay (Module I): dat don VNPay, IPN, trang quay ve.
 * Dung khoa gia (khong phai khoa sandbox that). Hai phan ben ngoai duoc thay bang mock:
 *   - VnPayClient: goi API querydr cua VNPay (test khong goi Internet);
 *   - AssignmentService (cua An): de kiem tra co/khong moi tai xe.
 * Can Docker Desktop dang chay.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=vnpay_payment_test_secret_dai_hon_32_ky_tu",
        "app.admin.phone=",
        "app.admin.password=",
        "app.vnpay.tmn-code=" + VnPayPaymentIntegrationTest.TMN_CODE,
        "app.vnpay.hash-secret=" + VnPayPaymentIntegrationTest.SECRET,
        "app.vnpay.pay-url=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
        "app.vnpay.return-url=http://localhost:5173/payment/vnpay-return"
})
@AutoConfigureMockMvc
@Testcontainers
class VnPayPaymentIntegrationTest {

    static final String TMN_CODE = "TESTTMN1";
    static final String SECRET = "khoa_gia_cho_test_khong_phai_khoa_that";
    private static final DateTimeFormatter VNPAY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @MockitoBean
    AssignmentService assignmentService;

    @MockitoBean
    VnPayClient vnPayClient;

    @MockitoSpyBean
    SimpMessagingTemplate realtime;

    // ----- Dat don -----

    @Test
    void vnpayOrderWaitsForPaymentAndGetsSignedPaymentUrl() throws Exception {
        String customer = login("0911000001");
        JsonNode order = placeOrder(customer, "VNPAY");
        long orderId = order.get("id").asLong();
        String code = order.get("code").asText();
        BigDecimal total = new BigDecimal(order.get("totalAmount").asText());

        // Don cho thanh toan, tra ve kem link sang VNPay
        assertEquals("PENDING_PAYMENT", order.get("status").asText());
        assertEquals("VNPAY", order.get("paymentMethod").asText());
        String paymentUrl = order.get("paymentUrl").asText();
        assertTrue(paymentUrl.startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"), paymentUrl);

        // Du tham so theo tai lieu; so tien nhan 100; han thanh toan = luc tao + 15 phut
        Map<String, String> p = queryParams(paymentUrl);
        assertEquals("2.1.0", p.get("vnp_Version"));
        assertEquals("pay", p.get("vnp_Command"));
        assertEquals(TMN_CODE, p.get("vnp_TmnCode"));
        assertEquals(total.multiply(BigDecimal.valueOf(100)).toBigInteger().toString(), p.get("vnp_Amount"));
        assertEquals("VND", p.get("vnp_CurrCode"));
        assertEquals(code, p.get("vnp_TxnRef"));
        assertEquals("Thanh toan don hang " + code, p.get("vnp_OrderInfo"));
        assertEquals("other", p.get("vnp_OrderType"));
        assertEquals("vn", p.get("vnp_Locale"));
        assertEquals("http://localhost:5173/payment/vnpay-return", p.get("vnp_ReturnUrl"));
        assertEquals("127.0.0.1", p.get("vnp_IpAddr"));
        LocalDateTime createDate = LocalDateTime.parse(p.get("vnp_CreateDate"), VNPAY_TIME);
        assertEquals(createDate.plusMinutes(15), LocalDateTime.parse(p.get("vnp_ExpireDate"), VNPAY_TIME));
        // Chu ky kiem lai duoc bang dung khoa
        assertTrue(VnPaySigner.isValid(p, SECRET));

        // Giao dich PENDING dung so tien, ma tham chieu = ma don, thoi diem = vnp_CreateDate (querydr can lai)
        Map<String, Object> payment = jdbc.queryForMap("""
                SELECT method, amount, status, transaction_ref, created_at FROM payments WHERE order_id = :id
                """, Map.of("id", orderId));
        assertEquals("VNPAY", payment.get("method"));
        assertEquals("PENDING", payment.get("status"));
        assertEquals(code, payment.get("transaction_ref"));
        assertEquals(0, total.compareTo((BigDecimal) payment.get("amount")));
        assertEquals(createDate, ((LocalDateTime) payment.get("created_at")));

        // Chua tra tien: quan chua thay don, chua moi tai xe. Gio da chuyen thanh don nen xoa
        verify(assignmentService, never()).createOffer(anyLong());
        assertFalse(merchantSeesOrder(orderId));
        mockMvc.perform(get("/api/merchant/orders/{id}", orderId).header("Authorization", login("0922000001")))
                .andExpect(status().isNotFound());
        assertEquals(0, cartItemCount("0911000001"));

        // Khach van thay don cua minh, loc duoc theo "Cho thanh toan"
        mockMvc.perform(get("/api/orders").param("status", "PENDING_PAYMENT").header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(orderId))
                .andExpect(jsonPath("$.items[0].status").value("PENDING_PAYMENT"));
    }

    @Test
    void codOrderIsUnchangedAndHasNoPaymentUrl() throws Exception {
        String customer = login("0911000001");
        String body = prepareCartAndOrderBody(customer, "COD");
        mockMvc.perform(post("/api/orders").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.paymentMethod").value("COD"))
                .andExpect(jsonPath("$.paymentUrl").value(nullValue()));
    }

    @Test
    void unsupportedPaymentMethodIsRejected() throws Exception {
        String customer = login("0911000001");
        String body = prepareCartAndOrderBody(customer, "MOMO");
        mockMvc.perform(post("/api/orders").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ----- IPN (may chu VNPay goi may chu minh) -----

    @Test
    void successfulIpnPlacesOrderExactlyOnce() throws Exception {
        JsonNode order = placeOrder(login("0911000001"), "VNPAY");
        long orderId = order.get("id").asLong();
        assertEquals(0, notificationRecipients(orderId, "placed").size());
        String code = order.get("code").asText();
        String amount = vnpAmount(order);

        callIpn(vnpayResult(code, amount, "00", "00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"))
                .andExpect(jsonPath("$.Message").value("Confirm Success"));

        // Don sang "Cho xac nhan", giao dich thanh cong, co luu du lieu VNPay gui (khong luu chu ky)
        assertEquals("PLACED", orderStatus(orderId));
        Map<String, Object> payment = paymentOf(orderId);
        assertEquals("SUCCESS", payment.get("status"));
        assertNotNull(payment.get("paid_at"));
        String payload = String.valueOf(payment.get("gateway_payload"));
        assertTrue(payload.contains("14226112"), payload);
        assertFalse(payload.contains("vnp_SecureHash"), payload);
        assertEquals(1, historyCount(orderId, "PLACED"));

        // Bao quan va moi tai xe nhu don COD
        verify(assignmentService).createOffer(orderId);
        assertTrue(merchantSeesOrder(orderId));

        // VNPay goi lai (toi da 10 lan) -> 02, khong xu ly lan 2
        clearInvocations(assignmentService);
        callIpn(vnpayResult(code, amount, "00", "00"))
                .andExpect(jsonPath("$.RspCode").value("02"))
                .andExpect(jsonPath("$.Message").value("Order already confirmed"));
        verify(assignmentService, never()).createOffer(anyLong());
        assertEquals(1, historyCount(orderId, "PLACED"));
        assertEquals(jdbc.queryForList("""
                SELECT o.customer_id FROM orders o WHERE o.id = :id
                UNION ALL
                SELECT m.owner_user_id FROM orders o JOIN restaurants r ON r.id = o.restaurant_id
                JOIN merchants m ON m.id = r.merchant_id WHERE o.id = :id
                ORDER BY 1
                """, Map.of("id", orderId), Long.class), notificationRecipients(orderId, "placed"));
        for (var entry : jdbc.queryForList("""
                SELECT user_id, id FROM notifications WHERE event_key = :key
                """, Map.of("key", "order:" + orderId + ":placed"))) {
            long recipient = ((Number) entry.get("user_id")).longValue();
            long notificationId = ((Number) entry.get("id")).longValue();
            verify(realtime).convertAndSendToUser(eq(NotificationRealtimeChannel.principalName(recipient)),
                    eq(NotificationRealtimeChannel.QUEUE), argThat(signal -> {
                        JsonNode data = objectMapper.valueToTree(signal);
                        return data.path("notificationId").asLong() == notificationId
                                && "ORDER_PLACED".equals(data.path("eventType").asText());
                    }));
        }
    }

    @Test
    void ipnRejectsBadSignatureUnknownOrderAndWrongAmount() throws Exception {
        JsonNode order = placeOrder(login("0911000001"), "VNPAY");
        long orderId = order.get("id").asLong();
        String code = order.get("code").asText();
        String amount = vnpAmount(order);

        // Sua so tien SAU khi ky -> chu ky sai
        Map<String, String> tampered = vnpayResult(code, amount, "00", "00");
        tampered.put("vnp_Amount", "100");
        callIpn(tampered)
                .andExpect(jsonPath("$.RspCode").value("97"))
                .andExpect(jsonPath("$.Message").value("Invalid signature"));

        callIpn(vnpayResult("FD000000000000", amount, "00", "00"))
                .andExpect(jsonPath("$.RspCode").value("01"))
                .andExpect(jsonPath("$.Message").value("Order not found"));

        // Ky dung nhung so tien khac don
        callIpn(vnpayResult(code, "100", "00", "00"))
                .andExpect(jsonPath("$.RspCode").value("04"))
                .andExpect(jsonPath("$.Message").value("invalid amount"));

        assertEquals("PENDING_PAYMENT", orderStatus(orderId));
        assertEquals("PENDING", paymentOf(orderId).get("status"));
        verify(assignmentService, never()).createOffer(anyLong());
    }

    @Test
    void ipnForCancelledPaymentCancelsOrder() throws Exception {
        JsonNode order = placeOrder(login("0911000001"), "VNPAY");
        long orderId = order.get("id").asLong();

        // 24 = khach bam huy o trang VNPay
        callIpn(vnpayResult(order.get("code").asText(), vnpAmount(order), "24", "02"))
                .andExpect(jsonPath("$.RspCode").value("00"));

        assertEquals("CANCELLED", orderStatus(orderId));
        assertEquals("Thanh toán VNPay không thành công (mã 24)", jdbc.queryForObject(
                "SELECT cancel_reason FROM orders WHERE id = :id", Map.of("id", orderId), String.class));
        Map<String, Object> payment = paymentOf(orderId);
        assertEquals("FAILED", payment.get("status"));
        assertNull(payment.get("paid_at"));
        verify(assignmentService, never()).createOffer(anyLong());
        assertFalse(merchantSeesOrder(orderId));
        assertEquals(List.of(jdbc.queryForObject("SELECT customer_id FROM orders WHERE id = :id",
                Map.of("id", orderId), Long.class)), notificationRecipients(orderId, "cancelled"));
        assertEquals(List.of(), notificationRecipients(orderId, "placed"));
    }

    // ----- Trang quay ve (trinh duyet cua khach) -----

    @Test
    void returnPageAsksVnPayWhileOrderStillWaitsThenStopsAsking() throws Exception {
        JsonNode order = placeOrder(login("0911000001"), "VNPAY");
        long orderId = order.get("id").asLong();
        String code = order.get("code").asText();
        String amount = vnpAmount(order);
        LocalDateTime createDate = LocalDateTime.parse(
                queryParams(order.get("paymentUrl").asText()).get("vnp_CreateDate"), VNPAY_TIME);
        when(vnPayClient.queryTransaction(eq(code), any())).thenReturn(new VnPayClient.QueryResult(
                "00", "00", amount, Map.of("vnp_TransactionNo", "14226112", "vnp_TransactionStatus", "00")));

        callReturn(vnpayResult(code, amount, "00", "00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.orderCode").value(code))
                .andExpect(jsonPath("$.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Thanh toán thành công"));

        // Hoi VNPay voi dung ma va dung thoi diem tao giao dich (= vnp_CreateDate luc tao URL)
        verify(vnPayClient).queryTransaction(code, createDate);
        assertEquals("PLACED", orderStatus(orderId));
        assertEquals("SUCCESS", paymentOf(orderId).get("status"));
        verify(assignmentService).createOffer(orderId);

        // Khach tai lai trang: giao dich da xu ly, khong hoi VNPay nua
        clearInvocations(vnPayClient);
        callReturn(vnpayResult(code, amount, "00", "00"))
                .andExpect(jsonPath("$.success").value(true));
        verify(vnPayClient, never()).queryTransaction(any(), any());
    }

    @Test
    void returnPageDoesNotTrustUrlWhenVnPayCannotConfirm() throws Exception {
        JsonNode order = placeOrder(login("0911000001"), "VNPAY");
        long orderId = order.get("id").asLong();
        String code = order.get("code").asText();
        String amount = vnpAmount(order);
        // VNPay khong tra loi duoc (loi mang, sai chu ky phan hoi...): khong doi gi, cho IPN
        when(vnPayClient.queryTransaction(any(), any())).thenReturn(new VnPayClient.QueryResult(
                "99", null, null, Map.of()));

        // URL quay ve noi "thanh cong" nhung server KHONG tin, van cho
        callReturn(vnpayResult(code, amount, "00", "00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.success").value(false));
        assertEquals("PENDING_PAYMENT", orderStatus(orderId));
        verify(assignmentService, never()).createOffer(anyLong());

        // URL quay ve bi sua -> 400, khong hoi VNPay
        clearInvocations(vnPayClient);
        Map<String, String> tampered = vnpayResult(code, amount, "00", "00");
        tampered.put("vnp_ResponseCode", "24");
        callReturn(tampered)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("chữ ký")));
        verify(vnPayClient, never()).queryTransaction(any(), any());
    }

    @Test
    void returnPageCancelsOrderWhenCustomerCancelledAndVnPayHasNoTransaction() throws Exception {
        JsonNode order = placeOrder(login("0911000001"), "VNPAY");
        long orderId = order.get("id").asLong();
        String code = order.get("code").asText();
        // 91 = VNPay khong co giao dich nao voi ma nay -> chac chan chua ai bi tru tien
        when(vnPayClient.queryTransaction(any(), any())).thenReturn(new VnPayClient.QueryResult(
                "91", null, null, Map.of("vnp_ResponseCode", "91")));

        callReturn(vnpayResult(code, vnpAmount(order), "24", "02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("FAILED"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Khách hàng hủy giao dịch")));

        assertEquals("CANCELLED", orderStatus(orderId));
        assertEquals("FAILED", paymentOf(orderId).get("status"));
    }

    // ----- Ham tien ich -----

    private String login(String phone) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\",\"password\":\"Demo@12345\"}".formatted(phone)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("token").asText();
    }

    /** Lam rong gio, them 1 phan Com suon nuong (45.000d) roi dat hang voi cach thanh toan da chon. */
    private JsonNode placeOrder(String token, String paymentMethod) throws Exception {
        String body = prepareCartAndOrderBody(token, paymentMethod);
        String response = mockMvc.perform(post("/api/orders").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response);
    }

    /** Gio chi con 1 phan Com suon nuong; tra ve body dat hang voi cach thanh toan da chon. */
    private String prepareCartAndOrderBody(String token, String paymentMethod) throws Exception {
        long menuItemId = jdbc.queryForObject("""
                SELECT mi.id FROM menu_items mi
                JOIN menu_categories mc ON mc.id = mi.category_id
                JOIN restaurants r ON r.id = mc.restaurant_id
                WHERE r.name = 'Cơm Nhà Demo - Chi nhánh Quận 1' AND mi.name = 'Cơm sườn nướng'
                """, Map.of(), Long.class);
        mockMvc.perform(delete("/api/cart").header("Authorization", token)).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/cart/items").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuItemId\":%d,\"quantity\":1}".formatted(menuItemId)))
                .andExpect(status().isOk());
        return """
                {"receiverName":"Khách Test","receiverPhone":"0911000001",
                 "deliveryAddress":"10 Lê Lợi, Quận 1","deliveryLat":10.7756,"deliveryLng":106.7004,
                 "paymentMethod":"%s"}
                """.formatted(paymentMethod);
    }

    /** So tien VNPay gui ve = tong tien x 100. */
    private static String vnpAmount(JsonNode order) {
        return new BigDecimal(order.get("totalAmount").asText()).multiply(BigDecimal.valueOf(100))
                .toBigInteger().toString();
    }

    /** Tham so VNPay gui ve (IPN hoac URL quay ve), ky bang khoa test giong cach VNPay ky. */
    private static Map<String, String> vnpayResult(String txnRef, String amount, String responseCode,
                                                   String transactionStatus) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", amount);
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_BankTranNo", "VNP14226112");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan don hang " + txnRef);
        params.put("vnp_PayDate", "20261117170112");
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TmnCode", TMN_CODE);
        params.put("vnp_TransactionNo", "14226112");
        params.put("vnp_TransactionStatus", transactionStatus);
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_SecureHash", VnPaySigner.hmacSha512(SECRET, VnPaySigner.buildQuery(params)));
        return params;
    }

    private ResultActions callIpn(Map<String, String> params) throws Exception {
        return mockMvc.perform(withParams(get("/api/payments/vnpay/ipn"), params));
    }

    /** Trang web goi khi khach quay ve; cong khai nen khong can token. */
    private ResultActions callReturn(Map<String, String> params) throws Exception {
        return mockMvc.perform(withParams(get("/api/payments/vnpay/return"), params));
    }

    private static MockHttpServletRequestBuilder withParams(MockHttpServletRequestBuilder request,
                                                            Map<String, String> params) {
        params.forEach(request::param);
        return request;
    }

    /** Doc query string cua URL thanh toan (giai ma URL-encode: "+" thanh dau cach). */
    private static Map<String, String> queryParams(String url) {
        Map<String, String> result = new HashMap<>();
        UriComponentsBuilder.fromUriString(url).build().getQueryParams().forEach((name, values) ->
                result.put(name, URLDecoder.decode(values.get(0), StandardCharsets.UTF_8)));
        return result;
    }

    private boolean merchantSeesOrder(long orderId) throws Exception {
        String response = mockMvc.perform(get("/api/merchant/orders").param("size", "50")
                        .header("Authorization", login("0922000001")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode item : objectMapper.readTree(response).get("items")) {
            if (item.get("id").asLong() == orderId) {
                return true;
            }
        }
        return false;
    }

    private List<Long> notificationRecipients(long orderId, String event) {
        return jdbc.queryForList("SELECT user_id FROM notifications WHERE event_key = :key ORDER BY user_id",
                Map.of("key", "order:" + orderId + ":" + event), Long.class);
    }

    private String orderStatus(long orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = :id", Map.of("id", orderId), String.class);
    }

    private Map<String, Object> paymentOf(long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT status, paid_at, gateway_payload FROM payments WHERE order_id = :id", Map.of("id", orderId));
        assertEquals(1, rows.size());
        return rows.get(0);
    }

    private int historyCount(long orderId, String status) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM order_status_history WHERE order_id = :id AND status = :status
                """, Map.of("id", orderId, "status", status), Integer.class);
    }

    private int cartItemCount(String phone) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM cart_items ci
                JOIN carts c ON c.id = ci.cart_id
                JOIN users u ON u.id = c.user_id
                WHERE u.phone = :phone
                """, Map.of("phone", phone), Integer.class);
    }
}
