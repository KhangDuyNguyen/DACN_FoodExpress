-- =====================================================================
-- V3__seed_restaurant_hours.sql
-- Seed operating hours for all restaurants (Week 1-2 completion)
-- All restaurants open 7 days/week with varied hours
-- =====================================================================

-- Restaurant 1: Phở 24 Nguyễn Trãi (7:00-22:00 every day)
INSERT INTO restaurant_hours (restaurant_id, day_of_week, open_time, close_time) VALUES
(1, 1, '07:00:00', '22:00:00'), -- Monday
(1, 2, '07:00:00', '22:00:00'), -- Tuesday
(1, 3, '07:00:00', '22:00:00'), -- Wednesday
(1, 4, '07:00:00', '22:00:00'), -- Thursday
(1, 5, '07:00:00', '22:00:00'), -- Friday
(1, 6, '07:00:00', '22:00:00'), -- Saturday
(1, 7, '07:00:00', '22:00:00'); -- Sunday

-- Restaurant 2: Gà Rán KFC Pasteur (9:00-23:00 every day)
INSERT INTO restaurant_hours (restaurant_id, day_of_week, open_time, close_time) VALUES
(2, 1, '09:00:00', '23:00:00'),
(2, 2, '09:00:00', '23:00:00'),
(2, 3, '09:00:00', '23:00:00'),
(2, 4, '09:00:00', '23:00:00'),
(2, 5, '09:00:00', '23:00:00'),
(2, 6, '09:00:00', '23:00:00'),
(2, 7, '09:00:00', '23:00:00');

-- Restaurant 3: Lẩu Hải Sản Mộc (10:00-22:30 every day)
INSERT INTO restaurant_hours (restaurant_id, day_of_week, open_time, close_time) VALUES
(3, 1, '10:00:00', '22:30:00'),
(3, 2, '10:00:00', '22:30:00'),
(3, 3, '10:00:00', '22:30:00'),
(3, 4, '10:00:00', '22:30:00'),
(3, 5, '10:00:00', '22:30:00'),
(3, 6, '10:00:00', '22:30:00'),
(3, 7, '10:00:00', '22:30:00');

-- Restaurant 4: Cà Phê Highlands Lê Lợi (6:30-23:30 every day)
INSERT INTO restaurant_hours (restaurant_id, day_of_week, open_time, close_time) VALUES
(4, 1, '06:30:00', '23:30:00'),
(4, 2, '06:30:00', '23:30:00'),
(4, 3, '06:30:00', '23:30:00'),
(4, 4, '06:30:00', '23:30:00'),
(4, 5, '06:30:00', '23:30:00'),
(4, 6, '06:30:00', '23:30:00'),
(4, 7, '06:30:00', '23:30:00');

-- Restaurant 5: Gogi House Nguyễn Huệ (11:00-23:00 every day)
INSERT INTO restaurant_hours (restaurant_id, day_of_week, open_time, close_time) VALUES
(5, 1, '11:00:00', '23:00:00'),
(5, 2, '11:00:00', '23:00:00'),
(5, 3, '11:00:00', '23:00:00'),
(5, 4, '11:00:00', '23:00:00'),
(5, 5, '11:00:00', '23:00:00'),
(5, 6, '11:00:00', '23:00:00'),
(5, 7, '11:00:00', '23:00:00');

-- Restaurant 6: Sushi Hokkaido Đồng Khởi (11:30-22:00, closed Monday)
INSERT INTO restaurant_hours (restaurant_id, day_of_week, open_time, close_time) VALUES
-- (6, 1, closed Monday)
(6, 2, '11:30:00', '22:00:00'),
(6, 3, '11:30:00', '22:00:00'),
(6, 4, '11:30:00', '22:00:00'),
(6, 5, '11:30:00', '22:00:00'),
(6, 6, '11:30:00', '22:00:00'),
(6, 7, '11:30:00', '22:00:00');

-- Restaurant 7: Bánh Mì Huỳnh Hoa (6:00-21:00 every day)
INSERT INTO restaurant_hours (restaurant_id, day_of_week, open_time, close_time) VALUES
(7, 1, '06:00:00', '21:00:00'),
(7, 2, '06:00:00', '21:00:00'),
(7, 3, '06:00:00', '21:00:00'),
(7, 4, '06:00:00', '21:00:00'),
(7, 5, '06:00:00', '21:00:00'),
(7, 6, '06:00:00', '21:00:00'),
(7, 7, '06:00:00', '21:00:00');

-- Restaurant 8: Lẩu Thái Tom Yum Hai Bà Trưng (10:30-22:00 every day)
INSERT INTO restaurant_hours (restaurant_id, day_of_week, open_time, close_time) VALUES
(8, 1, '10:30:00', '22:00:00'),
(8, 2, '10:30:00', '22:00:00'),
(8, 3, '10:30:00', '22:00:00'),
(8, 4, '10:30:00', '22:00:00'),
(8, 5, '10:30:00', '22:00:00'),
(8, 6, '10:30:00', '22:00:00'),
(8, 7, '10:30:00', '22:00:00');

-- Restaurant 9: Kem Bạc Hà Tràng Tiền (12:00-23:00 every day)
INSERT INTO restaurant_hours (restaurant_id, day_of_week, open_time, close_time) VALUES
(9, 1, '12:00:00', '23:00:00'),
(9, 2, '12:00:00', '23:00:00'),
(9, 3, '12:00:00', '23:00:00'),
(9, 4, '12:00:00', '23:00:00'),
(9, 5, '12:00:00', '23:00:00'),
(9, 6, '12:00:00', '23:00:00'),
(9, 7, '12:00:00', '23:00:00');

-- Restaurant 10: Cơm Tấm Mộc Quán Võ Văn Tần (7:00-20:00 every day)
INSERT INTO restaurant_hours (restaurant_id, day_of_week, open_time, close_time) VALUES
(10, 1, '07:00:00', '20:00:00'),
(10, 2, '07:00:00', '20:00:00'),
(10, 3, '07:00:00', '20:00:00'),
(10, 4, '07:00:00', '20:00:00'),
(10, 5, '07:00:00', '20:00:00'),
(10, 6, '07:00:00', '20:00:00'),
(10, 7, '07:00:00', '20:00:00');

-- Restaurant 11: Dimsum Chen Lý Tự Trọng (8:00-22:00 every day)
INSERT INTO restaurant_hours (restaurant_id, day_of_week, open_time, close_time) VALUES
(11, 1, '08:00:00', '22:00:00'),
(11, 2, '08:00:00', '22:00:00'),
(11, 3, '08:00:00', '22:00:00'),
(11, 4, '08:00:00', '22:00:00'),
(11, 5, '08:00:00', '22:00:00'),
(11, 6, '08:00:00', '22:00:00'),
(11, 7, '08:00:00', '22:00:00');

-- Restaurant 12: Bún Bò Huế Đông Ba (6:30-21:00 every day)
INSERT INTO restaurant_hours (restaurant_id, day_of_week, open_time, close_time) VALUES
(12, 1, '06:30:00', '21:00:00'),
(12, 2, '06:30:00', '21:00:00'),
(12, 3, '06:30:00', '21:00:00'),
(12, 4, '06:30:00', '21:00:00'),
(12, 5, '06:30:00', '21:00:00'),
(12, 6, '06:30:00', '21:00:00'),
(12, 7, '06:30:00', '21:00:00');
