# API contract - Tuần 3 (bản đề xuất, chờ duyệt)

Mục tiêu demo: Đăng nhập → xem nhà hàng → xem menu → thêm giỏ → đặt hàng → cửa hàng nhận đơn và đổi trạng thái → khách xem trạng thái đơn.

Quy ước chung (giữ nguyên như tuần 2, xem `docs/api/auth.md`):

- Tiền tố `/api`, JSON camelCase, thời gian ISO-8601 (`2026-10-30T11:20:00`), tiền là số nguyên VND (`55000`).
- Gửi token: `Authorization: Bearer <token>`.
- Lỗi luôn theo `ApiError`: `{ "status", "message", "fieldErrors", "timestamp" }`.
- Danh sách phân trang trả `PageResponse`: `{ "items", "page", "size", "totalItems", "totalPages" }`, `page` bắt đầu từ 0, `size` mặc định 10, tối đa 50.

| Mã | Ý nghĩa trong tuần 3 |
|---|---|
| 400 | Sai dữ liệu đầu vào (thiếu trường, sai định dạng, số lượng ngoài khoảng) |
| 401 | Chưa đăng nhập / token sai / hết hạn |
| 403 | Sai vai trò, hoặc không phải chủ dữ liệu (đơn của người khác, quán của chủ khác) |
| 404 | Không tìm thấy (quán, món, đơn, dòng giỏ) |
| 409 | Vi phạm quy tắc nghiệp vụ: món hết, quán đóng cửa, giỏ thuộc quán khác, giỏ rỗng, chưa đủ đơn tối thiểu, chuyển trạng thái sai thứ tự |

---

## Module A: Tài khoản và phân quyền (`feature/week3-auth`)

Đã có từ tuần 2, **giữ nguyên, không viết lại**:

| Method | URL | Quyền | Ghi chú |
|---|---|---|---|
| POST | `/api/auth/register` | Công khai | 201 + `{ token, user }`. 400, 409 trùng SĐT/email |
| POST | `/api/auth/login` | Công khai | 200 + `{ token, user }`. 401 sai tài khoản, 403 bị khóa |
| GET | `/api/users/me` | Đã đăng nhập | 200 `UserResponse` |

Bổ sung tuần 3:

1. **Phân quyền theo vai trò cho API mới** (sửa `SecurityConfig`):

   | Đường dẫn | Vai trò |
   |---|---|
   | `/api/cart/**` | `CUSTOMER` |
   | `/api/orders/**` | `CUSTOMER` |
   | `/api/merchant/**` | `MERCHANT` (đã có) |
   | `GET /api/restaurants/**`, `GET /api/cuisines` | Công khai (đã có) |

2. **Bổ sung `GlobalExceptionHandler`** để mọi lỗi đều ra `ApiError`, không lộ stack trace:
   - JSON hỏng (`HttpMessageNotReadableException`) → 400 `"Dữ liệu gửi lên không đúng định dạng"`.
   - Sai kiểu tham số / thiếu tham số (`?page=abc`) → 400.
   - Lỗi không lường trước (`Exception`) → 500 `"Lỗi hệ thống, vui lòng thử lại"` (log ở server, không trả chi tiết).
3. **Tài khoản mẫu** (migration mới, mật khẩu là dữ liệu giả để test, băm BCrypt sẵn): 1 khách, 2 chủ quán (mỗi người nhiều quán), 1 tài xế, 1 admin. Danh sách ghi ở `docs/week3-demo.md`.
4. **Web**: đã có trang đăng nhập, đăng ký, lưu token, `ProtectedRoute`. Bổ sung: bọc các trang `/cart`, `/checkout`, `/orders/**` bằng `ProtectedRoute roles={['CUSTOMER']}`; trang `/merchant` chỉ cho `MERCHANT` (hiện web cho cả `ADMIN` nhưng backend chặn ADMIN ở `/api/merchant/**`, nên ADMIN vào sẽ thấy lỗi 403).

---

## Module B: Nhà hàng và menu (`feature/week3-restaurant-menu`)

> Đã làm. Nhánh này dựng trên nhánh `duongtuan3` của Dưỡng (chi tiết quán, menu, trang web), chỉ bổ sung phần thiếu.
> Tên trường dưới đây là tên **thật** API đang trả (một vài chỗ khác bản đề xuất ban đầu để không phải sửa code đã có của Dưỡng).

