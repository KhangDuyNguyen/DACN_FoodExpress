-- =====================================================================
-- V2__seed_cuisines_restaurants.sql
-- Seed data for cuisines, restaurants, menu categories, and menu items
-- Week 1-2: Restaurant/menu area initial data (HCMC area)
-- Note: Combos and option_groups/options deferred to later weeks
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. SEED USER AND MERCHANT (prerequisite for restaurants)
-- ---------------------------------------------------------------------
-- Create a demo merchant user and merchant record
INSERT INTO users (id, full_name, email, phone, password_hash, role, status) VALUES
(1, 'Demo Merchant Owner', 'merchant@demo.com', '0901234567', '$2a$10$dummyhash', 'MERCHANT', 'ACTIVE');

INSERT INTO merchants (id, owner_user_id, business_name, status) VALUES
(1, 1, 'Demo Merchant Group', 'APPROVED');

-- ---------------------------------------------------------------------
-- 1. CUISINES (8 types)
-- ---------------------------------------------------------------------
INSERT INTO cuisines (id, name, icon_url) VALUES
(1, 'Món Việt', 'https://placehold.co/100x100/png?text=MonViet'),
(2, 'Món Hàn', 'https://placehold.co/100x100/png?text=MonHan'),
(3, 'Món Nhật', 'https://placehold.co/100x100/png?text=MonNhat'),
(4, 'Đồ Ăn Nhanh', 'https://placehold.co/100x100/png?text=DoAnNhanh'),
(5, 'Đồ Uống', 'https://placehold.co/100x100/png?text=DoUong'),
(6, 'Lẩu', 'https://placehold.co/100x100/png?text=Lau'),
(7, 'Nướng', 'https://placehold.co/100x100/png?text=Nuong'),
(8, 'Tráng Miệng', 'https://placehold.co/100x100/png?text=TrangMieng');

