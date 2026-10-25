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

---

## CẬP NHẬT TUẦN 2 — 21/10/2026

**Người thực hiện:** Lê Tấn Bình An

Phần cập nhật này ghi nhận trạng thái source Android tại ngày 21/10/2026. Nội dung hướng dẫn khởi tạo phía trên được giữ lại để lưu lịch sử ban đầu của project.

### 1. Trạng thái Android Foundation

Android Foundation đã được khởi tạo bằng Gradle Kotlin DSL và gồm ba module:

- `:customer-app`: application dành cho khách hàng, application ID `vn.hutech.foodexpress.customer`.
- `:driver-app`: application dành cho tài xế, application ID `vn.hutech.foodexpress.driver`.
- `:core`: Android library dùng chung cho theme và cấu hình mạng.

`customer-app` và `driver-app` cùng phụ thuộc `core`; `core` không phụ thuộc ngược vào hai application module. Hai ứng dụng có application ID riêng nên có thể build, cài đặt và chạy độc lập trên cùng một Emulator.

Baseline hiện tại:

- Android Gradle Plugin `8.11.1`, Gradle Wrapper `8.13`.
- Kotlin và Compose compiler plugin `2.2.21`.
- `compileSdk`/`targetSdk` 36, `minSdk` 26.
- Gradle chạy bằng JDK 21; Java/Kotlin bytecode target 17.
- Compose BOM `2025.11.01`, Material 3 và theme sáng `FoodExpressTheme` dùng chung.

### 2. Công nghệ đã tích hợp

- Retrofit `3.0.0` và Gson converter trong `core`.
- Hilt `2.57.2` với KAPT; mỗi app có lớp `Application` riêng và `MainActivity` là Hilt entry point.
- Navigation Compose `2.9.8`; mỗi app có `NavHost` và back stack riêng.
- Coil Compose `3.3.0`; đã dùng ảnh drawable local để kiểm tra hiển thị, không phụ thuộc Internet hay backend.
- Driver App có luồng UI prototype: Splash → Onboarding 1 → Onboarding 2 → Onboarding 3 → màn giải thích quyền → Login placeholder.
- Customer App hiện chỉ có hai màn Compose thử nghiệm để kiểm tra Navigation và Coil, chưa phải giao diện nghiệp vụ hoàn chỉnh.

Các phần **chưa triển khai** trong Android Foundation Tuần 2 gồm: đăng nhập thật, hồ sơ tài xế, Dashboard, gọi API nghiệp vụ, GPS realtime, bản đồ, gán/nhận đơn, WebSocket, FCM, thanh toán và xin runtime permission. Màn giải thích quyền trong Driver App chỉ là UI giới thiệu; nút bấm chưa yêu cầu quyền hệ thống. Login hiện vẫn là placeholder.

### 3. Clone và mở project Android

Yêu cầu môi trường tối thiểu:

- Android Studio có thể làm việc với AGP `8.11.1`.
- JDK 21 được chọn làm **Gradle JDK**.
- Android SDK Platform 36 đã được cài; nên tạo AVD API 36 hoặc API 37 để chạy thử.
- Không cần cài Gradle toàn hệ thống vì repository đã có Gradle Wrapper.

Các bước thực hiện:

1. Clone repository và vào thư mục dự án:

   ```bash
   git clone https://github.com/KhangDuyNguyen/DACN_FoodExpress.git
   cd DACN_FoodExpress
   ```

2. Trong Android Studio, chọn **Open** và mở trực tiếp thư mục `android/`, không mở riêng từng module.
3. Vào **Settings → Build, Execution, Deployment → Build Tools → Gradle**, chọn JDK 21 tại mục **Gradle JDK**.
4. Chờ Gradle Sync hoàn tất. Android Studio sẽ tạo `local.properties` theo Android SDK trên từng máy; không commit file này.
5. Có thể kiểm tra bằng Gradle Wrapper trên Windows:

   ```powershell
   cd android
   .\gradlew.bat projects
   .\gradlew.bat :core:assembleDebug
   .\gradlew.bat :customer-app:assembleDebug
   .\gradlew.bat :driver-app:assembleDebug
   ```

   Trên macOS/Linux, dùng `./gradlew` thay cho `.\gradlew.bat`.

### 4. Chạy hai ứng dụng trên Emulator