### B1. Danh sách nhà hàng (đổi API cũ sang phân trang)

`GET /api/restaurants?keyword=pho&cuisineId=1&lat=10.77&lng=106.70&page=0&size=10` (công khai)

- `keyword`: tìm theo tên (LIKE, tham số hóa). `cuisineId`: lọc danh mục. Chỉ trả quán `status = ACTIVE`.
- Có `lat`,`lng` → sắp xếp gần nhất trước (tính trong SQL bằng `ST_Distance_Sphere`, để phân trang đúng) và có `distanceKm`. Không có → sắp xếp `avgRating` giảm dần, `distanceKm = null`.
- `page` ≥ 0, `size` từ 1 đến 50, `lat` từ -90 đến 90, `lng` từ -180 đến 180. Sai → 400.
- **Thay đổi so với tuần 2**: tham số `cuisine` đổi thành `cuisineId`, kết quả từ mảng thành `PageResponse` (cần báo Dưỡng).
- `cuisineId`/`cuisineName` là **một** loại món đại diện của quán (giữ như DTO của Dưỡng, thay cho mảng `cuisines` trong bản đề xuất).

200:

```json
{
  "items": [
    {
      "id": 1, "name": "Phở 24 Nguyễn Trãi", "cuisineId": 1, "cuisineName": "Món Việt",
      "address": "5 Nguyễn Trãi, Quận 1", "lat": 10.77, "lng": 106.695, "logoUrl": "https://...",
      "avgRating": 4.5, "ratingCount": 320, "minOrderAmount": 30000,
      "prepTimeMinutes": 20, "openStatus": "OPEN", "distanceKm": 1.2
    }
  ],
  "page": 0, "size": 10, "totalItems": 15, "totalPages": 2
}
```

`GET /api/cuisines` (đã có): `[{ "id": 1, "name": "Món Việt", "iconUrl": "..." }]`

### B2. Chi tiết nhà hàng / chi nhánh

`GET /api/restaurants/{id}` (công khai). 404 nếu không có hoặc không `ACTIVE`.

```json
{
  "id": 13, "name": "Cơm Nhà Demo - Chi nhánh Quận 1", "description": "...",
  "phone": "028...", "address": "...", "lat": 10.7712, "lng": 106.6981,
  "logoUrl": "...", "coverUrl": "...", "openStatus": "OPEN",
  "minOrderAmount": 30000, "prepTimeMinutes": 15, "avgRating": 0.0, "ratingCount": 0,
  "cuisines": [{ "id": 1, "name": "Món Việt", "iconUrl": "..." }],
  "hours": [{ "dayOfWeek": 1, "openTime": "07:00:00", "closeTime": "21:00:00" }],
  "merchant": { "id": 2, "businessName": "Cơm Nhà Demo" },
  "otherBranches": [{ "id": 14, "name": "Cơm Nhà Demo - Chi nhánh Quận 3", "address": "...", "openStatus": "OPEN" }]
}
```

`otherBranches`: các quán `ACTIVE` khác cùng `merchant_id` (thể hiện "một chủ quán nhiều chi nhánh" theo ERD).

### B3. Menu theo nhà hàng (nhóm theo danh mục)

`GET /api/restaurants/{id}/menu` (công khai, Dưỡng làm). Chỉ danh mục `is_active = 1`, sắp theo `sort_order`. Món hết hàng vẫn trả về với `isAvailable: false` để web hiện "Hết món". Mỗi món có sẵn `optionGroups` (tuần 3 seed chưa có tùy chọn nên là mảng rỗng).

```json
{
  "restaurantId": 1,
  "categories": [
    {
      "id": 1, "name": "Món chính", "sortOrder": 1,
      "items": [
        { "id": 1, "name": "Phở bò tái", "description": "...", "price": 55000,
          "imageUrl": "...", "isAvailable": true, "sortOrder": 1, "optionGroups": [] }
      ]
    }
  ]
}
```

### B4. Chi tiết món