-- ---------------------------------------------------------------------
-- 2. RESTAURANTS (12 realistic HCMC locations, merchant_id=1)
-- ---------------------------------------------------------------------
INSERT INTO restaurants (id, merchant_id, name, description, phone, address, lat, lng, logo_url, cover_url, open_status, status, min_order_amount, prep_time_minutes, avg_rating, rating_count) VALUES
(1, 1, 'Phở 24 Nguyễn Trãi', 'Phở bò truyền thống Hà Nội, nước dùng ninh từ xương 12 tiếng', '028 3925 6699', '5 Nguyễn Trãi, Phường Bến Thành, Quận 1, TP.HCM', 10.7700, 106.6950, 'https://placehold.co/200x200/png?text=Pho24', 'https://placehold.co/800x400/png?text=Pho24Cover', 'OPEN', 'ACTIVE', 30000, 20, 4.5, 320),
(2, 1, 'Gà Rán KFC Pasteur', 'Gà rán giòn tan, phục vụ nhanh', '1900 5588', '142 Pasteur, Phường 6, Quận 3, TP.HCM', 10.7780, 106.6920, 'https://placehold.co/200x200/png?text=KFC', 'https://placehold.co/800x400/png?text=KFCCover', 'OPEN', 'ACTIVE', 50000, 15, 4.2, 450),
(3, 1, 'Lẩu Hải Sản Mộc', 'Lẩu hải sản tươi sống, nước dùng đậm đà', '028 3822 1133', '25 Lê Thánh Tôn, Phường Bến Nghé, Quận 1, TP.HCM', 10.7755, 106.7025, 'https://placehold.co/200x200/png?text=LauMoc', 'https://placehold.co/800x400/png?text=LauMocCover', 'OPEN', 'ACTIVE', 100000, 30, 4.6, 280),
(4, 1, 'Cà Phê Highlands Lê Lợi', 'Cà phê Việt Nam, không gian thoải mái', '028 3821 9977', '39 Lê Lợi, Phường Bến Nghé, Quận 1, TP.HCM', 10.7745, 106.7005, 'https://placehold.co/200x200/png?text=Highlands', 'https://placehold.co/800x400/png?text=HighlandsCover', 'OPEN', 'ACTIVE', 25000, 10, 4.4, 380),
(5, 1, 'Gogi House Nguyễn Huệ', 'Thịt nướng Hàn Quốc phong cách BBQ', '028 3914 3456', '72 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP.HCM', 10.7742, 106.7022, 'https://placehold.co/200x200/png?text=Gogi', 'https://placehold.co/800x400/png?text=GogiCover', 'OPEN', 'ACTIVE', 80000, 25, 4.7, 420),
(6, 1, 'Sushi Hokkaido Đồng Khởi', 'Sushi và sashimi Nhật Bản chính hiệu', '028 3823 7788', '68 Đồng Khởi, Phường Bến Nghé, Quận 1, TP.HCM', 10.7760, 106.7030, 'https://placehold.co/200x200/png?text=Sushi', 'https://placehold.co/800x400/png?text=SushiCover', 'OPEN', 'ACTIVE', 70000, 20, 4.5, 190),
(7, 1, 'Bánh Mì Huỳnh Hoa', 'Bánh mì thập cẩm ngon nổi tiếng Sài Gòn', '028 3829 5572', '26 Lê Thị Riêng, Phường Bến Thành, Quận 1, TP.HCM', 10.7690, 106.6940, 'https://placehold.co/200x200/png?text=BanhMi', 'https://placehold.co/800x400/png?text=BanhMiCover', 'OPEN', 'ACTIVE', 20000, 15, 4.3, 510),
(8, 1, 'Lẩu Thái Tom Yum Hai Bà Trưng', 'Lẩu Thái chua cay đặc trưng', '028 3822 4455', '156 Hai Bà Trưng, Phường Đa Kao, Quận 1, TP.HCM', 10.7820, 106.6990, 'https://placehold.co/200x200/png?text=LauThai', 'https://placehold.co/800x400/png?text=LauThaiCover', 'OPEN', 'ACTIVE', 90000, 25, 4.4, 220),
(9, 1, 'Kem Bạc Hà Tràng Tiền', 'Kem tươi và tráng miệng Việt Nam', '028 3824 1122', '88 Tràng Tiền, Phường Bến Thành, Quận 1, TP.HCM', 10.7710, 106.6970, 'https://placehold.co/200x200/png?text=Kem', 'https://placehold.co/800x400/png?text=KemCover', 'OPEN', 'ACTIVE', 15000, 10, 4.6, 340),
(10, 1, 'Cơm Tấm Mộc Quán Võ Văn Tần', 'Cơm tấm sườn bì chả truyền thống Sài Gòn', '028 3930 2211', '35 Võ Văn Tần, Phường 6, Quận 3, TP.HCM', 10.7800, 106.6955, 'https://placehold.co/200x200/png?text=ComTam', 'https://placehold.co/800x400/png?text=ComTamCover', 'OPEN', 'ACTIVE', 35000, 20, 4.5, 410),
(11, 1, 'Dimsum Chen Lý Tự Trọng', 'Dimsum Hồng Kông, hấp và chiên', '028 3914 5566', '201 Lý Tự Trọng, Phường Bến Thành, Quận 1, TP.HCM', 10.7720, 106.6980, 'https://placehold.co/200x200/png?text=Dimsum', 'https://placehold.co/800x400/png?text=DimsumCover', 'OPEN', 'ACTIVE', 40000, 18, 4.3, 260),
(12, 1, 'Bún Bò Huế Đông Ba', 'Bún bò Huế chuẩn vị xứ Huế, cay nồng', '028 3925 3344', '12 Lý Chính Thắng, Phường 8, Quận 3, TP.HCM', 10.7785, 106.6930, 'https://placehold.co/200x200/png?text=BunBo', 'https://placehold.co/800x400/png?text=BunBoCover', 'OPEN', 'ACTIVE', 30000, 22, 4.7, 380);

