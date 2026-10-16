# Sơ đồ ERD

Chỉ ghi khóa chính, khóa ngoại và các cột chính. Đủ cột nằm trong `V1__init.sql`. Bảng nào thuộc nhóm khác chỉ hiện `id` để làm điểm nối.

## 1. Tài khoản và cửa hàng

```mermaid
erDiagram
  USERS ||--o{ ADDRESSES : has
  USERS ||--o{ MERCHANTS : owns
  MERCHANTS ||--o{ RESTAURANTS : branches
  RESTAURANTS ||--o{ RESTAURANT_HOURS : opens
  RESTAURANTS ||--o{ RESTAURANT_CUISINES : tagged
  CUISINES ||--o{ RESTAURANT_CUISINES : groups
  USERS ||--o{ FAVORITES : saves
  RESTAURANTS ||--o{ FAVORITES : saved_in
  USERS {
    bigint id PK
    string full_name
    string email
    string phone
    string password_hash
    enum role
    enum status
    datetime created_at
  }
  ADDRESSES {
    bigint id PK
    bigint user_id FK
    string label
    string address_line
    decimal lat
    decimal lng
    boolean is_default
  }
  MERCHANTS {
    bigint id PK
    bigint owner_user_id FK
    string business_name
    string tax_code
    enum status
  }
  RESTAURANTS {
    bigint id PK
    bigint merchant_id FK
    string name
    string address
    decimal lat
    decimal lng
    enum open_status
    decimal avg_rating
  }
  RESTAURANT_HOURS {
    bigint id PK
    bigint restaurant_id FK
    int day_of_week
    time open_time
    time close_time
  }
  CUISINES {
    int id PK
    string name
  }
  RESTAURANT_CUISINES {
    bigint restaurant_id PK
    int cuisine_id PK
  }
  FAVORITES {
    bigint user_id PK
    bigint restaurant_id PK
    datetime created_at
  }
```

## 2. Thực đơn

```mermaid
erDiagram
  RESTAURANTS ||--o{ MENU_CATEGORIES : has
  MENU_CATEGORIES ||--o{ MENU_ITEMS : contains
  MENU_ITEMS ||--o{ OPTION_GROUPS : has
  OPTION_GROUPS ||--|{ OPTIONS : offers
  RESTAURANTS ||--o{ COMBOS : sells
  COMBOS ||--|{ COMBO_ITEMS : includes
  MENU_ITEMS ||--o{ COMBO_ITEMS : in
  RESTAURANTS {
    bigint id PK
  }
  MENU_CATEGORIES {
    bigint id PK
    bigint restaurant_id FK
    string name
    int sort_order
  }
  MENU_ITEMS {
    bigint id PK
    bigint category_id FK
    string name
    string description
    decimal price
    string image_url
    boolean is_available
  }
  OPTION_GROUPS {
    bigint id PK
    bigint menu_item_id FK
    string name
    boolean is_required
    int min_select
    int max_select
  }
  OPTIONS {
    bigint id PK
    bigint group_id FK
    string name
    decimal extra_price
    boolean is_available
  }
  COMBOS {
    bigint id PK
    bigint restaurant_id FK
    string name
    decimal price
    boolean is_available
  }
  COMBO_ITEMS {
    bigint combo_id PK
    bigint menu_item_id PK
    int quantity
  }
```

## 3. Giỏ hàng và đơn hàng

