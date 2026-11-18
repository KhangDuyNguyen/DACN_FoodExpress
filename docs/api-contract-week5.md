# Kế hoạch và API contract tuần 5 — phần của Khang (đã duyệt 17/11)

- Hạn tuần 5: **thứ Bảy 21/11/2026**. Xong MVP: 28/11.
- Kế hoạch chung tuần 5: thanh toán, voucher, đánh giá, Android.
- Phần của Khang: **voucher ở bước thanh toán** (Module H) và **thanh toán VNPay sandbox** (Module I).
- Đánh giá do Dưỡng làm, Android do An và Dưỡng làm.

| Ngày | Việc |
|---|---|
| Thứ Tư 18/11 | Module H: voucher ở bước thanh toán |
| Thứ Năm 19 – thứ Sáu 20/11 | Module I: VNPay sandbox (cần có mã sandbox trước thứ Năm) |
| Thứ Bảy 21/11 | Chạy lại toàn bộ test, sửa lỗi, báo cáo tuần 5 |

**Điều kiện bắt đầu (đã đủ ngày 17/11):**
- G và hotfix đã vào `main`: G đi theo PR #21 (nhánh E chứa sẵn G), hotfix theo PR #23. PR #22 của G không cần merge nữa.
- Cả 2 module đều sửa trang thanh toán của G.
- Mỗi module 1 nhánh tạo từ `main` mới nhất. Cuối module chạy test + build, push, dừng lại báo cáo.

## Module H. Voucher ở bước thanh toán (`feature/khang-tuan05-voucher`)

Dùng code voucher có sẵn của Điệp (package `promotion`). Không sửa code của Điệp, không ghi thẳng vào bảng của Điệp.

| Hàm của Điệp | Dùng để |
|---|---|
| `VoucherService.validateVoucher(request, userId)` | Kiểm tra mã và tính số tiền giảm (không tăng lượt dùng) |
| `VoucherService.recordVoucherUsage(voucherId, userId, orderId)` | Tăng `used_count` có điều kiện và ghi `voucher_usages`; hết lượt → 409 |
| `GET /api/vouchers?restaurantId=&orderAmount=` (API có sẵn) | Web lấy danh sách mã dùng được cho giỏ hiện tại |

API đổi (chỉ thêm trường, không phá API cũ):

| Method | URL | Thay đổi |
|---|---|---|
| POST | `/api/orders/preview` | Body thêm `voucherCode` (không bắt buộc). Trả thêm `voucherCode`, `voucherTitle`, `voucherMessage`. Mã không dùng được thì vẫn trả 200, `discountAmount = 0`, `voucherMessage` là lý do |
| POST | `/api/orders` | Body thêm `voucherCode` (không bắt buộc). Mã không dùng được → 409 kèm lý do; không tạo đơn, giỏ giữ nguyên |

Luật:
- **Không tin số tiền do web gửi.** Server tự tính `subtotal` từ giỏ và `deliveryFee` từ khoảng cách, rồi đưa 2 số này vào `validateVoucher`.
- `discount_amount` là số tiền được giảm. Với `FREE_SHIP` đây là phần phí ship được giảm. `total = subtotal + deliveryFee − discount` (không âm, `OrderPricing.total` đã làm).
- Đặt hàng:
  - Kiểm tra lại mã trong cùng transaction tạo đơn.
  - Insert đơn xong mới gọi `recordVoucherUsage(...)`, vì cần `order_id`.
  - Hết lượt giữa chừng → 409 và rollback cả đơn.
- 1 đơn tối đa 1 mã (`voucher_usages` có UNIQUE `order_id`).
- Đơn bị hủy **không trả lại lượt voucher**, vì Điệp chưa có hàm trả lượt. Ghi `TODO(khang)`. Xem câu hỏi 3 ở cuối.

Web:
- `Checkout.tsx`:
  - Ô nhập mã và danh sách mã dùng được (bấm để chọn); nút "Áp dụng" gọi lại preview.
  - Thêm dòng "Giảm giá −xx.000đ"; mã không dùng được thì hiện lý do.
