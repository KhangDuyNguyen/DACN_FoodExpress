-- =====================================================================
-- V20261107_01__seed_quan_demo_va_mon.sql  (Module B tuan 3)
-- DU LIEU GIA de demo va test, ten quan/dia chi/so dien thoai deu la tu dat.
--   1. Cho chu chuoi quan trong V2 (0901234567) dang nhap duoc bang mat khau Demo@12345
--   2. Bo sung mon de quan 1, 3, 4, 5, 6 co tu 10 mon tro len (cung KFC la 6 quan)
--   3. Chu quan demo 0922000001: thuong hieu "Com Nha Demo" co 2 chi nhanh, moi chi nhanh 11 mon
--      (chi nhanh Quan 1 co 1 mon het hang de demo chan mon het)
--   4. Chu quan demo 0922000002: 1 quan "Bun Cha Demo", 10 mon
-- Khong ghi id co dinh cho dong moi: dung bien phien MySQL (@ten_bien) giu id vua tao bang LAST_INSERT_ID().
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Chu chuoi quan seed V2: thay hash gia bang hash BCrypt that cua "Demo@12345".
--    Chi doi khi van con hash gia, de khong ghi de neu may ai do da tu doi mat khau.
-- ---------------------------------------------------------------------
UPDATE users
SET password_hash = '$2a$10$fzWKyu8V19NEeord90ek9ueuFKnb57BFpPozpUUwYC/mgkSE0CHWu'
WHERE phone = '0901234567' AND password_hash = '$2a$10$dummyhash';

-- ---------------------------------------------------------------------
-- 2. Bo sung mon cho quan seed V2 (id nhom mon co dinh trong V2)
-- ---------------------------------------------------------------------
INSERT INTO menu_items (category_id, name, description, price, image_url, is_available, sort_order) VALUES
-- Quan 1 (Pho): 8 -> 10 mon
(1,  'Phở bò viên', 'Phở bò viên dai giòn', 55000, 'https://placehold.co/400x300/png?text=PhoBoVien', 1, 5),
(2,  'Quẩy', 'Quẩy giòn ăn kèm phở (2 cái)', 10000, 'https://placehold.co/400x300/png?text=Quay', 1, 3),
-- Quan 3 (Lau hai san): 8 -> 10 mon
(9,  'Nghêu hấp sả', 'Nghêu hấp sả ớt (500g)', 70000, 'https://placehold.co/400x300/png?text=NgheuHapSa', 1, 4),
(10, 'Nấm tổng hợp', 'Nấm kim châm, nấm đùi gà, nấm hương', 45000, 'https://placehold.co/400x300/png?text=Nam', 1, 2),
-- Quan 4 (Ca phe): 7 -> 10 mon
(12, 'Cà phê muối', 'Cà phê sữa phủ lớp kem muối', 45000, 'https://placehold.co/400x300/png?text=CaPheMuoi', 1, 4),
(13, 'Trà sen kem sữa', 'Trà sen thơm, lớp kem sữa béo', 49000, 'https://placehold.co/400x300/png?text=TraSen', 1, 3),
(14, 'Bánh tiramisu', 'Bánh tiramisu vị cà phê', 45000, 'https://placehold.co/400x300/png?text=Tiramisu', 1, 3),
-- Quan 5 (Nuong Han Quoc): 9 -> 10 mon
(17, 'Mì lạnh Hàn Quốc', 'Mì lạnh nước dùng chua ngọt', 75000, 'https://placehold.co/400x300/png?text=MiLanh', 1, 3),
-- Quan 6 (Sushi): 8 -> 10 mon
(19, 'Sushi lươn', 'Sushi lươn nướng sốt (2 miếng)', 85000, 'https://placehold.co/400x300/png?text=SushiLuon', 1, 4),
(21, 'Gà karaage', 'Gà chiên kiểu Nhật (6 miếng)', 70000, 'https://placehold.co/400x300/png?text=Karaage', 1, 2);

-- ---------------------------------------------------------------------
-- 3. Chu quan demo 0922000001: "Com Nha Demo", 2 chi nhanh
--    Neu chua co user 0922000001 (seed V20261026_01) thi owner_user_id = NULL -> migration bao loi ngay.
-- ---------------------------------------------------------------------
INSERT INTO merchants (owner_user_id, business_name, contact_phone, status) VALUES
((SELECT id FROM users WHERE phone = '0922000001'), 'Cơm Nhà Demo', '0922000001', 'APPROVED');
SET @merchant_com_nha = LAST_INSERT_ID();

