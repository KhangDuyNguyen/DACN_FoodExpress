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

## 📐 Architecture Overview

### System Architecture
```
┌─────────────────────────────────────────────────────────────┐
│                        Load Balancer                         │
│                     (Future: Nginx/AWS ELB)                  │
└─────────────────────────────────────────────────────────────┘
                              │
                ┌─────────────┴─────────────┐
                │                           │
        ┌───────▼────────┐         ┌───────▼────────┐
        │   Web Client   │         │  Mobile Apps   │
        │  (React/Vite)  │         │   (Android)    │
        │                │         │                │
        │ • Customer UI  │         │ • Customer App │
        │ • Merchant UI  │         │ • Driver App   │
        │ • Admin UI     │         │                │
        └────────┬───────┘         └────────┬───────┘
                 │                          │
                 │  REST API + WebSocket    │
                 │                          │
        ┌────────▼──────────────────────────▼────────┐
        │          Spring Boot Backend               │
        │  Port: 8080 (dev), env-based (prod)        │
        ├────────────────────────────────────────────┤
        │  • REST Controllers (JSON)                 │
        │  • WebSocket/STOMP (Real-time tracking)    │
        │  • JWT Authentication                      │
        │  • Business Logic Services                 │
        │  • JDBC Repositories (SQL thuần)           │
        └────────┬───────────────────────┬───────────┘
                 │                       │
         ┌───────▼────────┐     ┌───────▼────────┐
         │  MySQL 8.4     │     │  AI Service    │
         │   (Docker)     │     │  (FastAPI)     │
         │                │     │                │
         │ • 42 tables    │     │ • ML models    │
         │ • Flyway       │     │ • Python 3.12  │
         └────────────────┘     └────────────────┘
```

### Component Breakdown

#### Backend (Spring Boot 3.5)
- **Controllers:** REST endpoints + WebSocket handlers
- **Services:** Business logic, validation
- **Repositories:** Direct SQL queries với `NamedParameterJdbcTemplate`
- **Security:** JWT-based auth, role-based access
- **Real-time:** STOMP over WebSocket cho order tracking
- **Database:** Flyway migrations, MySQL 8.4

**Key Packages:**
- `auth/` - Authentication & authorization
- `user/` - User management (customer, merchant, driver, admin)
- `restaurant/` - Restaurant & menu
- `order/` - Order management & lifecycle
- `payment/` - VNPay/COD integration
- `driver/` - Driver assignment & tracking
- `review/` - Reviews & ratings
- `tracking/` - Real-time GPS tracking
- `notification/` - FCM push notifications
- `report/` - Analytics & reports

#### Web (React + TypeScript)
- **Customer:** Browse → Cart → Checkout → Track → Review
- **Merchant:** Menu management → Orders → Reviews → Reports
- **Admin:** User management → System oversight

**Tech Stack:**
- React 18 + TypeScript
- Vite (dev server + build)
- TanStack Query (server state)
- Ant Design + Tailwind CSS
- Axios (HTTP client)
- React Router (routing)

#### Mobile (Android - Kotlin)
- **Customer App:** Browse restaurants, view menu, track orders, leave reviews
- **Driver App:** Accept orders, GPS tracking, delivery management, earnings

**Tech Stack:**
- Kotlin + Jetpack Compose
- Hilt (DI)
- Retrofit (HTTP)
- Coroutines + Flow (async)
- Vietmap SDK (Maps)
- FCM (Push notifications)
- WorkManager (Background tasks)

#### AI Service (Python FastAPI)
- Sentiment analysis (reviews)
- Driver assignment optimization
- Delivery time prediction
- Revenue forecasting

---

## 📚 Documentation

| Document | Description |
|----------|-------------|
| [README.md](README.md) | This file - project overview |
| [CONVENTIONS.md](docs/CONVENTIONS.md) | Coding standards & Git workflow |
| [TESTING_GUIDE.md](TESTING_GUIDE.md) | How to test the system |
| [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md) | Current limitations & future work |
| [Database README](docs/database/README.md) | Database schema & ERD |
| [API Docs](http://localhost:8080/swagger-ui.html) | Swagger UI (when backend running) |

---

## 🧪 Testing

See [TESTING_GUIDE.md](TESTING_GUIDE.md) for comprehensive testing instructions.

**Quick Start:**
```bash
# Backend tests
cd backend && ./mvnw test

# Web build check
cd web && npm run build

# Android tests
cd android && ./gradlew test
```

---

## 🚀 Deployment (Future)

Currently running in development mode. For production deployment:

1. **Backend:** Railway, Render, AWS ECS
2. **Web:** Vercel, Netlify, AWS S3 + CloudFront
3. **Database:** AWS RDS, DigitalOcean Managed MySQL
4. **Mobile:** Google Play Store, Firebase App Distribution

See [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md) for production readiness checklist.

---

## 🐛 Known Issues

See [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md) for:
- Android customer app authentication
- VNPay refund logic
- Shopping cart limitations
- And more...

---

## 📊 Project Status (Tuần 7 - MVP Complete)

| Component | Status | Coverage |
|-----------|--------|----------|
| Backend API | ✅ Complete | ~60% tested |
| Web Customer | ✅ Complete | Manual testing |
| Web Merchant | ✅ Complete | Manual testing |
| Web Admin | ⚠️ Basic | Manual testing |
| Android Customer | ⚠️ Partial | Property-based tests |
| Android Driver | ✅ Complete | UI instrumentation |
| AI Service | ⚠️ Basic | Manual testing |
| Documentation | ✅ Complete | — |

**Legend:**
- ✅ Complete: MVP functionality done
- ⚠️ Partial/Basic: Core features done, enhancements needed
- ❌ Not Started: Planned for post-MVP

---

## 👥 Contributing

### Git Workflow
1. Create feature branch: `feature/<name>-tuan<XX>-<description>`
2. Make changes, test locally
3. Commit: `feat(tuần<X>): description`
4. Push and create PR
5. Wait for review and merge

See [Git Workflow Guide](docs/GitWorkflow.md) for details.

### Code Review Checklist
- [ ] Code builds successfully
- [ ] Tests passing
- [ ] No hardcoded credentials
- [ ] API changes documented
- [ ] UI responsive (if applicable)

---

## 📞 Support

- **Team Zalo Group:** DACN FoodExpress
- **GitHub Issues:** Report bugs & feature requests
- **Lead:** Nguyễn Duy Khang (@khang)

---

## 📄 License

Educational project for HUTECH Computer Science Department.
Not for commercial use.

---

**Last Updated:** 24/11/2026 (Tuần 7 - Final Polish)
