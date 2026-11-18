# Kế hoạch và API contract tuần 6 — phần của Khang

- Hạn tuần 6: **thứ Bảy 28/11/2026** (cũng là mốc xong MVP). Đóng băng code 09/12.
- Kế hoạch đã duyệt ngày 18/11: J (hồ sơ khách, tuần 5) → **L** → **K**. MoMo, hoàn tiền VNPay, tùy chọn món dời sang tuần 7.
- Mỗi module 1 nhánh tạo từ `main` mới nhất. K làm sau khi J đã vào `main` (K sửa tiếp package `user`), tránh xung đột do squash.

## Module L. Tự hủy đơn VNPay quá hạn (`feature/khang-tuan06-huy-don-qua-han`)

`TODO(khang)` đã ghi ở tuần 5: đơn VNPay khách bỏ ngang (đóng trang VNPay, không bấm hủy) nằm mãi ở "Chờ thanh toán".

- Mỗi phút một tác vụ định kỳ quét giao dịch VNPay còn `PENDING` đã tạo quá **16 phút** (URL thanh toán hết hạn sau 15 phút theo `vnp_ExpireDate`, cộng 1 phút cho chắc).
- Với từng giao dịch, **hỏi VNPay lần cuối** (`querydr`) rồi mới quyết:

| VNPay trả lời | Làm gì |
|---|---|
| `00` + `vnp_TransactionStatus = 00` và đúng số tiền | Khách trả sát giờ mà IPN chưa tới: xác nhận đơn như IPN (đơn sang `PLACED`, báo quán, mời tài xế) |
| `91` (không có giao dịch) hoặc `00` + trạng thái khác `00` | Hủy đơn, lý do "Thanh toán VNPay không thành công (mã 11)" — mã 11 của VNPay là "Đã hết hạn chờ thanh toán" |
| Lỗi mạng, sai chữ ký phản hồi, mã khác, hoặc báo đã trả nhưng số tiền lệch | Để nguyên (ghi log), phút sau hỏi lại |

- Ghi kết quả dùng chung `PaymentResultService.apply` với IPN (chỉ xử lý 1 lần nhờ `UPDATE ... WHERE status = 'PENDING'`).
- Bật mặc định. Tắt bằng biến môi trường `VNPAY_CLEANUP_ENABLED=false` (`app.vnpay.cleanup.enabled`). Chưa cấu hình khóa VNPay thì không quét (không hỏi được VNPay).
- Web: trang đơn "Chờ thanh toán" ghi thêm "quá 15 phút chưa thanh toán thì đơn tự hủy".
- Test (trong `VnPayPaymentIntegrationTest`, `querydr` là mock): quá hạn + VNPay báo không có giao dịch → hủy; quá hạn + VNPay báo đã trả → xác nhận; đơn chưa quá hạn không bị hỏi; VNPay không trả lời → giữ nguyên.

## Module K. Admin phần của Khang (`feature/khang-tuan06-admin`)

Theo bảng phân công: "Admin: user, đơn toàn sàn, phí ship". Mọi API dưới `/api/admin/**` (SecurityConfig đã chỉ cho `ADMIN`). Trang web dùng Ant Design như các trang admin của Điệp.

### 1. Người dùng (package `user`)

| Method | URL | Ghi chú |
|---|---|---|
| GET | `/api/admin/users?role=&status=&q=&page=&size=` | Lọc vai trò, trạng thái; `q` tìm trong họ tên, SĐT, email. Mới nhất trước. Trả `PageResponse` |
| PATCH | `/api/admin/users/{id}/status` | Body `{ "status": "ACTIVE" | "LOCKED" }`. Không khóa tài khoản `ADMIN` (409) |

- Khóa có hiệu lực ngay: đăng nhập đã trả 403 "Tài khoản đã bị khóa", và `JwtAuthFilter` đọc lại trạng thái ở mỗi request nên token cũ cũng hết dùng được.
- Trả về: id, họ tên, SĐT, email, vai trò, trạng thái, ngày tạo, lần đăng nhập cuối. Không bao giờ trả `password_hash`.

### 2. Đơn toàn sàn (package `order`)

| Method | URL | Ghi chú |
|---|---|---|
| GET | `/api/admin/orders?status=&restaurantId=&paymentMethod=&q=&from=&to=&page=&size=` | `q`: mã đơn hoặc SĐT khách; `from`, `to`: ngày đặt (yyyy-MM-dd). Mới nhất trước |
| GET | `/api/admin/orders/{id}` | Chi tiết đơn (giống trang khách: món, tiền, tài xế, lịch sử) |

- Admin thấy mọi đơn, kể cả đơn VNPay chưa trả tiền (khác với trang quán).

### 3. Phí ship (package `delivery`)

| Method | URL | Ghi chú |
|---|---|---|
| GET | `/api/admin/delivery-fee-rules` | Danh sách quy tắc, mới nhất trước; đánh dấu quy tắc **đang áp dụng** |
| POST | `/api/admin/delivery-fee-rules` | Body `{ name, baseFee, baseDistanceKm, perKmFee }`. Tạo quy tắc mới (đang bật) → thành quy tắc đang áp dụng |
| PATCH | `/api/admin/delivery-fee-rules/{id}/active` | Body `{ "active": true | false }`. Không tắt được quy tắc bật cuối cùng (409), vì đặt hàng cần 1 quy tắc |

- Giá đặt hàng giữ nguyên cách tính hiện tại: dùng quy tắc **đang bật mới nhất** (`OrderRepository.findActiveFeeRule`). Đơn cũ không đổi tiền vì tiền đã lưu trong đơn.
- Tiền là VND, số nguyên ≥ 0; km cơ bản 0–50.

### Web

- 3 trang `/admin/users`, `/admin/orders`, `/admin/delivery-fees`.
- Thêm 3 mục menu vào `AdminLayout.tsx` (file của Điệp): chỉ thêm 3 mục, không sửa phần khác; nhắn Điệp biết.

### Test

- Tài khoản ADMIN cho test: đặt `app.admin.phone`, `app.admin.password` để `AdminInitializer` tạo sẵn.
- Người dùng: lọc, tìm; khóa → đăng nhập 403 và token cũ không gọi được API; mở khóa; không khóa được ADMIN; khách gọi API admin → 403.
- Đơn: lọc theo trạng thái, quán, mã đơn; chi tiết; khách gọi → 403.
- Phí ship: tạo quy tắc mới → xem trước đơn dùng phí mới; tắt quy tắc cuối cùng → 409.