- `OrderTracking.tsx`: thêm dòng giảm giá (`discountAmount` đã có trong chi tiết đơn).

File dự kiến:
- `order/dto/OrderPreviewRequest`, `CreateOrderRequest`, `PriceBreakdown` (thêm trường voucher).
- `order/OrderService`: thêm hàm áp mã, dùng chung cho preview và đặt hàng.
- Test mới `order/OrderVoucherIntegrationTest`.
- Web: `api/orders.ts`, `pages/customer/Checkout.tsx`, `OrderTracking.tsx`. Danh sách mã dùng lại `api/voucher.ts` của Điệp nếu có sẵn hàm.

Test:
- `PERCENT` (có trần `max_discount`), `FIXED`, `FREE_SHIP` ra đúng số tiền; tổng = subtotal + phí − giảm.
- Mã không tồn tại, hết hạn, chưa đủ đơn tối thiểu, sai quán → preview trả lý do, đặt hàng trả 409.
- Dùng quá `per_user_limit` → 409.
- Đặt thành công thì `used_count` tăng 1 và có 1 dòng `voucher_usages`.
- Web gửi kèm `discountAmount` giả → bị bỏ qua.
- Dữ liệu voucher cho test được tạo trong DB của test. Voucher mẫu cho demo: xem câu hỏi 4 ở cuối.

## Module I. Thanh toán VNPay sandbox (`feature/khang-tuan05-vnpay`)

Làm đúng theo tài liệu chính thức:
- https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html (tạo URL, Return URL, IPN, bảng mã lỗi)
- https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr&refund.html (truy vấn giao dịch)
- https://sandbox.vnpayment.vn/apis/vnpay-demo/ (thẻ test)

Cấu hình đọc từ `.env`: `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET` (đã có chỗ trong `.env.example`). Thêm 3 biến, mặc định trỏ sandbox và localhost:
- `VNPAY_PAY_URL` (`https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`)
- `VNPAY_API_URL` (`https://sandbox.vnpayment.vn/merchant_webapi/api/transaction`)
- `VNPAY_RETURN_URL` (`http://localhost:5173/payment/vnpay-return`)

### Luồng

1. Khách chọn "VNPay" ở trang thanh toán; web gọi `POST /api/orders` với `paymentMethod: "VNPAY"`.
2. Server tạo đơn ở trạng thái **`PENDING_PAYMENT`**: chưa gửi cho quán, chưa mời tài xế. Đồng thời:
   - Tạo dòng `payments` với `PENDING`, `amount = total_amount`, `transaction_ref = vnp_TxnRef`.
   - Trả về chi tiết đơn kèm `paymentUrl`.
3. Web chuyển trình duyệt sang `paymentUrl` (trang VNPay sandbox). Khách trả bằng thẻ test NCB.
4. VNPay gọi **IPN** `GET /api/payments/vnpay/ipn` (máy chủ VNPay gọi máy chủ mình). Server làm theo thứ tự trong tài liệu:
   1. Kiểm tra chữ ký.
   2. Tìm đơn theo `vnp_TxnRef`.
   3. So số tiền (`vnp_Amount / 100`).
   4. Kiểm tra giao dịch chưa được xử lý.
   5. Cập nhật DB, rồi trả JSON.
5. Trình duyệt quay về `vnp_ReturnUrl` (trang web `/payment/vnpay-return`). Trang này gọi `GET /api/payments/vnpay/return?...`:
   - Server **chỉ kiểm tra chữ ký** và trả kết quả để hiển thị. Tài liệu ghi rõ không cập nhật kết quả giao dịch ở Return URL.

Khi cập nhật kết quả (dùng chung cho IPN và truy vấn):
- **Thành công** (`vnp_ResponseCode = 00` và `vnp_TransactionStatus = 00`):
  - `payments` → `SUCCESS`, lưu `paid_at`, `gateway_payload`.
  - Đơn `PENDING_PAYMENT → PLACED` và ghi lịch sử.
  - Phát `OrderPlacedEvent`: quán thấy đơn qua WebSocket của Điệp, mời tài xế.
