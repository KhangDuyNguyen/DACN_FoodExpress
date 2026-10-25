# FoodExpress Web Frontend

React-based web application cho FoodExpress food delivery platform. Web app này phục vụ 3 nhóm người dùng: Khách hàng (Customer), Chủ quán (Merchant), và Quản trị viên (Admin).

## Tech Stack

- **Framework**: React 19 + TypeScript
- **Build Tool**: Vite 8
- **Routing**: React Router v7
- **Styling**: Tailwind CSS v4
- **UI Components**: Ant Design v6
- **State Management**: TanStack Query v5
- **HTTP Client**: Axios
- **Linting**: OxLint

## Project Structure

```
web/
├── src/
│   ├── api/                    # API client functions
│   │   └── health.ts           # Example: health check endpoint
│   ├── layouts/                # Layout components
│   │   ├── AdminLayout.tsx     # Layout for admin pages
│   │   ├── CustomerLayout.tsx  # Layout for customer pages
│   │   └── MerchantLayout.tsx  # Layout for merchant pages
│   ├── pages/                  # Page components
│   │   ├── admin/              # Admin pages (Toàn owns)
│   │   │   └── AdminHome.tsx
│   │   ├── customer/           # Customer pages (Dưỡng owns)
│   │   │   ├── CustomerHome.tsx
│   │   │   ├── RestaurantList.tsx
│   │   │   └── RestaurantDetail.tsx
│   │   └── merchant/           # Merchant pages (Toàn owns)
│   │       ├── MerchantHome.tsx
│   │       ├── Login.tsx
│   │       └── MenuManagement.tsx
│   ├── App.tsx                 # Main app with routing
│   ├── main.tsx                # Entry point
│   └── index.css               # Global styles
├── public/                     # Static assets
├── index.html                  # HTML template
├── vite.config.ts              # Vite configuration
├── tsconfig.json               # TypeScript configuration
├── package.json                # Dependencies
└── README.md                   # This file
```

## URL Structure

Web app được chia thành 3 khu vực chính:

### Customer Routes (/)
```
/                          → Trang chủ khách hàng
/restaurants               → Danh sách quán ăn
/restaurants/:id           → Chi tiết quán + menu
/cart                      → Giỏ hàng (Week 3+)
/checkout                  → Thanh toán (Week 3+)
/orders                    → Lịch sử đơn hàng (Week 4+)
/orders/:id                → Chi tiết đơn hàng + tracking (Week 4+)
```

### Merchant Routes (/merchant)
```
/merchant                  → Dashboard merchant
/merchant/login            → Đăng nhập merchant
/merchant/menu             → Quản lý menu
/merchant/orders           → Danh sách đơn hàng (Week 4+)
/merchant/analytics        → Thống kê doanh thu (Week 5+)
```

### Admin Routes (/admin)
```
/admin                     → Dashboard admin
/admin/restaurants         → Quản lý quán ăn (Week 5+)
/admin/users               → Quản lý người dùng (Week 5+)
/admin/orders              → Tổng quan đơn hàng (Week 5+)
/admin/reports             → Báo cáo hệ thống (Week 5+)
```

## Ownership & Responsibilities

### Customer Pages (Dưỡng)
- Tất cả các trang trong `pages/customer/`
- Trang chủ, danh sách quán, chi tiết quán, giỏ hàng
- Giao diện người dùng cuối, tập trung vào UX

### Merchant & Admin Pages (Toàn)
- Tất cả các trang trong `pages/merchant/` và `pages/admin/`
- Dashboard quản lý, form CRUD
- Authentication & authorization

### Shared Responsibilities
- **Layouts**: Ai cần layout mới thì tự tạo, nhưng cần follow design system
- **API Client**: Mỗi người tạo functions trong `api/` cho endpoints mình dùng
- **Components**: Tạo shared components trong `components/` nếu tái sử dụng nhiều

## Development

### Setup
```bash
# Install dependencies
npm install

# Start dev server (http://localhost:5173)
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview

# Lint code
npm run lint
```

### Environment Variables

Create `.env.local` file (copy from `.env.example`):
```bash
VITE_API_BASE_URL=http://localhost:8080
```

### Adding New Pages

1. **Create page component** in appropriate folder (`pages/customer/`, `pages/merchant/`, `pages/admin/`)
2. **Add route** in `src/App.tsx`
3. **Create API functions** in `src/api/` if needed
4. **Follow design system** (see `/docs/DESIGN.md`)

