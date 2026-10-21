# ĐẶC TẢ THIẾT KẾ FIGMA GIAO DIỆN QUẢN TRỊ VIÊN (ADMIN DASHBOARD)
**Dự án:** Hệ thống web và ứng dụng di động giao đồ ăn nhanh (Food Delivery Express)  
**Thành viên phụ trách:** Sầm Xuân Điệp (MSSV: 2380614940 - Lớp: 23DTHA5)  
**Giai đoạn:** Tuần 2 — Nền tảng giao diện và Design System  

---

## 1. TỔNG QUAN VÀ TRIẾT LÝ THIẾT KẾ

- **Phong cách:** SaaS Admin Dashboard hiện đại, chuyên nghiệp, gọn gàng, tối ưu khả năng thao tác nhanh và xử lý tác vụ quản trị.
- **Tone & Mood:** 
  - Nền trung tính sáng (Light theme) tạo cảm giác thoáng đãng, dễ tập trung.
  - Màu nhấn chính (Brand Primary): Cam ẩm thực `#FA541C` / `#FA8C16` (thực phẩm, giao hàng nhanh).
  - Màu bổ trợ: Xanh dương `#1677FF` (công nghệ, tin cậy), Xanh lá `#52C41A` (thành công, trạng thái hoạt động).
- **Độ phân giải chuẩn:** Desktop Frame **1440 × 900 px** (tương thích 1366 × 768, 1920 × 1080).
- **Mức độ tương thích:** 100% ánh xạ tương ứng với component của thư viện **Ant Design 5/6** trong React + TypeScript.

---

## 2. DESIGN TOKENS (HỆ THỐNG THÔNG SỐ THIẾT KẾ)

### 2.1. Color Palette (Bảng màu)

| Nhóm màu | Tên token | Mã HEX | Mục đích sử dụng |
|---|---|---|---|
| **Primary (Nhấn chính)** | `primary-500` | `#FA541C` | Nút bấm chính, icon thương hiệu, viền active |
| | `primary-hover` | `#FF7A45` | Trạng thái hover của nút bấm và link |
| | `primary-bg` | `#FFF2E8` | Nền tag, highlight row, badge nhẹ |
| **Neutral (Trung tính)** | `sidebar-bg` | `#001529` | Màu nền Sidebar bên trái (Ant Design dark sider) |
| | `sidebar-header` | `#002140` | Khối chứa logo trên đỉnh Sidebar |
| | `body-bg` | `#F8FAFC` | Nền toàn bộ vùng content làm việc |
| | `card-bg` | `#FFFFFF` | Nền các thẻ Card, Table, Modal |
| | `border-color` | `#E2E8F0` | Đường kẻ chia line, viền card, viền table |
| **Typography (Chữ)** | `text-title` | `#0F172A` | Tiêu đề H1, H2, H3, tên trang |
| | `text-body` | `#334155` | Nội dung văn bản thường, nhãn form |
| | `text-secondary` | `#64748B` | Mô tả phụ, chú thích nhỏ, placeholder |
| | `text-disabled` | `#94A3B8` | Trạng thái disable của chữ và input |
| **Functional (Trạng thái)** | `success` | `#52C41A` | ACTIVE, Đã duyệt, Thành công |
| | `warning` | `#FAAD14` | PENDING, Chờ duyệt, Cảnh báo |
| | `error` | `#FF4D4F` | REJECTED, Hết hạn, Xóa, Lỗi |
| | `info` | `#1677FF` | Thông tin, thông báo hệ thống |

### 2.2. Typography (Kiểu chữ)

- **Font Family:** `Inter`, `-apple-system`, `BlinkMacSystemFont`, `Segoe UI`, `Roboto`, `sans-serif`.
- **Thang kích thước & Line Height:**
  - `Display / Page Title (H2)`: 24px / 32px — Bold (Weight 700)
  - `Section Title (H3)`: 18px / 26px — Semi-bold (Weight 600)
  - `Card Title (H4)`: 16px / 24px — Semi-bold (Weight 600)
  - `Body Text (Normal)`: 14px / 22px — Regular (Weight 400)
  - `Body Text (Medium/Strong)`: 14px / 22px — Medium (Weight 500)
  - `Small / Caption`: 12px / 18px — Regular (Weight 400)
  - `Micro / Badge`: 10px / 14px — Bold (Weight 700)

### 2.3. Spacing & Sizing (Khoảng cách & Kích thước)

- Hệ thống lưới 8pt: `4px`, `8px`, `12px`, `16px`, `20px`, `24px`, `32px`, `40px`.
- Kích thước khung thành phần:
  - Header Height: `64px`
  - Sidebar Width: `250px` (Expanded) / `80px` (Collapsed)
  - Content Padding: `24px`
  - Card Border Radius: `10px`
  - Button Height: `32px` (Default), `40px` (Large)
  - Input Height: `36px`

### 2.4. Elevation & Shadow (Đổ bóng)

- `Shadow-1 (Card standard)`: `0px 1px 4px rgba(0, 0, 0, 0.06)`
- `Shadow-2 (Dropdown / Popover)`: `0px 4px 12px rgba(0, 0, 0, 0.10)`
- `Shadow-3 (Modal)`: `0px 8px 24px rgba(0, 0, 0, 0.15)`

