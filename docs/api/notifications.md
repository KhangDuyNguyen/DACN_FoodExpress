# Notification Inbox

Hộp thông báo dùng MySQL và hỗ trợ `CUSTOMER`, `DRIVER`, `MERCHANT`. Gửi JWT qua header `Authorization: Bearer <JWT>`. Backend lấy user ID và role của tài khoản từ phiên đã xác thực. Hai API đặt `Cache-Control: no-store` khi thành công.

## GET /api/notifications

| Tham số | Kiểu | Mặc định | Quy tắc |
| --- | --- | --- | --- |
| `limit` | Số nguyên | 20 | Từ 1 đến 50. |
| `unreadOnly` | Boolean | `false` | Chấp nhận `true` hoặc `false`. |
| `cursor` | Chuỗi | Bỏ qua ở trang đầu | Truyền lại nguyên giá trị `nextCursor` của trang trước với cùng bộ lọc. |

Tham số lặp, không được hỗ trợ, rỗng hoặc sai định dạng trả `400`. Client không gửi `userId`.

Ví dụ `200 OK` với dữ liệu minh họa:

```json
{
  "items": [
    {
      "id": 105,
      "type": "ORDER",
      "eventType": "ORDER_DELIVERED",
      "title": "Giao hàng thành công",
      "body": "Đơn hàng của bạn đã được giao thành công.",
      "isRead": false,
      "createdAt": "2026-11-26T08:00:00Z",
      "data": { "orderId": 11, "orderCode": "FD261124575853" }
    }
  ],
  "nextCursor": null,
  "unreadCount": 1
}
```

- Thứ tự là `created_at DESC, id DESC`. Cursor Base64 URL-safe mã hóa mốc sắp xếp gồm thời gian đã lưu và ID. Nó không cấp quyền truy cập. Mọi trang vẫn ràng buộc user ID từ phiên.
- Server lấy thêm một dòng để trả `nextCursor` khi còn trang. Hết dữ liệu trả `nextCursor: null`, danh sách rỗng trả `items: []`.
- `unreadCount` là tổng chưa đọc trong inbox của người gọi, độc lập với giới hạn, cursor và bộ lọc. Số đếm và trang dùng cùng snapshot DB trong một request. Khi có thông báo mới, tải lại trang đầu để nhận các dòng mới hơn cursor.
- `type` nhận `ORDER`, `PROMOTION` hoặc `SYSTEM`. `eventType` được lưu trong `data_json` và xuất thành trường riêng. Bản ghi cũ thiếu loại sự kiện trả `eventType: null`.
- `data` chỉ có các trường hợp lệ trong danh sách `orderId`, `orderCode`, `offerId` và `expiresAt`. Các trường không hợp lệ bị bỏ qua, payload cũ thiếu dữ liệu trả `{}`. `event_key` dùng nội bộ.

## PATCH /api/notifications/{id}/read

Gửi ID dương qua path, không cần body. Bản ghi thuộc tài khoản hiện tại trả `204 No Content` cả ở lần đầu và các lần tiếp theo. Câu lệnh cập nhật ràng buộc cả `id` và `user_id`. Nếu không có dòng đổi trạng thái, service kiểm tra lại quyền sở hữu để phân biệt bản ghi đã đọc và bản ghi không truy cập được.

| HTTP | Điều kiện |
| --- | --- |
| `200` | GET thành công. |
| `204` | PATCH thành công, kể cả đã đọc. |
| `400` | ID PATCH hoặc tham số GET không hợp lệ. |
| `401` | Thiếu JWT, JWT không hợp lệ hoặc hết hạn. |
| `403` | Role ngoài phạm vi, bao gồm ADMIN. |
| `404` | ID PATCH không tồn tại hoặc thuộc tài khoản khác. Hai trường hợp dùng cùng phản hồi. |

Lỗi sử dụng định dạng `ApiError` theo Backend.

## Lưu trữ và chống trùng

`V20261126_01__add_notification_event_key.sql` thêm `event_key VARCHAR(150) NULL` và khóa duy nhất `(user_id, event_key)`. Bản ghi cũ giữ nguyên và có thể cùng để khóa NULL. Các index đang có được giữ lại.