```mermaid
erDiagram
  USERS ||--o{ CARTS : owns
  RESTAURANTS ||--o{ CARTS : for
  CARTS ||--|{ CART_ITEMS : contains
  MENU_ITEMS ||--o{ CART_ITEMS : added_as
  CART_ITEMS ||--o{ CART_ITEM_OPTIONS : chooses
  OPTIONS ||--o{ CART_ITEM_OPTIONS : picked
  USERS ||--o{ ORDERS : places
  RESTAURANTS ||--o{ ORDERS : receives
  ORDERS ||--|{ ORDER_ITEMS : contains
  MENU_ITEMS ||--o{ ORDER_ITEMS : sold_as
  ORDER_ITEMS ||--o{ ORDER_ITEM_OPTIONS : chooses
  OPTIONS ||--o{ ORDER_ITEM_OPTIONS : picked
  ORDERS ||--o{ ORDER_STATUS_HISTORY : logs
  USERS ||--o{ ORDER_STATUS_HISTORY : changes
  USERS {
    bigint id PK
  }
  RESTAURANTS {
    bigint id PK
  }
  MENU_ITEMS {
    bigint id PK
  }
  OPTIONS {
    bigint id PK
  }
  CARTS {
    bigint id PK
    bigint user_id FK
    bigint restaurant_id FK
    datetime updated_at
  }
  CART_ITEMS {
    bigint id PK
    bigint cart_id FK
    bigint menu_item_id FK
    int quantity
    string note
  }
  CART_ITEM_OPTIONS {
    bigint cart_item_id PK
    bigint option_id PK
  }
  ORDERS {
    bigint id PK
    string code
    bigint customer_id FK
    bigint restaurant_id FK
    bigint driver_id FK
    enum status
    string delivery_address
    decimal delivery_lat
    decimal delivery_lng
    decimal subtotal
    decimal delivery_fee
    decimal discount_amount
    decimal total_amount
    enum payment_method
    datetime created_at
  }
  ORDER_ITEMS {
    bigint id PK
    bigint order_id FK
    bigint menu_item_id FK
    string item_name
    decimal unit_price
    int quantity
    string note
  }
  ORDER_ITEM_OPTIONS {
    bigint id PK
    bigint order_item_id FK
    bigint option_id FK
    string option_name
    decimal extra_price
  }
  ORDER_STATUS_HISTORY {
    bigint id PK
    bigint order_id FK
    enum status
    bigint changed_by FK
    datetime changed_at
    string note
  }
```

## 4. Thanh toán và khuyến mãi

```mermaid
erDiagram
  ORDERS ||--o{ PAYMENTS : paid_by
  PAYMENTS ||--o{ REFUNDS : refunded
  VOUCHERS ||--o{ VOUCHER_USAGES : used
  USERS ||--o{ VOUCHER_USAGES : uses
  ORDERS ||--o| VOUCHER_USAGES : applies
  RESTAURANTS |o--o{ VOUCHERS : offers
  ORDERS {
    bigint id PK
  }
  USERS {
    bigint id PK
  }
  RESTAURANTS {
    bigint id PK
  }
  PAYMENTS {
    bigint id PK
    bigint order_id FK
    enum method
    decimal amount
    enum status
    string transaction_ref
    datetime paid_at
  }
  REFUNDS {
    bigint id PK
    bigint payment_id FK
    decimal amount
    string reason
    enum status
    datetime created_at
  }
  VOUCHERS {
    bigint id PK
    string code
    enum discount_type
    decimal discount_value
    decimal min_order
    decimal max_discount
    datetime start_at
    datetime end_at
    int usage_limit
    bigint restaurant_id FK
    enum status
  }
  VOUCHER_USAGES {
    bigint id PK
    bigint voucher_id FK
    bigint user_id FK
    bigint order_id FK
    datetime used_at
  }
```

## 5. Tài xế và giao hàng

```mermaid
erDiagram
  USERS ||--o| DRIVERS : is
  DRIVERS ||--o{ DRIVER_LOCATIONS : sends
  ORDERS ||--o{ DRIVER_LOCATIONS : tracked_in
  ORDERS ||--o{ ORDER_ASSIGNMENTS : offered
  DRIVERS ||--o{ ORDER_ASSIGNMENTS : receives
  DRIVERS |o--o{ ORDERS : delivers
  DRIVERS ||--o{ DRIVER_EARNINGS : earns
  ORDERS ||--o| DRIVER_EARNINGS : yields
  USERS {
    bigint id PK
  }
  ORDERS {
    bigint id PK
    bigint driver_id FK
  }
  DRIVERS {
    bigint id PK
    bigint user_id FK
    enum vehicle_type
    string license_plate
    enum status
    boolean is_online
    decimal current_lat
    decimal current_lng
    decimal avg_rating
  }
  DRIVER_LOCATIONS {
    bigint id PK
    bigint driver_id FK
    bigint order_id FK
    decimal lat
    decimal lng
    datetime recorded_at
  }
  ORDER_ASSIGNMENTS {
    bigint id PK
    bigint order_id FK
    bigint driver_id FK
    enum status
    decimal distance_km
    datetime offered_at
    datetime responded_at
  }
  DRIVER_EARNINGS {
    bigint id PK
    bigint driver_id FK
    bigint order_id FK
    decimal amount
    decimal bonus
    datetime created_at
  }
  DELIVERY_FEE_RULES {
    int id PK
    decimal base_fee
    decimal base_distance_km
    decimal per_km_fee
    decimal peak_surcharge
    boolean is_active
  }
```