- **Không thành công** (ví dụ `24`: khách hủy):
  - `payments` → `FAILED`.
  - Đơn → `CANCELLED`, lý do "Thanh toán VNPay không thành công (mã xx)".
- **Chỉ xử lý 1 lần:** `UPDATE payments ... WHERE id = :id AND status = 'PENDING'`; khớp 0 dòng nghĩa là đã xử lý rồi.

Trả lời IPN (`{"RspCode": "..", "Message": ".."}`):

| RspCode | Message | Khi nào |
|---|---|---|
| 00 | Confirm Success | Đã cập nhật |
| 01 | Order not found | Không có đơn theo `vnp_TxnRef` |
| 02 | Order already confirmed | Giao dịch đã xử lý rồi |
| 04 | invalid amount | Số tiền không khớp |
| 97 | Invalid signature | Sai chữ ký |
| 99 | Unknow error | Lỗi khác (chữ "Unknow" giữ y như mẫu của VNPay) |

### Tham số tạo URL (`vnp_Command=pay`, phiên bản `2.1.0`)

- `vnp_TxnRef` = mã đơn (`orders.code`, đã duy nhất). Mỗi đơn chỉ thanh toán 1 lần, không có "thanh toán lại".
- `vnp_Amount` = `total_amount × 100`.
- Các tham số cố định:
  - `vnp_CurrCode=VND`, `vnp_Locale=vn`, `vnp_OrderType=other`.
  - `vnp_OrderInfo` = "Thanh toan don hang FD..." (không dấu, không ký tự đặc biệt).
- Thời gian theo giờ GMT+7, dạng `yyyyMMddHHmmss`: `vnp_CreateDate` là lúc tạo, `vnp_ExpireDate` là lúc tạo + 15 phút.
- `vnp_IpAddr` = IP của khách. `vnp_ReturnUrl` = `VNPAY_RETURN_URL`.
- Chữ ký **HMAC-SHA512** với `VNPAY_HASH_SECRET`. Chuỗi ký được dựng như sau:
  - Lấy mọi tham số `vnp_*`, trừ `vnp_SecureHash` và `vnp_SecureHashType`.
  - Sắp xếp tên tăng dần.
  - Viết từng cặp `ten=giatri`, giá trị URL-encode (dấu cách thành `+`), nối bằng `&`.
  - Thêm `vnp_SecureHash` vào cuối URL.
- Không log `VNPAY_HASH_SECRET`, không log chữ ký.

### Vấn đề khi demo trên máy (cần bạn chọn)

Tài liệu yêu cầu IPN là địa chỉ **công khai có SSL**, và phải gửi URL này cho VNPay đăng ký. Chạy ở `localhost` thì VNPay không gọi IPN được, nên đơn sẽ kẹt ở "Chờ thanh toán". Có 2 cách:

- **Cách 1 (đề xuất): truy vấn giao dịch khi khách quay về.** Nếu đơn còn `PENDING_PAYMENT`, server tự gọi API `querydr` của VNPay (server gọi server, có kiểm tra chữ ký phản hồi), rồi cập nhật giống IPN.
  - Ưu điểm: không tin tham số trên URL quay về. Không cần mở máy ra Internet. Khi có IPN thật thì 2 đường dùng chung 1 hàm, không xử lý trùng.
  - Lưu ý: trang `querydr` ghi rõ chuỗi ký (các trường nối bằng `|`) nhưng không ghi tên thuật toán. Nhiều khả năng là HMAC-SHA512 như API thanh toán; sẽ thử trên sandbox, sai thì VNPay trả mã 97.
- **Cách 2: mở tunnel HTTPS** (ví dụ cloudflared) và gửi URL IPN cho VNPay đăng ký.
  - Tài liệu không ghi cách đăng ký IPN cho tài khoản sandbox, nên không chắc kịp tuần này.

### API

