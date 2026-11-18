# 🔄 QUY TRÌNH GIT CHO NHÓM - TRÁNH XUNG ĐỘT CODE

## 📋 Quy tắc vàng

1. **KHÔNG BAO GIỜ** làm việc trực tiếp trên `main`
2. **LUÔN LUÔN** tạo nhánh riêng cho mỗi tuần/tính năng
3. **THƯỜNG XUYÊN** cập nhật nhánh của mình từ `main`
4. **CHỈ MERGE** qua Pull Request, không merge trực tiếp

---

## 🚀 Quy trình chuẩn mỗi tuần

### **Bước 1: Cập nhật main mới nhất**
```bash
# Đảm bảo đang ở main
git checkout main

# Kéo code mới nhất từ GitHub
git pull origin main
```

### **Bước 2: Tạo nhánh mới từ main**
```bash
# Quy ước tên: duongtuan<số_tuần>
git checkout -b duongtuan7

# Hoặc nếu đã có nhánh cũ:
git checkout duongtuan7
git merge main  # Cập nhật main vào nhánh
```

### **Bước 3: Làm việc trên nhánh của mình**
```bash
# Code ở đây...
# Sau khi hoàn thành một phần nhỏ:

git add <files>
git commit -m "feat(tuần7): <mô tả ngắn gọn>"
```

### **Bước 4: Trước khi push — CẬP NHẬT MAIN**
```bash
# Fetch thay đổi mới từ GitHub
git fetch origin main

# Kiểm tra xem main có commit mới không
git log main..origin/main

# Nếu có commit mới → merge vào nhánh của mình
git merge origin/main

# Giải quyết conflict (nếu có) → test lại
```

### **Bước 5: Push lên GitHub**
```bash
# Lần đầu push nhánh mới
git push -u origin duongtuan7

# Các lần sau
git push origin duongtuan7
```

### **Bước 6: Tạo Pull Request**
```bash
# Tạo PR bằng gh CLI
gh pr create \
  --title "feat(tuần7): <mô tả>" \
  --body "## Nội dung
- Làm gì
- Test như thế nào
" \
  --base main \
  --head duongtuan7

# Hoặc tạo trên GitHub UI
```

### **Bước 7: Sau khi PR được merge**
```bash
# Cập nhật main local
git checkout main
git pull origin main

# Xóa nhánh cũ (optional)
git branch -d duongtuan7
```

---

## ⚠️ Xử lý conflict khi merge

### **Tình huống: Git báo conflict**
```bash
Auto-merging web/src/App.tsx
CONFLICT (content): Merge conflict in web/src/App.tsx
```

### **Cách giải quyết:**

**1. Xem files bị conflict:**
```bash
git status
```

**2. Mở file conflict, tìm đoạn:**
```typescript
<<<<<<< HEAD
// Code của bạn
import MyComponent from './MyComponent'
=======
// Code của người khác
import TheirComponent from './TheirComponent'
>>>>>>> origin/main
```

**3. Sửa thủ công — giữ cả 2:**
```typescript
// Kết hợp cả 2
import MyComponent from './MyComponent'
import TheirComponent from './TheirComponent'
```

**4. Đánh dấu resolved:**
```bash
git add web/src/App.tsx
git commit -m "merge: resolve conflicts with main"
```

**5. Test kỹ trước khi push:**
```bash
# Backend
cd backend && ./mvnw compile

# Web
cd web && npm run build

# Android
cd android && ./gradlew assembleDebug
```

---

## 📅 Lịch làm việc hàng ngày

### **Sáng (trước khi code):**
```bash
git checkout main
git pull origin main
git checkout duongtuan7
git merge main  # Cập nhật code mới từ team
```

### **Chiều (sau khi code xong):**
```bash
git add .
git commit -m "feat(tuần7): <công việc hôm nay>"
git fetch origin main
git merge origin/main  # Merge lần cuối trước khi push
git push origin duongtuan7
```

---

## 🎯 Phân chia file tránh conflict

### **Files dễ conflict (cần phối hợp):**
| File | Ai sửa | Quy tắc |
|------|--------|---------|
| `App.tsx` | Tất cả | Mỗi người thêm route của mình, comment rõ ràng |
| `MerchantLayout.tsx` | Dưỡng, Điệp | Sidebar items - mỗi người 1 section |
| `SecurityConfig.java` | Khang, An | Mỗi người thêm rule cho endpoint của mình |
| `pom.xml` | Tất cả | Không xóa dependency người khác |
| `package.json` | Tất cả | Không xóa package người khác |