`NotificationInboxService.record` kiểm tra độ dài khóa, tiêu đề và nội dung theo schema, giới hạn metadata, chuẩn hóa khóa sự kiện về chữ thường và dùng INSERT tham số hóa. MySQL chọn một bản ghi khi hai yêu cầu ghi cùng user và khóa. Bản ghi thắng trả `created: true`. Lượt phát lại trả cùng ID và `created: false`. Service chỉ xử lý duplicate MySQL nếu tìm thấy đúng cặp user và khóa, các lỗi database khác được chuyển tiếp.

## Múi giờ

MySQL lưu `created_at` dưới dạng `DATETIME` không có offset. Docker Compose dùng `TZ=Asia/Ho_Chi_Minh`, phù hợp cấu hình JDBC mặc định. Backend đọc wall time dưới dạng `LocalDateTime`, diễn giải theo `app.notification.storage-zone` (mặc định `Asia/Ho_Chi_Minh`) rồi chuyển thành UTC trước khi phản hồi. Ví dụ `2026-11-26 15:00:00` trong DB thành `2026-11-26T08:00:00Z`. Ghi mới đặt rõ giờ lưu theo cùng zone. Với môi trường khác, xác minh múi giờ của dữ liệu legacy trước khi đổi cấu hình.

## Nguồn sự kiện Phase N2-B

| `eventType` | `event_key` nội bộ | Người nhận |
| --- | --- | --- |
| `ORDER_PLACED` | `order:{orderId}:placed` | Customer và chủ Merchant. COD phát khi đặt thành công, VNPay phát sau xác nhận thanh toán. |
| `OFFER_CREATED` | `offer:{offerId}:offered` | Driver được mời. Có `offerId` và `expiresAt` theo giờ của lời mời, chuyển sang UTC. |
| `OFFER_ACCEPTED` | `offer:{offerId}:accepted` | Chủ Merchant, chỉ lần accept thực sự đầu tiên. |
| `ORDER_PREPARING` | `order:{orderId}:preparing` | Customer. |
| `ORDER_READY_FOR_PICKUP` | `order:{orderId}:ready_for_pickup` | Customer và Driver đã được gán, nếu có. |
| `ORDER_DELIVERING` | `order:{orderId}:delivering` | Customer và chủ Merchant. |
| `ORDER_DELIVERED` | `order:{orderId}:delivered` | Customer, chủ Merchant và Driver đã được gán, nếu có. |
| `ORDER_CANCELLED` | `order:{orderId}:cancelled` | Khách hủy thì báo chủ Merchant nếu quán đã thấy đơn. Quán hủy thì báo Customer. VNPay thất bại trước PLACED chỉ báo Customer. Driver chỉ nhận khi lời mời của họ còn hiệu lực lúc đơn bị hủy. |

Các sự kiện dùng `type: ORDER`. Customer lấy từ đơn, Merchant lấy từ chủ quán, Driver lấy từ hồ sơ tài xế sang user ID. Từ chối lời mời giữ nguyên trạng thái đơn và không tạo `ORDER_CANCELLED`. Luồng Merchant tự giao giữ các thông báo Customer và Merchant. Các bước Order trong bảng có thứ tự một chiều theo luồng hiện hành, nên khóa theo đơn và trạng thái. Offer dùng ID từng lời mời.

Khi nhận event nguồn, listener chụp người nhận và metadata trong transaction nghiệp vụ. Snapshot được xử lý với `AFTER_COMMIT`. Một bean riêng ghi inbox qua `REQUIRES_NEW` bằng `NotificationInboxService.record`. Lỗi ghi được bắt sau khi ra khỏi transaction mới. B2 có thể mất thông báo khi tiến trình dừng giữa hai transaction hoặc khi ghi inbox thất bại, và không có retry bền vững trong phase này.

## Tín hiệu STOMP Phase N3-B

Web Customer và Web Merchant kết nối `/ws` bằng native WebSocket hoặc SockJS, gửi JWT trong header `Authorization` của STOMP CONNECT và subscribe đúng `/user/queue/notifications`. Backend dùng định danh nội bộ theo user ID đã xác thực. Kết quả ghi mới chỉ được phát khi transaction inbox đã commit. Tín hiệu có hai trường:

```json
{"notificationId":105,"eventType":"ORDER_DELIVERED"}
```

