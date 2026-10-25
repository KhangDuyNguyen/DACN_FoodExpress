# FoodExpress Design Document

## UX Vision

FoodExpress là nền tảng giao đồ ăn hiện đại, tập trung vào trải nghiệm người dùng liền mạch và trực quan. Hệ thống phục vụ ba nhóm người dùng chính với những mục tiêu khác nhau:

### Customer Experience (Khách hàng)
- **Mục tiêu**: Đặt đồ ăn nhanh chóng, dễ dàng, theo dõi đơn hàng real-time
- **Ưu tiên**: Tốc độ, đơn giản, trực quan
- **Luồng chính**: Tìm quán → Xem menu → Đặt hàng → Theo dõi giao hàng

### Merchant Experience (Chủ quán)
- **Mục tiêu**: Quản lý menu, xử lý đơn hàng, theo dõi doanh thu
- **Ưu tiên**: Hiệu quả, dễ cập nhật, thông tin rõ ràng
- **Luồng chính**: Cập nhật menu → Nhận đơn → Xác nhận → Hoàn thành

### Admin Experience (Quản trị)
- **Mục tiêu**: Giám sát hệ thống, quản lý người dùng, phân tích dữ liệu
- **Ưu tiên**: Tổng quan toàn diện, báo cáo chi tiết, kiểm soát hệ thống
- **Luồng chính**: Dashboard → Quản lý entities → Analytics

## Design Principles

### 1. Mobile-First Approach
- Thiết kế ưu tiên cho màn hình di động (responsive)
- Touch-friendly interface với kích thước nút/link đủ lớn
- Progressive Web App (PWA) capabilities cho trải nghiệm app-like

### 2. Clarity Over Complexity
- Thông tin hiển thị rõ ràng, không gây nhầm lẫn
- Ưu tiên nội dung quan trọng, ẩn chi tiết phụ
- Feedback tức thì cho mọi hành động của người dùng

### 3. Consistency
- Tái sử dụng component để đảm bảo nhất quán
- Màu sắc, typography, spacing theo design system
- Pattern giống nhau cho các tác vụ tương tự

### 4. Performance
- Tải trang nhanh (< 3 giây)
- Lazy loading cho images và components
- Optimistic UI updates cho trải nghiệm mượt mà

### 5. Accessibility
- WCAG 2.1 Level AA compliance
- Keyboard navigation support
- Screen reader friendly
- Sufficient color contrast

## Design System

### Color Palette

```
Primary (Brand):
- Primary-500: #FF6B35 (Orange) - Call-to-action, brand color
- Primary-600: #E85D2F
- Primary-400: #FF8555

Secondary:
- Secondary-500: #4ECDC4 (Teal) - Accent, success states
- Secondary-600: #3FB8AF

Neutral:
- Gray-900: #1A202C (Text primary)
- Gray-700: #4A5568 (Text secondary)
- Gray-500: #A0AEC0 (Text disabled)
- Gray-200: #E2E8F0 (Borders)
- Gray-50: #F7FAFC (Background)

Semantic:
- Success: #48BB78 (Order completed, available)
- Warning: #ECC94B (Pending, waiting)
- Error: #F56565 (Failed, unavailable)
- Info: #4299E1 (Notifications, tips)
```

### Typography

```
Font Family: 
- Primary: Inter, -apple-system, sans-serif (English)
- Vietnamese: Nunito Sans, Inter, sans-serif

Headings:
- H1: 32px / 2rem, font-weight: 700 (Page titles)
- H2: 24px / 1.5rem, font-weight: 600 (Section titles)
- H3: 20px / 1.25rem, font-weight: 600 (Card titles)

Body:
- Large: 18px / 1.125rem (Important content)
- Regular: 16px / 1rem (Default)
- Small: 14px / 0.875rem (Secondary info)
- Tiny: 12px / 0.75rem (Labels, captions)
```

### Spacing Scale

```
Based on 4px base unit:
- xs: 4px
- sm: 8px
- md: 16px
- lg: 24px
- xl: 32px
- 2xl: 48px
- 3xl: 64px
```

### Component Guidelines

#### Buttons
```
Primary: Orange background, white text, bold
  - Use for main actions (Submit order, Login, Add to cart)
  - Min height: 44px (touch friendly)
  
Secondary: White background, orange border, orange text
  - Use for secondary actions (Cancel, View details)
  
Text: No background, colored text
  - Use for tertiary actions (Learn more, Skip)

States: Default, Hover, Active, Disabled, Loading
```