`GET /api/restaurants/{restaurantId}/menu-items/{itemId}` (công khai). 404 nếu món không thuộc quán đó, nhóm món bị tắt hoặc quán không `ACTIVE`.
Đặt dưới `/api/restaurants/**` để dùng luôn quy tắc công khai sẵn có trong `SecurityConfig`.

```json
{ "id": 1, "restaurantId": 1, "categoryId": 1, "categoryName": "Món chính",
  "name": "Phở bò tái", "description": "...", "price": 55000, "imageUrl": "...", "isAvailable": true }
```

### B5. Quán của chủ quán đang đăng nhập (cần cho màn hình nhận đơn)

`GET /api/merchant/restaurants` (MERCHANT): các quán thuộc `merchants.owner_user_id = user.id`.

```json
[{ "id": 13, "name": "Cơm Nhà Demo - Chi nhánh Quận 1", "address": "...", "openStatus": "OPEN", "status": "ACTIVE" }]
```

### Seed (`V20261107_01__seed_quan_demo_va_mon.sql`, không sửa V1/V2/V3)

- Bổ sung món cho quán 1, 3, 4, 5, 6 → cùng KFC là **6 quán cũ có từ 10 món trở lên**.
- Chủ chuỗi 12 quán của V2 (`0901234567`) đổi từ hash giả sang hash thật của `Demo@12345`, nên đăng nhập được.
- Chủ quán demo `0922000001`: thương hiệu "Cơm Nhà Demo" có **2 chi nhánh** (Quận 1, Quận 3), mỗi chi nhánh 11 món. Chi nhánh Quận 1 có món "Cơm gà xối mỡ" `is_available = 0` để demo chặn món hết. Món "Cơm bò lúc lắc" có giá khác nhau ở 2 chi nhánh.
- Chủ quán demo `0922000002`: 1 quán "Bún Chả Demo", 10 món.
- Dòng `delivery_fee_rules` chuyển sang Module C (đặt cạnh code tính phí ship).

### Web

- Trang chủ `/` chuyển sang `/restaurants`: ô tìm kiếm, chip danh mục, nút "Gần tôi" (hiện km), lưới thẻ quán, phân trang 12 quán/trang.
- `/restaurants/:id`: thông tin quán, chủ quán, giờ mở cửa, chi nhánh khác (bấm để chuyển), menu theo danh mục, nút "+ Thêm" (hoạt động ở Module C).

---

## Module C: Giỏ hàng và đặt hàng (`feature/week3-cart-order`)

> Đã làm. Code ở package `cart` và `order`. Chỗ khác so với bản đề xuất: trường `available` đổi thành `isAvailable` (giống menu của Dưỡng), thêm giới hạn khoảng cách giao 30 km, seed phí ship nằm ở `V20261107_02__seed_phi_giao_hang.sql`.

Mỗi khách có tối đa 1 giỏ (`carts.user_id` UNIQUE), giỏ gắn với **một** quán (`carts.restaurant_id`). Tuần 3 chưa làm tùy chọn món (topping, size) vì seed chưa có `option_groups`.

### C1. Xem giỏ

`GET /api/cart` (CUSTOMER). Giá luôn đọc lại từ `menu_items`, không lưu giá trong giỏ.

```json
{
  "restaurantId": 1, "restaurantName": "Phở 24 Nguyễn Trãi", "minOrderAmount": 30000,
  "items": [
    { "id": 7, "menuItemId": 1, "name": "Phở bò tái", "imageUrl": "...",
      "unitPrice": 55000, "quantity": 2, "note": "Không hành", "lineTotal": 110000, "isAvailable": true }
  ],
  "itemCount": 2, "subtotal": 110000
}
```

Giỏ trống: `{ "restaurantId": null, "restaurantName": null, "minOrderAmount": 0, "items": [], "itemCount": 0, "subtotal": 0 }`.

### C2. Sửa giỏ

| Method | URL | Body | Kết quả |
|---|---|---|---|
| POST | `/api/cart/items` | `{ "menuItemId": 1, "quantity": 2, "note": "Không hành", "replaceCart": false }` | 200 giỏ mới (như C1) |
| PATCH | `/api/cart/items/{cartItemId}` | `{ "quantity": 3, "note": "..." }` | 200 giỏ mới |
| DELETE | `/api/cart/items/{cartItemId}` | | 200 giỏ mới |
| DELETE | `/api/cart` | | 204 |