| Method | URL | Ai gọi | Ghi chú |
|---|---|---|---|
| POST | `/api/orders` | Khách | `paymentMethod` nhận thêm `VNPAY`; trả thêm `paymentUrl` (COD thì `null`) |
| GET | `/api/payments/vnpay/ipn` | VNPay | Công khai (SecurityConfig đã mở `/api/payments/*/ipn`); trả JSON như bảng trên |
| GET | `/api/payments/vnpay/return` | Trang quay về của web | Công khai (`/api/payments/*/return` đã mở); kiểm tra chữ ký, trả `{ orderId, success, message }`. Theo cách 1: nếu đơn còn chờ thì truy vấn VNPay rồi mới trả |

Ảnh hưởng tới phần khác:
- Đơn `PENDING_PAYMENT`:
  - Quán không thấy: lọc khỏi danh sách đơn của quán, trong package `order` của Khang.
  - Tài xế không được mời: code của An chỉ mời đơn `PLACED`, `PREPARING`, `READY_FOR_PICKUP`.
  - Khách thấy "Chờ thanh toán".
- Đơn COD giữ nguyên như cũ: tạo xong là `PLACED`.

Web:
- `Checkout.tsx`: chọn "Thanh toán khi nhận hàng (COD)" hoặc "VNPay". Chọn VNPay thì đặt xong chuyển sang `paymentUrl`.
- Trang mới `/payment/vnpay-return`: hiện "Thanh toán thành công" hoặc "không thành công" kèm lý do theo bảng mã lỗi, nút "Xem đơn".
- `OrderTracking.tsx`: trạng thái "Chờ thanh toán"; hiện phương thức VNPay và tình trạng thanh toán.

File dự kiến:
- Package `payment` (đang trống):
  - `VnPayProperties` (cấu hình)
  - `VnPaySigner` (ký và kiểm tra chữ ký)
  - `VnPayService` (tạo URL, xử lý IPN, quay về, truy vấn)
  - `PaymentRepository` (SQL bảng `payments`)
  - `PaymentController`
  - `dto/`
- Sửa `order/OrderService` (VNPAY → `PENDING_PAYMENT`), `OrderRepository` (lọc đơn chờ thanh toán khỏi trang quán), `CreateOrderRequest`.
- Cấu hình: `application.yml`, `.env.example`.
- Test: `VnPaySignerTest`, `VnPayPaymentIntegrationTest`.
- Web: `Checkout.tsx`, `VnPayReturn.tsx` (mới), `App.tsx` (1 route), `api/payments.ts`, `OrderTracking.tsx`.

Test:
- URL tạo ra đủ tham số theo tài liệu, `vnp_Amount` đã nhân 100, chữ ký kiểm lại được. Unit test dùng secret giả.
- IPN:
  - Sai chữ ký → 97; không có đơn → 01; sai số tiền → 04.
  - Thành công → 00, đơn thành `PLACED`, `payments` thành `SUCCESS`, có lời mời tài xế.
  - Gọi lần 2 → 02 và không xử lý trùng.
  - Khách hủy (`24`) → đơn `CANCELLED`.
- Đơn VNPay chưa trả tiền không hiện ở trang quán.
- Thử thật trên sandbox bằng thẻ test NCB khi có mã TMN.

### Kết quả Module I (đã làm, 17/11)

Nhánh `feature/khang-tuan05-vnpay` tạo từ nhánh voucher (H), vì I sửa tiếp `OrderService` và `Checkout.tsx` của H. **Cần merge H trước rồi mới merge I.**

Test: `VnPaySignerTest` (5 test, giá trị đối chiếu tính bằng `openssl`) và `VnPayPaymentIntegrationTest` (9 test, khóa giả, `querydr` được thay bằng mock). Chưa thử thật trên sandbox vì `.env` chưa có mã TMN.

Những điểm chốt khi làm (khác hoặc chi tiết hơn bản kế hoạch):

