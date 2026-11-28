# Tích hợp Notification cho Web Merchant và Android

[Notification Inbox và FCM](notifications.md) · [WebSocket security](websocket-security.md)

## Web Merchant

- Trang `/merchant/notifications` nằm trong vùng MERCHANT. Chuông trên header lấy `unreadCount` từ GET. Hai bộ lọc lưu cursor riêng. Mở thông báo có `orderId` sẽ gửi PATCH rồi vào `/merchant/orders`. Route đơn hiện chỉ cung cấp danh sách và bộ lọc chi nhánh tại chỗ.
- Một provider theo tài khoản mở STOMP cá nhân `/user/queue/notifications`. Nội dung toast được xác minh qua REST của phiên. Chuyển chi nhánh giữ nguyên kết nối cá nhân.
- `MerchantOrdersPage.tsx` tiếp tục dùng `/topic/restaurants/{restaurantId}/orders` cho danh sách đơn và các thao tác hiện có. Client chỉ subscribe các quán có trong danh sách của phiên. Logout, đổi token và storage event hủy client cũ và bỏ callback của phòng đã rời.
- Khi kênh cá nhân đang kết nối, toast Notification và hai tiếng chime báo `ORDER_PLACED`. Topic nghiệp vụ tiếp tục làm mới đơn. Nếu kênh cá nhân chưa kết nối, popup nghiệp vụ vẫn có thể hiển thị. Hai nguồn dùng cùng dấu nhận diện theo order ID trong phiên để tránh cảnh báo lặp. Preference âm thanh giữ theo tài khoản.
- Điệp và Khang review phạm vi WebSocket, phối hợp popup và điểm gắn route. Cảnh báo realtime là best-effort. Inbox REST là lịch sử để xem lại.

## Android Driver

- Chuông trong header đỏ dẫn tới `driver-notifications`. Bốn tab và các luồng trạng thái tài xế, polling, countdown và GPS dùng component hiện có.
- Inbox dùng `DriverAuthRepository.accessTokens` của phiên hoạt động và quy tắc hết hạn của `currentAccessToken()`. API này hỗ trợ cả khi tắt Ghi nhớ đăng nhập. Chỉ đăng nhập hoặc restore đã xác thực mới phát token, logout phát null. Kho lưu đĩa tiếp tục dùng quy tắc Ghi nhớ đăng nhập cũ. Hủy GET/PATCH khi đổi phiên, kiểm tra lại token sau khi chờ, và xóa bản ghi khi nhận 401/403.
- Driver dùng cùng phiên bản Firebase Messaging với Customer. Cấu hình tài nguyên theo biến `FIREBASE_DRIVER_APP_ID`, `FIREBASE_DRIVER_API_KEY`, `FIREBASE_DRIVER_PROJECT_ID`, `FIREBASE_DRIVER_SENDER_ID`. Tất cả phải có giá trị để khởi tạo SDK. Khi cấu hình thiếu, REST inbox vẫn dùng được.
- Khi Firebase đã cấu hình, màn inbox cung cấp nút xin quyền Android 13+. Permission denial chỉ ảnh hưởng việc hiện system notification. `driver_new_offer` là channel riêng có rung và âm hệ thống. Channel GPS giữ nguyên.
- Receiver kiểm tra `notificationId` và `eventType` bằng REST dưới token hiện tại. Lời mời phải khớp offer ID và order ID trong API lời mời và còn hạn theo `serverTime`. Bấm lời mời đã xác minh sẽ đưa người dùng về màn nhận đơn hiện có. Nếu lời mời đã hết hiệu lực, inbox báo trạng thái. Push không thực hiện Accept. Khi app ở foreground, Driver tiếp tục nhận lời mời qua UI hiện hữu.
- Khi tiến trình mới chưa restore hoặc đăng nhập để xác thực phiên, receiver bỏ qua data-only. Inbox đồng bộ lại sau khi phiên Driver được xác thực.

## Android Customer

- Header nhà hàng gắn chuông cạnh icon đơn. Route `notifications` dùng chung màn và controller inbox với Driver. RETROFIT yêu cầu token từ phiên chính. App có thể hiển thị trạng thái cần đăng nhập trong khi luồng phiên Customer đang chờ tích hợp.
- Điểm nối cần Khang/Dưỡng duyệt nằm ở `MainActivity` gọi `CustomerAppNavigation`. Truyền `StateFlow<String?>` có JWT của phiên Customer được xác thực vào `accessTokens`. Khi đã có route chi tiết đơn thuộc phiên chính, truyền callback nội bộ `onOpenOrder`. Đối chiếu `MyOrdersViewModel` đang giữ token rỗng và tập route chính trước khi hoàn tất điểm nối.
- Customer giữ hành vi push LEGACY do Firebase hiển thị trong nền. Data-only mới chỉ hiển thị sau khi xác minh quyền với GET theo phiên Customer đang mở. Khi nguồn phiên chính vắng mặt, receiver không có dữ liệu đủ để xác minh.

Diff đề xuất cho lần tích hợp được Khang/Dưỡng duyệt. Hai giá trị mới phải đến từ owner phiên và route đơn của app chính sau khi các phần đó tồn tại.

```diff
- CustomerAppNavigation(inboxClick = inboxClick, onConsumeClick = { inboxClick.value = null })
+ CustomerAppNavigation(
+     accessTokens = verifiedCustomerAccessTokens,
+     onOpenOrder = openOwnedOrder,
+     inboxClick = inboxClick,
+     onConsumeClick = { inboxClick.value = null },
+ )
```

## Vòng đời chung Android

- Badge dùng tổng chưa đọc do Backend trả. GET dùng `limit`, `unreadOnly` và cursor. PATCH gửi ID theo path và chỉ xác nhận đã đọc cho HTTP 204. Sự cố mạng tạm thời giữ danh sách của đúng phiên. Foreground, vào inbox, tín hiệu đã xác minh và thao tác Thử lại là các điểm làm mới.
- Device binding dùng POST/DELETE hiện có. Coroutine hủy yêu cầu cũ khi phiên hoặc FCM token đổi và gỡ binding trước khi đăng ký tiếp. Nếu POST cũ đã đến server, việc hủy ở client không xác định được thứ tự binding cuối cùng. Verify qua REST và so token trước khi hiện thông báo là bước bảo vệ tại client.
- Tra cứu tín hiệu giới hạn ba trang, mỗi trang tối đa 50 item. Khi REST không xác minh được ID, inbox vẫn có thể đồng bộ nhưng app bỏ qua cảnh báo hệ thống của tín hiệu đó. Các chế độ giới hạn chạy nền, TTL và FCM best-effort áp dụng như contract N4.
