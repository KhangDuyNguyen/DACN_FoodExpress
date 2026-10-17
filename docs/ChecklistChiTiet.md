# CHECKLIST CHI TIẾT THEO TUẦN - FOOD DELIVERY PROJECT

**Sinh viên**: Trần Xuân Dưỡng (2380614779)  
**Phần công việc**: Restaurant, Menu, Review (Backend + Frontend + AI)

---

## TUẦN 1 (12-18/10/2026) - ✅ HOÀN THÀNH

### Setup & Configuration
- [x] Clone repository từ Git
- [x] Cài đặt JDK 21, Node.js 22, Docker Desktop
- [x] Fix TypeScript config errors
- [x] Cài đặt npm dependencies (web)
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

## TUẦN 2 (19-25/10/2026) - 🎯 MỤC TIÊU CHÍNH

**Mục tiêu**: Implement Restaurant API + Trang chủ web

### Backend - Restaurant API (3-4 ngày)

#### Ngày 1: Repository Layer
- [ ] Tạo package `vn.hutech.fooddelivery.restaurant`
- [ ] Tạo class `Restaurant.java` (entity/model)
- [ ] Tạo class `RestaurantRepository.java`
- [ ] Implement method `findAll(int page, int size, String sort)`
  - Query: SELECT với LIMIT, OFFSET
  - Support sort by: name, avg_rating, min_order_amount
- [ ] Implement method `findById(Long id)`
- [ ] Implement method `findByStatus(String status)`
- [ ] Implement method `search(String keyword, int page, int size)`
  - Query: WHERE name LIKE %keyword%
- [ ] Implement method `countAll()` cho pagination
- [ ] Test các query trong TablePlus trước

#### Ngày 2: Service Layer
- [ ] Tạo class `RestaurantService.java`
- [ ] Implement `getAllRestaurants(page, size, sort, filters)`
  - Gọi repository.findAll()
  - Map ResultSet → Restaurant objects
  - Return PagedResponse<Restaurant>
- [ ] Implement `getRestaurantById(id)`
  - Gọi repository.findById()
  - Throw NotFoundException nếu không tìm thấy
- [ ] Implement `searchRestaurants(keyword, page, size)`
- [ ] Implement business logic:
  - Filter theo cuisine_id (join bảng restaurant_cuisines)
  - Filter theo khoảng giá (min_order_amount)
  - Filter theo rating (avg_rating >= X)
  - Calculate distance (nếu có lat/lng của user)
- [ ] Handle exceptions (NotFoundException, etc.)

#### Ngày 3: Controller Layer
- [ ] Tạo class `RestaurantController.java`
- [ ] Implement `GET /api/restaurants`
  - Query params: page, size, sort, status, cuisineId, minRating, maxPrice
  - Response: PagedResponse với total, pages, data[]
  - Status code: 200 OK
- [ ] Implement `GET /api/restaurants/{id}`
  - Path variable: id
  - Response: Restaurant object với full details
  - Status code: 200 OK hoặc 404 Not Found
- [ ] Add validation:
  - @Valid annotations
  - @Min, @Max cho pagination params
- [ ] Add OpenAPI documentation:
  - @Operation, @ApiResponse annotations
  - Example requests/responses

#### Ngày 4: Testing & Documentation
- [ ] Test API với Postman:
  - GET /api/restaurants (no params)
  - GET /api/restaurants?page=0&size=10
  - GET /api/restaurants?sort=avg_rating&order=desc
  - GET /api/restaurants?status=ACTIVE
  - GET /api/restaurants/1
  - GET /api/restaurants/999 (test 404)
- [ ] Test với Swagger UI
- [ ] Fix bugs nếu có
- [ ] Update Swagger documentation
- [ ] Commit code với message rõ ràng

### Frontend - Home Page (3-4 ngày)

