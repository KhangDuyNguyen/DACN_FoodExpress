# 📝 PHONG CÁCH LÀM VIỆC CỦA DƯỠNG - CHO KIRO AI

## ✅ Khi làm tuần 7+ cho Dưỡng, nhớ:

### **1. Implement đầy đủ cả 3 platform song song:**
- Backend: DTOs → Repository → Service → Controller
- Web: API client → Components → Pages  
- Android: Models → API → ViewModel → UI Screen

### **2. Build + verify TỪNG platform sau mỗi phần:**
```bash
backend: ./mvnw compile
web: npx tsc --noEmit  
android: ./gradlew assembleDebug
```

### **3. Fix bugs NGAY khi phát hiện:**
- Không để bugs dồn lại
- Review code 1 lần cuối trước push
- Security bugs = CRITICAL priority

### **4. Commit format chuẩn:**
```
feat(tuần<X>): <mô tả chi tiết>

Backend:
- File 1: làm gì
- File 2: làm gì

Web/Android:
- Component 1: làm gì

BUILD SUCCESS ✅ / assembleDebug SUCCESSFUL ✅
```

### **5. PR description đầy đủ:**
- Checklist những gì đã làm
- Build status của cả 3 platforms
- Bugs đã fix (nếu có)
- Tiêu chí hoàn thành

### **6. Document updates:**
- Cập nhật TienDoSetup.md sau mỗi tuần
- Ghi rõ % hoàn thành

---

## 🎯 Ví dụ workflow tuần 7:

1. Đọc ChecklistChiTiet.md tuần 7
2. Implement Testing cho backend
3. Build backend → fix errors
4. Implement Testing cho web
5. Build web → fix errors  
6. Implement Testing cho Android
7. Build Android → fix errors
8. Review code toàn bộ → fix issues
9. Commit với message chi tiết
10. Push + tạo PR với description đầy đủ

---

**Nguyên tắc vàng: Code phải CHẠY được trước khi commit!**