Quy tắc:

- `quantity` từ 1 đến 99 (400 nếu sai). `note` tối đa 255 ký tự.
- PATCH gửi cả `quantity` và `note` (`note` rỗng hoặc không gửi = xóa ghi chú).
- Thêm cùng món, cùng ghi chú đã có trong giỏ → cộng dồn số lượng (cộng dồn vượt 99 → 409).
- Món không tồn tại → 404. Món hết (`is_available = 0`), danh mục bị tắt, hoặc quán không `ACTIVE`/`OPEN` → 409.
- Giỏ đang có món của **quán khác** và `replaceCart = false` → 409 `"Giỏ hàng đang có món của quán khác"`. Web hỏi "Xóa giỏ cũ và thêm món này?", đồng ý thì gửi lại với `replaceCart = true`.
- `cartItemId` không thuộc giỏ của mình → 404.
- Xóa món cuối cùng → xóa luôn giỏ.

### C3. Xem trước tiền trước khi đặt

`POST /api/orders/preview` (CUSTOMER), body `{ "deliveryLat": 10.7756, "deliveryLng": 106.7004 }`

```json
{ "subtotal": 110000, "deliveryFee": 15000, "discountAmount": 0, "totalAmount": 125000, "distanceKm": 1.8 }
```

Cách tính (dùng chung với C4, viết thành 1 class riêng để test):

- `subtotal = Σ (giá trong DB × số lượng)`.
- `distanceKm` = Haversine (đã có `common/Haversine`) từ quán đến điểm giao, làm tròn 2 số lẻ.
- Phí ship theo dòng `delivery_fee_rules` đang bật: `base_fee` cho `base_distance_km` đầu, mỗi km tiếp theo (làm tròn lên) cộng `per_km_fee`. Seed đề xuất: 15.000đ cho 2 km đầu, 5.000đ/km sau đó. Chưa tính giờ cao điểm.
- `discountAmount = 0` (voucher là phần của Điệp, tuần sau).
- `totalAmount = subtotal + deliveryFee - discountAmount`.
- Code: `order/OrderPricing.java` (chỉ tính toán, không gọi database), unit test `OrderPricingTest`.
- Xem trước chỉ báo 409 khi giỏ trống hoặc điểm giao xa quá **30 km**. Món hết, quán đóng, đơn tối thiểu được kiểm tra lại khi đặt hàng thật (C4); trang giỏ hàng cũng chặn nút "Thanh toán" trong các trường hợp này.

### C4. Đặt hàng

`POST /api/orders` (CUSTOMER)

```json
{
  "receiverName": "Khách Demo", "receiverPhone": "0911000001",
  "deliveryAddress": "10 Lê Lợi, Bến Nghé, Quận 1", "deliveryLat": 10.7756, "deliveryLng": 106.7004,
  "note": "Gọi trước khi tới", "paymentMethod": "COD"
}
```

- Lấy món từ **giỏ trên server**, client không gửi danh sách món hay giá.
- `paymentMethod` tuần 3 chỉ nhận `COD` (VNPay/MoMo làm sau). `receiverPhone` theo mẫu `0\d{9}`. Tọa độ bắt buộc vì cột `delivery_lat/lng` là NOT NULL; web tuần 3 cho chọn vài địa chỉ mẫu có sẵn tọa độ (chưa có bản đồ).

Toàn bộ chạy trong **một** `@Transactional`:

1. Khóa giỏ của khách (`SELECT ... FOR UPDATE`) để bấm "Đặt hàng" 2 lần không tạo 2 đơn. Giỏ rỗng → 409.
2. Đọc lại từng món + danh mục + quán từ DB. Món hết / quán không `OPEN` → 409 (nêu tên món).
3. `subtotal < min_order_amount` của quán → 409.
4. Tính tiền như C3.
5. INSERT `orders` (status `PLACED`, mã đơn `FD` + `yyMMdd` + 6 số ngẫu nhiên, trùng thì sinh lại), INSERT `order_items` (lưu `item_name`, `unit_price` lúc đặt), INSERT `order_status_history` (`PLACED`).
6. Xóa giỏ.

