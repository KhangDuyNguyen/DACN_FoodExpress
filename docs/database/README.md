# Cơ sở dữ liệu

MySQL 8.4, 41 bảng, tạo bằng `backend/src/main/resources/db/migration/V1__init.sql` (Flyway chạy tự động khi khởi động backend).

Sơ đồ ERD (chia 7 nhóm): xem [ERD.md](ERD.md). GitHub hiển thị sơ đồ trực tiếp.

## 41 bảng chia theo nhóm

| Nhóm | Bảng |
|---|---|
| 1. Tài khoản và cửa hàng (8) | users, addresses, merchants, restaurants, restaurant_hours, cuisines, restaurant_cuisines, favorites |
| 2. Thực đơn (6) | menu_categories, menu_items, option_groups, options, combos, combo_items |
| 3. Giỏ hàng và đơn hàng (7) | carts, cart_items, cart_item_options, orders, order_items, order_item_options, order_status_history |
| 4. Thanh toán và khuyến mãi (4) | payments, refunds, vouchers, voucher_usages |
| 5. Giao hàng (5) | drivers, driver_locations, order_assignments, driver_earnings, delivery_fee_rules |
| 6. Đánh giá, thông báo, hỗ trợ (5) | reviews, review_replies, notifications, device_tokens, support_tickets |
| 7. AI (6) | ai_chat_sessions, ai_chat_messages, user_item_interactions, review_analysis, delivery_time_predictions, demand_forecasts |

## Điểm cần nhớ

- `users.role` gồm CUSTOMER, MERCHANT, DRIVER, ADMIN. Tài xế có thêm bảng `drivers`, chủ quán có thêm bảng `merchants`.
- Một chủ quán (`merchants`) có nhiều quán/chi nhánh (`restaurants`).
- `orders` lưu sẵn địa chỉ giao và `order_items` lưu tên món, giá tại thời điểm đặt, nên sửa menu sau này không làm đổi đơn cũ.
- `vouchers.restaurant_id` rỗng nghĩa là voucher toàn sàn.
- Tiền tệ là VND, kiểu `DECIMAL(12,0)`.
- Dữ liệu AI (mô phỏng) chỉ để chạy thử thuật toán, ghi rõ trong báo cáo.