#### Ngày 1: API Integration
- [ ] Tạo file `src/api/restaurants.ts`
- [ ] Define types:
  ```typescript
  interface Restaurant {
    id: number;
    name: string;
    description: string;
    address: string;
    logoUrl: string;
    coverUrl: string;
    avgRating: number;
    ratingCount: number;
    minOrderAmount: number;
    prepTimeMinutes: number;
    openStatus: 'OPEN' | 'CLOSED' | 'TEMP_CLOSED';
    status: 'ACTIVE' | 'PENDING' | 'HIDDEN' | 'SUSPENDED';
  }
  
  interface PagedResponse<T> {
    data: T[];
    page: number;
    size: number;
    total: number;
    totalPages: number;
  }
  ```
- [ ] Implement `fetchRestaurants(params)` với axios
- [ ] Implement `fetchRestaurantById(id)`
- [ ] Setup TanStack Query hooks:
  - `useRestaurants(filters)`
  - `useRestaurant(id)`
- [ ] Test API calls trong browser console

#### Ngày 2: Components (Part 1)
- [ ] Tạo `src/components/restaurant/RestaurantCard.tsx`
  - Props: restaurant object
  - Display: logo, name, rating (stars), prepTime, minOrder
  - Badge: OPEN/CLOSED
  - Hover effect
  - Click → navigate to /restaurants/:id
- [ ] Tạo `src/components/restaurant/RestaurantGrid.tsx`
  - Props: restaurants[], loading, error
  - Grid layout (3-4 columns)
  - Loading skeleton (Ant Design Skeleton)
  - Empty state (no restaurants found)
- [ ] Style với Tailwind CSS
- [ ] Test components với mock data

#### Ngày 3: Components (Part 2)
- [ ] Tạo `src/components/restaurant/RestaurantFilters.tsx`
  - Filter by cuisine (Select dropdown)
  - Filter by rating (Rate component)
  - Filter by price range (Slider)
  - "Clear filters" button
- [ ] Tạo `src/components/restaurant/RestaurantSort.tsx`
  - Sort dropdown: Name A-Z, Rating high-low, Price low-high
- [ ] Tạo `src/components/common/SearchBar.tsx`
  - Input with search icon
  - Debounce search (500ms)
  - Clear button
- [ ] Connect filters → API query params

#### Ngày 4: Page Integration
- [ ] Update `src/pages/customer/CustomerHome.tsx`
  - Remove demo content
  - Add SearchBar
  - Add RestaurantFilters (sidebar hoặc collapsible)
  - Add RestaurantSort
  - Add RestaurantGrid
  - Add Pagination (Ant Design Pagination)
- [ ] Integrate với useRestaurants hook
- [ ] Handle loading states
- [ ] Handle error states (show Alert)
- [ ] Test full flow: search → filter → sort → pagination
- [ ] Responsive design (mobile, tablet, desktop)
- [ ] Commit code

### 📝 Deliverables Tuần 2
- [ ] Backend: 3 API endpoints hoạt động
- [ ] Frontend: Trang chủ hiển thị 12 quán
- [ ] Screenshot demo
- [ ] Update README.md với API docs

**Deadline**: Thứ Bảy 25/10/2026, 23:59

---

## TUẦN 3 (26/10 - 01/11/2026) - 🎯 MỤC TIÊU CHÍNH

**Mục tiêu**: Trang chi tiết quán + Menu API + Merchant Dashboard

### Backend - Menu API (3 ngày)

#### Ngày 1: Repository Layer
- [ ] Tạo `MenuItem.java`, `MenuCategory.java` models
- [ ] Tạo `MenuItemRepository.java`
- [ ] Implement `findByRestaurantId(restaurantId, includeUnavailable)`
- [ ] Implement `findById(id)`
- [ ] Implement `findByCategoryId(categoryId)`
- [ ] Implement `create(menuItem)` - INSERT query
- [ ] Implement `update(id, menuItem)` - UPDATE query
- [ ] Implement `softDelete(id)` - UPDATE status='DELETED'
- [ ] Tạo `MenuCategoryRepository.java`
- [ ] Implement `findByRestaurantId(restaurantId)`
- [ ] Implement `create(category)`, `update(id, category)`