-- Chi nhanh Quan 1
INSERT INTO restaurants (merchant_id, name, description, phone, address, lat, lng, logo_url, cover_url,
                         open_status, status, min_order_amount, prep_time_minutes) VALUES
(@merchant_com_nha, 'Cơm Nhà Demo - Chi nhánh Quận 1', 'Cơm văn phòng nấu theo kiểu nhà làm', '028 3000 0001',
 '12 Nguyễn Thị Minh Khai, Phường Bến Nghé, Quận 1, TP.HCM', 10.7812000, 106.7008000,
 'https://placehold.co/200x200/png?text=ComNha', 'https://placehold.co/800x400/png?text=ComNhaQ1',
 'OPEN', 'ACTIVE', 30000, 15);
SET @com_nha_q1 = LAST_INSERT_ID();

INSERT INTO menu_categories (restaurant_id, name, sort_order) VALUES (@com_nha_q1, 'Cơm', 1);
SET @q1_com = LAST_INSERT_ID();
INSERT INTO menu_categories (restaurant_id, name, sort_order) VALUES (@com_nha_q1, 'Món thêm', 2);
SET @q1_them = LAST_INSERT_ID();
INSERT INTO menu_categories (restaurant_id, name, sort_order) VALUES (@com_nha_q1, 'Đồ uống', 3);
SET @q1_uong = LAST_INSERT_ID();

INSERT INTO menu_items (category_id, name, description, price, image_url, is_available, sort_order) VALUES
(@q1_com,  'Cơm sườn nướng', 'Sườn heo nướng than, mỡ hành, đồ chua', 45000, 'https://placehold.co/400x300/png?text=ComSuon', 1, 1),
-- Mon het hang (is_available = 0): dung de demo khong cho them vao gio
(@q1_com,  'Cơm gà xối mỡ', 'Đùi gà chiên giòn, cơm chiên', 50000, 'https://placehold.co/400x300/png?text=ComGa', 0, 2),
(@q1_com,  'Cơm bò lúc lắc', 'Bò lúc lắc xào ớt chuông', 65000, 'https://placehold.co/400x300/png?text=ComBo', 1, 3),
(@q1_com,  'Cơm cá kho tộ', 'Cá basa kho tộ, kèm canh rau', 45000, 'https://placehold.co/400x300/png?text=ComCaKho', 1, 4),
(@q1_com,  'Cơm chay thập cẩm', 'Đậu hũ, nấm, rau củ xào', 40000, 'https://placehold.co/400x300/png?text=ComChay', 1, 5),
(@q1_them, 'Trứng ốp la', 'Thêm 1 trứng ốp la', 8000, 'https://placehold.co/400x300/png?text=Trung', 1, 1),
(@q1_them, 'Canh chua cá lóc', 'Canh chua chén nhỏ', 20000, 'https://placehold.co/400x300/png?text=CanhChua', 1, 2),
(@q1_them, 'Chả trứng hấp', 'Chả trứng hấp thịt', 12000, 'https://placehold.co/400x300/png?text=ChaTrung', 1, 3),
(@q1_uong, 'Trà đá', 'Trà đá ly lớn', 5000, 'https://placehold.co/400x300/png?text=TraDa', 1, 1),
(@q1_uong, 'Nước sâm bí đao', 'Nước sâm nhà nấu', 15000, 'https://placehold.co/400x300/png?text=NuocSam', 1, 2),
(@q1_uong, 'Trà tắc', 'Trà tắc mật ong', 15000, 'https://placehold.co/400x300/png?text=TraTac', 1, 3);

-- Chi nhanh Quan 3 (cung thuc don, mon bo gia khac de thay gia lay theo tung chi nhanh)
INSERT INTO restaurants (merchant_id, name, description, phone, address, lat, lng, logo_url, cover_url,
                         open_status, status, min_order_amount, prep_time_minutes) VALUES
