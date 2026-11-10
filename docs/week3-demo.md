# Demo tuần 3 — luồng đặt hàng cốt lõi

Cả 4 module đã xong, trên 4 nhánh (xem thứ tự merge ở cuối file):

| Module | Nhánh | Nội dung |
|---|---|---|
| A | `feature/week3-auth` | Phân quyền giỏ/đơn theo vai trò, lỗi luôn trả `ApiError`, tài khoản demo |
| B | `feature/week3-restaurant-menu` | Danh sách quán phân trang, chi tiết quán + chi nhánh khác, menu, chi tiết món |
| C | `feature/week3-cart-order` | Giỏ hàng, tính tiền, đặt hàng trong 1 transaction |
| D | `feature/week3-order-management` | Lịch sử đơn, theo dõi trạng thái, màn hình nhận đơn của cửa hàng |

Chi tiết từng API nằm ở [api-contract-week3.md](api-contract-week3.md).

## 1. Chạy dự án từ đầu

Cần cài: JDK 21, Node.js 22+, Docker Desktop (xem [README.md](../README.md) ở gốc repo để biết đầy đủ).

```bash
cp .env.example .env
docker compose up -d mysql
```

```bash
cd backend
./mvnw spring-boot:run          # Git Bash; Windows cmd dùng mvnw.cmd
```

Lần chạy đầu Flyway tự tạo 41 bảng (`V1__init.sql`) và chạy các file seed (`V2`, `V3`, `V20261026_01`, `V20261107_01`, `V20261107_02`). Kiểm tra: http://localhost:8080/api/health, Swagger http://localhost:8080/swagger-ui.html.

```bash
cd web
npm install
npm run dev                     # http://localhost:5173
```

## 2. Tài khoản mẫu

Dữ liệu giả để test, không phải người thật. Mật khẩu chung cho mọi vai trò: **`Demo@12345`**.

| Vai trò | Số điện thoại | Ghi chú |
|---|---|---|
| Khách hàng | `0911000001`, `0911000002` | 2 khách, dùng để test "không xem được đơn của người khác" |
| Chủ quán | `0901234567` | 12 quán (seed tuần 1–2): Phở 24, KFC, Gogi House... |
| Chủ quán | `0922000001` | "Cơm Nhà Demo" — 2 chi nhánh (Quận 1, Quận 3) |
| Chủ quán | `0922000002` | "Bún Chả Demo" — 1 quán |
| Tài xế | `0933000001` | Chưa gắn với luồng đơn (phần của An, chưa tới tuần này) |
| Admin | `0900000001` | |
| Khách bị khóa | `0911000009` | Đăng nhập đúng mật khẩu vẫn báo 403, dùng để test tài khoản `LOCKED` |

Bảng đầy đủ và quy tắc phân quyền: [docs/api/auth.md](api/auth.md).

## 3. Các bước demo luồng đặt hàng

### 3.1. Khách đặt hàng

1. Vào http://localhost:5173, **Đăng nhập** bằng `0911000001` / `Demo@12345`.
2. Trang `/restaurants`: thử ô tìm kiếm, chip lọc theo loại món, nút "📍 Gần tôi", và phân trang ở cuối danh sách (15 quán, 12 quán/trang).
3. Mở một quán bất kỳ, ví dụ **Cơm Nhà Demo - Chi nhánh Quận 1**. Xem giờ mở cửa, mục "Chi nhánh khác" (bấm sang chi nhánh Quận 3), menu theo danh mục. Món **"Cơm gà xối mỡ"** cố tình để hết hàng, nút "+ Thêm" bị khóa.
4. Bấm "+ Thêm" vài món. Thanh giỏ hàng hiện ở cuối trang và trên header (🛒).
5. Vào `/cart`: tăng/giảm số lượng, xóa món. Bấm **Thanh toán**.
6. Trang `/checkout`: chọn 1 trong 3 địa chỉ mẫu (toạ độ có sẵn), xem phí giao thay đổi theo khoảng cách. Bấm **Đặt hàng**.
7. Trang thành công hiện mã đơn, bấm **Theo dõi đơn** sang `/orders/:id`.

### 3.2. Cửa hàng nhận đơn và đổi trạng thái

