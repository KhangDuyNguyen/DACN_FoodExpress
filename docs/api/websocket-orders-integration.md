# Tích hợp WebSocket STOMP Đơn hàng mới (Điệp ↔ Khang)

Tài liệu mô tả cơ chế tích hợp sự kiện đơn hàng mới giữa module đặt hàng/thanh toán (Khang) và kênh WebSocket đẩy thông báo realtime cho cửa hàng (Điệp).

## 1. Kiến trúc sự kiện (Spring Application Event)

Để tránh phụ thuộc vòng giữa nghiệp vụ đặt hàng/thanh toán và WebSocket, hệ thống sử dụng **Spring Application Event**:

- **Event class:** `vn.hutech.fooddelivery.order.event.OrderPlacedEvent`
  ```java
  public record OrderPlacedEvent(
          long orderId,
          long restaurantId,
          OrderDetailResponse order
  ) {}
  ```
- **Listener:** `vn.hutech.fooddelivery.order.event.OrderEventListener`
  - Dùng `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` để đảm bảo chỉ broadcast khi transaction lưu đơn hàng vào MySQL đã commit thành công.
  - Nếu giao dịch đặt hàng hoặc thanh toán thất bại / rollback, sự kiện **không** được phát đi.

## 2. Điểm tích hợp hiện tại và mở rộng thanh toán Online

### Đã tích hợp (COD - `OrderService.createOrder`):
Tại cuối phương thức `@Transactional createOrder(...)` trong `OrderService.java`, sau khi xóa giỏ hàng và gọi `getDetail(orderId)`:
```java
if (eventPublisher != null) {
    eventPublisher.publishEvent(new OrderPlacedEvent(orderId, restaurant.id(), response));
}
```

### Dành cho Khang khi bổ sung thanh toán Online (VNPay / MoMo):
- Đối với đơn `VNPAY` hoặc `MOMO` khởi tạo ở trạng thái `PENDING_PAYMENT`: **KHÔNG** phát `OrderPlacedEvent` lúc mới tạo đơn chờ thanh toán.
- Chỉ khi nhận callback/IPN thanh toán thành công và cập nhật trạng thái đơn sang `PLACED` trong database thành công, hãy gọi:
  ```java
  OrderDetailResponse detail = orderService.getDetail(orderId);
  eventPublisher.publishEvent(new OrderPlacedEvent(orderId, detail.restaurant().id(), detail));
  ```

## 3. Cấu hình WebSocket STOMP

- **Endpoint:** `/ws` (hỗ trợ cả native WebSocket `ws://.../ws` và SockJS fallback).
- **Topic broadcast:** `/topic/restaurants/{id}/orders`
- **Payload (`MerchantOrderSummaryResponse`):** Chỉ chứa các trường cần thiết cho cửa hàng xử lý đơn (không phát tán thông tin nhạy cảm của tài khoản khách hàng):
  ```json
  {
    "id": 101,
    "code": "FD2611110101",
    "restaurantId": 1,
    "restaurantName": "Cơm Tấm Demo",
    "status": "PLACED",
    "totalAmount": 65000,
    "itemCount": 2,
    "receiverName": "Nguyễn Văn Khách",
    "receiverPhone": "0911000001",
    "deliveryAddress": "10 Lê Lợi, Q1",
    "note": "Ít cơm, nhiều sườn",
    "paymentMethod": "COD",
    "items": [
      {
        "menuItemId": 5,
        "name": "Cơm sườn nướng",
        "unitPrice": 25000,
        "quantity": 2,
        "note": "Không hành",
        "lineTotal": 50000
      }
    ],
    "createdAt": "2026-11-11T20:00:00"
  }
  ```

## 4. Đồng bộ phía Frontend (`/merchant/orders`)

- Sử dụng `@stomp/stompjs`, tự động reconnect mỗi 5 giây khi mất mạng và hiển thị badge trạng thái kết nối (`Online` / `Connecting` / `Offline`).
- Chống trùng đơn hàng qua bộ nhớ tạm `processedOrderIdsRef` và kiểm tra trùng `id` trong cache TanStack Query.
- Khi kết nối lại (`onConnect`), tự động invalidate query `['merchant-orders']` để gọi `GET /api/merchant/orders` đồng bộ lại toàn bộ đơn bị sót trong thời gian mất kết nối.

