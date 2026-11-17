# Kế hoạch và API contract tuần 4 — phần của Khang (đã duyệt, đã làm G và E)

Kế hoạch chung của nhóm (từ tuần 4):

| Tuần | Nội dung |
|---|---|
| 4 | Cửa hàng/chi nhánh, quản lý menu, luồng tài xế, bản đồ và phí ship |
| 5 | Thanh toán, voucher, đánh giá, bắt đầu app Android |
| 6–7 | AI (gợi ý món, dự đoán thời gian giao, phân tích đánh giá, phân công tài xế, dự báo doanh thu), trang admin |
| 8 | Kiểm thử, sửa lỗi, hoàn thiện báo cáo |
| 9 | Chốt sản phẩm, luyện demo |

Phần phối hợp lấy từ file phân công của An, Điệp, Dưỡng:

- **An**: "Khang: khi đơn được tạo (PLACED) thì gọi AssignmentService của An; khi đơn đổi trạng thái thì gọi NotificationService của An."
- **Điệp**: "khi có đơn mới, Khang phát sự kiện để Điệp đẩy lên dashboard cửa hàng. Cửa hàng đổi trạng thái đơn qua API đơn hàng của Khang."

Trong tuần 4, cửa hàng/chi nhánh và quản lý menu do Dưỡng làm (PR #15), gán tài xế do An làm (PR #17), phí ship đã xong ở Module C (PR #13). Phần còn lại của Khang là **nối đơn hàng với tài xế** và **bản đồ chọn địa chỉ giao**.

## Module E. Nối đơn hàng với luồng tài xế (phối hợp An, Điệp)

> Đã làm (nhánh `feature/week4-driver-flow`). Nhánh đã gộp `main` ngày 17/11 (có PR #14, #15, #17, #19) và chứa luôn Module G (`feature/week4-address-map`, chưa vào `main`). Code ở package `order` và `order.event`.

### E1. Sự kiện đơn hàng (package `order.event`)

| Sự kiện | Khi nào | Dữ liệu |
|---|---|---|
| `OrderPlacedEvent` (Điệp tạo ở PR #19, dùng chung) | Đặt hàng thành công | `orderId, restaurantId, order` (`OrderDetailResponse`) |
| `OrderStatusChangedEvent` | Mỗi lần đổi trạng thái (quán, khách hoặc tài xế) | `orderId, restaurantId, customerId, fromStatus, toStatus, changedBy` |

- Sự kiện phát trong transaction. Bên nghe dùng `@TransactionalEventListener(phase = AFTER_COMMIT)`, nên chỉ chạy khi đơn đã thật sự lưu xong.
- Bên nghe hiện có:
  - `OrderEventListener` (Điệp): đẩy đơn mới lên `/topic/restaurants/{id}/orders`.
  - `OrderAssignmentListener` (Khang): mời tài xế.
- An (thông báo, thu nhập tài xế khi `DELIVERED`) tự viết listener ở package của mình, không cần sửa code đơn hàng.
- Bản đề xuất có `OrderPlacedEvent(orderId, restaurantId, customerId)` riêng. Khi gộp `main` đã bỏ bản này, dùng chung sự kiện của Điệp, để mỗi lần đặt hàng chỉ phát 1 sự kiện.

### E2. Đặt xong là mời tài xế ngay

`OrderAssignmentListener` nghe `OrderPlacedEvent` rồi gọi `AssignmentService.createOffer(orderId)` của An. Tài xế gần nhất nhận lời mời ngay, không phải chờ bộ quét 30 giây. Phần gán tài xế có lỗi thì chỉ ghi log: đơn đã lưu, khách vẫn nhận 201.

### E3. Trạng thái đơn khi có tài xế

| Từ | Sang | Ai đổi |
|---|---|---|
| `PLACED` | `PREPARING` | Chủ quán (nhận đơn) |
| `PREPARING` | `READY_FOR_PICKUP` | Chủ quán (món đã xong) — **mới** |
| `READY_FOR_PICKUP` | `DELIVERING` | Tài xế được gán (đã lấy món); đơn chưa có tài xế thì chủ quán |
| `DELIVERING` | `DELIVERED` | Tài xế được gán (đã giao); đơn chưa có tài xế thì chủ quán |
| `PLACED`, `PREPARING` | `CANCELLED` | Như tuần 3 (khách chỉ hủy được khi `PLACED`) |

Luật nằm một chỗ trong `OrderStatusFlow.canChange(actor, from, to, hasDriver)`:
- Đơn **chưa có tài xế** (`driver_id` rỗng): chủ quán tự bấm "Bắt đầu giao", "Hoàn thành", để demo được khi không có tài xế online.
- Đơn **đã có tài xế**: 2 bước giao hàng chỉ tài xế đó bấm. Chủ quán bấm → 409 "Đơn đã có tài xế nhận, bước giao hàng do tài xế cập nhật".

API mới cho app tài xế (đặt ở package `order` để mọi luật đổi trạng thái vẫn do `OrderManagementService` xử lý):

| Method | URL | Ghi chú |
|---|---|---|
| PATCH | `/api/driver/orders/{id}/status` | Vai trò DRIVER. Body `{ "status": "DELIVERING" }` hoặc `"DELIVERED"` → 200 + chi tiết đơn |

Lỗi của API trên:
- 400: `status` khác 2 giá trị trên.
- 403: đơn không phải của tài xế đang đăng nhập.
- 409: sai bước, ví dụ món chưa xong (`PREPARING`) mà báo đang giao.

Dữ liệu trả về thêm:
- `OrderDetailResponse.driver`: `{ name, phone, vehicleType, licensePlate }`, hoặc `null` khi chưa có tài xế.
- `OrderSummaryResponse.driverName` và `MerchantOrderSummaryResponse.driverName`: tên tài xế hoặc `null`.
- Tham số `?status=` nhận thêm `READY_FOR_PICKUP`.

Web:
- Trang theo dõi đơn của khách:
  - Thanh tiến trình 5 bước (thêm "Chờ lấy món").
  - Thẻ tài xế: tên, SĐT, loại xe, biển số. Chưa có tài xế thì hiện "Chưa có tài xế nhận", quán tự giao thì "Quán tự giao".
- Trang đơn của cửa hàng (bản realtime của Điệp):
  - Tab "Chờ lấy món", nút "Món đã xong".
  - Cột "Tài xế"; chi tiết đơn có thông tin tài xế.
  - Đơn đã có tài xế thì ẩn nút "Bắt đầu giao"/"Hoàn thành", hiện "Tài xế cập nhật".

Sửa thêm khi gộp `main` (PR #19 của Điệp có sửa package `order`):
- `OrderService` trên `main` có 2 constructor nên Spring báo `No default constructor found`, backend không khởi động được. Nhánh E giữ 1 constructor 3 tham số.
- Để khớp, có sửa 3 chỗ nhỏ trong code của Điệp ở package `order`:
  - `MerchantOrderControllerWebMvcTest`: `new OrderService(null, orderRepository, null)`; thêm `null` cho `driverName`.
  - `OrderEventListener`: truyền tên tài xế (đơn mới thường là `null`).
  - `OrderEventListenerTest`: thêm `null` cho `driver`.

**Cần chốt với An:**
- Code của An chỉ mời tài xế khi đơn còn `PLACED` (`assignOrder ... WHERE status = 'PLACED'`), và hủy lời mời ngay khi đơn rời `PLACED`. Nếu quán nhận đơn trước khi có tài xế nhận, đơn sẽ không bao giờ có tài xế (quán phải tự giao). Đề xuất An cho mời khi đơn ở `PLACED`, `PREPARING` hoặc `READY_FOR_PICKUP` mà chưa có `driver_id`.
- Bộ quét định kỳ chỉ chạy khi bật `app.assignment.scheduling.enabled=true`, nhưng `application.yml` chưa bật. Khi đó lời mời hết hạn không được dọn, và tài xế bỏ qua lời mời bị coi là đang bận.
- App tài xế gọi `PATCH /api/driver/orders/{id}/status` ở trên để báo đã lấy món / đã giao.

### E4. Test

- `DriverFlowIntegrationTest` (4 test):
  - Đặt hàng xong có ngay lời mời trong `order_assignments` cho tài xế online gần quán.
  - Tài xế nhận lời mời rồi đi `READY_FOR_PICKUP → DELIVERING → DELIVERED`; chủ quán bấm bước giao → 409; lịch sử ghi đúng tài xế; khách thấy tên tài xế.
  - Tài xế khác → 403; món chưa xong mà tài xế báo đang giao → 409; trạng thái sai → 400.
  - Đặt hàng lỗi (dưới đơn tối thiểu) → không có đơn, không mời ai.
- `OrderPlacedEventTest`: phần gán tài xế ném lỗi thì đặt hàng vẫn 201, đơn vẫn `PLACED`.
- `OrderStatusFlowTest` (6 test): bảng chuyển trạng thái của từng vai trò, có và không có tài xế.
- `OrderManagementIntegrationTest`: chủ quán tự đi đủ 5 bước khi đơn chưa có tài xế.

## Module G. Sổ địa chỉ có bản đồ

> Đã làm (nhánh `feature/week4-address-map`). Code ở package `user` (`AddressController`, `AddressService`, `AddressRepository`).

| Method | URL | Ghi chú |
|---|---|---|
| GET | `/api/users/me/addresses` | Địa chỉ của mình, mặc định lên đầu |
| POST | `/api/users/me/addresses` | `{ label, receiverName, receiverPhone, addressLine, lat, lng, isDefault }` → 201 |
| PUT | `/api/users/me/addresses/{id}` | Sửa; địa chỉ của người khác → 404 |
| PATCH | `/api/users/me/addresses/{id}/default` | Đặt làm mặc định (thêm so với bản đề xuất, để web không phải gửi lại cả địa chỉ) |
| DELETE | `/api/users/me/addresses/{id}` | Xóa → 204 |

- Có ít nhất 1 địa chỉ thì luôn có đúng 1 địa chỉ mặc định:
  - Địa chỉ đầu tiên tự thành mặc định.
  - Đặt 1 địa chỉ làm mặc định thì bỏ mặc định các địa chỉ khác (trong 1 transaction).
  - PUT với `isDefault: false` không bỏ mặc định; muốn đổi thì đặt địa chỉ khác làm mặc định.
  - Xóa địa chỉ mặc định thì địa chỉ mới nhất còn lại thành mặc định.
- `lat`, `lng` bắt buộc (cột trong V1 cho phép rỗng, nhưng cần tọa độ để tính phí ship).
- Mọi câu SQL đều có `WHERE user_id = :userId`; địa chỉ của người khác trả 404, không lộ là có tồn tại.
- Seed `V20261108_01__seed_dia_chi_demo.sql`: khách `0911000001` có 3 địa chỉ (đúng 3 địa chỉ mẫu cũ), khách `0911000002` có 1.
- Web:
  - Trang `/profile/addresses` (bấm tên khách trên header để vào): danh sách, thêm/sửa/xóa, đặt mặc định.
  - Component `components/map/LocationPicker.tsx`: bản đồ Leaflet + OpenStreetMap, bấm để chọn điểm, nút "Dùng vị trí hiện tại". Điểm vẽ bằng `CircleMarker` nên không cần ảnh icon của Leaflet.
  - Trang thanh toán chọn từ sổ địa chỉ (địa chỉ mặc định chọn sẵn), phí ship tính lại theo điểm đã chọn. Chưa có địa chỉ thì có link "Thêm địa chỉ", lưu xong tự quay lại trang thanh toán.
  - Thư viện mới (khóa đúng phiên bản): `leaflet` 1.9.4, `react-leaflet` 5.0.0, `@types/leaflet` 1.9.22.
- Test `AddressIntegrationTest` (6 test): danh sách seed, địa chỉ đầu tự mặc định và luôn chỉ 1 mặc định, sửa không làm mất mặc định, xóa mặc định thì chuyển cho địa chỉ mới nhất, khách khác sửa/xóa/đặt mặc định → 404, sai dữ liệu → 400, chưa đăng nhập → 401.

## Chuyển sang tuần 5 (theo kế hoạch chung)

| Việc | Ghi chú |
|---|---|
| Voucher ở bước thanh toán | Gọi `VoucherService.validateVoucher(...)` và `recordVoucherUsage(...)` của Điệp trong cùng transaction đặt hàng |
| Thanh toán VNPay sandbox | Cần bạn đăng ký tài khoản sandbox (mã TMN, khóa bí mật); mình đọc tài liệu chính thức của VNPay, không đoán tham số |
| Tùy chọn món (size, topping) trong giỏ và đơn | Dùng dữ liệu tùy chọn từ PR #15 của Dưỡng |
| Hồ sơ khách (sửa tên, email, đổi mật khẩu) | Nhỏ, làm kèm |

Trang admin của Khang (quản lý người dùng, đơn toàn sàn, sửa phí ship) để tuần 6–7 cùng phần admin.

## Quy trình

Như tuần 3: mỗi module 1 nhánh `feature/week4-<ten>`, tạo từ `main` sau khi các PR phụ thuộc đã merge. Cuối mỗi module chạy build + test, push, dừng lại báo cáo.