Lỗi ở bất kỳ bước nào → rollback hết, không có đơn "nửa vời", giỏ còn nguyên.

201: trả chi tiết đơn như D2.

### Test

- Unit test `OrderPricingTest` (6 test): tạm tính, phí ship (0; 1,8; 2; 2,01; 2,1; 3; 3,01; 5 km), tổng không âm, làm tròn khoảng cách.
- Test tích hợp `CartOrderIntegrationTest` (10 test, Testcontainers): thêm/cộng dồn/sửa/xóa giỏ; món hết, quán đóng → 409; giỏ 1 quán và `replaceCart`; khách B không sửa được giỏ khách A → 404; đặt hàng dùng giá **mới nhất trong DB** và xóa giỏ, bấm lần 2 → 409; món hết lúc đặt → 409, không có đơn mới; dưới đơn tối thiểu → 409; sai dữ liệu → 400; giỏ trống, địa chỉ quá xa → 409.
- **Rollback**: test gắn tạm vào `order_items` một ràng buộc `CHECK` từ chối ghi chú `"gia lap loi"`. Khi đặt hàng, INSERT `orders` thành công rồi INSERT `order_items` lỗi → API trả 500, số dòng `orders`, `order_items`, `order_status_history` không đổi, giỏ còn nguyên.
- Phân quyền: MERCHANT gọi `/api/cart` → 403 (trong `SecurityIntegrationTest`).

### Web

- Nút "+ Thêm" ở trang chi tiết quán: chưa đăng nhập thì chuyển sang `/login`; giỏ đang có món quán khác thì hỏi trước khi thay giỏ; thêm xong hiện thanh "🛒 n phần · tổng tiền".
- `/cart`: danh sách món, tăng/giảm/xóa, báo món hết và thiếu đơn tối thiểu, nút "Thanh toán".
- `/checkout`: form người nhận (điền sẵn từ tài khoản) + chọn địa chỉ mẫu, hiện phí ship và tổng (gọi C3), nút "Đặt hàng".
- `/orders/:id/success`: mã đơn, quán, địa chỉ, tổng tiền. Nút "Theo dõi đơn" thêm ở Module D.
- Header hiện "🛒 Giỏ hàng (n)" cho khách. Đăng xuất thì xóa toàn bộ dữ liệu đã tải, để người dùng sau không thấy giỏ của người trước.

---

## Module D: Quản lý đơn (`feature/week3-order-management`)

### Trạng thái tuần 3

Dùng tập con của enum `orders.status` trong V1 (không đổi cấu trúc bảng):

| Trạng thái | Hiển thị |
|---|---|
| `PLACED` | Chờ xác nhận |
| `PREPARING` | Đang chuẩn bị |
| `DELIVERING` | Đang giao |
| `DELIVERED` | Hoàn thành |
| `CANCELLED` | Đã hủy |

Bảng chuyển hợp lệ (mọi chuyển khác → 409 `"Không thể chuyển đơn từ X sang Y"`):

| Từ | Sang | Ai được đổi | Ghi thêm |
|---|---|---|---|
| `PLACED` | `PREPARING` | Chủ quán của quán nhận đơn | `accepted_at = now` |
| `PREPARING` | `DELIVERING` | Chủ quán | |
| `DELIVERING` | `DELIVERED` | Chủ quán (tạm, khi chưa có luồng tài xế) | `delivered_at = now` |
| `PLACED` | `CANCELLED` | Khách (chủ đơn) hoặc chủ quán | bắt buộc `reason`, `cancelled_at = now` |
| `PREPARING` | `CANCELLED` | Chủ quán | bắt buộc `reason` |

Mỗi lần đổi: UPDATE `orders` có điều kiện `WHERE id = :id AND status = :fromStatus` (nếu 0 dòng thì có người vừa đổi trước → 409) và INSERT `order_status_history` (`changed_by` = người đổi), trong một transaction. Luật chuyển viết thành 1 class riêng để unit test.

### D1. Đơn của khách

`GET /api/orders?status=PLACED&page=0&size=10` (CUSTOMER, chỉ đơn của mình, mới nhất trước)