1. Mở **Device Manager** và khởi động một Android Emulator phù hợp.
2. Chọn run configuration `customer-app`, sau đó bấm **Run** để cài và mở Customer App.
3. Chọn run configuration `driver-app`, sau đó bấm **Run** để cài và mở Driver App.
4. Nếu Android Studio chưa tạo run configuration, chọn **Edit Configurations → Android App**, rồi tạo cấu hình tương ứng với module cần chạy.

Hai app có thể cùng tồn tại trên một Emulator. Driver App mở luồng Splash/Onboarding; Customer App mở màn thử nghiệm Navigation/Coil. Các màn này dùng để xác minh nền tảng kỹ thuật, không chứng minh nghiệp vụ khách hàng hoặc tài xế đã hoàn thành.

### 5. Địa chỉ backend khi chạy Emulator

Debug build đang cấu hình base URL:

```text
http://10.0.2.2:8080/api/
```

Trong Android Emulator, `10.0.2.2` trỏ về máy tính đang chạy Emulator; đây không phải địa chỉ của Emulator và cũng không phải địa chỉ dùng cho máy Android thật. Cổng `8080` khớp cấu hình backend hiện tại, còn `/api/` là tiền tố API của dự án. Muốn kết nối, backend phải đang chạy trên máy tính và endpoint cụ thể phải tồn tại.

Cấu hình trên mới chỉ thiết lập Retrofit và base URL cho debug. Android App chưa có API service nghiệp vụ và **chưa xác minh gọi backend thực tế thành công**. Release build dùng URL placeholder chưa cấu hình và không được bật cleartext HTTP chỉ để kết nối backend local.

### 6. Kết quả kiểm thử đã ghi nhận

- Gradle Wrapper `8.13` chạy với JDK 21.
- `gradlew projects`, `:core:assembleDebug`, `:customer-app:assembleDebug` và `:driver-app:assembleDebug` đã chạy thành công trong quá trình triển khai Tuần 2.
- Hilt/KAPT đã sinh component và biên dịch thành công; Retrofit được cung cấp qua Hilt trong `core`.
- Theme dùng chung biên dịch và được cả hai app sử dụng.
- Navigation Compose đã được kiểm tra với các route độc lập; back stack của màn thử nghiệm hoạt động theo luồng đã định.
- Coil đã hiển thị ảnh drawable local trong màn thử nghiệm.
- Customer App và Driver App đã mở trên Android Emulator API 37.
- Luồng Driver Splash → Onboarding 1–4 → Login placeholder đã được chạy thử trên Emulator. Màn giải thích quyền không xin quyền Android thật.

Phạm vi trên chỉ xác nhận build debug, khởi chạy ứng dụng, điều hướng và UI prototype. Chưa kiểm thử API nghiệp vụ, dữ liệu backend, GPS, bản đồ, WebSocket, FCM, thanh toán hoặc luồng giao hàng hoàn chỉnh.

### 7. Kiểm thử chưa hoàn thành

- Chưa thực hiện clean clone và chạy project trên máy của ba thành viên còn lại.
- Chưa xác minh với các phiên bản Android Studio, JDK và Android SDK khác trên máy thành viên.
- Chưa kiểm thử trên thiết bị Android thật.
- Chưa kiểm thử gọi backend thật từ Emulator hoặc thiết bị thật.
- Chưa kiểm thử release build, cấu hình release base URL hoặc môi trường production.
- Chưa kiểm thử runtime permission, mất mạng, cấu hình proxy/firewall và các chức năng realtime.

Mục tiêu “cả 4 thành viên clone và chạy được” chỉ được xem là hoàn thành sau khi ba thành viên còn lại tự clean clone, Gradle Sync, build và mở được module họ cần trên máy của mình.

### 8. Lưu ý phối hợp nhóm

- Tuân thủ `docs/CONVENTIONS.md` và `docs/GIT-WORKFLOW.md`; không tạo quy ước Android riêng mâu thuẫn với quy ước chung.
- Làm việc trên branch riêng, không push trực tiếp lên `main`; Pull Request cần người khác xem trước khi gộp.
- Mỗi người phát triển trong package/phạm vi được phân công, không tự ý sửa code của thành viên khác.
- Trao đổi trước khi sửa `settings.gradle.kts`, version catalog, Gradle root, `core`, theme hoặc Navigation chung vì các file này dễ gây conflict cho cả nhóm.
- Không commit `local.properties`, `.gradle/`, `.idea/`, thư mục `build/`, APK, token, API key hoặc đường dẫn SDK/JDK cá nhân.
- Không tự nâng phiên bản AGP, Gradle, Kotlin, Compose hoặc thư viện dùng chung khi chưa thống nhất với nhóm.
