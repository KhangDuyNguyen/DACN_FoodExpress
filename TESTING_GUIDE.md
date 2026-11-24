# 🧪 Testing Guide - DACN FoodExpress

**Last Updated:** 24/11/2026 (Tuần 7)

---

## 🎯 Test Coverage Overview

| Component | Coverage | Test Type |
|-----------|----------|-----------|
| Backend | ~60% | Integration + Unit |
| Web | Manual | None automated |
| Android Customer | Property-based | Critical flows |
| Android Driver | UI Instrumentation | Key screens |

---

## 🏗️ Backend Testing

### Running Tests
```bash
cd backend

# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=ReviewServiceTest

# Run with coverage
./mvnw test jacoco:report
# View: target/site/jacoco/index.html
```

### Test Structure
```
src/test/java/
├── integration/          # Integration tests (@SpringBootTest)
│   ├── OrderFlowIntegrationTest
│   ├── TrackingIntegrationTest
│   └── ...
├── unit/                 # Unit tests
│   ├── ReviewServiceTest
│   ├── DriverEarningsCalculatorTest
│   └── ...
└── web/                  # WebMvc tests
    ├── ReviewControllerTest
    └── ...
```

### Key Test Scenarios

#### 1. Order Flow
**File:** `DriverFlowIntegrationTest.java`
- Customer places order → PLACED
- Driver accepts → DRIVER_ASSIGNED
- Driver picks up → IN_TRANSIT
- Driver delivers → DELIVERED
- Customer reviews → Review saved

#### 2. Payment Flow
**File:** `PaymentResultServiceTest.java`  
- VNPay success → Order PLACED
- VNPay failure → Order CANCELLED
- Payment timeout → Order CANCELLED

#### 3. Review System
**File:** `ReviewServiceTest.java`
- Create review only for DELIVERED orders
- One review per order
- Auto-recalc avg_rating
- Merchant reply

#### 4. Tracking
**File:** `TrackingStompWebSocketIntegrationTest.java`
- WebSocket subscription auth
- GPS location broadcast
- Realtime updates

---

## 🌐 Web Testing

### Manual Testing Checklist

#### Customer Flow
- [ ] **Search & Browse**
  - Search restaurants by name
  - Filter by cuisine type
  - Sort by rating/distance
  - Pagination works
- [ ] **Restaurant Detail**
  - View menu items
  - Check operating hours
  - See reviews
- [ ] **Cart & Checkout**
  - Add items to cart
  - Apply voucher
  - Choose payment method (COD/VNPay)
  - Enter delivery address
- [ ] **Payment**
  - COD: Order placed immediately
  - VNPay: Redirect to payment page
  - VNPay return: Order status updated
- [ ] **Order Tracking**
  - View order status
  - See driver location (if assigned)
- [ ] **Review**
  - Rate restaurant (1-5 stars)
  - Rate driver (optional)
  - Write comment
  - View merchant reply

#### Merchant Flow
- [ ] **Login**
  - Login with merchant account
  - Switch between branches
- [ ] **Menu Management**
  - Add menu item
  - Edit menu item
  - Delete menu item
  - Manage categories
  - Upload images
- [ ] **Order Management**
  - View incoming orders
  - Accept/reject orders
  - Mark ready for pickup
- [ ] **Reviews**
  - View all reviews
  - Reply to reviews
  - See stats (avg rating, reply rate)
- [ ] **Reports**
  - View revenue chart
  - View order statistics
  - Filter by date range

#### Driver Flow (Web Minimal)
- [ ] Login as driver
- [ ] View dashboard

---

## 📱 Android Customer App Testing

### Running Tests
```bash
cd android

# Unit tests
./gradlew :customer-app:test

# Instrumentation tests (requires emulator)
./gradlew :customer-app:connectedAndroidTest
```

### Property-Based Tests
**Location:** `customer-app/src/test/`
- `TrackingSessionTest` - GPS tracking logic
- `TrackingRoutePlannerTest` - Route calculation
- `OrderTrackingPresentationTest` - UI state management

### Manual Testing
- [ ] Restaurant list loads
- [ ] Search works
- [ ] Filter by cuisine
- [ ] Restaurant detail shows menu
- [ ] My Orders screen (needs hardcoded token)
- [ ] Review dialog

---

## 📱 Android Driver App Testing