#### Ngày 2: Service & Controller
- [ ] Tạo `MenuService.java`
- [ ] Implement business logic: validate price, check restaurant ownership
- [ ] Tạo `MenuController.java`
- [ ] API: `GET /api/restaurants/{id}/menu` - lấy menu theo quán
- [ ] API: `GET /api/menu-items/{id}` - chi tiết món
- [ ] API: `POST /api/menu-items` - thêm món (merchant only)
- [ ] API: `PUT /api/menu-items/{id}` - sửa món
- [ ] API: `DELETE /api/menu-items/{id}` - xóa món
- [ ] API: `GET /api/menu-categories?restaurantId=X`
- [ ] API: `POST /api/menu-categories` - thêm category
- [ ] Add authentication check (JWT) cho POST/PUT/DELETE

#### Ngày 3: Testing
- [ ] Test all Menu APIs với Postman
- [ ] Fix bugs
- [ ] Update Swagger docs

### Frontend - Restaurant Detail Page (2 ngày)

#### Ngày 1: Layout & Info
- [ ] Tạo `src/pages/customer/RestaurantDetail.tsx`
- [ ] Route: `/restaurants/:id`
- [ ] Fetch restaurant data với `useRestaurant(id)` hook
- [ ] Component `RestaurantHeader.tsx`:
  - Cover image (full width)
  - Logo (circular, overlay cover)
  - Name, rating stars, review count
  - Address, phone
  - Badge: OPEN/CLOSED
  - Prep time, min order, delivery fee
- [ ] Component `RestaurantInfo.tsx`:
  - Description
  - Opening hours (từ bảng restaurant_hours)
  - Cuisines (tags)
- [ ] Loading skeleton
- [ ] Error handling (404 not found)

#### Ngày 2: Menu Display
- [ ] Fetch menu với `useMenu(restaurantId)` hook
- [ ] Component `MenuSection.tsx`:
  - Tabs cho từng category (Món chính, Đồ uống...)
  - Hoặc sections với sticky headers
- [ ] Component `MenuItemCard.tsx`:
  - Image, name, price
  - Description (truncated)
  - "Add to cart" button
  - Badge: "Hết hàng" nếu unavailable
- [ ] Modal `MenuItemDetail.tsx` khi click vào món:
  - Full image
  - Full description
  - Options (size, topping) - TẠM THỜI BỎ QUA, làm sau
  - Quantity selector
  - "Add to cart" button
- [ ] Test responsive design

### Frontend - Merchant Dashboard (2 ngày)

#### Ngày 1: Menu Management Page
- [ ] Tạo `src/pages/merchant/MenuManagement.tsx`
- [ ] Route: `/merchant/menu`
- [ ] Table hiển thị món ăn (Ant Design Table):
  - Columns: Image, Name, Category, Price, Status, Actions
  - Actions: Edit, Delete icons
- [ ] Button "Add Menu Item" → open modal
- [ ] Modal `MenuItemForm.tsx`:
  - Form fields: name, description, price, category, image upload
  - Validation với Ant Design Form
  - Submit → POST /api/menu-items
- [ ] Edit: populate form với existing data → PUT /api/menu-items/{id}
- [ ] Delete: confirm modal → DELETE /api/menu-items/{id}
- [ ] Refresh table sau khi CRUD

#### Ngày 2: Category Management
- [ ] Section "Manage Categories" trong cùng page
- [ ] List categories với add/edit/delete
- [ ] Form thêm/sửa category
- [ ] Test full CRUD flow
- [ ] Polish UI/UX

### 📝 Deliverables Tuần 3
- [ ] Backend: 9 Menu API endpoints
- [ ] Frontend: Restaurant detail page hoàn chỉnh
- [ ] Frontend: Merchant menu management
- [ ] Demo video (2-3 phút)

**Deadline**: Thứ Bảy 01/11/2026, 23:59

---

## TUẦN 4 (02-08/11/2026) - 🎯 MỤC TIÊU CHÍNH

**Mục tiêu**: Review System (Backend + Frontend)

### Backend - Review API (3 ngày)

