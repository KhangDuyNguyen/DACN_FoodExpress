# Phân quyền WebSocket Merchant, GPS và Notification

Endpoint STOMP `/ws` hỗ trợ native WebSocket và SockJS. Gửi JWT trong header `Authorization: Bearer <JWT>` của STOMP CONNECT. Backend lấy user ID từ JWT đã xác thực, lấy role và trạng thái tài khoản từ database. JWT đã gửi sai chữ ký, sai định dạng, hết hạn hoặc thuộc tài khoản bị khóa/xóa làm CONNECT bị từ chối. Kết nối guest được duy trì ở mức transport, các destination được bảo vệ đều từ chối SUBSCRIBE guest.

| Topic | Điều kiện SUBSCRIBE |
| --- | --- |
| `/topic/restaurants/{restaurantId}/orders` | Merchant còn quyền truy cập tài khoản và sở hữu nhà hàng. Backend dùng cùng truy vấn với Merchant REST qua `restaurants.merchant_id → merchants.owner_user_id`. ID là số dương kiểu `long`, theo đúng dạng topic. |
| `/topic/orders/{orderId}/location` | Customer đúng chủ đơn. Luồng phân quyền và payload GPS hiện hành được giữ nguyên. |
| `/user/queue/notifications` | Customer hoặc Merchant đã xác thực bằng STOMP CONNECT. Khi subscribe mới, Backend đọc lại role và trạng thái hiện tại của tài khoản. |

Backend xác thực đầy đủ đích SUBSCRIBE, loại wildcard, suffix và ID ngoài phạm vi. Chỉ chấp nhận destination cá nhân chính xác nêu trên. Queue nội bộ đã resolve và đường dẫn chứa định danh tài khoản thuộc quyền quản lý của broker. Client inbound `SEND` hoặc `MESSAGE` không được ghi vào `/topic`, `/queue` hay `/user`. Broker phục vụ `/topic` và `/queue`, prefix ứng dụng `/app` giữ nguyên.

Principal STOMP có tên `user:{id}`. `Authentication.getPrincipal()` vẫn là `AuthUser` để tương thích ACL GPS và Merchant. Publisher lấy cùng định danh từ người nhận inbox đã commit, dùng `convertAndSendToUser`. REST authentication giữ nguyên. Xem [contract Notification](notifications.md) để biết payload.

## Vòng đời quyền và phiên

JWT được kiểm tra ở CONNECT. Quyền sở hữu và trạng thái Merchant được đọc lại cho mỗi SUBSCRIBE mới. Khi mở subscription Notification mới, role từ database phải còn khớp với phiên. Publisher cũng bỏ lượt gửi cho tài khoản đang bị chặn hoặc role ngoài phạm vi. Broker hiện giữ subscription đang mở cho đến UNSUBSCRIBE hoặc DISCONNECT. JWT hết hạn, khóa tài khoản hoặc đổi chủ quán trong lúc đang kết nối chưa có cơ chế tự thu hồi subscription cũ. Reconnect phải xác thực lại. Nếu cần thu hồi realtime, chốt cơ chế quản lý session theo user và nhà hàng, đóng session khi quyền thay đổi hoặc JWT hết hạn, và kiểm thử nhiều instance trước khi áp dụng.

## Phần Web Merchant chờ duyệt

`MerchantOrdersPage.tsx` lấy danh sách quán, đăng ký theo quán chọn hoặc toàn bộ quán, hủy subscriptions khi đổi danh sách và deactivate khi unmount. Logout cùng tab thông qua `AuthProvider` làm route bảo vệ rời trang. Effect STOMP hiện có dependency rỗng và `beforeConnect` giữ header cũ nếu token đã biến mất. Đây là điểm cần review cho đổi tài khoản mà trang vẫn mount hoặc logout ở tab khác.

Phạm vi đề xuất là buộc socket theo token và user của `useAuth`, xác nhận phiên trước reconnect và trước khi cập nhật cache từ callback. Bản phác dưới đây minh họa các điểm chèn ở `MerchantOrdersPage.tsx`. Callback trong `updateSubscriptions` cần mang guard của socket đã đăng ký. Sự kiện `storage` khi token đổi ở tab khác cần đóng socket cũ cả khi socket đang kết nối, rồi hủy listener trong cleanup.

```diff
+ const { token, user } = useAuth()
+ const sessionUserId = user?.id
  useEffect(() => {
-   const token = localStorage.getItem('token')
+   if (!token || !sessionUserId) return
+   let active = true
+   const isCurrentSession = () => active && localStorage.getItem('token') === token
    const client = new Client({
      beforeConnect: () => {
-       const currentToken = localStorage.getItem('token')
-       if (currentToken) {
-         client.connectHeaders = { Authorization: `Bearer ${currentToken}` }
-       }
+       if (!isCurrentSession()) void client.deactivate()
      },
      onConnect: () => {
+       if (!isCurrentSession()) {
+         void client.deactivate()
+         return
+       }
      },
    })
    return () => {
+     active = false
    }
- }, [])
+ }, [sessionUserId, token])
```

Khang review định danh Principal, ACL cá nhân và các prefix broker. Điệp review cấu hình dùng chung với topic đơn Merchant, phạm vi socket Web Merchant, đổi quán, logout và đổi phiên. Cleanup phía Web vẫn cần nghiệm thu trước khi bật UI Notification.