1. Mở tab ẩn danh khác (hoặc đăng xuất rồi đăng nhập lại), đăng nhập **`0922000001`** / `Demo@12345`.
2. Vào `/merchant/orders`. Tab **"Chờ xác nhận"** sẽ thấy đơn vừa đặt ở bước 3.1.
3. Bấm **"Nhận đơn"** → **"Bắt đầu giao"** → **"Hoàn thành"**. Mỗi bước có thể xem **"Chi tiết"** đơn (món, địa chỉ, tổng tiền).
4. Thử bấm **"Chọn chi nhánh"** ở góc trên để lọc theo "Cơm Nhà Demo - Chi nhánh Quận 3".

### 3.3. Khách xem trạng thái cập nhật

1. Quay lại tab khách, vào `/orders` (hoặc chờ trang `/orders/:id` tự tải lại, tối đa 10 giây).
2. Thấy trạng thái đổi theo đúng các bước cửa hàng vừa bấm, kèm thanh tiến trình 4 bước và lịch sử thời gian từng bước.
3. Đặt một đơn khác rồi bấm **"Hủy đơn"** ngay khi còn "Chờ xác nhận" để xem luồng hủy (phải nhập lý do).

### 3.4. Vài trường hợp lỗi đáng thử

- Khách đăng nhập `0911000002`, cố sửa giỏ hoặc xem đơn của `0911000001` bằng cách sửa URL → 403/404.
- Đăng nhập `0922000002` (Bún Chả Demo), cố mở chi tiết đơn của "Cơm Nhà Demo" → 403.
- Ở giỏ hàng, thêm món của 2 quán khác nhau → hỏi "Xóa giỏ cũ?" trước khi đổi.
- Trong Swagger, gọi `PATCH /api/merchant/orders/{id}/status` với `{"status":"DELIVERED"}` trong khi đơn còn "Chờ xác nhận" → 409 báo không chuyển được.

## 4. Việc còn tồn cho tuần 4 trở đi

Ghi theo module để mỗi người biết phần của mình:

**Chung / hạ tầng**
- WebSocket realtime cho đơn mới và theo dõi trạng thái (hiện web tự tải lại mỗi 10 giây bằng TanStack Query `refetchInterval`) — phần của An, Điệp.
- Chủ quán tự đăng ký và chờ admin duyệt (`merchants.status = PENDING`) — hiện mọi tài khoản `MERCHANT` coi như đã duyệt.

**Module A (Khang)**
- Đăng ký vai trò `MERCHANT` chưa tự tạo dòng `merchants`; vai trò `DRIVER` chưa tự tạo dòng `drivers` khi không đi qua API đăng ký có hỗ trợ riêng.

**Module B (Dưỡng)**
- `option_groups`/`options` (size, topping) seed tuần 3 chưa có dữ liệu, DTO đã có sẵn chỗ (`optionGroups: []`).
- Chưa kiểm tra `restaurant_hours` khi đặt hàng, hiện chỉ xét `open_status`.

**Module C (Khang)**
- Chưa trừ voucher (`discountAmount` luôn là 0) — chờ API voucher của Điệp.
- Thanh toán mới có COD; VNPay/MoMo sandbox chưa làm.
- Địa chỉ giao hàng đang là 3 địa chỉ mẫu hard-code ở `Checkout.tsx`, chưa có sổ địa chỉ (bảng `addresses`) hay bản đồ chọn điểm.
- Giới hạn giao 30 km và chưa tính phụ phí giờ cao điểm.

**Module D (Khang, cần bàn với An/Điệp)**
- Bước "Đang giao → Hoàn thành" hiện tạm để chủ quán bấm; khi An nối xong luồng tài xế thì chuyển bước này cho tài xế xác nhận giao xong.
- Chưa có đánh giá sau khi đơn hoàn thành (phần của Dưỡng).
- Màn hình `/merchant/orders` đang là trang tự viết (Ant Design), cần Điệp xem lại có khớp phong cách các trang admin/merchant khác không.

## 5. Thứ tự merge Pull Request

Các nhánh dựng nối tiếp nhau (B dựa trên A, C dựa trên B, D dựa trên C) nên **phải merge đúng thứ tự A → B → C → D**. Sau khi một nhánh được merge (nhất là nếu GitHub squash-merge), các nhánh sau cần được cập nhật lại từ `main` trước khi mở PR tiếp theo, nếu không PR sẽ hiện lẫn các commit đã merge trước đó.