---

## 3. DANH SÁCH COMPONENT CẦN TẠO TRÊN FIGMA (COMPONENTS & VARIANTS)

1. **Logo Component:**
   - Icon tia chớp nền gradient cam `#FA541C` -> `#FA8C16`
   - Text "FoodExpress" (trắng) + Tag "ADMIN PORTAL" (vàng cam).
2. **Sidebar Menu Item:**
   - Variants: `Default`, `Hover`, `Selected`, `Collapsed`.
   - Thuộc tính: Icon trái, Text nhãn, Badge đếm (nếu có).
3. **Admin Header:**
   - Left: Nút Toggle Menu (Fold/Unfold icon), Breadcrumb / Page Title.
   - Right: System Online Badge, Notification Bell (Badge 3), Avatar + Profile Dropdown.
4. **Statistic KPI Card:**
   - Icon tròn góc (Primary/Success/Warning/Error)
   - Tiêu đề nhỏ (14px secondary)
   - Con số thống kê lớn (28px bold)
   - Suffix / Tăng trưởng nhỏ (+12 hôm nay).
5. **Shortcut Action Card:**
   - Thẻ hành động có dải màu nhấn bên trái (border-left: 4px).
   - Tiêu đề hành động, mô tả 2 dòng, liên kết "Đi tới trang ->".
6. **Data Table Component:**
   - Table Header (nền xám nhẹ `#FAFAFA`, chữ đậm 13px).
   - Table Row: Trạng thái Normal, Hover `#F8FAFC`.
   - Cell Tag: Tag trạng thái (Chờ duyệt, Hoạt động, Tạm dừng).
7. **Placeholder Roadmap Box:**
   - Card trung tâm chứa Icon cờ lê / công cụ màu cam.
   - Tiêu đề chức năng, các Tag phân kỳ tuần thực hiện (Tuần 3/4/6).
   - Danh sách gạch đầu dòng tính năng dự kiến có icon tích xanh.

---

## 4. BẢN MÔ TẢ WIREFRAME (LAYOUT CHÍNH TRÊN FRAME 1440 × 900)

```
+---------------------------------------------------------------------------------------------------+
|  [Logo FoodExpress] | [=] Hệ thống Quản trị Trung tâm               [Trực tuyến] [Bell (3)] [Admin] |
|  [ADMIN PORTAL]     |                                                                             |
|---------------------+-----------------------------------------------------------------------------|
| [*] Tổng quan       | [Alert: Giao diện Quản trị viên - Phiên bản nền tảng Tuần 2]                |
| [ ] Quản lý voucher |                                                                             |
| [ ] Duyệt chủ quán  | [KPI: 1.280 Users]  [KPI: 86 Quán ăn]  [KPI: 342 Đơn]  [KPI: 42.580.000 ₫]  |
| [ ] Duyệt tài xế    |                                                                             |
| [ ] Hỗ trợ KH       | +-------------------------------------------------------------------------+ |
|                     | | Lối tắt quản trị nhanh:                                                  | |
|                     | | [1. Quản lý Voucher]   [2. Duyệt chủ quán]     [3. Duyệt tài xế]          | |
|                     | +-------------------------------------------------------------------------+ |
|                     |                                                                             |
|                     | +-------------------------------------------------------------------------+ |
|                     | | Yêu cầu chờ xử lý gần đây:                                               | |
|                     | | Thời gian | Hoạt động hệ thống             | Phân loại | Trạng thái        | |
|                     | | 19:40     | Cửa hàng KOI Thé đăng ký quán   | MERCHANT  | Chờ duyệt         | |
|                     | | 18:15     | Tài xế Nguyễn Văn A nộp bằng lái | DRIVER    | Chờ duyệt         | |
|                     | | 16:00     | Voucher WELCOME2026 đạt 50 lượt | VOUCHER   | Hoạt động         | |
|                     | +-------------------------------------------------------------------------+ |
|                     |                                                                             |
|                     |                 Food Delivery Express ©2026 — HUTECH (Điệp)                 |
+---------------------+-----------------------------------------------------------------------------+
```

---

## 5. HƯỚNG DẪN DỰNG GIAO DIỆN TRÊN FIGMA THEO TỪNG BƯỚC

### Bước 1: Tạo Frame chính và Cột lưới (Layout Grid)
1. Nhấn phím `F`, chọn preset **Desktop (1440 × 900)**. Đặt tên frame là `Admin / Overview`.
2. Bật Layout Grid:
   - Type: **Columns**, Count: **12**, Margin: **24px**, Gutter: **16px**.
3. Đặt màu nền Background của Frame thành `#F8FAFC`.

### Bước 2: Dựng Sidebar bên trái (Navigation)
1. Vẽ hình chữ nhật kích thước **250 × 900 px**, đặt sát mép trái frame. Màu nền `#001529`.
2. Tạo Header Sidebar (Logo):
   - Kích thước **250 × 64 px**, màu nền `#002140`.
   - Đặt icon tròn bo góc 8px (34 × 34 px) màu cam gradient, text "FoodExpress" trắng (16px bold) và text "ADMIN PORTAL" vàng cam (11px semi-bold).
