# Kế hoạch và API contract tuần 7 — phần của Khang

- Tuần 7 (23–28/11), thứ Bảy 28/11 là mốc MVP. Đóng băng code 09/12.
- Người dùng yêu cầu ngày 26/11: làm phần của Khang cho đến khi xong. Phần còn lại theo `CLAUDE.md` và phân công của Dưỡng ("giỏ hàng và đặt hàng dùng `menu_item_id` và `option_id`"):
  - **M.** Tùy chọn món (size, topping…) trong giỏ và đơn.
  - **N.** Hoàn tiền VNPay khi đơn đã trả tiền bị hủy.
  - **O.** Thanh toán MoMo sandbox.
- Cả tuần 7 làm trên **1 nhánh** `feature/khang-tuan07-hoan-thien`, đã gộp sẵn J, L, K (chưa có PR riêng). Người dùng chỉ cần mở 1 PR. Mỗi module vẫn là các commit riêng.

## Module M. Tùy chọn món trong giỏ và đơn

Bảng có sẵn từ V1: `option_groups` (`is_required`, `min_select`, `max_select`), `options` (`extra_price`, `is_available`), `cart_item_options`, `order_item_options` (lưu tên và giá tùy chọn lúc đặt). Bảng menu là của Dưỡng: chỉ **đọc**.

API đổi (chỉ thêm trường):

| Method | URL | Thay đổi |
|---|---|---|
| POST | `/api/cart/items` | Body thêm `optionIds` (không bắt buộc, mặc định rỗng) |
| GET | `/api/cart` (và mọi API trả giỏ) | Mỗi dòng thêm `options: [{ id, name, extraPrice }]`; `unitPrice` = giá món + tổng giá tùy chọn |
| GET | chi tiết đơn (khách, quán, admin) | Mỗi món thêm `options: [{ name, extraPrice }]` (lấy từ `order_item_options`) |

Luật khi thêm vào giỏ:
- Mỗi `optionId` phải thuộc đúng món đó, đang bán, không trùng → sai thì 400.
- Mỗi nhóm: số tùy chọn chọn phải nằm trong [tối thiểu, `max_select`], trong đó tối thiểu = `max(min_select, 1)` nếu nhóm **bắt buộc**, ngược lại là 0 → sai thì 400 kèm tên nhóm, ví dụ "Vui lòng chọn Size".
- Cùng món, cùng ghi chú **và cùng bộ tùy chọn** thì cộng dồn số lượng; khác thì thành dòng mới.
- **Không tin giá client:** giá tùy chọn đọc từ DB mỗi lần xem giỏ và khi đặt hàng.
- Tùy chọn ngừng bán sau khi đã nằm trong giỏ → dòng đó `isAvailable = false` (giỏ hiện "đã hết"), đặt hàng trả 409.
- Quán xóa hẳn tùy chọn (dòng `cart_item_options` tự xóa theo) hoặc đổi luật nhóm sau khi khách đã thêm → lúc đặt hàng kiểm tra lại bằng **đúng luật lúc thêm** (`OptionRules`), sai thì 409 kèm lý do, bảo khách xóa món và thêm lại.

Khi đặt hàng: `order_items.unit_price` = giá món + giá tùy chọn lúc đặt; từng tùy chọn ghi 1 dòng `order_item_options` (tên, giá lúc đặt), nên menu đổi sau này không làm đổi đơn cũ.

Web:
- Giỏ hàng, thanh toán, theo dõi đơn, admin xem đơn: hiện tùy chọn dưới tên món.
- Trang chi tiết quán (của Dưỡng): món có nhóm tùy chọn thì bấm "+ Thêm" mở hộp chọn tùy chọn rồi mới thêm. Hộp chọn là component riêng `components/cart/OptionPicker.tsx`; trong trang của Dưỡng chỉ sửa tối thiểu chỗ gọi thêm vào giỏ — nhắn Dưỡng biết.
- Trang đơn của quán (của Điệp): hiện tùy chọn dưới tên món (sửa tối thiểu, nhắn Điệp biết) — quán cần biết size/topping để làm món.

Test: thiếu nhóm bắt buộc → 400; chọn quá `max_select` → 400; tùy chọn của món khác → 400; giá dòng = giá món + tùy chọn; cùng bộ tùy chọn thì cộng dồn, khác thì tách dòng; đặt hàng lưu `order_item_options` và chi tiết đơn có tùy chọn; tùy chọn ngừng bán → đặt hàng 409.

### Kết quả Module M (27/11)

