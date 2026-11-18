# ⚡ QUICK START - DÀNH CHO DƯỠNG

## 🚀 Bắt đầu tuần mới (3 bước)

```bash
# 1. Tạo nhánh mới
./new-week.sh 7 testing-review

# 2. Bắt đầu code
# ... code ở đây ...

# 3. Push lên GitHub
git add .
git commit -m "feat(tuần7): mô tả"
git push -u origin feature/duong-tuan07-testing-review
```

---

## 📅 Workflow hàng ngày

### **Sáng:**
```bash
cd ~/DACN_FoodExpress
./sync-main.sh
# → Bắt đầu code
```

### **Chiều:**
```bash
git add .
git commit -m "feat(tuần7): làm gì đó"
./sync-main.sh
git push
```

---

## 📝 Gợi ý tên nhánh các tuần

| Tuần | Tính năng | Command |
|------|-----------|---------|
| **7** | Testing & Bug Fixes | `./new-week.sh 7 testing-bugfix` |
| **8** | Cart & Checkout | `./new-week.sh 8 cart-checkout` |
| **9** | Payment Integration | `./new-week.sh 9 payment-integration` |
| **10** | Notifications | `./new-week.sh 10 notifications` |
| **11** | Search & Filter | `./new-week.sh 11 search-filter` |
| **12** | Final Polish | `./new-week.sh 12 final-polish` |

---

## 🎯 Template commit messages

```bash
# Feature mới
git commit -m "feat(tuần7): thêm giỏ hàng"

# Fix bug
git commit -m "fix(tuần7): sửa lỗi payment"

# Docs
git commit -m "docs(tuần7): cập nhật README"

# Refactor
git commit -m "refactor(tuần7): tối ưu API"

# Test
git commit -m "test(tuần7): thêm unit tests"
```

---

## 🔧 Commands cấp tốc

```bash
# Xem trạng thái
git status

# Xem nhánh hiện tại
git branch

# Xem commits gần đây
git log --oneline -5

# Undo commit cuối (chưa push)
git reset --soft HEAD~1

# Xem ai sửa file này
git log --oneline <file>

# Xem diff trước khi commit
git diff
```

---

## 🚨 Xử lý conflict (5 bước)

```bash
# 1. Kéo main
./sync-main.sh

# 2. Nếu có conflict → mở file
code <file_conflict>

# 3. Tìm và sửa đoạn:
# <<<<<<< HEAD
# code của bạn
# =======
# code người khác
# >>>>>>> origin/main

# 4. Giữ cả 2 hoặc chọn 1
# Xóa các dòng <<< === >>>

# 5. Commit
git add <file>
git commit -m "merge: resolve conflicts"
git push
```

---

## ✅ Checklist trước mỗi PR

```bash
# 1. Sync main lần cuối
./sync-main.sh

# 2. Test backend
cd backend && ./mvnw compile

# 3. Test web
cd ../web && npm run build

# 4. Test Android
cd ../android && ./gradlew assembleDebug

# 5. Push
git push origin feature/duong-tuan07-testing-review

# 6. Tạo PR
gh pr create \
  --title "feat(tuần7): Testing & Review System" \
  --body "## Nội dung
- Fix bugs tuần 6
- Thêm tests
- Review code
" \
  --base main \
  --head feature/duong-tuan07-testing-review
```

---

## 📊 Progress tracker

| Tuần | Branch | Status | PR |
|------|--------|--------|-----|
| 1-5 | Setup + Android | ✅ Merged | #20 |
| 6 | Review System | ✅ Merged | #24 |
| 7 | Testing & Bugs | 🔄 Working | - |
| 8 | Cart & Payment | ⏳ Todo | - |
| 9+ | TBD | ⏳ Todo | - |

---

## 💡 Tips

1. **Chạy `./sync-main.sh` MỖI SÁNG** → 0% conflict
2. **Commit nhỏ thường xuyên** → dễ revert nếu sai
3. **Test trước khi push** → không break main
4. **Hỏi team trước khi sửa file chung** → tránh đụng độ

---

## 📞 Cần giúp?

1. Xem: `docs/GitWorkflow.md` (chi tiết)
2. Xem: `README_GIT.md` (đơn giản)
3. Xem: `DAILY_ROUTINE.txt` (checklist)
4. Hỏi: Nhóm Zalo

---

**Remember:** `./sync-main.sh` mỗi sáng = Happy coding! ✨