3. Tạo 5 Menu Items (dùng Auto-layout Shift + A):
   - Chiều cao mỗi item **44px**, padding trái **20px**, khoảng cách icon và text **12px**.
   - Item đang chọn (`Tổng quan`): Nền `#1677FF` (hoặc `#0958D9`), chữ trắng, icon trắng.
   - Các item còn lại: Nền trong suốt, chữ `#A6B0BA`, icon xám.

### Bước 3: Dựng Top Header
1. Vẽ hình chữ nhật **1190 × 64 px** (bắt đầu từ X = 250, Y = 0). Màu nền `#FFFFFF`.
2. Đổ bóng Effect: Drop Shadow `Y = 1, Blur = 4, Spread = 0, Color = rgba(0, 0, 0, 0.06)`.
3. Auto-layout nằm ngang:
   - Trái: Icon thu gọn Sidebar (24px) + Tiêu đề "Hệ thống Quản trị Trung tâm" (16px bold, `#1E293B`).
   - Phải: Tag xanh lá "Trực tuyến", Icon chuông kèm chấm đỏ (3), Avatar tròn 36px nền cam + Text "Sầm Xuân Điệp" (14px) và role "Quản trị viên" (11px secondary).

### Bước 4: Dựng Vùng Nội dung (Content Area)
1. **Banner thông báo Tuần 2:**
   - Khung viền Auto-layout, nền `#E6F4FF`, viền `#91CAFF`, bo góc **8px**, padding **16px**.
   - Icon thông tin chữ `i` tròn màu xanh, tiêu đề đậm, mô tả phụ 13px.
2. **4 Thẻ thống kê KPI (Card Row):**
   - Tạo Auto-layout 4 cột, mỗi cột card rộng khoảng **270px**, cao **110px**.
   - Nền `#FFFFFF`, bo góc **10px**, shadow nhẹ.
   - Gồm: Icon tròn nhỏ bên trái, label trên (13px), số lớn ở giữa (26px bold), chú thích tăng trưởng dưới (12px).
3. **Thẻ lối tắt nhanh (Quick Actions):**
   - Card lớn nền trắng bo góc 10px.
   - 3 sub-card bên trong, mỗi sub-card có viền trái dày 4px (màu cam, xanh dương, xanh lá).
4. **Bảng hoạt động gần đây (Table):**
   - Header table cao 40px nền `#FAFAFA`.
   - 3 Rows cao 50px, có kẻ line ngang phân cách `#F0F0F0`.
   - Dùng các badge trạng thái (Tag Ant Design) với bo viền mềm mại.

### Bước 5: Tạo Trang Placeholder cho các mục điều hướng khác
Nhân bản frame `Admin / Overview` thành 4 frame bổ sung:
- `Admin / Vouchers`
- `Admin / Merchants Approval`
- `Admin / Drivers Approval`
- `Admin / Support Tickets`
Giữ nguyên Sidebar và Header, thay thế vùng Content bằng khối **Placeholder Card** (như đã lập trình trong file `PlaceholderPage.tsx`) thể hiện rõ ràng milestone tuần phát triển tương ứng.

---

## 6. ĐỐI SOÁT GIỮA THIẾT KẾ FIGMA VÀ CODE REACT (ANT DESIGN)

| Thành phần Figma | Component Ant Design trong React | File code thực tế |
|---|---|---|
| Frame tổng thể | `<Layout>` | [AdminLayout.tsx](file:///d:/DACN_FoodExpress/web/src/layouts/AdminLayout.tsx) |
| Sidebar trái | `<Layout.Sider>` | [AdminLayout.tsx](file:///d:/DACN_FoodExpress/web/src/layouts/AdminLayout.tsx) |
| Header trên | `<Layout.Header>` | [AdminLayout.tsx](file:///d:/DACN_FoodExpress/web/src/layouts/AdminLayout.tsx) |
| Menu điều hướng | `<Menu items={...}>` | [AdminLayout.tsx](file:///d:/DACN_FoodExpress/web/src/layouts/AdminLayout.tsx) |
| User Profile | `<Dropdown menu={...}>`, `<Avatar>` | [AdminLayout.tsx](file:///d:/DACN_FoodExpress/web/src/layouts/AdminLayout.tsx) |
| Thẻ thống kê KPI | `<Card>`, `<Statistic>` | [AdminOverviewPage.tsx](file:///d:/DACN_FoodExpress/web/src/pages/admin/AdminOverviewPage.tsx) |
| Bảng yêu cầu | `<Table dataSource={...}>` | [AdminOverviewPage.tsx](file:///d:/DACN_FoodExpress/web/src/pages/admin/AdminOverviewPage.tsx) |
| Trang lộ trình | `<Result>`, `<Card>`, `<Tag>`, `<List>` | [PlaceholderPage.tsx](file:///d:/DACN_FoodExpress/web/src/components/common/PlaceholderPage.tsx) |