Example:
```tsx
// src/pages/customer/MyNewPage.tsx
export default function MyNewPage() {
  return <div>My New Page</div>
}

// Add to src/App.tsx
import MyNewPage from './pages/customer/MyNewPage'

// In Routes:
<Route path="my-new-page" element={<MyNewPage />} />
```

### API Integration Pattern

```tsx
// 1. Define API function (src/api/restaurants.ts)
import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL
})

export async function fetchRestaurants() {
  const response = await api.get('/api/restaurants')
  return response.data
}

// 2. Use in component with TanStack Query
import { useQuery } from '@tanstack/react-query'
import { fetchRestaurants } from '../../api/restaurants'

export default function RestaurantList() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['restaurants'],
    queryFn: fetchRestaurants
  })

  if (isLoading) return <div>Loading...</div>
  if (error) return <div>Error: {error.message}</div>

  return <div>{/* Render restaurants */}</div>
}
```

## Design Guidelines

See `/docs/DESIGN.md` for comprehensive design system including:
- Color palette
- Typography
- Component guidelines
- Spacing
- Responsive breakpoints
- Accessibility standards

### Quick Reference

**Colors** (Tailwind classes):
- Primary: `bg-orange-500`, `text-orange-500`
- Secondary: `bg-teal-500`, `text-teal-500`
- Success: `bg-green-500`
- Warning: `bg-yellow-500`
- Error: `bg-red-500`

**Typography**:
- Page title: `text-2xl font-semibold`
- Section title: `text-xl font-semibold`
- Body: `text-base`
- Small: `text-sm`

**Spacing**:
- Use Tailwind's spacing scale: `p-4`, `m-4`, `gap-4`, etc.

## Testing

```bash
# Run tests (coming in Week 3)
npm test

# Run tests with coverage
npm run test:coverage
```

## Code Style

- Use **TypeScript** strictly (no `any` without good reason)
- Use **functional components** + hooks
- Follow **React best practices** (keys in lists, proper useEffect dependencies)
- Use **Tailwind classes** for styling (avoid inline styles)
- Keep components **small and focused** (< 200 lines)
- Extract **reusable logic** into custom hooks
- Use **meaningful variable names** (English preferred, Vietnamese OK for domain terms)

## Common Issues & Solutions

### CORS errors
Make sure backend is running and `VITE_API_BASE_URL` is correct. Backend should allow CORS from `http://localhost:5173`.

### Tailwind classes not working
Make sure Tailwind is properly configured in `vite.config.ts` and classes are written correctly.

### Build errors
Run `npm run build` locally before pushing. Fix TypeScript errors.

### Route not found
Check that route is added in `App.tsx` and component is imported correctly.

## Week-by-Week Progress

### ✅ Week 1-2 (Current)
- [x] Project setup (React, Vite, TypeScript, Tailwind, Ant Design)
- [x] Basic routing structure (Customer, Merchant, Admin)
- [x] Layout components (AdminLayout, CustomerLayout, MerchantLayout)
- [x] Home pages for each user type
- [x] Backend health check integration
- [x] Placeholder pages: RestaurantList, RestaurantDetail, Login, MenuManagement

### 📅 Week 3 (Upcoming)
- [ ] Complete RestaurantList page (fetch & display from API)
- [ ] Complete RestaurantDetail page (menu items display)
- [ ] Complete Merchant Login (authentication)
- [ ] Complete MenuManagement (CRUD operations)
- [ ] Shopping cart functionality
- [ ] Checkout flow

### 📅 Week 4+
- [ ] Order management
- [ ] Real-time order tracking
- [ ] Payment integration
- [ ] Notifications
- [ ] Admin features
- [ ] Analytics dashboard

## Deployment

```bash
# Build for production
npm run build

# Output in dist/ folder
# Deploy to Vercel/Netlify/your hosting
```

## Contributing

1. Create feature branch from `main`
2. Make changes
3. Test locally (`npm run dev` + `npm run lint`)
4. Commit with clear message
5. Push and create PR
6. Get review from team
7. Merge to `main`

## Resources

- [React Docs](https://react.dev)
- [TypeScript Docs](https://www.typescriptlang.org/docs/)
- [Tailwind CSS Docs](https://tailwindcss.com/docs)
- [Ant Design Components](https://ant.design/components/overview/)
- [TanStack Query Docs](https://tanstack.com/query/latest/docs/react/overview)
- [Vite Docs](https://vitejs.dev)

## Contact

- **Dưỡng** (Customer pages): [contact info]
- **Toàn** (Merchant/Admin pages): [contact info]

---

**Last updated**: Week 1-2  
**Node version**: 18+  
**npm version**: 9+
