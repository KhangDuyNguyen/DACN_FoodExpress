# Kế hoạch và API contract tuần 4 — phần của Khang (bản đề xuất, chờ duyệt)

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

**Phụ thuộc:** PR #13, #14 (đơn hàng) và #17 (gán tài xế của An) đã merge.

### E1. Sự kiện đơn hàng

Phát 2 sự kiện Spring (`ApplicationEventPublisher`) trong package `order`:

| Sự kiện | Khi nào | Dữ liệu |
|---|---|---|
| `OrderPlacedEvent` | Đặt hàng thành công | `orderId, restaurantId, customerId` |
| `OrderStatusChangedEvent` | Mỗi lần đổi trạng thái | `orderId, restaurantId, customerId, fromStatus, toStatus, changedBy` |

Bên nghe dùng `@TransactionalEventListener(phase = AFTER_COMMIT)`: chỉ chạy khi đơn đã thật sự lưu xong. Điệp (WebSocket dashboard) và An (thông báo) tự viết listener ở package của mình, không cần sửa code đơn hàng.

### E2. Đặt xong là mời tài xế ngay

`OrderAssignmentListener` nghe `OrderPlacedEvent` rồi gọi `AssignmentService.createOffer(orderId)` của An. Tài xế gần nhất nhận lời mời ngay, không phải chờ bộ quét 30 giây.

### E3. Trạng thái đơn khi có tài xế

| Từ | Sang | Ai đổi |
|---|---|---|
| `PLACED` | `PREPARING` | Chủ quán (nhận đơn) |
| `PREPARING` | `READY_FOR_PICKUP` | Chủ quán (món đã xong) — **mới** |
| `READY_FOR_PICKUP` | `DELIVERING` | Tài xế được gán (đã lấy món, đang giao) — **mới** |
| `DELIVERING` | `DELIVERED` | Tài xế được gán (đã giao) — **mới** |
| `PLACED`, `PREPARING` | `CANCELLED` | Như tuần 3 (khách chỉ hủy được khi `PLACED`) |

- Đơn **chưa có tài xế** (`driver_id` rỗng): chủ quán vẫn bấm được "Bắt đầu giao", "Hoàn thành" như tuần 3, để demo được khi không có tài xế online.
- Thêm hàm `OrderManagementService.changeStatusByDriver(driverUserId, orderId, toStatus)` có kiểm tra `orders.driver_id` đúng là tài xế đó. API của An (`/api/driver/orders/...`) gọi hàm này, để mọi luật đổi trạng thái vẫn nằm một chỗ trong `OrderStatusFlow`.
- Web: nút "Món đã xong" ở màn nhận đơn của cửa hàng; trang theo dõi đơn của khách hiện thêm bước "Chờ tài xế lấy món" và tên tài xế.

**Cần chốt với An:** code của An chỉ mời tài xế khi đơn còn `PLACED` (`assignOrder ... WHERE status = 'PLACED'`), và hủy lời mời ngay khi đơn rời `PLACED`. Nếu quán nhận đơn trước khi có tài xế nhận, đơn sẽ không bao giờ có tài xế. Đề xuất An cho mời khi đơn ở `PLACED`, `PREPARING` hoặc `READY_FOR_PICKUP` mà chưa có `driver_id`.

### E4. Test

- Đặt hàng xong có lời mời tài xế trong `order_assignments` (có tài xế online gần quán).
- Tài xế được gán đi `READY_FOR_PICKUP → DELIVERING → DELIVERED`; tài xế khác hoặc chủ quán (khi đơn đã có tài xế) → 403/409.
- Sự kiện chỉ phát sau khi transaction commit (đặt hàng lỗi giữa chừng thì không có lời mời).

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