```json
{
  "items": [
    { "id": 5, "code": "FD261030123456", "restaurantId": 1, "restaurantName": "Phở 24 Nguyễn Trãi",
      "status": "PREPARING", "totalAmount": 125000, "itemCount": 2, "createdAt": "2026-10-30T11:20:00" }
  ],
  "page": 0, "size": 10, "totalItems": 1, "totalPages": 1
}
```

### D2. Chi tiết đơn (khách)

`GET /api/orders/{id}` (CUSTOMER). Không phải đơn của mình → 403. Không có → 404.

```json
{
  "id": 5, "code": "FD261030123456", "status": "PREPARING",
  "restaurant": { "id": 1, "name": "Phở 24 Nguyễn Trãi", "phone": "028...", "address": "..." },
  "receiverName": "Khách Demo", "receiverPhone": "0911000001",
  "deliveryAddress": "...", "note": "Gọi trước khi tới", "paymentMethod": "COD",
  "items": [{ "menuItemId": 1, "name": "Phở bò tái", "unitPrice": 55000, "quantity": 2, "note": "Không hành", "lineTotal": 110000 }],
  "subtotal": 110000, "deliveryFee": 15000, "discountAmount": 0, "totalAmount": 125000, "distanceKm": 1.8,
  "cancelReason": null,
  "createdAt": "2026-10-30T11:20:00", "acceptedAt": "2026-10-30T11:22:00", "deliveredAt": null, "cancelledAt": null,
  "history": [
    { "status": "PLACED", "changedAt": "2026-10-30T11:20:00", "note": null },
    { "status": "PREPARING", "changedAt": "2026-10-30T11:22:00", "note": null }
  ]
}
```

### D3. Khách hủy đơn

`POST /api/orders/{id}/cancel` (CUSTOMER), body `{ "reason": "Đặt nhầm món" }` → 200 chi tiết đơn. Chỉ khi `PLACED`, ngược lại 409.

### D4. Đơn của cửa hàng

`GET /api/merchant/orders?restaurantId=13&status=PLACED&page=0&size=20` (MERCHANT)

- Chỉ đơn của các quán mình sở hữu. Không gửi `restaurantId` → tất cả chi nhánh. Gửi `restaurantId` của chủ khác → 403.
- Item giống D1, thêm `receiverName`, `deliveryAddress`.

`GET /api/merchant/orders/{id}` (MERCHANT): như D2, thêm `customerName`. Đơn của quán khác → 403.

### D5. Cửa hàng đổi trạng thái

`PATCH /api/merchant/orders/{id}/status` (MERCHANT), body `{ "status": "PREPARING", "reason": null }` → 200 chi tiết đơn.

- `status` phải là `PREPARING`, `DELIVERING`, `DELIVERED` hoặc `CANCELLED` (400 nếu khác). `CANCELLED` thiếu `reason` → 400.
- Không phải quán của mình → 403. Sai thứ tự → 409.

### Test

- Unit test luật chuyển: mọi cặp hợp lệ/không hợp lệ, đúng/sai vai trò.
- Tích hợp: khách A xem đơn khách B → 403; chủ quán 1 đổi đơn của quán chủ 2 → 403; `PLACED → DELIVERED` → 409; khách hủy khi `PREPARING` → 409.

### Web

- Khách `/orders`: lịch sử đơn, lọc theo trạng thái. `/orders/:id`: chi tiết + thanh tiến trình trạng thái, tự tải lại mỗi 10 giây (TanStack Query `refetchInterval`; realtime bằng WebSocket để An/Điệp làm sau), nút "Hủy đơn" khi đang chờ xác nhận.
- Cửa hàng `/merchant/orders` (Ant Design): chọn chi nhánh, tab theo trạng thái, bảng đơn, nút hành động theo trạng thái ("Nhận đơn", "Bắt đầu giao", "Hoàn thành", "Hủy" có ô lý do), tự tải lại mỗi 10 giây.

---

## Ngoài phạm vi tuần 3 (ghi TODO)

Tùy chọn món/combo, voucher, VNPay/MoMo, tài xế nhận đơn, WebSocket realtime, bản đồ chọn địa chỉ, sổ địa chỉ (`addresses`), kiểm tra giờ mở cửa theo `restaurant_hours` (tuần 3 chỉ xét `open_status`), phụ phí giờ cao điểm, chủ quán tự đăng ký và chờ admin duyệt.