## 6. Đánh giá, thông báo, hỗ trợ

```mermaid
erDiagram
  ORDERS ||--o| REVIEWS : reviewed_by
  USERS ||--o{ REVIEWS : writes
  RESTAURANTS ||--o{ REVIEWS : gets
  REVIEWS ||--o{ REVIEW_REPLIES : answered
  USERS ||--o{ REVIEW_REPLIES : replies
  USERS ||--o{ NOTIFICATIONS : receives
  USERS ||--o{ DEVICE_TOKENS : registers
  USERS ||--o{ SUPPORT_TICKETS : opens
  ORDERS |o--o{ SUPPORT_TICKETS : about
  USERS {
    bigint id PK
  }
  ORDERS {
    bigint id PK
  }
  RESTAURANTS {
    bigint id PK
  }
  REVIEWS {
    bigint id PK
    bigint order_id FK
    bigint customer_id FK
    bigint restaurant_id FK
    int restaurant_rating
    int driver_rating
    string comment
    datetime created_at
  }
  REVIEW_REPLIES {
    bigint id PK
    bigint review_id FK
    bigint merchant_user_id FK
    string content
    datetime created_at
  }
  NOTIFICATIONS {
    bigint id PK
    bigint user_id FK
    enum type
    string title
    string body
    boolean is_read
    datetime created_at
  }
  DEVICE_TOKENS {
    bigint id PK
    bigint user_id FK
    string fcm_token
    enum platform
    datetime updated_at
  }
  SUPPORT_TICKETS {
    bigint id PK
    bigint user_id FK
    bigint order_id FK
    string subject
    enum status
    datetime created_at
  }
```

## 7. Bảng phục vụ AI

```mermaid
erDiagram
  USERS ||--o{ AI_CHAT_SESSIONS : starts
  AI_CHAT_SESSIONS ||--o{ AI_CHAT_MESSAGES : has
  USERS ||--o{ USER_ITEM_INTERACTIONS : does
  MENU_ITEMS ||--o{ USER_ITEM_INTERACTIONS : on
  REVIEWS ||--o| REVIEW_ANALYSIS : analyzed
  ORDERS ||--o| DELIVERY_TIME_PREDICTIONS : predicted
  RESTAURANTS ||--o{ DEMAND_FORECASTS : forecast
  USERS {
    bigint id PK
  }
  MENU_ITEMS {
    bigint id PK
  }
  REVIEWS {
    bigint id PK
  }
  ORDERS {
    bigint id PK
  }
  RESTAURANTS {
    bigint id PK
  }
  AI_CHAT_SESSIONS {
    bigint id PK
    bigint user_id FK
    datetime started_at
  }
  AI_CHAT_MESSAGES {
    bigint id PK
    bigint session_id FK
    enum role
    text content
    datetime created_at
  }
  USER_ITEM_INTERACTIONS {
    bigint id PK
    bigint user_id FK
    bigint menu_item_id FK
    enum type
    datetime created_at
  }
  REVIEW_ANALYSIS {
    bigint id PK
    bigint review_id FK
    enum sentiment
    string keywords
    datetime analyzed_at
  }
  DELIVERY_TIME_PREDICTIONS {
    bigint id PK
    bigint order_id FK
    int predicted_minutes
    int actual_minutes
    datetime created_at
  }
  DEMAND_FORECASTS {
    bigint id PK
    bigint restaurant_id FK
    date forecast_date
    int predicted_orders
    decimal predicted_revenue
    string model_version
  }
```