-- ---------------------------------------------------------------------
-- 3. RESTAURANT_CUISINES (link restaurants to cuisines)
-- ---------------------------------------------------------------------
INSERT INTO restaurant_cuisines (restaurant_id, cuisine_id) VALUES
-- Phở 24 Nguyễn Trãi
(1, 1), -- Món Việt
-- Gà Rán KFC Pasteur
(2, 4), -- Đồ Ăn Nhanh
-- Lẩu Hải Sản Mộc
(3, 6), -- Lẩu
-- Cà Phê Highlands Lê Lợi
(4, 5), -- Đồ Uống
-- Gogi House Nguyễn Huệ
(5, 2), -- Món Hàn
(5, 7), -- Nướng
-- Sushi Hokkaido Đồng Khởi
(6, 3), -- Món Nhật
-- Bánh Mì Huỳnh Hoa
(7, 1), -- Món Việt
(7, 4), -- Đồ Ăn Nhanh
-- Lẩu Thái Tom Yum Hai Bà Trưng
(8, 6), -- Lẩu
-- Kem Bạc Hà Tràng Tiền
(9, 8), -- Tráng Miệng
(9, 5), -- Đồ Uống
-- Cơm Tấm Mộc Quán Võ Văn Tần
(10, 1), -- Món Việt
-- Dimsum Chen Lý Tự Trọng
(11, 3), -- Món Nhật (dimsum có gốc châu Á)
-- Bún Bò Huế Đông Ba
(12, 1); -- Món Việt

-- ---------------------------------------------------------------------
-- 4. MENU_CATEGORIES (3-5 categories per restaurant)
-- ---------------------------------------------------------------------
-- Restaurant 1: Phở 24
INSERT INTO menu_categories (id, restaurant_id, name, sort_order, is_active) VALUES
(1, 1, 'Món chính', 1, 1),
(2, 1, 'Món phụ', 2, 1),
(3, 1, 'Đồ uống', 3, 1);

-- Restaurant 2: KFC
INSERT INTO menu_categories (id, restaurant_id, name, sort_order, is_active) VALUES
(4, 2, 'Gà rán', 1, 1),
(5, 2, 'Burger & Cơm', 2, 1),
(6, 2, 'Món phụ', 3, 1),
(7, 2, 'Đồ uống', 4, 1);

-- Restaurant 3: Lẩu Hải Sản Mộc
INSERT INTO menu_categories (id, restaurant_id, name, sort_order, is_active) VALUES
(8, 3, 'Lẩu hải sản', 1, 1),
(9, 3, 'Hải sản tươi sống', 2, 1),
(10, 3, 'Rau và nấm', 3, 1),
(11, 3, 'Đồ uống', 4, 1);

-- Restaurant 4: Highlands Coffee
INSERT INTO menu_categories (id, restaurant_id, name, sort_order, is_active) VALUES
(12, 4, 'Cà phê', 1, 1),
(13, 4, 'Trà & Freeze', 2, 1),
(14, 4, 'Bánh ngọt', 3, 1);

-- Restaurant 5: Gogi House
INSERT INTO menu_categories (id, restaurant_id, name, sort_order, is_active) VALUES
(15, 5, 'Thịt nướng', 1, 1),
(16, 5, 'Món khai vị', 2, 1),
(17, 5, 'Món chính', 3, 1),
(18, 5, 'Đồ uống', 4, 1);

-- Restaurant 6: Sushi Hokkaido
INSERT INTO menu_categories (id, restaurant_id, name, sort_order, is_active) VALUES
(19, 6, 'Sushi & Sashimi', 1, 1),
(20, 6, 'Món nướng', 2, 1),
(21, 6, 'Món chiên', 3, 1),
(22, 6, 'Đồ uống', 4, 1);

-- Restaurant 7: Bánh Mì Huỳnh Hoa
INSERT INTO menu_categories (id, restaurant_id, name, sort_order, is_active) VALUES
(23, 7, 'Bánh mì', 1, 1),
(24, 7, 'Đồ uống', 2, 1);

