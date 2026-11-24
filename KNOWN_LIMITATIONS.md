# 🚧 Known Limitations & Future Work

**Project:** DACN FoodExpress  
**Last Updated:** 24/11/2026 (Tuần 7)

---

## 📱 Android Customer App

### Authentication
**Status:** ⚠️ Not Implemented

**Current State:**
- Customer app does not have login/register flow
- Token hardcoded as empty string in `MyOrdersViewModel`
- Orders API will return 401 Unauthorized

**Workaround for Demo:**
- Use web app for customer flows
- Or hardcode a valid JWT token for testing

**Future Work:**
- Implement login/register screens
- Add DataStore for token persistence
- Handle token refresh

**Files Affected:**
- `android/customer-app/.../MyOrdersViewModel.kt:40`

---

## 💳 Payment

### VNPay Refund
**Status:** ⚠️ Partial Implementation

**Current State:**
- Orders paid via VNPay can be cancelled
- No automatic refund to customer
- Manual refund required

**TODOs:**
- `backend/.../OrderManagementService.java:159` - Refund on cancel
- `backend/.../PaymentResultService.java:63` - Refund on payment conflict
  
**Future Work:**
- Create `refunds` table
- Implement VNPay refund API
- Add merchant refund management UI

---

## 🛒 Shopping Cart

### Add to Cart from Restaurant Detail
**Status:** ⚠️ UI Placeholder

**Current State:**
- "Thêm vào giỏ" button exists but does nothing
- Cart flow not implemented in Android app

**Files Affected:**
- `android/customer-app/.../RestaurantDetailScreen.kt:328`

**Workaround:**
- Use web app for cart/checkout

**Future Work:**
- Implement Cart screen
- Add CartViewModel
- Persist cart to local storage

---

## 🔄 Realtime Features

### WebSocket Tracking
**Status:** ✅ Implemented (Backend + Driver App)
**Status:** ⚠️ Not Integrated (Customer Android App)

**Current State:**
- Backend has WebSocket STOMP for order tracking
- Driver app sends GPS updates
- Customer Android app polls every 10 seconds instead of WebSocket

**Files Affected:**
- `web/src/pages/customer/OrderTracking.tsx:14-15`

**Future Work:**
- Replace polling with WebSocket subscription
- Real-time map updates

---

## 📊 Reporting

### Advanced Analytics
**Status:** ⚠️ Basic Implementation

**Current State:**
- Basic earnings/revenue charts
- No drill-down capabilities
- No export to CSV/PDF

**Future Work:**
- Advanced filtering (date range, restaurant, driver)
- Export reports
- Predictive analytics

---

## 🧪 Testing

### Test Coverage
**Current Coverage:**
- Backend: ~60% (mostly integration tests)
- Web: Minimal (manual testing only)
- Android: Property-based tests for critical flows

**Future Work:**
- Increase backend unit test coverage to 80%
- Add E2E tests with Cypress/Playwright
- Add Android instrumentation tests

---

## 🌐 Deployment

### Production Readiness
**Status:** ⚠️ Development Only

**Missing for Production:**
- [ ] Environment configuration (staging/prod)
- [ ] Database migration strategy
- [ ] Monitoring & logging (Sentry, New Relic)
- [ ] CI/CD pipeline
- [ ] Load balancing
- [ ] Database backup strategy
- [ ] SSL certificates
- [ ] Rate limiting
- [ ] API versioning

**Future Work:**
- Deploy to Railway/Render (backend)
- Deploy to Vercel/Netlify (web)
- Setup Firebase App Distribution (Android)

---

## 🔐 Security

### Known Issues
1. **CORS:** Wildcard allowed in dev
2. **Rate Limiting:** Not implemented
3. **SQL Injection:** Mitigated by parameterized queries, but needs audit
4. **XSS:** React auto-escapes, but needs manual review
5. **JWT:** No refresh token strategy

**Future Work:**
- Security audit
- Penetration testing
- OWASP compliance check

---

## 📱 Mobile

### iOS App
**Status:** ❌ Not Started

**Future Work:**
- Port Android app to iOS using Kotlin Multiplatform
- Or rebuild with React Native/Flutter

---

## 🌍 Internationalization
**Status:** ❌ Not Implemented

**Current State:**
- All text hardcoded in Vietnamese
- No i18n framework

**Future Work:**
- Add i18n support
- English translations
- Multi-currency support

---

## 📝 Documentation

### Missing Docs
- [ ] API endpoint documentation (Swagger is basic)
- [ ] Database schema diagram
- [ ] Deployment runbook
- [ ] Troubleshooting guide

---

**Note:** This document tracks known limitations as of Tuần 7 MVP deadline.  
Many of these will be addressed in post-MVP iterations.