Các phiên của cùng tài khoản có thể nhận cùng tín hiệu. Client dùng GET REST để lấy nội dung và `unreadCount`, kể cả lúc reconnect. Socket không phát lại lịch sử. Nếu mất tín hiệu hoặc người nhận offline, bản ghi đã commit vẫn nằm trong MySQL. Trường hợp phát lại event có `created=false` không gửi tín hiệu lần nữa. Chi tiết ACL xem [WebSocket security](websocket-security.md).

## Đăng ký thiết bị Android

`POST /api/devices/token` đăng ký hoặc gắn lại token cho Customer hoặc Driver đã xác thực. `DELETE /api/devices/token` xóa binding thuộc tài khoản gọi. Cả hai nhận JSON `{"token":"<FCM_DEVICE_TOKEN>"}` và trả `204` khi thành công, không có response body. Token dài tối đa 255 ký tự, không rỗng và không có khoảng trắng. Thiếu JWT hoặc JWT lỗi trả `401`, role ngoài phạm vi trả `403`, token sai validation trả `400`.

Đăng ký lại cùng token không tạo bản ghi thứ hai. Một tài khoản có thể có nhiều thiết bị. Khi chuyển chủ bằng đúng token, DELETE của chủ cũ không xóa binding mới. So sánh token theo byte khi gắn lại, kiểm tra và xóa. Schema dùng unique theo collation hiện hành, nên biến thể chỉ khác hoa/thường bị giữ ngoài binding mới nếu trùng bản ghi sẵn có.

Contract hiện tại dùng thứ tự request được xử lý tại server. POST đến muộn vẫn có thể gắn lại token cho phiên cũ. Android cần đóng luồng đăng ký cũ khi đổi phiên, hủy các request còn chờ, gỡ binding và xoay token nếu khả thi, đồng thời xác minh quyền sở hữu qua REST bằng JWT hiện tại trước khi hiển thị. Nếu không xác minh được Notification ID bằng danh sách REST, bỏ qua tín hiệu. Endpoint đọc theo ID cần duyệt riêng nếu trở thành phụ thuộc.

## Nguồn FCM Phase N4

`app.fcm.source` mặc định `LEGACY` và có thể đặt `INBOX` ở lúc khởi động. Cả hai dùng `app.fcm.project-id` và ADC hiện có. Khi không cấu hình project, không gửi FCM.

| Mode | Nguồn và dữ liệu |
| --- | --- |
| `LEGACY` | Listener `OrderStatusChangedEvent` gửi notification chung cho Android Customer sau khi nghiệp vụ đổi trạng thái commit, kể cả kết quả VNPay có đổi trạng thái. Luồng này giữ độc lập với ghi inbox như Tuần 6. Đặt COD ban đầu và lời mời Driver nằm ngoài nguồn sự kiện legacy. |
| `INBOX` | Nguồn duy nhất là kết quả `created=true` sau transaction inbox đã commit. Customer nhận PLACED, PREPARING, READY_FOR_PICKUP, DELIVERING, DELIVERED và CANCELLED khi thuộc danh sách nhận. Driver nhận OFFER_CREATED, READY_FOR_PICKUP, DELIVERED và CANCELLED khi liên quan. Merchant và Admin không nhận FCM. |

`INBOX` dùng FCM data-only với `notificationId` và `eventType` dạng chuỗi. Worker có một thread và hàng đợi tối đa 128 tác vụ. Nó đọc lại bản ghi inbox, role, trạng thái tài khoản và binding Android trước từng lượt gửi. Lời mời yêu cầu đúng driver/order, trạng thái OFFERED, đã đến thời điểm mời, đơn còn PLACED chưa gán tài xế, và chưa đến hạn. TTL dùng thời gian còn lại theo đồng hồ Assignment. Android cần kiểm tra lại API lời mời trước khi hiển thị hoặc mở thao tác. Polling và countdown vẫn giữ nghiệp vụ chính.

`UNREGISTERED` chỉ dọn binding còn đúng chủ. Lỗi mạng hoặc lỗi tạm thời giữ binding để lần gửi sau có thể hoạt động. Hàng đợi đầy, Firebase lỗi hoặc client offline có thể làm mất tín hiệu push, còn MySQL và REST giữ inbox. Push đã gửi đi trước lúc token đổi chủ không thể thu hồi. Android giới hạn chạy nền hoặc force-stop cũng có thể làm chậm hoặc mất data-only. Giữ LEGACY cho demo tới khi các client mới được nghiệm thu.