### UI Instrumentation Tests
```bash
# Run all UI tests
./gradlew :driver-app:connectedAndroidTest

# Specific test suite
cd driver-app/src/test/delivery-host
python run_checks.py
```

### Test Scenarios
- **Offer Flow:** Accept order
- **Delivery Flow:** Pick up → deliver
- **Earnings Flow:** View reports
- **GPS Flow:** Background location updates

---

## 🔧 Integration Testing

### End-to-End Flow (Manual)

#### Prerequisites
1. Backend running on `localhost:8080`
2. Database seeded with test data
3. Web dev server on `localhost:5173`
4. Android emulator or physical device

#### Test Case: Complete Order Flow

**Step 1: Customer places order (Web)**
```
1. Navigate to http://localhost:5173
2. Login as customer (email: customer@test.com)
3. Search for "Phở"
4. Click on a restaurant
5. Add items to cart
6. Checkout with COD
7. Confirm order
8. Note order ID
```

**Step 2: Merchant accepts (Web)**
```
1. Login as merchant (email: merchant@test.com)
2. Go to Orders tab
3. See new order
4. Accept order
5. Mark "Ready for pickup"
```

**Step 3: Driver delivers (Android)**
```
1. Open Driver app
2. Login as driver
3. See order in Offers
4. Accept order
5. Mark "Picked up"
6. Navigate to delivery location
7. Mark "Delivered"
```

**Step 4: Customer reviews (Web)**
```
1. Go to My Orders
2. Click "Đánh giá" on delivered order
3. Rate restaurant (5 stars)
4. Rate driver (5 stars)
5. Write comment
6. Submit
```

**Step 5: Merchant replies (Web)**
```
1. Go to Reviews tab
2. See new review
3. Click "Trả lời"
4. Write reply
5. Submit
```

**Expected Results:**
- ✅ Order status transitions correctly
- ✅ GPS tracking works (if driver app)
- ✅ Review appears with merchant reply
- ✅ Earnings calculated for driver

---

## 🐛 Bug Reporting

### When you find a bug:

1. **Check if it's a known limitation**
   - See `KNOWN_LIMITATIONS.md`

2. **Gather information:**
   - Steps to reproduce
   - Expected vs actual behavior
   - Screenshots/videos
   - Browser/device info
   - Error messages from console/logs

3. **Report:**
   - Create GitHub issue
   - Or add to `docs/BUGS.md`

4. **Priority:**
   - **P0 (Critical):** Blocks main flow, security issue
   - **P1 (High):** Important feature broken
   - **P2 (Medium):** Minor feature issue
   - **P3 (Low):** UI polish, nice-to-have

---

## 📊 Performance Testing

### Backend Load Testing (Optional)

```bash
# Install Apache Bench
brew install apache-bench  # macOS

# Test API endpoint
ab -n 1000 -c 10 http://localhost:8080/api/restaurants

# Parameters:
# -n: Total requests
# -c: Concurrent requests
```

### Expected Performance:
- API response time: <200ms (p95)
- Database query time: <100ms (p95)
- Page load time: <2s

---

## ✅ Pre-Deployment Checklist

Before deploying to production:

### Backend
- [ ] All tests passing
- [ ] No TODO/FIXME in critical paths
- [ ] Environment variables configured
- [ ] Database migrations tested
- [ ] API documentation updated
- [ ] Logs configured (not System.out)

### Web
- [ ] Build succeeds (`npm run build`)
- [ ] No console errors in browser
- [ ] Responsive on mobile/tablet/desktop
- [ ] Cross-browser tested (Chrome, Safari, Firefox)
- [ ] Error boundaries in place
- [ ] API URLs use env variables

### Android
- [ ] Build succeeds (`./gradlew assembleRelease`)
- [ ] Signed with release keystore
- [ ] ProGuard rules tested
- [ ] No hardcoded API URLs
- [ ] Permissions properly requested
- [ ] Network security config set

---

## 🔄 Continuous Testing

### Recommended CI/CD (Future)

```yaml
# .github/workflows/test.yml
name: Tests

on: [push, pull_request]

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
      - run: cd backend && ./mvnw test
      
  web:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
      - run: cd web && npm ci && npm run build
      
  android:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
      - run: cd android && ./gradlew test
```

---

**For questions:** Contact team on Zalo or check `README.md`