#### Ngày 1: Repository
- [ ] Tạo `Review.java`, `ReviewReply.java` models
- [ ] Tạo `ReviewRepository.java`
- [ ] Implement `findByRestaurantId(restaurantId, page, size)`
- [ ] Implement `findByUserId(userId)`
- [ ] Implement `findByOrderId(orderId)` - mỗi order chỉ 1 review
- [ ] Implement `create(review)` - INSERT
- [ ] Implement `update(id, review)` - UPDATE
- [ ] Implement `delete(id)` - soft delete
- [ ] Implement `findById(id)`
- [ ] Tạo `ReviewReplyRepository.java`
- [ ] Implement `findByReviewId(reviewId)`
- [ ] Implement `create(reply)`

#### Ngày 2: Service & Controller
- [ ] Tạo `ReviewService.java`
- [ ] Business logic:
  - Validate: user phải hoàn thành order mới review được
  - Validate: mỗi order chỉ review 1 lần
  - Calculate new avg_rating cho restaurant sau khi add/update/delete review
  - Update rating_count
- [ ] Tạo `ReviewController.java`
- [ ] API: `GET /api/restaurants/{id}/reviews?page=0&size=10`
- [ ] API: `GET /api/reviews/{id}`
- [ ] API: `POST /api/reviews` - tạo review (customer only)
- [ ] API: `PUT /api/reviews/{id}` - sửa review (owner only)
- [ ] API: `DELETE /api/reviews/{id}` - xóa review (owner only)
- [ ] API: `POST /api/reviews/{id}/reply` - merchant reply
- [ ] API: `PUT /api/reviews/{reviewId}/reply/{replyId}` - edit reply
- [ ] Add auth checks: JWT token → user_id

#### Ngày 3: Testing
- [ ] Test tất cả Review APIs
- [ ] Test avg_rating calculation
- [ ] Test permissions (customer vs merchant)
- [ ] Fix bugs

### Frontend - Review Features (2 ngày)

#### Ngày 1: Display Reviews
- [ ] Component `ReviewList.tsx`
  - Hiển thị list reviews với avatar, name, rating, date, comment
  - Pagination
  - Filter by rating (5 sao, 4 sao, ...)
- [ ] Component `ReviewCard.tsx`
  - User avatar, name
  - Rating stars (filled)
  - Comment text
  - Images (if any) - OPTIONAL, làm sau
  - Date (relative: "2 ngày trước")
  - Merchant reply (if exists) - nested, với badge "Phản hồi từ quán"
- [ ] Add `ReviewList` vào `RestaurantDetail.tsx` page
- [ ] Fetch reviews với `useReviews(restaurantId)` hook

#### Ngày 2: Create Review Form
- [ ] Tạo `src/pages/customer/OrderReview.tsx`
- [ ] Route: `/orders/:orderId/review`
- [ ] Form:
  - Rating selector (Rate component Ant Design, 1-5 sao)
  - Textarea cho comment (min 10 chars)
  - Optional: upload ảnh món ăn - SKIP tuần này
  - Submit button
- [ ] Submit → POST /api/reviews
- [ ] Success → redirect về /orders với success message
- [ ] Handle errors (order already reviewed, order not completed, etc.)

### Frontend - Merchant Review Management (2 ngày)

#### Ngày 1: Review Dashboard
- [ ] Tạo `src/pages/merchant/ReviewManagement.tsx`
- [ ] Route: `/merchant/reviews`
- [ ] Display reviews của quán mình (filter by restaurant_id)
- [ ] Stats cards:
  - Avg rating (lớn, nổi bật)
  - Total reviews
  - % positive (rating >= 4)
- [ ] Table/List reviews:
  - User info, rating, comment, date
  - Status: "Chưa trả lời" / "Đã trả lời"
  - Action: "Reply" button

#### Ngày 2: Reply Feature
- [ ] Modal `ReplyReviewModal.tsx`
- [ ] Textarea để merchant gõ reply
- [ ] Submit → POST /api/reviews/{id}/reply
- [ ] Hiển thị reply đã gửi
- [ ] Allow edit reply: PUT /api/reviews/{reviewId}/reply/{replyId}
- [ ] Test full flow