-- Restaurant 8: Lẩu Thái Tom Yum
INSERT INTO menu_categories (id, restaurant_id, name, sort_order, is_active) VALUES
(25, 8, 'Lẩu Thái', 1, 1),
(26, 8, 'Món thêm', 2, 1),
(27, 8, 'Đồ uống', 3, 1);

-- Restaurant 9: Kem Bạc Hà
INSERT INTO menu_categories (id, restaurant_id, name, sort_order, is_active) VALUES
(28, 9, 'Kem', 1, 1),
(29, 9, 'Tráng miệng', 2, 1),
(30, 9, 'Đồ uống', 3, 1);

-- Restaurant 10: Cơm Tấm Mộc Quán
INSERT INTO menu_categories (id, restaurant_id, name, sort_order, is_active) VALUES
(31, 10, 'Cơm tấm', 1, 1),
(32, 10, 'Món thêm', 2, 1),
(33, 10, 'Đồ uống', 3, 1);

-- Restaurant 11: Dimsum Chen
INSERT INTO menu_categories (id, restaurant_id, name, sort_order, is_active) VALUES
(34, 11, 'Dimsum hấp', 1, 1),
(35, 11, 'Dimsum chiên', 2, 1),
(36, 11, 'Món chính', 3, 1),
(37, 11, 'Đồ uống', 4, 1);

-- Restaurant 12: Bún Bò Huế Đông Ba
INSERT INTO menu_categories (id, restaurant_id, name, sort_order, is_active) VALUES
(38, 12, 'Món chính', 1, 1),
(39, 12, 'Món thêm', 2, 1),
(40, 12, 'Đồ uống', 3, 1);

-- ---------------------------------------------------------------------
-- 5. MENU_ITEMS (6-10 items per restaurant)
-- ---------------------------------------------------------------------
-- Restaurant 1: Phở 24 (8 items)
INSERT INTO menu_items (category_id, name, description, price, image_url, is_available, sort_order) VALUES
(1, 'Phở bò tái', 'Phở bò với thịt tái, nước dùng đậm đà', 55000, 'https://placehold.co/400x300/png?text=PhoBoTai', 1, 1),
(1, 'Phở bò chín', 'Phở bò với thịt chín mềm', 55000, 'https://placehold.co/400x300/png?text=PhoBoChin', 1, 2),
(1, 'Phở gà', 'Phở gà nước dùng thanh ngọt', 50000, 'https://placehold.co/400x300/png?text=PhoGa', 1, 3),
(1, 'Phở đặc biệt', 'Phở bò đầy đủ các phần thịt', 70000, 'https://placehold.co/400x300/png?text=PhoDacBiet', 1, 4),
(2, 'Gỏi cuốn', 'Gỏi cuốn tôm thịt tươi ngon', 25000, 'https://placehold.co/400x300/png?text=GoiCuon', 1, 1),
(2, 'Chả giò', 'Chả giò chiên giòn', 30000, 'https://placehold.co/400x300/png?text=ChaGio', 1, 2),
(3, 'Trà đá', 'Trà đá miễn phí', 0, 'https://placehold.co/400x300/png?text=TraDa', 1, 1),
(3, 'Nước ngọt', 'Coca Cola / Pepsi', 15000, 'https://placehold.co/400x300/png?text=NuocNgot', 1, 2);

