# Food Delivery - Hệ thống web và ứng dụng di động giao đồ ăn nhanh

Đồ án chuyên ngành 2026-2027, Khoa Công nghệ Thông tin, chuyên ngành Công nghệ Phần Mềm.
Giảng viên hướng dẫn: Trịnh Đồng Thạch Trúc.

## Nhóm thực hiện (lớp 23DTHA5)

| Thành viên | MSSV |
|---|---|
| Nguyễn Duy Khang | 2380600985 |
| Trần Xuân Dưỡng | 2380614779 |
| Lê Tấn Bình An | 2380600006 |
| Sầm Xuân Điệp | 2380614940 |

## Công nghệ

| Thành phần | Công nghệ |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Maven, SQL thuần (`NamedParameterJdbcTemplate`), Flyway |
| Database | MySQL 8.4 (chạy bằng Docker) |
| Realtime, tài liệu API | WebSocket (STOMP), springdoc-openapi (Swagger UI) |
| Web | React + TypeScript + Vite, Tailwind CSS, Ant Design, TanStack Query, Axios |
| App | Kotlin + Jetpack Compose (Android) |
| AI | Python FastAPI, scikit-learn, pandas, statsmodels, LLM API |
| Thanh toán, thông báo | VNPay/MoMo sandbox, Firebase Cloud Messaging |

## Cấu trúc thư mục

```
food-delivery/
├── backend/            Spring Boot (API, WebSocket, Flyway)
│   └── src/main/resources/db/migration/V1__init.sql   41 bảng MySQL
├── web/                React: khách (/), cửa hàng (/merchant), admin (/admin)
├── android/            App Android (An tạo project ở tuần 2)
├── ai-service/         Service AI (Python FastAPI)
├── docs/               Quy ước làm việc, ERD, file phân công
├── docker-compose.yml  MySQL + ai-service
└── .env.example        Mẫu biến môi trường
```

## Chạy lần đầu

Cần cài: JDK 21, Node.js (22 trở lên), Docker Desktop, Python 3.12, Android Studio (cho An). Không cần cài Maven, backend đã có sẵn `mvnw` (tự tải Maven ở lần chạy đầu).

1. Tạo file biến môi trường (không commit file `.env`):
   ```bash
   cp .env.example .env
   ```
2. Chạy MySQL:
   ```bash
   docker compose up -d mysql
   ```
3. Chạy backend (Flyway tự tạo 41 bảng ở lần chạy đầu):
   ```bash
   cd backend
   mvnw.cmd spring-boot:run        # Windows
   ./mvnw spring-boot:run          # macOS / Linux
   ```
   Hoặc mở thư mục `backend` bằng IntelliJ và chạy `FoodDeliveryApplication`.
   - Kiểm tra: http://localhost:8080/api/health
   - Swagger UI: http://localhost:8080/swagger-ui.html
4. Chạy web:
   ```bash
   cd web
   npm install
   npm run dev
   ```
   Mở http://localhost:5173
5. Chạy service AI (khi cần): xem [ai-service/README.md](ai-service/README.md).
6. App Android: xem [android/README.md](android/README.md).

## Phân công

Mỗi người có phần backend, giao diện và một phần AI riêng. Chi tiết việc từng tuần, danh sách trang/màn hình, API và deadline nằm trong [docs/phan-cong](docs/phan-cong).

| Người | Backend | Giao diện chính | AI |
|---|---|---|---|
| Khang | auth, user, cart, order, payment, security | Web/app khách: đăng nhập, giỏ hàng, thanh toán, hồ sơ. Admin: user, đơn toàn sàn, phí ship | Dự đoán thời gian giao (nếu kịp) |
| Dưỡng | restaurant, menu, review | Web/app khách: trang chủ, chi tiết quán, lịch sử + đánh giá. Dashboard cửa hàng: menu, thông tin quán, đánh giá | Phân tích đánh giá |
| An | driver, assignment, tracking, notification | App tài xế (7 màn), theo dõi đơn, thông báo | Gán tài xế tối ưu |
| Điệp | promotion, admin, report, support, ai | Admin: tổng quan, duyệt, voucher, hỗ trợ. Dashboard cửa hàng: đơn realtime, voucher, thống kê | Gợi ý món, chatbot, dự báo doanh thu |

## Mốc thời gian

| Mốc | Nội dung |
|---|---|
| Tuần 1 (12/10 - 18/10) | Chốt đề tài, ERD, dựng repo, phân công |
| 28/11/2026 | Hết tuần 7: xong MVP |
| Tuần 8 (30/11 - 05/12) | Thầy đánh giá % hoàn thành |
| 09/12/2026 | Đóng băng code |
| 12/12/2026 | Nộp đồ án |

Deadline mỗi tuần là thứ Bảy.

## Tài liệu

- [Quy ước làm việc (Git, code, API, database)](docs/CONVENTIONS.md)
- [Cơ sở dữ liệu và sơ đồ ERD](docs/database/README.md)

## Lưu ý bảo mật

Không commit file `.env`, API key, mật khẩu, file khóa. Nếu lỡ commit, đổi key ngay rồi báo cả nhóm.
