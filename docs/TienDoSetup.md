# BÁO CÁO TIẾN ĐỘ SETUP DỰ ÁN FOOD DELIVERY

**Sinh viên**: Trần Xuân Dưỡng (2380614779)  
**Lớp**: 23DTHA5  
**Cập nhật**: 10/11/2026

---

## ✅ TUẦN 1 (12-18/10/2026) - HOÀN THÀNH 100%
- Setup môi trường, Docker MySQL, Spring Boot, Vite React

## ✅ TUẦN 2 (19-25/10/2026) - HOÀN THÀNH 100%
- Backend: `GET /api/cuisines`, `GET /api/restaurants`
- Frontend: Dựng project React + Vite, layout 3 khu (khách/merchant/admin)

## ✅ TUẦN 3 (26/10-31/10/2026) - HOÀN THÀNH 100%
- Backend: `GET /api/restaurants/{id}`, `GET /api/restaurants/{id}/menu`
- Web khách: Trang chủ (search, lọc cuisine, nút Gần tôi), Chi tiết quán + menu

## ✅ TUẦN 4 (02-07/11/2026) - HOÀN THÀNH 100%
- Backend Merchant APIs: PUT info, PUT hours, PATCH open-status, CRUD categories, CRUD items, PATCH availability, CRUD option-groups/options, POST tạo chi nhánh, GET chi tiết quán cho merchant
- Dashboard: BranchSelector, MenuManagement (CRUD nhóm món + món), RestaurantSettings (thông tin, giờ mở cửa, toggle mở/đóng)
- Fix 3 lỗi sau review: icon import crash, Jackson @JsonProperty, endpoint sai cho quán PENDING

## ✅ TUẦN 5 (09-14/11/2026) - HOÀN THÀNH 100%

### App khách Android (Kotlin + Jetpack Compose)

**Màn 1: Danh sách quán** (`restaurants/ui/list/`)
- Tìm kiếm theo tên quán (debounce 400ms)
- Lọc theo loại món (cuisine filter chips)
- Load more khi cuộn tới cuối (infinite scroll)
- Loading skeleton, empty state, error + retry
- Card hiển thị: logo, tên, open/closed badge, rating, prepTime, minOrder, địa chỉ

**Màn 2: Chi tiết quán + Menu** (`restaurants/ui/detail/`)
- Cover image, thông tin quán đầy đủ
- Tabs theo category menu (ScrollableTabRow)
- MenuItemCard: ảnh, tên, mô tả, giá, option group hints, nút "+ Thêm", badge "Hết món"
- Load song song detail + menu (async)
- Loading spinner, error + retry, back navigation

**Kỹ thuật:**
- Hilt + KSP (migrate từ KAPT để fix Kotlin 2.2.21 compatibility)
- Retrofit 2.11.0 + Gson, Coroutines, StateFlow
- Navigation Compose với argument `restaurantId: Long`
- `assembleDebug` BUILD SUCCESSFUL ✅

### Dashboard cửa hàng: Lịch sử đơn
- `MerchantOrdersPage` đã có sẵn trên main (Khang implement)
- Chủ quán xem đơn theo chi nhánh, lọc theo trạng thái, đổi trạng thái đơn
- Sidebar "Đơn hàng" link đã có trong MerchantLayout

---

## 📅 TIẾP THEO

**Tuần 6 (16-21/11)**: Backend đánh giá + Web khách lịch sử đơn + App khách lịch sử đơn + Dashboard đánh giá

---

## 📊 TIẾN ĐỘ TỔNG QUAN

| Tuần | Deadline | Trạng thái |
|------|----------|-----------|
| Tuần 1 | 17/10 | ✅ Hoàn thành |
| Tuần 2 | 24/10 | ✅ Hoàn thành |
| Tuần 3 | 31/10 | ✅ Hoàn thành |
| Tuần 4 | 07/11 | ✅ Hoàn thành |
| Tuần 5 | 14/11 | ✅ Hoàn thành |
| Tuần 6 | 21/11 | ⏳ Chưa bắt đầu |
| Tuần 7 | 28/11 | ⏳ Chưa bắt đầu |