### 📝 Deliverables Tuần 4
- [ ] Backend: 8 Review API endpoints
- [ ] Frontend: Hiển thị reviews trong trang chi tiết
- [ ] Frontend: Form tạo review
- [ ] Frontend: Merchant reply system
- [ ] Test end-to-end flow

**Deadline**: Thứ Bảy 08/11/2026, 23:59

---

## TUẦN 5 (09-15/11/2026) - 🎯 MỤC TIÊU CHÍNH

**Mục tiêu**: AI Service - Review Sentiment Analysis

### Chuẩn bị Dataset (1 ngày)
- [ ] Thu thập dataset đánh giá món ăn tiếng Việt
  - Nguồn: Foody.vn, Shopee Food, Grab Food (scrape hoặc tìm dataset có sẵn)
  - Target: ~1000-5000 reviews
- [ ] Label data:
  - POSITIVE: 5 sao, 4 sao
  - NEGATIVE: 1 sao, 2 sao
  - NEUTRAL: 3 sao
- [ ] Clean data:
  - Remove emoji, special chars (hoặc giữ lại tùy model)
  - Lowercase
  - Remove duplicates
- [ ] Split: 80% train, 20% test
- [ ] Save: `ai-service/data/reviews.csv`

### Training Model (2 ngày)

#### Ngày 1: Preprocessing & Feature Engineering
- [ ] Setup Python environment:
  ```bash
  cd ai-service
  python -m venv venv
  source venv/bin/activate
  pip install -r requirements.txt
  ```
- [ ] Install libraries:
  - pandas, numpy
  - scikit-learn
  - underthesea (Vietnamese NLP)
  - joblib (save model)
- [ ] Tạo `ai-service/scripts/train_sentiment.py`
- [ ] Load và preprocess data:
  - Tokenize tiếng Việt với underthesea
  - TF-IDF vectorization hoặc CountVectorizer
  - Handle class imbalance (nếu có)

#### Ngày 2: Training & Evaluation
- [ ] Try multiple models:
  - Naive Bayes (baseline, nhanh)
  - Logistic Regression
  - SVM (nếu dataset không quá lớn)
- [ ] Train với train set
- [ ] Evaluate với test set:
  - Accuracy
  - Precision, Recall, F1-score (cho từng class)
  - Confusion matrix
- [ ] Chọn model tốt nhất
- [ ] Save model: `ai-service/models/sentiment_model.pkl`
- [ ] Save vectorizer: `ai-service/models/vectorizer.pkl`

### API Development (2 ngày)

#### Ngày 1: FastAPI Endpoint
- [ ] Tạo `ai-service/app/routers/sentiment.py`
- [ ] Load trained model khi startup
- [ ] Implement endpoint `POST /ai/analyze-review`:
  ```python
  {
    "text": "Món ăn rất ngon, tuy nhiên giao hơi chậm"
  }
  
  Response:
  {
    "sentiment": "POSITIVE",
    "confidence": 0.85,
    "scores": {
      "POSITIVE": 0.85,
      "NEGATIVE": 0.10,
      "NEUTRAL": 0.05
    }
  }
  ```
- [ ] Add input validation (min length, max length)
- [ ] Add error handling
- [ ] Test với Postman/curl

#### Ngày 2: Integration với Backend
- [ ] Backend: Tạo service call AI API
- [ ] Tạo `AiServiceClient.java`:
  ```java
  public SentimentResult analyzeSentiment(String reviewText) {
    // HTTP POST to ai-service
    // Parse response
    return sentimentResult;
  }
  ```
- [ ] Update `ReviewService.java`:
  - Sau khi create review → gọi AI service
  - Save kết quả vào bảng `review_analysis`
- [ ] Handle AI service unavailable (fallback: không analyze)
- [ ] Test integration

### 📝 Deliverables Tuần 5
- [ ] AI model trained (accuracy >= 75%)
- [ ] FastAPI endpoint `/ai/analyze-review`
- [ ] Backend tích hợp AI service
- [ ] Reviews mới tự động được analyze
- [ ] Document: model performance report