-- Restaurant 2: KFC (10 items)
INSERT INTO menu_items (category_id, name, description, price, image_url, is_available, sort_order) VALUES
(4, 'Gà rán phần 1', '1 miếng gà giòn tan', 35000, 'https://placehold.co/400x300/png?text=GaRan1', 1, 1),
(4, 'Gà rán phần 3', '3 miếng gà giòn tan', 95000, 'https://placehold.co/400x300/png?text=GaRan3', 1, 2),
(4, 'Gà rán phần 6', '6 miếng gà giòn tan', 175000, 'https://placehold.co/400x300/png?text=GaRan6', 1, 3),
(5, 'Burger Zinger', 'Burger gà cay giòn', 55000, 'https://placehold.co/400x300/png?text=BurgerZinger', 1, 1),
(5, 'Cơm gà KFC', 'Cơm gà rán với nước sốt', 45000, 'https://placehold.co/400x300/png?text=ComGa', 1, 2),
(6, 'Khoai tây chiên', 'Khoai tây chiên giòn', 20000, 'https://placehold.co/400x300/png?text=KhoaiTay', 1, 1),
(6, 'Bắp cải trộn', 'Bắp cải trộn mayonnaise', 15000, 'https://placehold.co/400x300/png?text=BapCai', 1, 2),
(7, 'Pepsi vừa', 'Pepsi vừa có đá', 18000, 'https://placehold.co/400x300/png?text=Pepsi', 1, 1),
(7, '7Up vừa', '7Up vừa có đá', 18000, 'https://placehold.co/400x300/png?text=7Up', 1, 2),
(7, 'Trà đào', 'Trà đào ngọt mát', 22000, 'https://placehold.co/400x300/png?text=TraDao', 1, 3);

-- Restaurant 3: Lẩu Hải Sản Mộc (8 items)
INSERT INTO menu_items (category_id, name, description, price, image_url, is_available, sort_order) VALUES
(8, 'Lẩu hải sản 2-3 người', 'Lẩu hải sản tổng hợp cho 2-3 người', 350000, 'https://placehold.co/400x300/png?text=LauHaiSan23', 1, 1),
(8, 'Lẩu hải sản 4-5 người', 'Lẩu hải sản tổng hợp cho 4-5 người', 600000, 'https://placehold.co/400x300/png?text=LauHaiSan45', 1, 2),
(9, 'Tôm sú', 'Tôm sú tươi sống (100g)', 120000, 'https://placehold.co/400x300/png?text=TomSu', 1, 1),
(9, 'Cá lăng', 'Cá lăng phi lê (100g)', 80000, 'https://placehold.co/400x300/png?text=CaLang', 1, 2),
(9, 'Mực ống', 'Mực ống tươi (100g)', 90000, 'https://placehold.co/400x300/png?text=MucOng', 1, 3),
(10, 'Rau tổng hợp', 'Rau các loại cho lẩu', 40000, 'https://placehold.co/400x300/png?text=Rau', 1, 1),
(11, 'Bia Sài Gòn', 'Bia Sài Gòn lon', 25000, 'https://placehold.co/400x300/png?text=Bia', 1, 1),
(11, 'Nước chanh', 'Nước chanh tươi', 20000, 'https://placehold.co/400x300/png?text=Chanh', 1, 2);

-- Restaurant 4: Highlands Coffee (7 items)
INSERT INTO menu_items (category_id, name, description, price, image_url, is_available, sort_order) VALUES
(12, 'Cà phê đen đá', 'Cà phê đen truyền thống', 35000, 'https://placehold.co/400x300/png?text=CaPhe', 1, 1),
(12, 'Cà phê sữa đá', 'Cà phê sữa Việt Nam', 39000, 'https://placehold.co/400x300/png?text=CaPheSua', 1, 2),
(12, 'Bạc xỉu', 'Bạc xỉu Sài Gòn', 39000, 'https://placehold.co/400x300/png?text=BacXiu', 1, 3),
(13, 'Trà đào cam sả', 'Trà đào cam sả mát lạnh', 49000, 'https://placehold.co/400x300/png?text=TraDao', 1, 1),
(13, 'Freeze trà xanh', 'Freeze trà xanh đá xay', 55000, 'https://placehold.co/400x300/png?text=FreezeTraXanh', 1, 2),
(14, 'Bánh croissant', 'Bánh croissant bơ', 35000, 'https://placehold.co/400x300/png?text=Croissant', 1, 1),
(14, 'Bánh mousse socola', 'Bánh mousse socola đắng', 45000, 'https://placehold.co/400x300/png?text=Mousse', 1, 2);