(@merchant_com_nha, 'Cơm Nhà Demo - Chi nhánh Quận 3', 'Cơm văn phòng nấu theo kiểu nhà làm', '028 3000 0003',
 '88 Võ Văn Tần, Phường 6, Quận 3, TP.HCM', 10.7765000, 106.6880000,
 'https://placehold.co/200x200/png?text=ComNha', 'https://placehold.co/800x400/png?text=ComNhaQ3',
 'OPEN', 'ACTIVE', 30000, 15);
SET @com_nha_q3 = LAST_INSERT_ID();

INSERT INTO menu_categories (restaurant_id, name, sort_order) VALUES (@com_nha_q3, 'Cơm', 1);
SET @q3_com = LAST_INSERT_ID();
INSERT INTO menu_categories (restaurant_id, name, sort_order) VALUES (@com_nha_q3, 'Món thêm', 2);
SET @q3_them = LAST_INSERT_ID();
INSERT INTO menu_categories (restaurant_id, name, sort_order) VALUES (@com_nha_q3, 'Đồ uống', 3);
SET @q3_uong = LAST_INSERT_ID();

INSERT INTO menu_items (category_id, name, description, price, image_url, is_available, sort_order) VALUES
(@q3_com,  'Cơm sườn nướng', 'Sườn heo nướng than, mỡ hành, đồ chua', 45000, 'https://placehold.co/400x300/png?text=ComSuon', 1, 1),
(@q3_com,  'Cơm gà xối mỡ', 'Đùi gà chiên giòn, cơm chiên', 50000, 'https://placehold.co/400x300/png?text=ComGa', 1, 2),
(@q3_com,  'Cơm bò lúc lắc', 'Bò lúc lắc xào ớt chuông', 69000, 'https://placehold.co/400x300/png?text=ComBo', 1, 3),
(@q3_com,  'Cơm cá kho tộ', 'Cá basa kho tộ, kèm canh rau', 45000, 'https://placehold.co/400x300/png?text=ComCaKho', 1, 4),
(@q3_com,  'Cơm chay thập cẩm', 'Đậu hũ, nấm, rau củ xào', 40000, 'https://placehold.co/400x300/png?text=ComChay', 1, 5),
(@q3_them, 'Trứng ốp la', 'Thêm 1 trứng ốp la', 8000, 'https://placehold.co/400x300/png?text=Trung', 1, 1),
(@q3_them, 'Canh chua cá lóc', 'Canh chua chén nhỏ', 20000, 'https://placehold.co/400x300/png?text=CanhChua', 1, 2),
(@q3_them, 'Chả trứng hấp', 'Chả trứng hấp thịt', 12000, 'https://placehold.co/400x300/png?text=ChaTrung', 1, 3),
(@q3_uong, 'Trà đá', 'Trà đá ly lớn', 5000, 'https://placehold.co/400x300/png?text=TraDa', 1, 1),
(@q3_uong, 'Nước sâm bí đao', 'Nước sâm nhà nấu', 15000, 'https://placehold.co/400x300/png?text=NuocSam', 1, 2),
(@q3_uong, 'Trà tắc', 'Trà tắc mật ong', 15000, 'https://placehold.co/400x300/png?text=TraTac', 1, 3);

-- ---------------------------------------------------------------------
-- 4. Chu quan demo 0922000002: 1 quan "Bun Cha Demo"
-- ---------------------------------------------------------------------
INSERT INTO merchants (owner_user_id, business_name, contact_phone, status) VALUES
((SELECT id FROM users WHERE phone = '0922000002'), 'Bún Chả Demo', '0922000002', 'APPROVED');
SET @merchant_bun_cha = LAST_INSERT_ID();

INSERT INTO restaurants (merchant_id, name, description, phone, address, lat, lng, logo_url, cover_url,
                         open_status, status, min_order_amount, prep_time_minutes) VALUES
(@merchant_bun_cha, 'Bún Chả Demo', 'Bún chả nướng than hoa kiểu Hà Nội', '028 3000 0005',
 '20 Trần Quốc Thảo, Phường 6, Quận 3, TP.HCM', 10.7840000, 106.6858000,
 'https://placehold.co/200x200/png?text=BunCha', 'https://placehold.co/800x400/png?text=BunChaCover',
 'OPEN', 'ACTIVE', 40000, 20);
SET @bun_cha = LAST_INSERT_ID();