**Deadline**: Thứ Bảy 15/11/2026, 23:59

---

## TUẦN 6 (16-22/11/2026) - 🎯 MỤC TIÊU CHÍNH

**Mục tiêu**: Testing, Bug Fixes, Performance Optimization

### Backend Testing (2 ngày)

#### Ngày 1: Unit Tests
- [ ] Setup JUnit 5 + Mockito
- [ ] Test `RestaurantService`:
  - Test getAllRestaurants() với mock repository
  - Test getRestaurantById() - success case
  - Test getRestaurantById() - not found case
  - Test filters logic
- [ ] Test `MenuService`:
  - Test CRUD operations
  - Test validation (negative price, etc.)
- [ ] Test `ReviewService`:
  - Test create review
  - Test avg_rating calculation
  - Test duplicate review prevention
- [ ] Target: >= 70% code coverage

#### Ngày 2: Integration Tests
- [ ] Test REST APIs với TestRestTemplate
- [ ] Test `GET /api/restaurants` - full flow
- [ ] Test `POST /api/menu-items` với auth
- [ ] Test `POST /api/reviews` - full flow
- [ ] Test AI service integration (mock AI service)
- [ ] Fix bugs found during testing

### Frontend Testing (1 ngày)
- [ ] Setup React Testing Library
- [ ] Test `RestaurantCard` component
- [ ] Test `MenuItemCard` component
- [ ] Test form validation (ReviewForm)
- [ ] Test API error handling
- [ ] Fix bugs

### Bug Fixes & Polish (2 ngày)

#### Ngày 1: Fix Known Issues
- [ ] Review GitHub Issues / TODO comments
- [ ] Fix UI bugs:
  - Responsive issues on mobile
  - Loading states
  - Error messages
- [ ] Fix backend bugs:
  - Validation errors
  - Edge cases

#### Ngày 2: Performance Optimization
- [ ] Backend:
  - Add database indexes (nếu thiếu)
  - Optimize N+1 queries
  - Add caching cho restaurant list (Redis - OPTIONAL)
- [ ] Frontend:
  - Lazy load images
  - Code splitting với React.lazy()
  - Optimize bundle size
- [ ] Test performance với Lighthouse

### Documentation (1 ngày)
- [ ] Update README.md:
  - Setup instructions
  - API documentation overview
  - Architecture diagram
- [ ] Update Swagger docs:
  - Add descriptions
  - Add examples
- [ ] Code comments cho phần phức tạp
- [ ] Tạo file `CHANGELOG.md`

### 📝 Deliverables Tuần 6
- [ ] Unit + Integration tests (>= 70% coverage)
- [ ] All critical bugs fixed
- [ ] Performance optimized
- [ ] Documentation updated

**Deadline**: Thứ Bảy 22/11/2026, 23:59

---

## TUẦN 7 (23-28/11/2026) - 🎯 MVP DEADLINE

**Mục tiêu**: Hoàn thiện tất cả, chuẩn bị demo

### Hoàn thiện tính năng còn thiếu (2 ngày)
- [ ] Review lại TODO list toàn bộ project
- [ ] Implement các tính năng nhỏ còn thiếu:
  - Upload ảnh cho review (nếu kịp)
  - Search autocomplete (nếu kịp)
  - Notification khi có review mới (nếu kịp)
- [ ] Polish UI/UX:
  - Consistent spacing, colors
  - Better error messages
  - Loading animations

### Testing tổng thể (1 ngày)
- [ ] Test toàn bộ user flows:
  - Customer: Tìm quán → xem menu → đặt hàng → review
  - Merchant: Login → quản lý menu → trả lời review
- [ ] Test cross-browser (Chrome, Safari, Firefox)
- [ ] Test responsive (mobile, tablet, desktop)
- [ ] Test với data thật (12 quán, 94 món)
- [ ] Fix bugs tìm được

### Chuẩn bị demo (1 ngày)
- [ ] Tạo account demo:
  - Customer account
  - Merchant account