| Chỗ | Đã làm | Lý do |
|---|---|---|
| Khi nào tính là thành công | `vnp_ResponseCode = 00` **và** `vnp_TransactionStatus = 00` | Mẫu PHP của VNPay dùng "hoặc". Nhóm dùng "và" để chỉ giao món khi cả hai mã cùng xác nhận |
| `POST /api/orders` trả gì | `CreateOrderResponse` = chi tiết đơn + `paymentUrl`, dùng `@JsonUnwrapped` | JSON giữ nguyên như cũ, chỉ thêm 1 trường; không phải sửa `OrderDetailResponse` mà test của Điệp đang dùng |
| Quán thấy đơn nào | Đơn COD, hoặc đơn VNPay đã có giao dịch `SUCCESS` | Đơn VNPay hủy vì thanh toán lỗi cũng là đơn quán chưa từng nhận, nên không hiện (kể cả trang chi tiết) |
| Khách quay về mà đơn còn chờ | Hỏi VNPay bằng `querydr`. Kết quả `00` + `00` → đã trả (có so số tiền); `00` + `01` → chờ tiếp; `00` + mã khác → thất bại; `91` (VNPay không có giao dịch) và URL quay về báo thất bại → hủy đơn; còn lại → không đổi gì | `91` nghĩa là chưa ai bị trừ tiền, nên hủy được. Các trường hợp không rõ thì giữ nguyên, chờ IPN |
| `payments.created_at` | Ghi đúng `vnp_CreateDate` (giờ Việt Nam) | `querydr` cần gửi lại giá trị này ở `vnp_TransactionDate` |
| Giỏ hàng | Đơn VNPay cũng xóa giỏ khi tạo đơn, giống COD | Món đã nằm trong đơn; thanh toán lỗi thì đơn bị hủy, khách đặt lại |
| Đã nhận tiền nhưng đơn không còn chờ | Ghi giao dịch `SUCCESS`, không đổi đơn, ghi log cảnh báo + `TODO(khang)` hoàn tiền | Hoàn tiền làm ở tuần 6–7 |
| Đơn chờ thanh toán quá lâu | Chưa tự hủy, khách cũng chưa tự hủy được: `TODO(khang)` tuần 6–7 | Kế hoạch đã dời việc này |
| Lỗi 400 | Thêm `BadRequestException` vào `common` | URL quay về sai chữ ký phải trả 400 qua `GlobalExceptionHandler` |
| `vnp_IpAddr` | `request.getRemoteAddr()` | Chạy trên máy có thể là `0:0:0:0:0:0:0:1`; tài liệu cho phép tới 45 ký tự. Khi thử sandbox cần để ý chỗ này |

Còn phải làm khi có mã TMN: thử thật trên sandbox bằng thẻ NCB (thành công, khách bấm hủy), xem `querydr` trả gì với giao dịch bị hủy, và kiểm tra chữ ký phản hồi `querydr`. Tài liệu chỉ ghi thứ tự trường, còn trường bỏ trống thì nhóm đang để chuỗi rỗng.

## Module J. Hồ sơ khách (`feature/khang-tuan05-ho-so`, duyệt 18/11)

Mục "Hồ sơ khách" trong bảng dời việc ở cuối file, làm nốt trong tuần 5.

| Method | URL | Body | Trả về |
|---|---|---|---|
| PUT | `/api/users/me` | `{ "fullName", "email" }` | Thông tin user mới (`UserResponse`, giống `GET /api/users/me`) |
| PUT | `/api/users/me/password` | `{ "currentPassword", "newPassword" }` | 204 |

Luật:
- Cần đăng nhập, vai trò nào cũng được (chỉ sửa chính mình, id lấy từ JWT, không nhận id từ client).
- Họ tên: bắt buộc, tối đa 100 ký tự, cắt khoảng trắng. Email: không bắt buộc (để trống là bỏ email), cắt khoảng trắng, đổi sang chữ thường như lúc đăng ký. Email đã thuộc tài khoản khác → 409 "Email đã được đăng ký".
- Số điện thoại không đổi được (dùng để đăng nhập).
- Đổi mật khẩu: sai mật khẩu hiện tại → 400 "Mật khẩu hiện tại không đúng"; mật khẩu mới 8–72 ký tự như lúc đăng ký (BCrypt chỉ dùng 72 byte đầu); trùng mật khẩu cũ → 400. Lưu bằng BCrypt.
- Đổi mật khẩu không thu hồi token đang dùng (JWT còn hạn đến 24 giờ); muốn thu hồi cần thêm cột phiên bản token, chưa làm.