-- Restaurant 5: Gogi House (9 items)
INSERT INTO menu_items (category_id, name, description, price, image_url, is_available, sort_order) VALUES
(15, 'Thịt ba chỉ bò Mỹ', 'Thịt ba chỉ bò Mỹ nướng (200g)', 150000, 'https://placehold.co/400x300/png?text=BaChiBo', 1, 1),
(15, 'Thịt ba rọi heo', 'Thịt ba rọi heo Hàn Quốc (200g)', 120000, 'https://placehold.co/400x300/png?text=BaRoiHeo', 1, 2),
(15, 'Sườn bò nướng', 'Sườn bò Úc nướng (250g)', 180000, 'https://placehold.co/400x300/png?text=SuonBo', 1, 3),
(16, 'Kimchi truyền thống', 'Kim chi cải thảo Hàn Quốc', 35000, 'https://placehold.co/400x300/png?text=Kimchi', 1, 1),
(16, 'Salad rong biển', 'Salad rong biển Wakame', 40000, 'https://placehold.co/400x300/png?text=Salad', 1, 2),
(17, 'Canh kim chi', 'Canh kim chi nóng', 60000, 'https://placehold.co/400x300/png?text=CanhKimchi', 1, 1),
(17, 'Cơm trắng', 'Cơm trắng Hàn Quốc', 15000, 'https://placehold.co/400x300/png?text=Com', 1, 2),
(18, 'Trà gạo rang', 'Trà gạo rang Oksusu', 25000, 'https://placehold.co/400x300/png?text=TraGao', 1, 1),
(18, 'Nước ngọt', 'Coca / Sprite', 18000, 'https://placehold.co/400x300/png?text=NuocNgot', 1, 2);

-- Restaurant 6: Sushi Hokkaido (8 items)
INSERT INTO menu_items (category_id, name, description, price, image_url, is_available, sort_order) VALUES
(19, 'Sushi cá hồi', 'Sushi cá hồi Na Uy tươi (2 miếng)', 65000, 'https://placehold.co/400x300/png?text=SushiSalmon', 1, 1),
(19, 'Sushi cá ngừ', 'Sushi cá ngừ đại dương (2 miếng)', 75000, 'https://placehold.co/400x300/png?text=SushiTuna', 1, 2),
(19, 'Sashimi cá hồi', 'Sashimi cá hồi (5 lát)', 120000, 'https://placehold.co/400x300/png?text=SashimiSalmon', 1, 3),
(20, 'Unagi nướng', 'Lươn Nhật nướng sốt teriyaki', 95000, 'https://placehold.co/400x300/png?text=Unagi', 1, 1),
(20, 'Saba nướng muối', 'Cá saba nướng muối', 85000, 'https://placehold.co/400x300/png?text=Saba', 1, 2),
(21, 'Tempura tôm', 'Tempura tôm (4 con)', 80000, 'https://placehold.co/400x300/png?text=Tempura', 1, 1),
(22, 'Trà xanh Matcha', 'Trà xanh Matcha nóng', 35000, 'https://placehold.co/400x300/png?text=Matcha', 1, 1),
(22, 'Ramune', 'Nước ngọt Nhật Ramune', 30000, 'https://placehold.co/400x300/png?text=Ramune', 1, 2);

-- Restaurant 7: Bánh Mì Huỳnh Hoa (6 items)
INSERT INTO menu_items (category_id, name, description, price, image_url, is_available, sort_order) VALUES
(23, 'Bánh mì thập cẩm', 'Bánh mì thập cẩm đầy ắp nhân', 35000, 'https://placehold.co/400x300/png?text=BanhMiTC', 1, 1),
(23, 'Bánh mì chả lụa', 'Bánh mì chả lụa truyền thống', 25000, 'https://placehold.co/400x300/png?text=BanhMiCL', 1, 2),
(23, 'Bánh mì xíu mại', 'Bánh mì xíu mại sốt cà', 30000, 'https://placehold.co/400x300/png?text=BanhMiXM', 1, 3),
(23, 'Bánh mì pate', 'Bánh mì pate bơ', 22000, 'https://placehold.co/400x300/png?text=BanhMiPate', 1, 4),
(24, 'Sữa đậu nành', 'Sữa đậu nành tự làm', 15000, 'https://placehold.co/400x300/png?text=SuaDN', 1, 1),
(24, 'Nước cam', 'Nước cam vắt tươi', 20000, 'https://placehold.co/400x300/png?text=NuocCam', 1, 2);