- Backend: `OptionRules` (luật chọn, unit test 8 ca), `CartOptionsIntegrationTest` (6 ca, viết trước và chạy đỏ trước khi code). Toàn bộ backend 1075/1075 test xanh.
- Web: `OptionPicker` (radio cho nhóm bắt buộc chọn 1, checkbox cho nhóm chọn nhiều, tùy chọn hết thì khóa, giá cộng dồn trên nút). `npm run build` không lỗi. Đã thử trên app thật: chọn "Thêm cơm" + "Trứng ốp la" → trang thanh toán hiện đúng tùy chọn và giá.
- Cần nhắn bạn khác:
  - **Dưỡng**: (1) `RestaurantDetail.tsx` sửa tối thiểu: món có nhóm tùy chọn thì mở hộp chọn. (2) **Lỗi có sẵn trên `main`**: `MerchantMenuRepository.isOwner` (dòng 33) dùng `m.user_id`, bảng `merchants` không có cột này (cột đúng là `owner_user_id`) → mọi API quản lý menu của chủ quán (`/api/merchant/restaurants/{id}/...`) trả 500, chủ quán không tạo được nhóm tùy chọn. (3) Seed chưa có tùy chọn nào: nên thêm dữ liệu mẫu tùy chọn cho demo.
  - **Điệp**: `MerchantOrdersPage.tsx` sửa tối thiểu: hiện tùy chọn dưới tên món, sửa `key` của danh sách món (1 món có thể nằm ở nhiều dòng). Nút "+ Thêm" ở khối "Gợi ý cho bạn" thêm món không kèm tùy chọn: món có nhóm bắt buộc sẽ báo lỗi "Vui lòng chọn…", nên chuyển khách sang trang quán (`highlightItem`) để chọn.

## Module N. Hoàn tiền VNPay

`TODO(khang)` từ tuần 5–6: đơn đã trả tiền VNPay mà bị hủy (khách hủy lúc chờ xác nhận, quán hủy, hoặc tiền về sau khi đơn đã hủy) thì phải trả lại tiền. Dùng bảng `refunds` có sẵn và API hoàn tiền của VNPay (`vnp_Command=refund`, cùng địa chỉ với `querydr`; thứ tự trường trong chuỗi ký theo tài liệu đã đọc ở tuần 5).

- Khi đơn sang `CANCELLED` mà có giao dịch VNPay `SUCCESS`: trong **cùng transaction** hủy đơn, ghi 1 dòng `refunds` (`PENDING`, số tiền = số tiền đã trả, lý do = lý do hủy). Tiền về sau khi đơn đã hủy (IPN đến muộn) cũng ghi 1 dòng như vậy.
- **Chỉ tác vụ định kỳ của L (mỗi phút) gửi yêu cầu hoàn tiền** sang VNPay: hủy đơn không phải chờ gọi mạng, lỗi mạng thì phút sau tự gửi lại. Hoàn toàn phần (`vnp_TransactionType=02`), gửi kèm `vnp_TransactionNo` lấy từ dữ liệu VNPay đã lưu lúc thanh toán thành công.
- Theo bảng mã của tài liệu refund: `00` (nhận yêu cầu) và `94` (đã gửi yêu cầu trước đó, VNPay đang xử lý) → `refunds.COMPLETED`, `payments.REFUNDED`; `91` (không thấy giao dịch), `95` (giao dịch không thành công bên VNPay) → `REJECTED`, ghi log để admin xử lý tay; còn lại (lỗi mạng, sai chữ ký phản hồi, `99`...) → giữ `PENDING`, lần sau gửi lại.
- Chi tiết đơn thêm `paymentStatus`: `PENDING` / `SUCCESS` / `FAILED` / `REFUNDING` (đang chờ hoàn) / `REFUNDED` / `REFUND_FAILED` (VNPay từ chối hoàn); COD = `null`. Web hiện "đã thanh toán", "đang hoàn tiền", "đã hoàn tiền".

## Module O. Thanh toán MoMo sandbox

Làm theo tài liệu chính thức của MoMo (sẽ tra lại khi bắt đầu, không đoán tham số): tạo giao dịch (chữ ký HMAC-SHA256), IPN, trang quay về hỏi lại MoMo, tự hủy đơn quá hạn và hoàn tiền dùng chung cách làm với VNPay. Khóa `MOMO_PARTNER_CODE`, `MOMO_ACCESS_KEY`, `MOMO_SECRET_KEY` đọc từ `.env` (đã có chỗ trong `.env.example`). Test dùng khóa giả, chưa có khóa thì đặt MoMo trả 409 giống VNPay.
