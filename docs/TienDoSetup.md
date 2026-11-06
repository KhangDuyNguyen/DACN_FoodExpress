# BÁO CÁO TIẾN ĐỘ SETUP DỰ ÁN FOOD DELIVERY

**Sinh viên**: Trần Xuân Dưỡng (2380614779)  
**Lớp**: 23DTHA5  
**Cập nhật**: 27/10/2026

---

## ✅ TUẦN 1 (12-18/10/2026) - HOÀN THÀNH 100%

### Setup Môi Trường
- [x] Clone repository từ Git
- [x] Cài đặt JDK 21, Node.js 22, Docker Desktop
- [x] Fix TypeScript config errors
- [x] Cài đặt npm dependencies (168 packages)
- [x] Cấu hình file .env (DB password, JWT secret)
- [x] Tạo script run.sh cho backend

### Database Setup
- [x] Khởi động MySQL container (Docker)
- [x] Tạo database fooddelivery
- [x] Chạy Flyway migration (42 bảng)
- [x] Kết nối TablePlus/MySQL Workbench
- [x] Xác minh data: 12 quán, 94 món, 1 user

### Backend & Frontend
- [x] Chạy backend thành công (port 8080)
- [x] Test API /api/health
- [x] Chạy web dev server (port 5173)
- [x] Xác nhận kết nối frontend-backend
- [x] Xem Swagger UI documentation

---

## ✅ TUẦN 2 (19-25/10/2026) - HOÀN THÀNH 100%

### Backend APIs
- [x] `GET /api/cuisines` - Danh sách loại món ăn
- [x] `GET /api/restaurants` - Tìm kiếm, lọc quán (keyword, cuisine, lat, lng)

### Frontend Web
- [x] Dựng project React + TypeScript + Vite
- [x] Cấu trúc 3 khu: khách (/), merchant (/merchant), admin (/admin)
- [x] React Router, TanStack Query, Axios, Tailwind CSS
- [x] Layout component với Header/Footer
- [x] Merchant layout với sidebar navigation

---

## ✅ TUẦN 3 (26/10-31/10/2026) - HOÀN THÀNH 100%

### Backend APIs
- [x] `GET /api/restaurants/{id}` - Chi tiết quán (tên, địa chỉ, rating, cuisines, giờ mở cửa)
- [x] `GET /api/restaurants/{id}/menu` - Menu đầy đủ (categories → items → option_groups → options)
- [x] Fix bug TINYINT(1) - MySQL JDBC trả Boolean/Number, đã thêm helper `toBool/toInt/toLong`

### Frontend Web Khách
- [x] **Trang chủ** (`/restaurants`) - Danh sách quán, tìm kiếm, lọc cuisine, nút "Gần tôi"
  - Tìm kiếm theo tên quán (Enter hoặc click button)
  - Lọc theo loại món (cuisine chips)
  - Nút "📍 Gần tôi" dùng Geolocation API → sort theo khoảng cách
  - Loading skeleton, error state, empty state
  - Grid responsive: 2 cột (mobile) / 3 cột (tablet) / 4 cột (desktop)
- [x] **Chi tiết quán** (`/restaurants/:id`) - Thông tin quán + Menu đầy đủ
  - Cover image, logo, tên, rating, badge mở/đóng
  - Cuisine tags, địa chỉ, SĐT, prep time, min order
  - Giờ mở cửa 7 ngày
  - Tabs theo category menu
  - MenuItemCard: ảnh, tên, mô tả, giá, nút "+ Thêm"
  - Hiển thị option groups (size, topping) với expand/collapse
  - Badge "Hết món" khi item unavailable

---

## 📅 TIẾP THEO

**Tuần 4 (02-07/11)**: Dashboard cửa hàng
- Backend: merchant APIs (quản lý quán, menu, giờ mở cửa)
- Frontend: Đăng nhập chọn chi nhánh, quản lý menu, settings quán

---

## 🛠️ THÔNG TIN KỸ THUẬT

| Service | Port | Trạng thái |
|---------|------|-----------|
| Backend (Spring Boot) | 8080 | ✅ Running |
| Frontend (Vite/React) | 5173 | ✅ Running |
| MySQL (Docker) | 3306 | ✅ Running |
| AI Service (FastAPI) | 8000 | ⏳ Chưa cần |

## 📊 TIẾN ĐỘ TỔNG QUAN

| Tuần | Deadline | Trạng thái |
|------|----------|-----------|
| Tuần 1 | 17/10 | ✅ Hoàn thành |
| Tuần 2 | 24/10 | ✅ Hoàn thành |
| Tuần 3 | 31/10 | ✅ Hoàn thành |
| Tuần 4 | 07/11 | ⏳ Chưa bắt đầu |
| Tuần 5 | 14/11 | ⏳ Chưa bắt đầu |
| Tuần 6 | 21/11 | ⏳ Chưa bắt đầu |
| Tuần 7 | 28/11 | ⏳ Chưa bắt đầu |