-- Restaurant 8: Lẩu Thái Tom Yum (7 items)
INSERT INTO menu_items (category_id, name, description, price, image_url, is_available, sort_order) VALUES
(25, 'Lẩu Tom Yum hải sản 2-3 người', 'Lẩu Tom Yum chua cay hải sản', 320000, 'https://placehold.co/400x300/png?text=TomYum23', 1, 1),
(25, 'Lẩu Tom Yum gà 2-3 người', 'Lẩu Tom Yum gà thơm ngon', 250000, 'https://placehold.co/400x300/png?text=TomYumGa', 1, 2),
(26, 'Tôm sú nướng', 'Tôm sú nướng bơ tỏi (100g)', 110000, 'https://placehold.co/400x300/png?text=TomNuong', 1, 1),
(26, 'Mỳ trứng', 'Mỳ trứng cho lẩu', 20000, 'https://placehold.co/400x300/png?text=MyTrung', 1, 2),
(26, 'Rau tổng hợp', 'Rau tổng hợp cho lẩu', 35000, 'https://placehold.co/400x300/png?text=RauTH', 1, 3),
(27, 'Nước dừa tươi', 'Nước dừa tươi mát lạnh', 25000, 'https://placehold.co/400x300/png?text=DuaTuoi', 1, 1),
(27, 'Bia Tiger', 'Bia Tiger lon', 25000, 'https://placehold.co/400x300/png?text=BiaTiger', 1, 2);

-- Restaurant 9: Kem Bạc Hà (8 items)
INSERT INTO menu_items (category_id, name, description, price, image_url, is_available, sort_order) VALUES
(28, 'Kem dâu', 'Kem dâu tươi 1 viên', 25000, 'https://placehold.co/400x300/png?text=KemDau', 1, 1),
(28, 'Kem socola', 'Kem socola Bỉ 1 viên', 28000, 'https://placehold.co/400x300/png?text=KemSocola', 1, 2),
(28, 'Kem vani', 'Kem vani Madagascar 1 viên', 25000, 'https://placehold.co/400x300/png?text=KemVani', 1, 3),
(28, 'Kem bạc hà', 'Kem bạc hà mát lạnh 1 viên', 28000, 'https://placehold.co/400x300/png?text=KemBacHa', 1, 4),
(29, 'Bánh flan', 'Bánh flan caramen', 20000, 'https://placehold.co/400x300/png?text=Flan', 1, 1),
(29, 'Chè ba màu', 'Chè ba màu truyền thống', 22000, 'https://placehold.co/400x300/png?text=Che3Mau', 1, 2),
(30, 'Nước cam', 'Nước cam vắt tươi', 20000, 'https://placehold.co/400x300/png?text=NuocCam', 1, 1),
(30, 'Soda chanh', 'Soda chanh mát lạnh', 18000, 'https://placehold.co/400x300/png?text=SodaChanh', 1, 2);