#### Cards
```
Restaurant Card:
  - Image (16:9 ratio)
  - Name (H3)
  - Cuisine badge
  - Rating + delivery time
  - Opening hours
  - Shadow on hover for interactivity

Menu Item Card:
  - Image (square 1:1)
  - Name + Price
  - Description (truncated)
  - Add button
```

#### Forms
```
Input Fields:
  - Label above input
  - Placeholder for format hints
  - Clear error messages below
  - Min height: 40px
  
Validation:
  - Real-time validation on blur
  - Clear error indicators (red border + message)
  - Success indicators (green checkmark)
```

#### Navigation
```
Customer:
  - Top header: Logo, Search, Cart, Profile
  - Sticky on scroll
  
Merchant:
  - Sidebar: Orders, Menu, Analytics, Settings
  - Collapsible on mobile
  
Admin:
  - Sidebar: Dashboard, Users, Restaurants, Orders, Reports
  - Full-height fixed sidebar
```

## Responsive Breakpoints

```
Mobile: < 640px (sm)
Tablet: 640px - 1024px (md, lg)
Desktop: > 1024px (xl, 2xl)

Design priorities:
- Mobile: Single column, stacked cards
- Tablet: 2-column grid where appropriate
- Desktop: 3-4 column grid, sidebar layouts
```

## Interaction Patterns

### Loading States
- Skeleton screens for content loading
- Spinner for actions (submit, delete)
- Progress bar for multi-step processes

### Empty States
- Friendly illustration + message
- Clear call-to-action to fill the empty state
- Examples: "No restaurants found", "Cart is empty"

### Error Handling
- Toast notifications for temporary messages
- Inline errors for form validation
- Error pages for 404, 500 with helpful actions

### Feedback
- Success toast after actions
- Optimistic updates (immediate UI response)
- Clear state changes (button disabled → loading → success)

## Animation Guidelines

### Transitions
- Duration: 150-300ms for most interactions
- Easing: ease-in-out for natural feel
- Fade + slide for page transitions

### Micro-interactions
- Button hover: scale(1.02)
- Card hover: shadow increase
- Add to cart: quick bounce
- Success: checkmark animation

### Keep it subtle
- Animations enhance, don't distract
- Respect prefers-reduced-motion
- Never block interaction during animation

## Iconography

```
Library: Lucide React / Heroicons
Style: Outline (primary), Solid (emphasis)
Size: 20px (default), 24px (large), 16px (small)

Common icons:
- Search: MagnifyingGlass
- Cart: ShoppingCart
- Location: MapPin
- Time: Clock
- Star: Star (rating)
- Check: CheckCircle (success)
- Warning: ExclamationTriangle
- Info: InformationCircle
```

## Implementation Stack

### Web Frontend
```
Framework: React 19 + TypeScript
Routing: React Router v7
Styling: Tailwind CSS v4
UI Library: Ant Design v6 (complex components)
State: TanStack Query (server state)
Build: Vite
```

### Mobile
```
Framework: React Native + TypeScript
Navigation: React Navigation
Styling: NativeWind (Tailwind for RN)
State: TanStack Query
```

### Design Tokens
- Shared between web and mobile via JSON
- Colors, spacing, typography defined once
- Consistent experience across platforms

## Accessibility Checklist

- [ ] Color contrast ratios meet WCAG AA (4.5:1 for text)
- [ ] All interactive elements keyboard accessible
- [ ] Focus indicators visible
- [ ] Alt text for all images
- [ ] Semantic HTML (h1-h6, nav, main, article)
- [ ] ARIA labels where needed
- [ ] Form labels associated with inputs
- [ ] Error messages announced to screen readers
- [ ] Skip links for main content
- [ ] No autoplay media without controls

## Performance Targets

```
Metrics:
- First Contentful Paint (FCP): < 1.5s
- Largest Contentful Paint (LCP): < 2.5s
- Time to Interactive (TTI): < 3.5s
- Cumulative Layout Shift (CLS): < 0.1

Strategies:
- Code splitting by route
- Image optimization (WebP, lazy loading)
- Font subsetting
- CDN for static assets
- API response caching
```

## Future Considerations

### Internationalization (i18n)
- Currently Vietnamese + English
- Structure ready for additional languages
- Date/time/currency formatting per locale

### Dark Mode
- Design system includes dark mode tokens
- User preference detection
- Smooth theme switching

### Offline Support
- Service Worker for caching
- Offline order queue
- Sync when connection restored

### Advanced Features
- Voice search/order
- AR menu visualization
- Live video of food preparation
- Social features (reviews, sharing)

---

**Document version**: 1.0  
**Last updated**: Week 1-2 (Initial setup)  
**Owner**: Design & Product Team  
**Review cycle**: Monthly or on major feature additions