### **Files riêng biệt (không conflict):**
- `backend/.../review/*` → Dưỡng
- `backend/.../driver/*` → An
- `backend/.../payment/*` → Khang
- `web/.../merchant/*` → Dưỡng, Điệp
- `web/.../customer/*` → Khang, Dưỡng
- `android/customer-app/*` → Dưỡng
- `android/driver-app/*` → An

---

## 💬 Giao tiếp trong nhóm

### **Trước khi sửa file chung:**
```
💬 Nhóm Zalo:
"Em sắp sửa App.tsx thêm route /merchant/reviews. 
Có ai đang sửa file này không?"

→ Đợi 5-10 phút cho người khác reply
→ Không ai reply → tiến hành
```

### **Sau khi push PR:**
```
💬 Nhóm Zalo:
"Em vừa push PR #28 (tuần7). 
Anh/chị review giúp em nhé!"

→ Tag người review: @Khang
```

### **Khi thấy conflict trên PR:**
```bash
# Không panic! Làm theo:
git checkout duongtuan7
git pull origin main
# Giải quyết conflict
git push origin duongtuan7
# PR tự động update
```

---

## 🔧 Tools hỗ trợ

### **1. VSCode Extensions:**
- **GitLens** — xem ai sửa dòng code nào
- **Git Graph** — xem tree commit trực quan
- **Merge Conflict** — giải quyết conflict dễ hơn

### **2. Git aliases (thêm vào ~/.gitconfig):**
```ini
[alias]
    st = status -sb
    co = checkout
    br = branch -vv
    lg = log --oneline --graph --decorate -10
    up = !git fetch origin main && git merge origin/main
    sync = !git checkout main && git pull && git checkout - && git merge main
```

Dùng: `git sync` → tự động cập nhật main vào nhánh hiện tại

### **3. Pre-push hook (tự động check trước khi push):**
```bash
# Tạo file .git/hooks/pre-push
#!/bin/bash
echo "🔍 Checking if main is up to date..."
git fetch origin main
BEHIND=$(git rev-list --count HEAD..origin/main)
if [ $BEHIND -gt 0 ]; then
    echo "⚠️  Main has $BEHIND new commits. Please merge first:"
    echo "   git merge origin/main"
    exit 1
fi
echo "✅ Ready to push!"
```

---

## 📊 Checklist mỗi tuần

### **Đầu tuần:**
- [ ] Pull main mới nhất
- [ ] Tạo nhánh `duongtuan<số>` từ main
- [ ] Đọc code các bạn đã merge tuần trước

### **Giữa tuần:**
- [ ] Commit nhỏ thường xuyên (mỗi ngày)
- [ ] Merge main vào nhánh mỗi sáng
- [ ] Hỏi nhóm trước khi sửa file chung

### **Cuối tuần:**
- [ ] Merge main lần cuối
- [ ] Test kỹ (backend, web, android)
- [ ] Tạo PR với description đầy đủ
- [ ] Tag người review
- [ ] Fix review comments
- [ ] Merge sau khi approve

---

## 🚨 Tình huống khẩn cấp

### **"Tôi push nhầm lên main!"**
```bash
# ĐỪNG PANIC! Liên hệ admin repo ngay
# Admin có thể revert commit
git revert <commit_hash>
```

### **"Nhánh của tôi lệch main quá xa!"**
```bash
# Option 1: Rebase (clean history)
git checkout duongtuan7
git rebase origin/main
# Giải quyết conflict từng commit
git push -f origin duongtuan7

# Option 2: Merge (safe)
git checkout duongtuan7
git merge origin/main
git push origin duongtuan7
```

### **"Tôi muốn undo commit cuối cùng"**
```bash
# Chưa push
git reset --soft HEAD~1

# Đã push → tạo commit mới
git revert HEAD
git push
```

---

## ✅ Nguyên tắc cuối cùng

1. **Luôn test trước khi push**
2. **Đọc code người khác trước khi merge**
3. **Hỏi khi không chắc**
4. **Backup code quan trọng**
5. **Commit message phải rõ ràng**

---

**Tạo bởi:** Nhóm DACN FoodExpress  
**Cập nhật:** 17/11/2026