-- Restaurant 10: Cơm Tấm Mộc Quán (7 items)
INSERT INTO menu_items (category_id, name, description, price, image_url, is_available, sort_order) VALUES
(31, 'Cơm tấm sườn bì chả', 'Cơm tấm sườn nướng bì chả', 45000, 'https://placehold.co/400x300/png?text=ComTamSBC', 1, 1),
(31, 'Cơm tấm sườn nướng', 'Cơm tấm sườn nướng thơm ngon', 40000, 'https://placehold.co/400x300/png?text=ComTamSuon', 1, 2),
(31, 'Cơm tấm bì', 'Cơm tấm bì chả', 35000, 'https://placehold.co/400x300/png?text=ComTamBi', 1, 3),
(31, 'Cơm tấm gà nướng', 'Cơm tấm gà nướng mật ong', 42000, 'https://placehold.co/400x300/png?text=ComTamGa', 1, 4),
(32, 'Trứng ốp la', 'Trứng gà ốp la', 10000, 'https://placehold.co/400x300/png?text=TrungOp', 1, 1),
(33, 'Trà đá', 'Trá đá miễn phí', 0, 'https://placehold.co/400x300/png?text=TraDa', 1, 1),
(33, 'Nước ngọt', 'Coca Cola / Pepsi', 15000, 'https://placehold.co/400x300/png?text=NuocNgot', 1, 2);

-- Restaurant 11: Dimsum Chen (9 items)
INSERT INTO menu_items (category_id, name, description, price, image_url, is_available, sort_order) VALUES
(34, 'Há cảo tôm', 'Há cảo tôm tươi hấp (4 viên)', 50000, 'https://placehold.co/400x300/png?text=HaCao', 1, 1),
(34, 'Sủi cảo', 'Sủi cảo thịt hấp (4 viên)', 45000, 'https://placehold.co/400x300/png?text=SuiCao', 1, 2),
(34, 'Xíu mại', 'Xíu mại hấp (4 viên)', 40000, 'https://placehold.co/400x300/png?text=XiuMai', 1, 3),
(35, 'Bánh bao chiên', 'Bánh bao chiên nhân thịt', 35000, 'https://placehold.co/400x300/png?text=BanhBaoChien', 1, 1),
(35, 'Há cảo chiên', 'Há cảo chiên giòn (4 viên)', 52000, 'https://placehold.co/400x300/png?text=HaCaoChien', 1, 2),
(36, 'Cháo hải sản', 'Cháo hải sản tổng hợp', 60000, 'https://placehold.co/400x300/png?text=ChaoHS', 1, 1),
(36, 'Mì xào hải sản', 'Mì xào hải sản', 65000, 'https://placehold.co/400x300/png?text=MiXao', 1, 2),
(37, 'Trà Phổ Nhĩ', 'Trà Phổ Nhĩ nóng', 30000, 'https://placehold.co/400x300/png?text=TraPhoNhi', 1, 1),
(37, 'Trà Ô Long', 'Trà Ô Long thơm', 30000, 'https://placehold.co/400x300/png?text=TraOLong', 1, 2);

-- Restaurant 12: Bún Bò Huế Đông Ba (7 items)
INSERT INTO menu_items (category_id, name, description, price, image_url, is_available, sort_order) VALUES
(38, 'Bún bò Huế', 'Bún bò Huế chuẩn vị Huế', 45000, 'https://placehold.co/400x300/png?text=BunBoHue', 1, 1),
(38, 'Bún bò giò heo', 'Bún bò với giò heo mềm', 50000, 'https://placehold.co/400x300/png?text=BunBoGioHeo', 1, 2),
(38, 'Bún bò đặc biệt', 'Bún bò với đầy đủ topping', 65000, 'https://placehold.co/400x300/png?text=BunBoDB', 1, 3),
(39, 'Chả cua', 'Chả cua Huế (2 viên)', 20000, 'https://placehold.co/400x300/png?text=ChaCua', 1, 1),
(39, 'Giò heo thêm', 'Giò heo thêm (100g)', 25000, 'https://placehold.co/400x300/png?text=GioHeo', 1, 2),
(40, 'Trà đá', 'Trà đá miễn phí', 0, 'https://placehold.co/400x300/png?text=TraDa', 1, 1),
(40, 'Nước mía', 'Nước mía tươi', 18000, 'https://placehold.co/400x300/png?text=NuocMia', 1, 2);

-- =====================================================================
-- NOTE: Combos and option_groups/options will be seeded in later weeks
-- when implementing cart and combo functionality (Week 3+).
-- =====================================================================
