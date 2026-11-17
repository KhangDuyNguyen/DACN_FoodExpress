-- =====================================================================
-- verify_mock_orders.sql
-- Kịch bản truy vấn kiểm thử và kiểm tra chất lượng dữ liệu mock orders.
-- Đáp ứng yêu cầu:
--   1. Tổng số đơn mock và kiểm tra tính toàn vẹn số học (exact arithmetic).
--   2. Số lượng đơn và doanh thu theo từng quán.
--   3. Phân bố đơn theo ngày và theo khung giờ ăn uống thực tế.
--   4. Top món bán chạy nhất (toàn sàn và theo từng quán).
--   5. Phân bổ phương thức thanh toán và khách hàng.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. TỔNG QUAN ĐƠN HÀNG MOCK VÀ TỶ LỆ TRẠNG THÁI
-- ---------------------------------------------------------------------
SELECT 
    COUNT(*) AS total_mock_orders,
    COUNT(CASE WHEN status = 'DELIVERED' THEN 1 END) AS delivered_orders,
    ROUND(COUNT(CASE WHEN status = 'DELIVERED' THEN 1 END) * 100.0 / COUNT(*), 2) AS delivered_pct,
    COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) AS cancelled_orders,
    ROUND(COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) * 100.0 / COUNT(*), 2) AS cancelled_pct,
    COUNT(CASE WHEN status NOT IN ('DELIVERED', 'CANCELLED') THEN 1 END) AS other_status_orders,
    MIN(created_at) AS earliest_order,
    MAX(created_at) AS latest_order,
    DATEDIFF(MAX(created_at), MIN(created_at)) + 1 AS day_span,
    SUM(CASE WHEN status = 'DELIVERED' THEN total_amount ELSE 0 END) AS total_delivered_revenue_vnd,
    ROUND(AVG(CASE WHEN status = 'DELIVERED' THEN total_amount ELSE NULL END), 0) AS avg_order_value_vnd
FROM orders
WHERE code LIKE 'MOCK-%';

-- ---------------------------------------------------------------------
-- 1B. KIỂM TRA TÍNH TOÀN VẸN SỐ HỌC (EXACT ARITHMETIC AUDIT)
-- Cả 2 truy vấn dưới đây BẮT BUỘC trả về 0 dòng nếu dữ liệu chuẩn xác.
-- ---------------------------------------------------------------------
-- Kiểm tra: total_amount = subtotal + delivery_fee - discount_amount
SELECT id, code, subtotal, delivery_fee, discount_amount, total_amount,
       (subtotal + delivery_fee - discount_amount) AS expected_total
FROM orders
WHERE code LIKE 'MOCK-%'
  AND total_amount != (subtotal + delivery_fee - discount_amount);

-- Kiểm tra: subtotal = SUM(order_items.quantity * order_items.unit_price)
SELECT o.id, o.code, o.subtotal, SUM(oi.quantity * oi.unit_price) AS calculated_subtotal
FROM orders o
JOIN order_items oi ON oi.order_id = o.id
WHERE o.code LIKE 'MOCK-%'
GROUP BY o.id, o.code, o.subtotal
HAVING o.subtotal != calculated_subtotal;

-- ---------------------------------------------------------------------
-- 2. SỐ LƯỢNG ĐƠN VÀ DOANH THU THEO TỪNG QUÁN
-- Dùng để kiểm tra báo cáo doanh thu merchant (/api/merchant/reports/revenue)
-- ---------------------------------------------------------------------
SELECT 
    r.id AS restaurant_id,
    r.name AS restaurant_name,
    COUNT(o.id) AS total_orders,
    COUNT(CASE WHEN o.status = 'DELIVERED' THEN 1 END) AS delivered_count,
    COUNT(CASE WHEN o.status = 'CANCELLED' THEN 1 END) AS cancelled_count,
    ROUND(COUNT(CASE WHEN o.status = 'DELIVERED' THEN 1 END) * 100.0 / COUNT(o.id), 1) AS success_rate_pct,
    COALESCE(SUM(CASE WHEN o.status = 'DELIVERED' THEN o.total_amount ELSE 0 END), 0) AS total_revenue_vnd,
    ROUND(COALESCE(AVG(CASE WHEN o.status = 'DELIVERED' THEN o.total_amount ELSE NULL END), 0), 0) AS avg_order_value_vnd
FROM restaurants r
LEFT JOIN orders o ON o.restaurant_id = r.id AND o.code LIKE 'MOCK-%'
GROUP BY r.id, r.name
ORDER BY total_revenue_vnd DESC;

-- ---------------------------------------------------------------------
-- 3A. PHÂN BỐ ĐƠN HÀNG THEO NGÀY (DAILY DISTRIBUTION)
-- Kiểm tra chuỗi thời gian liên tục từ 2026-10-11 đến 2026-11-21
-- ---------------------------------------------------------------------
SELECT 
    DATE(created_at) AS order_date,
    DAYNAME(created_at) AS day_of_week,
    COUNT(*) AS total_orders,
    COUNT(CASE WHEN status = 'DELIVERED' THEN 1 END) AS delivered_orders,
    COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) AS cancelled_orders,
    SUM(CASE WHEN status = 'DELIVERED' THEN total_amount ELSE 0 END) AS daily_revenue_vnd
FROM orders
WHERE code LIKE 'MOCK-%'
GROUP BY DATE(created_at), DAYNAME(created_at)
ORDER BY order_date ASC;

