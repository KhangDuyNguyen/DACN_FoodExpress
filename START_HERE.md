# 🎯 BẮT ĐẦU TẠI ĐÂY - TRẦN XUÂN DƯỠNG

## 📚 Tài liệu theo mức độ

| File | Khi nào đọc | Thời gian |
|------|-------------|-----------|
| **START_HERE.md** | 👈 Bây giờ | 2 phút |
| `QUICK_START.md` | Hàng ngày | 30 giây |
| `DAILY_ROUTINE.txt` | Mỗi sáng | 10 giây |
| `README_GIT.md` | Khi bắt đầu tuần mới | 5 phút |
| `docs/GitWorkflow.md` | Khi gặp vấn đề | 15 phút |

---

## ⚡ Siêu nhanh - 3 lệnh cần nhớ

### **1. Mỗi sáng (trước khi code):**
```bash
dsync
```

### **2. Bắt đầu tuần mới:**
```bash
dweek 7 testing-review
```

### **3. Sau khi code xong:**
```bash
ga && gc "feat(tuần7): mô tả" && gp
```

---

## 🚀 Workflow hoàn chỉnh

### **Tuần 7 - Testing & Bug Fixes (ví dụ)**

#### **Thứ 2 - Bắt đầu tuần:**
```bash
# 1. Tạo nhánh mới
dweek 7 testing-bugfix
# → Tạo: feature/duong-tuan07-testing-bugfix

# 2. Code...
# 3. Commit
ga && gc "feat(tuần7): thêm unit tests backend"
gp
```

#### **Thứ 3-6 - Làm việc hàng ngày:**
```bash
# Sáng
dsync  # Kéo code mới từ main

# Code cả ngày...

# Chiều
ga && gc "feat(tuần7): sửa bug review API"
dsync  # Merge main lần cuối
gp
```

#### **Thứ 7 - Hoàn thành:**
```bash
# 1. Test tất cả
dbuild  # Test backend
wbuild  # Test web
abuild  # Test Android

# 2. Push lần cuối
ga && gc "feat(tuần7): hoàn thành testing"
gp

# 3. Tạo PR
gh pr create \
  --title "feat(tuần7): Testing & Bug Fixes" \
  --body "## Nội dung
- Thêm unit tests cho backend
- Sửa bugs từ review tuần 6
- Test coverage 80%
" \
  --base main \
  --head feature/duong-tuan07-testing-bugfix
```

---

## 📋 Gợi ý tên nhánh

| Tuần | Tính năng chính | Command | Branch name |
|------|-----------------|---------|-------------|
| 7 | Testing + Bugs | `dweek 7 testing-bugfix` | `feature/duong-tuan07-testing-bugfix` |
| 8 | Cart & Checkout | `dweek 8 cart-checkout` | `feature/duong-tuan08-cart-checkout` |
| 9 | Payment (VNPay) | `dweek 9 payment-vnpay` | `feature/duong-tuan09-payment-vnpay` |
| 10 | Notifications | `dweek 10 notifications` | `feature/duong-tuan10-notifications` |
| 11 | Search Advanced | `dweek 11 search-advanced` | `feature/duong-tuan11-search-advanced` |
| 12 | Final Polish | `dweek 12 final-polish` | `feature/duong-tuan12-final-polish` |

---

## 🎨 Shortcuts đã cài

Gõ trực tiếp trong Terminal:

| Shortcut | Command | Mô tả |
|----------|---------|-------|
| `dacn` | `cd ~/DACN_FoodExpress` | Vào folder dự án |
| `dsync` | `./sync-main.sh` | Merge main vào nhánh |
| `dweek` | `./new-week.sh` | Tạo nhánh tuần mới |
| `dquick` | `cat QUICK_START.md` | Xem quick guide |
| `droutine` | `cat DAILY_ROUTINE.txt` | Xem checklist |
| `gs` | `git status` | Xem trạng thái |
| `ga` | `git add .` | Stage tất cả |
| `gc` | `git commit -m` | Commit |
| `gp` | `git push` | Push |
| `gl` | `git log` | Xem commits |
| `dbuild` | Backend compile | Test backend |
| `wbuild` | Web build | Test web |
| `abuild` | Android build | Test Android |

---

## ✅ Checklist tuần mới

- [ ] Chạy `dweek <số> <mô-tả>` → tạo nhánh mới
- [ ] Đọc `dquick` → biết phải làm gì
- [ ] Đọc `docs/ChecklistChiTiet.md` → tasks tuần này
- [ ] Code + test hàng ngày
- [ ] Chạy `dsync` mỗi sáng
- [ ] Push + tạo PR cuối tuần

---

## 🚨 Khi gặp vấn đề

### **Conflict:**
1. Đọc `README_GIT.md` → phần "Xử lý conflict"
2. Vẫn không được → đọc `docs/GitWorkflow.md`
3. Vẫn bí → hỏi nhóm Zalo

### **Lỗi build:**
1. Backend: xem log `./mvnw compile`
2. Web: xem log `npm run build`
3. Android: xem log `./gradlew assembleDebug`

### **Không nhớ làm gì:**
```bash
dquick  # Xem quick guide
droutine  # Xem checklist hàng ngày
```

---

## 📞 Liên hệ

- **Lead:** Khang (@khang_zalo)
- **Nhóm:** DACN FoodExpress (Zalo)
- **Docs:** `docs/GitWorkflow.md`

---

## 🎯 Nguyên tắc vàng

1. ✅ `dsync` mỗi sáng → 0% conflict
2. ✅ Commit nhỏ thường xuyên
3. ✅ Test trước khi push
4. ✅ Hỏi trước khi sửa file chung

---

## 📊 Timeline dự án

```
Tuần 1-5: Setup + Android          ✅ Done
Tuần 6:   Review System             ✅ Done
Tuần 7:   Testing & Bug Fixes       🔄 Current
Tuần 8:   Cart & Checkout           ⏳ Next
Tuần 9:   Payment Integration       ⏳ Coming
Tuần 10+: Advanced Features         ⏳ TBD
```

---

## 🎉 Bắt đầu ngay

```bash
# Xem checklist hôm nay
droutine

# Sync main
dsync

# Bắt đầu code!
```

---

**Chúc bạn code vui vẻ! Nhớ `dsync` mỗi sáng nhé! ✨**
