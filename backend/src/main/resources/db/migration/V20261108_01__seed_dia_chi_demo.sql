-- =====================================================================
-- V20261108_01__seed_dia_chi_demo.sql  (Khang, tuan 4)
-- So dia chi cho 2 khach demo (DU LIEU GIA): thay cho 3 dia chi mau tung hard-code o trang thanh toan.
-- Toa do quanh Quan 1, Quan 3, Phu Nhuan (TP.HCM), gan cac quan demo de phi ship hop ly.
-- =====================================================================

INSERT INTO addresses (user_id, label, receiver_name, receiver_phone, address_line, lat, lng, is_default) VALUES
((SELECT id FROM users WHERE phone = '0911000001'), 'Nhà riêng', 'Khách Demo Một', '0911000001',
 '10 Lê Lợi, Phường Bến Nghé, Quận 1, TP.HCM', 10.7756000, 106.7004000, 1),
((SELECT id FROM users WHERE phone = '0911000001'), 'Công ty', 'Khách Demo Một', '0911000001',
 '200 Nguyễn Thị Minh Khai, Phường 6, Quận 3, TP.HCM', 10.7795000, 106.6905000, 0),
((SELECT id FROM users WHERE phone = '0911000001'), 'Nhà bạn', 'Bạn của Khách Một', '0911000003',
 '15 Phan Xích Long, Phường 2, Quận Phú Nhuận, TP.HCM', 10.7990000, 106.6870000, 0),
((SELECT id FROM users WHERE phone = '0911000002'), 'Nhà riêng', 'Khách Demo Hai', '0911000002',
 '88 Nguyễn Đình Chiểu, Phường 6, Quận 3, TP.HCM', 10.7802000, 106.6930000, 1);