-- ---------------------------------------------------------------------
-- 3B. PHÂN BỐ ĐƠN HÀNG THEO KHUNG GIỜ VÀ KHUNG BỮA ĂN (MEAL SLOTS)
-- Xác nhận phân bố thực tế: Sáng, Trưa, Chiều xế, Tối, Đêm
-- ---------------------------------------------------------------------
SELECT 
    CASE 
        WHEN TIME(created_at) BETWEEN '07:00:00' AND '09:30:00' THEN '1. Buổi sáng (07:00 - 09:30)'
        WHEN TIME(created_at) BETWEEN '11:00:00' AND '13:30:00' THEN '2. Buổi trưa (11:00 - 13:30)'
        WHEN TIME(created_at) BETWEEN '14:30:00' AND '17:00:00' THEN '3. Chiều xế (14:30 - 17:00)'
        WHEN TIME(created_at) BETWEEN '18:00:00' AND '21:30:00' THEN '4. Buổi tối (18:00 - 21:30)'
        ELSE '5. Khung giờ khác / Đêm (21:30 - 23:30)'
    END AS meal_slot,
    COUNT(*) AS order_count,
    ROUND(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM orders WHERE code LIKE 'MOCK-%'), 2) AS percentage
FROM orders
WHERE code LIKE 'MOCK-%'
GROUP BY meal_slot
ORDER BY meal_slot ASC;

-- ---------------------------------------------------------------------
-- 3C. CHI TIẾT THEO TỪNG GIỜ TRONG NGÀY (HOURLY BREAKDOWN)
-- ---------------------------------------------------------------------
SELECT 
    HOUR(created_at) AS order_hour,
    COUNT(*) AS order_count,
    ROUND(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM orders WHERE code LIKE 'MOCK-%'), 2) AS percentage
FROM orders
WHERE code LIKE 'MOCK-%'
GROUP BY HOUR(created_at)
ORDER BY order_hour ASC;

-- ---------------------------------------------------------------------
-- 4. TOP 20 MÓN BÁN CHẠY NHẤT TOÀN SÀN
-- Dùng để đối chiếu với API /api/merchant/reports/top-items
-- ---------------------------------------------------------------------
SELECT 
    oi.item_name,
    r.name AS restaurant_name,
    SUM(oi.quantity) AS total_quantity_sold,
    oi.unit_price,
    SUM(oi.quantity * oi.unit_price) AS total_revenue_vnd
FROM order_items oi
JOIN orders o ON o.id = oi.order_id
JOIN restaurants r ON r.id = o.restaurant_id
WHERE o.code LIKE 'MOCK-%' AND o.status = 'DELIVERED'
GROUP BY oi.item_name, r.name, oi.unit_price
ORDER BY total_quantity_sold DESC, total_revenue_vnd DESC
LIMIT 20;

-- ---------------------------------------------------------------------
-- 5. TOP 3 MÓN BÁN CHẠY NHẤT CỦA MỖI QUÁN (WINDOW FUNCTION)
-- ---------------------------------------------------------------------
WITH ranked_items AS (
    SELECT 
        r.id AS restaurant_id,
        r.name AS restaurant_name,
        oi.item_name,
        oi.unit_price,
        SUM(oi.quantity) AS quantity_sold,
        SUM(oi.quantity * oi.unit_price) AS revenue_vnd,
        ROW_NUMBER() OVER (PARTITION BY r.id ORDER BY SUM(oi.quantity) DESC) AS rank_in_restaurant
    FROM order_items oi
    JOIN orders o ON o.id = oi.order_id
    JOIN restaurants r ON r.id = o.restaurant_id
    WHERE o.code LIKE 'MOCK-%' AND o.status = 'DELIVERED'
    GROUP BY r.id, r.name, oi.item_name, oi.unit_price
)
SELECT 
    restaurant_id,
    restaurant_name,
    rank_in_restaurant,
    item_name,
    quantity_sold,
    revenue_vnd
FROM ranked_items
WHERE rank_in_restaurant <= 3
ORDER BY restaurant_id ASC, rank_in_restaurant ASC;

-- ---------------------------------------------------------------------
-- 6. PHÂN BỔ PHƯƠNG THỨC THANH TOÁN
-- ---------------------------------------------------------------------
SELECT 
    payment_method,
    COUNT(*) AS order_count,
    ROUND(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM orders WHERE code LIKE 'MOCK-%'), 2) AS percentage,
    SUM(total_amount) AS total_volume_vnd
FROM orders
WHERE code LIKE 'MOCK-%'
GROUP BY payment_method
ORDER BY order_count DESC;

-- ---------------------------------------------------------------------
-- 7. PHÂN BỔ THEO TÀI KHOẢN KHÁCH HÀNG (CUSTOMER DISTRIBUTION)
-- ---------------------------------------------------------------------
SELECT 
    u.phone AS customer_phone,
    u.full_name AS customer_name,
    COUNT(o.id) AS orders_placed,
    COUNT(CASE WHEN o.status = 'DELIVERED' THEN 1 END) AS orders_delivered,
    SUM(CASE WHEN o.status = 'DELIVERED' THEN o.total_amount ELSE 0 END) AS total_spent_vnd
FROM users u
JOIN orders o ON o.customer_id = u.id
WHERE o.code LIKE 'MOCK-%'
GROUP BY u.phone, u.full_name
ORDER BY orders_placed DESC;