- [ ] Seed thêm data nếu cần (reviews, orders)
- [ ] Tạo slide PowerPoint (10-15 slides):
  - Giới thiệu dự án
  - Công nghệ sử dụng
  - Chức năng đã implement
  - Demo screenshots
  - Kiến trúc hệ thống
  - Kết quả đạt được
  - Kế hoạch tương lai
- [ ] Viết script demo (2-3 phút)
- [ ] Luyện tập demo
- [ ] Record demo video (backup nếu live demo fail)

### Deployment (OPTIONAL)
- [ ] Deploy backend lên Railway/Render
- [ ] Deploy frontend lên Vercel/Netlify
- [ ] Setup environment variables
- [ ] Test production deployment

### Nộp báo cáo (28/11)
- [ ] Tổng hợp tất cả tài liệu:
  - Source code (GitHub repo)
  - Database script (schema + seed data)
  - Documentation (README, API docs)
  - Slide thuyết trình
  - Demo video
  - Báo cáo tiến độ (file này)
- [ ] Nén thành file ZIP hoặc push lên GitHub
- [ ] Submit trước 23:59 ngày 28/11/2026

### 📝 Deliverables Tuần 7
- [x] MVP hoàn chỉnh, chạy ổn định
- [x] Slide thuyết trình
- [x] Demo video
- [x] Tất cả tài liệu
- [x] Nộp đúng hạn

**HARD DEADLINE**: Thứ Năm 28/11/2026, 23:59 ⏰

---

## 📊 TỔNG KẾT CHECKLIST

### Theo Tuần
| Tuần | Tasks | Hoàn thành | Tiến độ |
|------|-------|------------|---------|
| Tuần 1 | 15 | 15 | 100% ✅ |
| Tuần 2 | 32 | 0 | 0% 🔄 |
| Tuần 3 | 26 | 0 | 0% ⏳ |
| Tuần 4 | 20 | 0 | 0% ⏳ |
| Tuần 5 | 18 | 0 | 0% ⏳ |
| Tuần 6 | 16 | 0 | 0% ⏳ |
| Tuần 7 | 12 | 0 | 0% ⏳ |
| **TỔNG** | **139** | **15** | **11%** |

### Theo Module
| Module | Tasks | Hoàn thành |
|--------|-------|------------|
| Setup | 15 | 15 ✅ |
| Restaurant Backend | 14 | 0 |
| Restaurant Frontend | 18 | 0 |
| Menu Backend | 12 | 0 |
| Menu Frontend | 14 | 0 |
| Review Backend | 11 | 0 |
| Review Frontend | 9 | 0 |
| AI Service | 18 | 0 |
| Testing | 16 | 0 |
| Documentation | 12 | 0 |

---

## 💡 TIPS & BEST PRACTICES

### Git Workflow
- Commit nhỏ, thường xuyên (mỗi feature/fix 1 commit)
- Message format: `feat: add restaurant list API` hoặc `fix: resolve pagination bug`
- Push code mỗi ngày, tránh mất code
- Tạo branch riêng cho feature lớn (optional)

### Coding Standards
- Java: Follow Google Java Style Guide
- TypeScript: Use ESLint + Prettier
- Naming: camelCase (Java), kebab-case (files)
- Comment phức tạp logic, không comment code hiển nhiên

### Time Management
- Mỗi task estimate: 2-4 giờ
- Nếu task > 4 giờ → break thành subtasks
- Nghỉ giải lao 10 phút mỗi 1 tiếng
- Review progress cuối mỗi ngày

### When Stuck
1. Google error message (Stack Overflow)
2. Check documentation (Spring, React, Ant Design)
3. Debug với breakpoints/console.log
4. Hỏi ChatGPT/Claude
5. Hỏi teammates (Khang, An, Điệp)
6. Hỏi thầy Trúc (nếu vẫn không giải quyết được)

---

**Tạo bởi**: Kiro AI Assistant  
**Cập nhật lần cuối**: 17/10/2026  
**Liên hệ**: Trần Xuân Dưỡng - 2380614779
