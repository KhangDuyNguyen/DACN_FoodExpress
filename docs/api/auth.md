# API đăng nhập và phân quyền (Khang)

Tài liệu cho Dưỡng, An, Điệp: cách lấy token, cách gửi token, ai được gọi API nào. Thử trực tiếp trong Swagger: http://localhost:8080/swagger-ui.html

## 1. Đăng ký

`POST /api/auth/register` (công khai)

```json
{
  "fullName": "Nguyễn Văn A",
  "phone": "0911111111",
  "email": "a@example.com",
  "password": "matkhau123",
  "role": "CUSTOMER"
}
```

- `phone`: 10 chữ số, bắt đầu bằng `0`. `email` không bắt buộc. `password` từ 8 đến 72 ký tự.
- `role` chỉ nhận `CUSTOMER`, `MERCHANT`, `DRIVER`. Không đăng ký được `ADMIN`.
- Trùng số điện thoại hoặc email trả **409**. Sai dữ liệu trả **400**, chi tiết từng trường nằm trong `fieldErrors`.

Kết quả **201**:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": {
    "id": 2, "fullName": "Nguyễn Văn A", "phone": "0911111111", "email": "a@example.com",
    "role": "CUSTOMER", "status": "ACTIVE", "avatarUrl": null, "createdAt": "2026-10-25T09:00:00"
  }
}
```

## 2. Đăng nhập

`POST /api/auth/login` (công khai)

```json
{ "identifier": "0911111111", "password": "matkhau123" }
```

- `identifier` là số điện thoại **hoặc** email.
- Thành công trả **200**, nội dung giống kết quả đăng ký.
- Sai tài khoản hoặc sai mật khẩu trả **401** với cùng một câu `"Sai tài khoản hoặc mật khẩu"`.
- Mật khẩu đúng nhưng tài khoản `LOCKED`/`DELETED` trả **403** `"Tài khoản đã bị khóa"`.
- User mẫu trong V2 (`0901234567`) có hash giả nên **không đăng nhập được**.
- Tài khoản mẫu đăng nhập được (dữ liệu giả, file `V20261026_01__seed_tai_khoan_demo.sql`), mật khẩu chung `Demo@12345`:

  | Vai trò | Số điện thoại | Ghi chú |
  |---|---|---|
  | CUSTOMER | `0911000001`, `0911000002` | 2 khách để test "không xem được đơn của người khác" |
  | MERCHANT | `0922000001`, `0922000002` | Quán của từng người do seed của Dưỡng gắn vào |
  | DRIVER | `0933000001` | Chưa có dòng `drivers` (phần của An) |
  | ADMIN | `0900000001` | |
  | CUSTOMER bị khóa | `0911000009` | Đăng nhập đúng mật khẩu vẫn trả 403 |

## 3. Gửi token

Mọi API cần đăng nhập đều phải gửi header:

```
Authorization: Bearer <token>
```

- Token hết hạn sau 1440 phút (24 giờ). Hết hạn thì đăng nhập lại.
- **Swagger**: bấm nút **Authorize** ở góc phải, dán token (không cần gõ chữ `Bearer`).
- **Web**: `src/api/client.ts` tự gắn token. Trong component dùng `const { user, logout } = useAuth()` (`src/auth/useAuth.ts`). Muốn chặn trang theo vai trò thì bọc route bằng `<ProtectedRoute roles={['MERCHANT']} />`.
- **Android**: thêm header trên vào OkHttp interceptor. Emulator gọi `http://10.0.2.2:8080/api/`.
- WebSocket `/ws/**` đang công khai ở tầng HTTP. Nếu cần biết ai đang kết nối thì An kiểm tra token khi STOMP CONNECT.

`GET /api/users/me`: trả thông tin người đang đăng nhập (không bao giờ có mật khẩu).

## 4. Bảng quyền theo vai trò

| Đường dẫn | Ai được gọi |
|---|---|
| `/api/health`, `/api/auth/**`, Swagger, `/ws/**` | Mọi người (không cần token) |
| `GET /api/restaurants/**`, `GET /api/cuisines` | Mọi người (không cần token) |
| `/api/payments/*/return`, `/api/payments/*/ipn` | Mọi người (VNPay/MoMo gọi về) |
| `/api/admin/**` | Chỉ `ADMIN` |
| `/api/merchant/**` | Chỉ `MERCHANT` |
| `/api/driver/**` | Chỉ `DRIVER` |
| `/api/cart/**`, `/api/orders/**` | Chỉ `CUSTOMER` (từ tuần 3) |
| Còn lại (ví dụ `/api/users/me`) | Đã đăng nhập, vai trò nào cũng được |

Lỗi nào không lường trước cũng được `GlobalExceptionHandler` đổi thành `ApiError` (JSON hỏng → 400, sai kiểu tham số → 400, sai đường dẫn → 404, sai method → 405, lỗi khác → 500 kèm câu chung, chi tiết chỉ ghi log ở server).

Lưu ý:
- `POST/PUT/DELETE /api/restaurants/**` **không** công khai. API sửa quán/menu của chủ quán nên đặt dưới `/api/merchant/...`.
- Bảng trên chỉ kiểm tra **vai trò**. **Quyền sở hữu** (chủ quán chỉ sửa quán của mình, khách chỉ xem đơn của mình) mỗi người phải tự kiểm tra trong Service.
- Mỗi request backend đều đọc lại `role` và `status` từ database, nên khi admin khóa tài khoản thì tài khoản đó bị chặn ngay, không phải chờ token hết hạn.

## 5. Lấy người dùng hiện tại trong backend

```java
// Cach 1: trong controller
@GetMapping("/api/merchant/restaurants")
public List<RestaurantDTO> myRestaurants(@AuthenticationPrincipal AuthUser user) {
    return restaurantService.findByOwner(user.id());
}

// Cach 2: trong service (nem 401 neu chua dang nhap)
AuthUser user = CurrentUser.get();
if (!restaurant.ownerId().equals(user.id())) {
    throw new ForbiddenException("Bạn không phải chủ quán này");
}
```

`AuthUser(long id, String role)` nằm trong package `vn.hutech.fooddelivery.security`. Có thể dùng thêm `@PreAuthorize("hasRole('ADMIN')")` trên method vì method security đã được bật.

## 6. Định dạng lỗi

Mọi lỗi đều trả về theo `ApiError`:

```json
{ "status": 401, "message": "Bạn chưa đăng nhập hoặc phiên đăng nhập đã hết hạn", "fieldErrors": {}, "timestamp": "2026-10-25T02:00:00Z" }
```

| Mã | Khi nào | Exception trong `common` |
|---|---|---|
| 400 | Sai dữ liệu (Bean Validation) | (tự động) |
| 401 | Không có token, token sai hoặc hết hạn, sai mật khẩu | `UnauthorizedException` |
| 403 | Sai vai trò, không phải chủ dữ liệu | `ForbiddenException` |
| 404 | Không thấy dữ liệu | `NotFoundException` |
| 409 | Trùng dữ liệu | `ConflictException` |

API danh sách có phân trang trả `PageResponse<T>`: `{ items, page, size, totalItems, totalPages }` (`page` bắt đầu từ 0). Tạo bằng `PageResponse.of(items, page, size, totalItems)`.

## 7. Tài khoản admin đầu tiên

Đặt trong file `.env` ở thư mục gốc rồi khởi động lại backend:

```
ADMIN_PHONE=0900000000
ADMIN_PASSWORD=mot_mat_khau_manh
```

Backend chỉ tạo admin khi database **chưa có** user `ADMIN` nào. Đăng nhập bằng số điện thoại và mật khẩu trên.
