# FoodExpress - Week 2 Design Specification
## Figma Implementation Guide

**For:** Trần Xuân Dưỡng (Student)  
**Deadline:** 24/10/2026 (This Friday)  
**Scope:** Customer Web + Merchant Dashboard mockups  
**Status:** Ready for Figma implementation

---

## 📋 Table of Contents
1. [Customer Web Pages](#customer-web-pages)
2. [Merchant Dashboard Pages](#merchant-dashboard-pages)
3. [Design System](#design-system)
4. [Component Library](#component-library)
5. [Interaction Patterns](#interaction-patterns)
6. [Implementation Checklist](#implementation-checklist)

---

## 🛒 Customer Web Pages

### Page 1: Home / Restaurant List

**URL:** `/`  
**Purpose:** Browse all restaurants with search & filter

#### Layout Structure (Mobile First)
```
┌─ Header (sticky)
│  ├─ Logo "FoodExpress"
│  ├─ Search bar (search cuisine/restaurant)
│  └─ Cart icon (badge showing count)
├─ Filter Section (horizontal scroll)
│  └─ Cuisine chips (Vietnamese, Korean, Japanese, etc.)
├─ Restaurant List (vertical scroll)
│  └─ Restaurant Card (repeated)
└─ Footer (minimal)
```

#### Components Detail

**Header (Height: 56px)**
- Background: White (#FFFFFF)
- Border-bottom: 1px solid #E0E0E0
- Sticky: yes
- Padding: 8px 16px
- Items:
  - Logo: "FoodExpress" (24px font, orange #FF6B35)
  - Search input: 40px height, 16px padding, placeholder "Tìm quán..."
  - Cart icon: 24x24px, badge shows "0" (or count)

**Filter Section (Height: 50px)**
- Horizontal scrollable
- Padding: 12px 16px
- Cuisine chips (each ~100px):
  - Background: #F5F5F5
  - Border: 1px solid #E0E0E0
  - Border-radius: 20px
  - Padding: 8px 16px
  - Font: 14px
  - **States:**
    - Default: Gray text, gray border
    - Active/Selected: Orange background (#FF6B35), white text, orange border
  - Chips: "Tất cả", "Món Việt", "Món Hàn", "Món Nhật", "Đồ Ăn Nhanh", "Đồ Uống", "Lẩu", "Nướng", "Tráng Miệng"

**Restaurant Card**
- Width: Full width - 32px margin
- Height: ~160px
- Background: White
- Border-radius: 12px
- Box-shadow: 0 1px 3px rgba(0,0,0,0.1)
- Margin-bottom: 12px
- **Components:**
  - Image (Height: 100px)
    - Aspect ratio: 16:9
    - Background: #F5F5F5
    - Border-radius: 12px 12px 0 0
    - Placeholder: Restaurant photo or placeholder image
  - **Content area (Padding: 12px):**
    - **Row 1:** Restaurant name (16px, bold) + Rating star (⭐) + "4.5"
    - **Row 2:** Cuisine type (14px, gray) + "Phở, Cơm"
    - **Row 3:** Location icon + "2.5km" + Prep time "20-30 phút"
    - **Row 4:** "Min order: 30,000₫" (small text, gray)
  - **Footer:** "Open: 7:00 - 22:00" (small, green text if open, red if closed)

#### Responsive Breakpoints
- Mobile (< 640px): Single column, full width minus padding
- Tablet (640-1024px): 2 columns, sidebar filters
- Desktop (> 1024px): 3 columns, left sidebar filters

---

### Page 2: Restaurant Detail & Menu

**URL:** `/restaurants/:id`  
**Purpose:** View restaurant details and menu

#### Layout Structure
```
┌─ Header (fixed/sticky)
│  └─ Back button + Restaurant name
├─ Restaurant Hero (250px height on mobile)
│  ├─ Cover image (aspect: 16:9)
│  └─ Restaurant info overlay (semi-transparent)
│     ├─ Logo (48x48px, rounded)
│     ├─ Name (24px, bold, white)
│     └─ Rating + Open hours (14px, white)
├─ Info Section (expandable)
│  ├─ Full address
│  ├─ Phone
│  ├─ Hours (all days)
│  └─ "Explore menu" button
├─ Menu Categories (tabs)
│  └─ "Món chính", "Đồ uống", "Tráng miệng"
├─ Menu Items (grid)
│  └─ Menu Item Card (repeated)
└─ Footer (checkout bar - sticky)
   └─ "Add to cart" button or "Cart (N items)"
```

#### Components Detail

**Restaurant Hero Section (Mobile: 250px, Desktop: 350px)**
- Image (full width, aspect 16:9)
- Overlay gradient: Dark gradient bottom (rgba(0,0,0,0.4) → rgba(0,0,0,0.7))
- Content positioned at bottom:
  - Logo: 48x48px, -24px margin-top (overlaps image)
  - Name: 24px, bold, white, padding 16px
  - Info row: "⭐ 4.5 (320 reviews) • 🕐 7:00 - 22:00" (14px, white)

**Info Section (Initially collapsed)**
- "i" icon → click to expand
- When expanded:
  - 📍 Address: "5 Nguyễn Trãi, Quận 1"
  - 📞 Phone: "028 3925 6699"
  - 🕐 Hours: 7 day rows (Mon-Sun, each showing open time)
  - Min order: "30,000₫"
  - Avg prep: "20 phút"

**Menu Categories (Horizontal tabs)**
- Sticky below hero on scroll
- Background: White
- Border-bottom: 2px solid #E0E0E0
- Tab item (width: auto, min 80px):
  - Padding: 12px 16px
  - Font: 16px, bold
  - **States:**
    - Inactive: Gray text
    - Active: Orange text, orange bottom border (3px)
- Scroll to category on click

**Menu Item Card**
- Width: Full width - 32px
- Background: White
- Border-radius: 8px
- Border: 1px solid #E0E0E0
- Margin-bottom: 12px
- **Layout (horizontal flex):**
  - Image (80x80px, left)
    - Border-radius: 8px
    - Aspect: 1:1
  - Content (flex-grow: 1)
    - Name (16px, bold)
    - Description (14px, gray, 2 lines max, truncate)
    - Price (18px, bold, orange)
  - Add button (40x40px, right)
    - "+" icon
    - Background: Orange (#FF6B35)
    - Color: White
    - Border-radius: 8px
    - Hover: Orange darker (#E85D2F)

---

## 🏪 Merchant Dashboard Pages

### Page 1: Merchant Login

**URL:** `/merchant/login`  
**Purpose:** Authenticate merchant user

#### Layout Structure (Centered)
```
┌─ Background: Gradient orange to coral
├─ Card (width: 400px, max-width: 90%)
│  ├─ Logo + Title
│  ├─ Form
│  │  ├─ Email input
│  │  ├─ Password input
│  │  └─ "Forgot password?" link
│  ├─ Login button (full width)
│  └─ "Don't have account?" link
└─ Footer: "Powered by FoodExpress"
```

#### Components Detail

**Card**
- Width: 400px (mobile: 90% - 32px padding)
- Background: White
- Border-radius: 12px
- Box-shadow: 0 2px 12px rgba(0,0,0,0.15)
- Padding: 32px
- Margin: auto, centered

**Header**
- Logo: "FoodExpress" (28px, orange, bold)
- Title: "Đăng nhập cửa hàng" (20px, bold)
- Subtitle: "Dành cho chủ quán / nhân viên" (14px, gray)
- Margin-bottom: 24px

**Form Fields**
- **Email input:**
  - Label: "Email hoặc Tên đăng nhập" (14px)
  - Input height: 44px
  - Padding: 12px 16px
  - Border: 1px solid #E0E0E0
  - Border-radius: 8px
  - Placeholder: "nhập email..."
  - Margin-bottom: 16px
  
- **Password input:**
  - Label: "Mật khẩu" (14px)
  - Same styling as email
  - Eye icon toggle (show/hide password)
  - Margin-bottom: 8px

- **Forgot password link:**
  - Right-aligned
  - Color: #FF6B35
  - Font-size: 12px
  - Margin-bottom: 24px

**Login Button**
- Full width: 100%
- Height: 44px
- Background: Orange gradient (#FF6B35 → #F7931E)
- Color: White
- Font: 16px, bold
- Border: none
- Border-radius: 8px
- Cursor: pointer
- **States:**
  - Hover: Darker gradient, shadow
  - Active: Transform scale(0.98)
  - Loading: Spinner, disabled cursor
  - Disabled: Gray, cursor not-allowed

**Sign up link**
- Text: "Chưa có tài khoản? Đăng ký tại đây"
- Text-align: center
- Margin-top: 24px
- Link color: #FF6B35

---

### Page 2: Menu Management

**URL:** `/merchant/menu`  
**Purpose:** Manage restaurant menu items

#### Layout Structure (Sidebar + Content)
```
┌─ Header (sticky)
│  ├─ Back / Restaurant name
│  └─ Quick actions (Add category, Add item)
├─ Main content (flex layout)
│  ├─ Sidebar (20% width)
│  │  └─ Categories list (scroll)
│  │     └─ Category item (clickable)
│  └─ Content area (80% width)
│     ├─ Category header
│     ├─ Items grid
│     │  └─ Item card (repeated)
│     └─ "Add item to this category" button
└─ Footer: "Changes auto-saved"
```

#### Components Detail

**Header (Height: 56px, sticky)**
- Background: Orange (#FF6B35)
- Color: White
- Padding: 12px 16px
- **Left:** Back arrow + "Quản lý Menu"
- **Right:** Two buttons
  - "+ Category" (secondary)
  - "+ Item" (primary, white bg)

**Sidebar (Mobile: drawer, Desktop: 20% width)**
- Background: #F5F5F5
- Border-right: 1px solid #E0E0E0
- Padding: 16px 0
- **Category items:**
  - Height: 48px
  - Padding: 12px 16px
  - Font: 14px
  - **States:**
    - Default: Gray text
    - Active: Orange bg, white text, left border 3px orange
    - Hover: Light gray bg

**Content Area**
- Padding: 24px

**Category Header**
- Title: Category name (20px, bold)
- Description: Item count "5 items"
- Action buttons: [Edit] [Delete]
- Margin-bottom: 20px
- Border-bottom: 1px solid #E0E0E0

**Menu Item Card (Grid layout, responsive)**
- Desktop: 3 columns, 300px width
- Tablet: 2 columns
- Mobile: 1 column, full width
- Background: White
- Border: 1px solid #E0E0E0
- Border-radius: 8px
- Padding: 12px
- **Components:**
  - Image (100%, aspect 1:1)
    - Border-radius: 8px
    - Margin-bottom: 12px
  - Name (16px, bold)
  - Price (18px, bold, orange) + Status badge
    - Badge: "Available" (green) or "Out of stock" (gray)
    - Font-size: 12px
  - Action buttons (2 buttons, each ~40px):
    - [Edit] - light blue
    - [Delete] - light red
  - Hover: Shadow increase, slight scale

**Add Item Button (Full width)**
- Height: 44px
- Border: 2px dashed #E0E0E0
- Background: Transparent
- Border-radius: 8px
- Color: #999
- Font: 14px, bold
- Hover: Dashed border becomes solid orange
- Margin-top: 24px

---

## 🎨 Design System

### Color Palette

```
Primary Colors:
- Orange (#FF6B35)     - Main CTA, brand
- Orange Dark (#E85D2F) - Hover state
- Orange Light (#FF8555) - Light backgrounds

Secondary:
- Teal (#4ECDC4)       - Success, highlights
- Teal Dark (#3FB8AF)  - Hover

Neutral:
- White (#FFFFFF)      - Backgrounds
- Gray-50 (#F5F5F5)    - Light bg
- Gray-200 (#E0E0E0)   - Borders
- Gray-500 (#A0AEC0)   - Disabled text
- Gray-700 (#4A5568)   - Secondary text
- Gray-900 (#1A202C)   - Primary text

Semantic:
- Success (#48BB78)    - Positive actions
- Warning (#ECC94B)    - Caution
- Error (#F56565)      - Errors, unavailable
- Info (#4299E1)       - Information
```

### Typography

```
Font: Inter, -apple-system, sans-serif

Headings:
- H1: 32px, 700, line-height 1.2
- H2: 24px, 600, line-height 1.3
- H3: 20px, 600, line-height 1.3

Body:
- Large: 18px, 400, line-height 1.5
- Regular: 16px, 400, line-height 1.5
- Small: 14px, 400, line-height 1.5
- Tiny: 12px, 400, line-height 1.4
```

### Spacing (8px base unit)

```
xs:  4px   (0.5x)
sm:  8px   (1x)
md:  16px  (2x)
lg:  24px  (3x)
xl:  32px  (4x)
2xl: 48px  (6x)
3xl: 64px  (8x)
```

### Border Radius

```
sm: 4px    (inputs, small elements)
md: 8px    (cards)
lg: 12px   (modal, large cards)
full: 9999px (pills, badges)
```

### Shadow

```
sm:   0 1px 2px rgba(0,0,0,0.05)
md:   0 1px 3px rgba(0,0,0,0.1), 0 1px 2px rgba(0,0,0,0.06)
lg:   0 10px 15px -3px rgba(0,0,0,0.1)
xl:   0 20px 25px -5px rgba(0,0,0,0.1)
```

---

## 🧩 Component Library

### Buttons

**Primary Button**
- Background: Orange gradient
- Color: White
- Height: 44px (default), 40px (small), 48px (large)
- Padding: 0 24px (horizontal)
- Border-radius: 8px
- Font: 16px, 600
- Transition: all 150ms ease

**Secondary Button**
- Background: Transparent
- Border: 2px solid Orange
- Color: Orange
- Same dimensions as primary
- Hover: Orange light background

**Tertiary Button**
- Background: Transparent
- Color: Orange
- No border
- Hover: Light bg

### Input Fields

**Text Input (Email, Text, etc.)**
- Height: 44px
- Padding: 12px 16px
- Border: 1px solid #E0E0E0
- Border-radius: 8px
- Font: 16px
- Focus: Orange border (2px)
- Error: Red border
- Disabled: Gray bg, opacity 0.5

**Textarea**
- Min-height: 120px
- Same styling as text input
- Font: monospace for code (optional)

### Cards

**Restaurant Card** (see above)

**Menu Item Card** (see above)

**Info Card**
- Padding: 16px
- Background: Light blue (#E7F4F8)
- Border-left: 4px solid #4299E1
- Border-radius: 8px
- "ℹ️ Info text here"

### Badges

**Status Badge**
- Height: 24px
- Padding: 4px 12px
- Border-radius: 12px
- Font: 12px, 600
- **Colors:**
  - Green: Available
  - Gray: Out of stock
  - Orange: Coming soon
  - Red: Closed

### Modals (if needed)

**Overlay**
- Background: rgba(0,0,0,0.5)
- Backdrop-filter: blur(2px)

**Dialog**
- Width: 90% (mobile), 500px (desktop)
- Background: White
- Border-radius: 12px
- Padding: 32px
- Box-shadow: Large shadow

---

## 🎭 Interaction Patterns

### Loading States

**Skeleton Loading**
- Placeholder: Gray gradient animated shimmer
- Height: same as content
- Border-radius: same as content

**Spinner**
- 24x24px rotating circle
- Color: Orange
- Duration: 1s linear infinite

### Empty States

**No Restaurants Found**
```
┌─ Illustration (100x100px)
├─ "Không tìm thấy quán"
├─ "Thử tìm kiếm hoặc lọc khác"
└─ Search input (secondary)
```

**Empty Cart**
```
┌─ Illustration
├─ "Giỏ hàng trống"
├─ "Khám phá các quán ngon"
└─ Button: "Tiếp tục mua sắm"
```

### Toast Notifications

**Success**
- Background: Green (#48BB78)
- Icon: Checkmark
- Position: Top right
- Duration: 3s auto-dismiss

**Error**
- Background: Red (#F56565)
- Icon: X
- Duration: 5s auto-dismiss

**Info**
- Background: Blue (#4299E1)
- Icon: Info circle
- Duration: 3s auto-dismiss

### Hover Effects

- **Button hover:** 2px box-shadow, scale 1.02
- **Card hover:** 2px → 8px shadow increase
- **Link hover:** Underline + color change

### Transitions

- Duration: 150-300ms
- Easing: ease-in-out
- Properties: all except transforms

---

## ✅ Implementation Checklist

### Customer Web (Restaurant List + Detail)

- [ ] Create Figma file "FoodExpress-Week2"
- [ ] Set up artboards:
  - [ ] Home (Mobile 375x812)
  - [ ] Home (Tablet 768x1024)
  - [ ] Home (Desktop 1440x900)
  - [ ] Restaurant Detail (Mobile 375x812)
  - [ ] Restaurant Detail (Tablet 768x1024)
  - [ ] Restaurant Detail (Desktop 1440x900)

**Components to create:**
- [ ] Header component (sticky)
- [ ] Search bar
- [ ] Filter chips (with active state)
- [ ] Restaurant card (with hover state)
- [ ] Restaurant hero section
- [ ] Menu category tabs
- [ ] Menu item card (with 3 columns on desktop)
- [ ] Add to cart button
- [ ] Toast notification
- [ ] Empty state screen

### Merchant Dashboard (Login + Menu Management)

- [ ] Set up artboards:
  - [ ] Login (Mobile 375x812)
  - [ ] Login (Desktop 1440x900)
  - [ ] Menu Management (Desktop 1440x900)
  - [ ] Menu Management (Mobile 375x812)

**Components to create:**
- [ ] Login card (centered)
- [ ] Form inputs (text, password)
- [ ] Login button (with loading state)
- [ ] Menu sidebar
- [ ] Category item (active/inactive states)
- [ ] Menu item card with actions
- [ ] Category header
- [ ] Add item button (dashed border)
- [ ] Buttons (primary, secondary, tertiary)

### Design System
- [ ] Create color styles (all 12 colors)
- [ ] Create typography styles (H1-H3, Body, Small, Tiny)
- [ ] Create spacing styles (xs, sm, md, lg, xl, 2xl, 3xl)
- [ ] Create shadow styles (sm, md, lg, xl)
- [ ] Create component library with all variants

### Documentation
- [ ] Add component descriptions
- [ ] Add usage notes
- [ ] Create color guide
- [ ] Create typography guide

---

## 📱 Responsive Notes

### Mobile (< 640px)
- Full width (16px margin each side)
- Sidebar as drawer/modal
- Tabs horizontal scroll
- 1 column grid

### Tablet (640-1024px)
- 2 column grid for items
- Sidebar visible (20% width)
- Normal tabs

### Desktop (> 1024px)
- 3 column grid for items
- Sidebar always visible
- Full width

---

## 🎬 Animation Guidelines

**Page Transitions**
- Fade in + slide up: 300ms ease-out
- Content: opacity 0→1, transform translateY(20px)→translateY(0)

**Component Interactions**
- Button click: scale 0.98→1
- Card hover: shadow sm→lg
- Tab change: fade content, scroll smooth
- Toast appear: slide right, 300ms ease-out

---

## 📤 Handoff Checklist

Before submitting to development:

- [ ] All screens at all breakpoints designed
- [ ] All components in main component library
- [ ] Color styles defined
- [ ] Typography styles defined
- [ ] All hover/active/disabled states designed
- [ ] Spacing consistent (8px grid)
- [ ] Naming convention clear (Page/Component/State)
- [ ] Prototype interactions linked
- [ ] Design file organized in sections
- [ ] README with component usage

---

**Next Steps:**
1. Open Figma
2. Create new file "FoodExpress-Week2"
3. Import colors from "Color Palette" section
4. Set up typography styles
5. Create each component following this spec
6. Add to component library
7. Build artboards using components
8. Test responsive scales
9. Share link in README.md

**Estimated Time:** 4-6 hours for complete mockups  
**Deadline:** Friday 24/10/2026 before 20:00