Web: trang `/profile` (sửa họ tên, email, đổi mật khẩu, link sang sổ địa chỉ); bấm tên trên thanh đầu trang sẽ vào trang này.

Test: `UserProfileIntegrationTest`.

## Gộp PR #18 của An (chốt 17/11)

#18 (`feature/an-tuan5-delivery-earnings`) làm lại luồng tài xế giao hàng mà Module E đã đưa vào `main` (#21), theo luồng khác (thêm `PICKED_UP`, tài xế nhận đơn thì đơn tự sang `PREPARING`) và trùng API `PATCH /api/driver/orders/{id}/status`. Nhóm chốt:

- **Giữ luồng trên `main`**: PLACED → PREPARING (quán) → READY_FOR_PICKUP (quán) → DELIVERING → DELIVERED (tài xế; chưa có tài xế thì quán bấm thay). Không có `PICKED_UP`. Tài xế nhận đơn chỉ gắn `driver_id`.
- **Package `order` (Khang)** nhận 2 quy tắc của An, nhánh `feature/khang-tuan05-khoa-don-huy`:
  - Đổi trạng thái (khách hủy, quán đổi, tài xế đổi) đều đọc đơn kèm khóa dòng `SELECT ... FOR UPDATE` (`OrderRepository.lockForUpdate`), để không chen ngang lúc tài xế bấm nhận đơn.
  - Đơn đã có tài xế nhận (`orders.driver_id` khác null) thì khách và quán không hủy được: 409 "Tài xế đã nhận giao đơn, chưa thể hủy đơn hàng". Web ẩn nút "Hủy đơn" trong trường hợp này.
- **An** merge `main` vào #18 và lấy nguyên package `order` của `main`, bỏ API trùng cùng `DriverDeliveryService`, chuyển việc ghi thu nhập sang listener nghe `OrderStatusChangedEvent` (khi `DELIVERED`), bỏ `PICKED_UP` ở backend và app tài xế. Khang đã gửi hướng dẫn chi tiết.

## Không làm trong tuần 5 (đề xuất dời)

| Việc | Lý do, thời điểm |
|---|---|
| MoMo sandbox | Làm sau VNPay, dùng chung bảng `payments` (tuần 6) |
| Tùy chọn món (size, topping) trong giỏ và đơn | Sửa nhiều phần giỏ và đơn, dùng dữ liệu của Dưỡng (tuần 6) |
| Hồ sơ khách (sửa tên, email, đổi mật khẩu) | Đã làm ở Module J |
| Hoàn tiền VNPay, tự hủy đơn chờ thanh toán quá 15 phút | Tuần 6–7 cùng phần admin; tuần 5 ghi `TODO(khang)` |
| Phối hợp An cập nhật PR #18 theo `main` | Đã chốt cách gộp, xem mục "Gộp PR #18 của An" ở trên |

## Cần bạn chốt

Đã chốt 17/11: câu 2 chọn cách 1, câu 3 không trả lượt (ghi `TODO`), câu 4 nhờ Điệp thêm vào seed. Câu 1 còn chờ mã sandbox.

1. **Đăng ký VNPay sandbox** tại https://pay.vnpaytest.vn/dev-registration (link lấy từ trang tài liệu chính thức), rồi tự điền `VNPAY_TMN_CODE` và `VNPAY_HASH_SECRET` vào file `.env`. Không gửi khóa qua chat, không commit.
2. **Demo trên máy:** cách 1 (truy vấn VNPay khi khách quay về) hay cách 2 (tunnel và đăng ký IPN)?
3. **Đơn bị hủy hoặc thanh toán thất bại có trả lại lượt voucher không?** Nếu có, cần nhờ Điệp thêm hàm trả lượt trong `VoucherService`.
4. **Voucher mẫu cho demo:** nhờ Điệp thêm vào seed (bảng của Điệp), hay tự tạo qua trang admin khi demo?
