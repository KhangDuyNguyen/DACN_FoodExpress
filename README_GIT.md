# 🎯 HƯỚNG DẪN SỬ DỤNG - DÀNH CHO DƯỠNG

## 📝 Quy trình làm việc mỗi tuần

### **1. Bắt đầu tuần mới (chạy 1 lần)**
```bash
./new-week.sh 7
```
→ Tự động tạo nhánh `duongtuan7` từ main mới nhất

### **2. Mỗi sáng trước khi code (chạy hàng ngày)**
```bash
./sync-main.sh
```
→ Tự động kéo code mới từ main vào nhánh của bạn

### **3. Sau khi code xong**
```bash
git add .
git commit -m "feat(tuần7): mô tả ngắn gọn"
git push origin duongtuan7
```

### **4. Khi PR bị conflict**
```bash
./sync-main.sh
# Nếu có conflict → sửa file → test
git push origin duongtuan7
```

---

## 🛠️ Commands thường dùng

| Tình huống | Command |
|-----------|---------|
| Xem trạng thái | `git status` |
| Xem nhánh hiện tại | `git branch` |
| Đổi nhánh | `git checkout <tên_nhánh>` |
| Xem commits gần đây | `git log --oneline -10` |
| Xem ai sửa file | `git log <file>` |
| Undo commit cuối | `git reset --soft HEAD~1` |
| Xem diff | `git diff` |

---

## 🚨 Xử lý tình huống

### **Tình huống 1: Làm nhầm trên main**
```bash
# Chuyển sang nhánh đúng
git checkout duongtuan7
# Copy commit từ main sang
git cherry-pick <commit_hash>
# Undo commit trên main (báo Khang trước!)
```

### **Tình huống 2: Conflict khi merge**
```bash
# 1. Xem files conflict
git status

# 2. Mở file, tìm đoạn:
# <<<<<<< HEAD
# code của bạn
# =======
# code người khác
# >>>>>>> origin/main

# 3. Sửa thủ công, giữ cả 2 nếu cần

# 4. Đánh dấu resolved
git add <file>
git commit -m "merge: resolve conflicts"
git push
```

### **Tình huống 3: Quên merge main trước khi push**
```bash
# Kéo main về
./sync-main.sh
# Push lại
git push origin duongtuan7
```

---

## ✅ Checklist trước mỗi lần push

- [ ] Đã chạy `./sync-main.sh` hôm nay chưa?
- [ ] Build backend thành công? (`cd backend && ./mvnw compile`)
- [ ] Build web thành công? (`cd web && npm run build`)
- [ ] Build Android thành công? (`cd android && ./gradlew assembleDebug`)
- [ ] Commit message rõ ràng?
- [ ] Không có file thừa (node_modules, .DS_Store)?

---

## 📞 Liên hệ khi gặp vấn đề

1. **Xem docs trước:** `docs/GitWorkflow.md`
2. **Hỏi nhóm Zalo**
3. **Tag @Khang** (lead developer)

---

**Luôn nhớ:** Merge main mỗi sáng → Không bao giờ conflict! ✨