INSERT INTO menu_categories (restaurant_id, name, sort_order) VALUES (@bun_cha, 'Bún', 1);
SET @bun_bun = LAST_INSERT_ID();
INSERT INTO menu_categories (restaurant_id, name, sort_order) VALUES (@bun_cha, 'Món thêm', 2);
SET @bun_them = LAST_INSERT_ID();
INSERT INTO menu_categories (restaurant_id, name, sort_order) VALUES (@bun_cha, 'Đồ uống', 3);
SET @bun_uong = LAST_INSERT_ID();

INSERT INTO menu_items (category_id, name, description, price, image_url, is_available, sort_order) VALUES
(@bun_bun,  'Bún chả Hà Nội', 'Chả miếng, chả viên nướng than', 50000, 'https://placehold.co/400x300/png?text=BunCha', 1, 1),
(@bun_bun,  'Bún chả đặc biệt', 'Nhiều chả, thêm nem rán', 65000, 'https://placehold.co/400x300/png?text=BunChaDB', 1, 2),
(@bun_bun,  'Bún nem', 'Bún ăn kèm nem rán', 45000, 'https://placehold.co/400x300/png?text=BunNem', 1, 3),
(@bun_bun,  'Bún thịt nướng', 'Thịt nướng sả, đồ chua', 45000, 'https://placehold.co/400x300/png?text=BunThitNuong', 1, 4),
(@bun_them, 'Nem rán', 'Nem rán giòn (2 cái)', 20000, 'https://placehold.co/400x300/png?text=NemRan', 1, 1),
(@bun_them, 'Chả viên thêm', 'Thêm 1 phần chả viên', 20000, 'https://placehold.co/400x300/png?text=ChaVien', 1, 2),
(@bun_them, 'Rau sống thêm', 'Thêm 1 đĩa rau sống', 5000, 'https://placehold.co/400x300/png?text=RauSong', 1, 3),
(@bun_uong, 'Trà đá', 'Trà đá ly lớn', 5000, 'https://placehold.co/400x300/png?text=TraDa', 1, 1),
(@bun_uong, 'Nước mía', 'Nước mía tắc', 15000, 'https://placehold.co/400x300/png?text=NuocMia', 1, 2),
(@bun_uong, 'Sữa đậu nành', 'Sữa đậu nành nhà làm', 12000, 'https://placehold.co/400x300/png?text=SuaDauNanh', 1, 3);

-- ---------------------------------------------------------------------
-- 5. Loai mon va gio mo cua cho 3 quan moi (cuisine 1 = Mon Viet trong V2)
-- ---------------------------------------------------------------------
INSERT INTO restaurant_cuisines (restaurant_id, cuisine_id) VALUES
(@com_nha_q1, 1),
(@com_nha_q3, 1),
(@bun_cha, 1);

INSERT INTO restaurant_hours (restaurant_id, day_of_week, open_time, close_time) VALUES
(@com_nha_q1, 1, '07:00:00', '21:00:00'), (@com_nha_q1, 2, '07:00:00', '21:00:00'),
(@com_nha_q1, 3, '07:00:00', '21:00:00'), (@com_nha_q1, 4, '07:00:00', '21:00:00'),
(@com_nha_q1, 5, '07:00:00', '21:00:00'), (@com_nha_q1, 6, '07:00:00', '21:00:00'),
(@com_nha_q1, 7, '08:00:00', '20:00:00'),
(@com_nha_q3, 1, '07:00:00', '21:00:00'), (@com_nha_q3, 2, '07:00:00', '21:00:00'),
(@com_nha_q3, 3, '07:00:00', '21:00:00'), (@com_nha_q3, 4, '07:00:00', '21:00:00'),
(@com_nha_q3, 5, '07:00:00', '21:00:00'), (@com_nha_q3, 6, '07:00:00', '21:00:00'),
(@com_nha_q3, 7, '08:00:00', '20:00:00'),
(@bun_cha, 1, '10:00:00', '21:00:00'), (@bun_cha, 2, '10:00:00', '21:00:00'),
(@bun_cha, 3, '10:00:00', '21:00:00'), (@bun_cha, 4, '10:00:00', '21:00:00'),
(@bun_cha, 5, '10:00:00', '21:00:00'), (@bun_cha, 6, '10:00:00', '21:00:00'),
(@bun_cha, 7, '10:00:00', '21:00:00');
