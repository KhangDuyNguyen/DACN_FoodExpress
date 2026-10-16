# android - App Android (Kotlin + Jetpack Compose)

Thư mục này **chưa có project**. An tạo bằng Android Studio ở tuần 2 (xem file phân công của An), rồi đẩy lên đây.

## Cách tạo
1. Android Studio → New Project → "Empty Activity" (Jetpack Compose), ngôn ngữ Kotlin.
2. Lưu project vào thư mục `android/` của repo này.
3. Tách thành 3 module:
   - `customer-app`: app khách hàng
   - `driver-app`: app tài xế
   - `core`: dùng chung (Retrofit, Hilt, theme, model API)
4. Thư viện dự kiến: Retrofit + Gson/Moshi, Hilt, Navigation Compose, Coil, Firebase Cloud Messaging, osmdroid hoặc MapLibre (bản đồ OpenStreetMap).
5. Gọi backend: emulator Android truy cập máy tính qua `http://10.0.2.2:8080/api/`. Máy thật dùng IP LAN của máy tính.

## Quy ước làm chung
Mỗi người làm màn hình trong package riêng (ví dụ `...customer.restaurants`, `...customer.checkout`) để hạn chế xung đột khi gộp code.
