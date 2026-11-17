#!/usr/bin/env python3
"""
generate_mock_orders.py
Generate realistic mock orders and order items for the Food Delivery platform.
Outputs: backend/scripts/seed_mock_orders.sql

Dataset features:
- Exactly 3,000 orders distributed over 42 days (2026-10-11 to 2026-11-21).
- Distributed across all 15 active restaurants (12 from V2 + 3 from V20261107_01).
- Realistic meal time distributions: morning, lunch, afternoon, dinner, late night.
- Realistic statuses: ~85% DELIVERED, ~10% CANCELLED, ~5% in-progress/other.
- Exact arithmetic: subtotal = SUM(quantity * unit_price), total_amount = subtotal + delivery_fee - discount_amount.
- Safe and idempotent: cleans previous MOCK-% orders, wrapped in transaction, chunked inserts.
- Customer ID and restaurant ID resolution dynamically via join/lookup.
"""

import os
import random
import datetime

# Seed for reproducible realistic data
random.seed(42)

RESTAURANTS_DATA = [
    {
        "id": 1,
        "name": "Phở 24 Nguyễn Trãi",
        "meal_weights": {"morning": 0.35, "lunch": 0.40, "afternoon": 0.05, "dinner": 0.15, "late_night": 0.05},
        "popularity": 1.1,
        "items": [
            {"id": 1, "name": "Phở bò tái", "price": 55000, "weight": 25},
            {"id": 2, "name": "Phở bò chín", "price": 55000, "weight": 20},
            {"id": 3, "name": "Phở gà", "price": 50000, "weight": 15},
            {"id": 4, "name": "Phở đặc biệt", "price": 70000, "weight": 15},
            {"id": 5, "name": "Gỏi cuốn", "price": 25000, "weight": 10},
            {"id": 6, "name": "Chả giò", "price": 30000, "weight": 10},
            {"id": 7, "name": "Trà đá", "price": 0, "weight": 30},
            {"id": 8, "name": "Nước ngọt", "price": 15000, "weight": 15},
            {"id": 95, "name": "Phở bò viên", "price": 55000, "weight": 15},
            {"id": 96, "name": "Quẩy", "price": 10000, "weight": 25},
        ]
    },
    {
        "id": 2,
        "name": "Gà Rán KFC Pasteur",
        "meal_weights": {"morning": 0.05, "lunch": 0.45, "afternoon": 0.15, "dinner": 0.30, "late_night": 0.05},
        "popularity": 1.25,
        "items": [
            {"id": 9, "name": "Gà rán phần 1", "price": 35000, "weight": 20},
            {"id": 10, "name": "Gà rán phần 3", "price": 95000, "weight": 25},
            {"id": 11, "name": "Gà rán phần 6", "price": 175000, "weight": 15},
            {"id": 12, "name": "Burger Zinger", "price": 55000, "weight": 20},
            {"id": 13, "name": "Cơm gà KFC", "price": 45000, "weight": 20},
            {"id": 14, "name": "Khoai tây chiên", "price": 20000, "weight": 25},
            {"id": 15, "name": "Bắp cải trộn", "price": 15000, "weight": 15},
            {"id": 16, "name": "Pepsi vừa", "price": 18000, "weight": 25},
            {"id": 17, "name": "7Up vừa", "price": 18000, "weight": 15},
            {"id": 18, "name": "Trà đào", "price": 22000, "weight": 15},
        ]
    },
    {
        "id": 3,
        "name": "Lẩu Hải Sản Mộc",
        "meal_weights": {"morning": 0.00, "lunch": 0.20, "afternoon": 0.05, "dinner": 0.70, "late_night": 0.05},
        "popularity": 0.85,
        "items": [
            {"id": 19, "name": "Lẩu hải sản 2-3 người", "price": 350000, "weight": 35},
            {"id": 20, "name": "Lẩu hải sản 4-5 người", "price": 600000, "weight": 15},
            {"id": 21, "name": "Tôm sú", "price": 120000, "weight": 20},
            {"id": 22, "name": "Cá lăng", "price": 80000, "weight": 15},
            {"id": 23, "name": "Mực ống", "price": 90000, "weight": 20},
            {"id": 24, "name": "Rau tổng hợp", "price": 40000, "weight": 25},
            {"id": 25, "name": "Bia Sài Gòn", "price": 25000, "weight": 30},
            {"id": 26, "name": "Nước chanh", "price": 20000, "weight": 15},
            {"id": 97, "name": "Nghêu hấp sả", "price": 70000, "weight": 20},
            {"id": 98, "name": "Nấm tổng hợp", "price": 45000, "weight": 20},
        ]
    },
    {
        "id": 4,
        "name": "Cà Phê Highlands Lê Lợi",
        "meal_weights": {"morning": 0.40, "lunch": 0.15, "afternoon": 0.30, "dinner": 0.10, "late_night": 0.05},
        "popularity": 1.20,
        "items": [
            {"id": 27, "name": "Cà phê đen đá", "price": 35000, "weight": 25},
            {"id": 28, "name": "Cà phê sữa đá", "price": 39000, "weight": 30},
            {"id": 29, "name": "Bạc xỉu", "price": 39000, "weight": 25},
            {"id": 30, "name": "Trà đào cam sả", "price": 49000, "weight": 25},
            {"id": 31, "name": "Freeze trà xanh", "price": 55000, "weight": 20},
            {"id": 32, "name": "Bánh croissant", "price": 35000, "weight": 15},
            {"id": 33, "name": "Bánh mousse socola", "price": 45000, "weight": 10},
            {"id": 99, "name": "Cà phê muối", "price": 45000, "weight": 20},
            {"id": 100, "name": "Trà sen kem sữa", "price": 49000, "weight": 20},
            {"id": 101, "name": "Bánh tiramisu", "price": 45000, "weight": 10},
        ]
    },
    {
        "id": 5,
        "name": "Gogi House Nguyễn Huệ",
        "meal_weights": {"morning": 0.00, "lunch": 0.25, "afternoon": 0.05, "dinner": 0.65, "late_night": 0.05},
        "popularity": 0.90,
        "items": [
            {"id": 34, "name": "Thịt ba chỉ bò Mỹ", "price": 150000, "weight": 35},
            {"id": 35, "name": "Thịt ba rọi heo", "price": 120000, "weight": 25},
            {"id": 36, "name": "Sườn bò nướng", "price": 180000, "weight": 25},
            {"id": 37, "name": "Kimchi truyền thống", "price": 35000, "weight": 30},
            {"id": 38, "name": "Salad rong biển", "price": 40000, "weight": 20},
            {"id": 39, "name": "Canh kim chi", "price": 60000, "weight": 25},
            {"id": 40, "name": "Cơm trắng", "price": 15000, "weight": 25},
            {"id": 41, "name": "Trà gạo rang", "price": 25000, "weight": 20},
            {"id": 42, "name": "Nước ngọt", "price": 18000, "weight": 20},
            {"id": 102, "name": "Mì lạnh Hàn Quốc", "price": 75000, "weight": 15},
        ]
    },
    {
        "id": 6,
        "name": "Sushi Hokkaido Đồng Khởi",
        "meal_weights": {"morning": 0.00, "lunch": 0.30, "afternoon": 0.05, "dinner": 0.60, "late_night": 0.05},
        "popularity": 0.85,
        "items": [
            {"id": 43, "name": "Sushi cá hồi", "price": 65000, "weight": 30},
            {"id": 44, "name": "Sushi cá ngừ", "price": 75000, "weight": 25},
            {"id": 45, "name": "Sashimi cá hồi", "price": 120000, "weight": 25},
            {"id": 46, "name": "Unagi nướng", "price": 95000, "weight": 20},
            {"id": 47, "name": "Saba nướng muối", "price": 85000, "weight": 15},
            {"id": 48, "name": "Tempura tôm", "price": 80000, "weight": 20},
            {"id": 49, "name": "Trà xanh Matcha", "price": 35000, "weight": 25},
            {"id": 50, "name": "Ramune", "price": 30000, "weight": 15},
            {"id": 103, "name": "Sushi lươn", "price": 85000, "weight": 15},
            {"id": 104, "name": "Gà karaage", "price": 70000, "weight": 20},
        ]
    },
    {
        "id": 7,
        "name": "Bánh Mì Huỳnh Hoa",
        "meal_weights": {"morning": 0.40, "lunch": 0.30, "afternoon": 0.15, "dinner": 0.10, "late_night": 0.05},
        "popularity": 1.15,
        "items": [
            {"id": 51, "name": "Bánh mì thập cẩm", "price": 35000, "weight": 45},
            {"id": 52, "name": "Bánh mì chả lụa", "price": 25000, "weight": 20},
            {"id": 53, "name": "Bánh mì xíu mại", "price": 30000, "weight": 20},
            {"id": 54, "name": "Bánh mì pate", "price": 22000, "weight": 15},
            {"id": 55, "name": "Sữa đậu nành", "price": 15000, "weight": 30},
            {"id": 56, "name": "Nước cam", "price": 20000, "weight": 20},
        ]
    },
    {
        "id": 8,
        "name": "Lẩu Thái Tom Yum Hai Bà Trưng",
        "meal_weights": {"morning": 0.00, "lunch": 0.25, "afternoon": 0.05, "dinner": 0.65, "late_night": 0.05},
        "popularity": 0.85,
        "items": [
            {"id": 57, "name": "Lẩu Tom Yum hải sản 2-3 người", "price": 320000, "weight": 35},
            {"id": 58, "name": "Lẩu Tom Yum gà 2-3 người", "price": 250000, "weight": 25},
            {"id": 59, "name": "Tôm sú nướng", "price": 110000, "weight": 20},
            {"id": 60, "name": "Mỳ trứng", "price": 20000, "weight": 30},
            {"id": 61, "name": "Rau tổng hợp", "price": 35000, "weight": 25},
            {"id": 62, "name": "Nước dừa tươi", "price": 25000, "weight": 20},
            {"id": 63, "name": "Bia Tiger", "price": 25000, "weight": 25},
        ]
    },
    {
        "id": 9,
        "name": "Kem Bạc Hà Tràng Tiền",
        "meal_weights": {"morning": 0.05, "lunch": 0.15, "afternoon": 0.40, "dinner": 0.25, "late_night": 0.15},
        "popularity": 0.95,
        "items": [
            {"id": 64, "name": "Kem dâu", "price": 25000, "weight": 25},
            {"id": 65, "name": "Kem socola", "price": 28000, "weight": 30},
            {"id": 66, "name": "Kem vani", "price": 25000, "weight": 20},
            {"id": 67, "name": "Kem bạc hà", "price": 28000, "weight": 25},
            {"id": 68, "name": "Bánh flan", "price": 20000, "weight": 20},
            {"id": 69, "name": "Chè ba màu", "price": 22000, "weight": 20},
            {"id": 70, "name": "Nước cam", "price": 20000, "weight": 15},
            {"id": 71, "name": "Soda chanh", "price": 18000, "weight": 20},
        ]
    },
    {
        "id": 10,
        "name": "Cơm Tấm Mộc Quán Võ Văn Tần",
        "meal_weights": {"morning": 0.25, "lunch": 0.45, "afternoon": 0.05, "dinner": 0.20, "late_night": 0.05},
        "popularity": 1.10,
        "items": [
            {"id": 72, "name": "Cơm tấm sườn bì chả", "price": 45000, "weight": 40},
            {"id": 73, "name": "Cơm tấm sườn nướng", "price": 40000, "weight": 30},
            {"id": 74, "name": "Cơm tấm bì", "price": 35000, "weight": 15},
            {"id": 75, "name": "Cơm tấm gà nướng", "price": 42000, "weight": 20},
            {"id": 76, "name": "Trứng ốp la", "price": 10000, "weight": 35},
            {"id": 77, "name": "Trà đá", "price": 0, "weight": 30},
            {"id": 78, "name": "Nước ngọt", "price": 15000, "weight": 20},
        ]
    },
    {
        "id": 11,
        "name": "Dimsum Chen Lý Tự Trọng",
        "meal_weights": {"morning": 0.25, "lunch": 0.35, "afternoon": 0.10, "dinner": 0.25, "late_night": 0.05},
        "popularity": 0.90,
        "items": [
            {"id": 79, "name": "Há cảo tôm", "price": 50000, "weight": 35},
            {"id": 80, "name": "Sủi cảo", "price": 45000, "weight": 25},
            {"id": 81, "name": "Xíu mại", "price": 40000, "weight": 30},
            {"id": 82, "name": "Bánh bao chiên", "price": 35000, "weight": 20},
            {"id": 83, "name": "Há cảo chiên", "price": 52000, "weight": 20},
            {"id": 84, "name": "Cháo hải sản", "price": 60000, "weight": 15},
            {"id": 85, "name": "Mì xào hải sản", "price": 65000, "weight": 20},
            {"id": 86, "name": "Trà Phổ Nhĩ", "price": 30000, "weight": 20},
            {"id": 87, "name": "Trà Ô Long", "price": 30000, "weight": 20},
        ]
    },
    {
        "id": 12,
        "name": "Bún Bò Huế Đông Ba",
        "meal_weights": {"morning": 0.35, "lunch": 0.40, "afternoon": 0.05, "dinner": 0.15, "late_night": 0.05},
        "popularity": 1.05,
        "items": [
            {"id": 88, "name": "Bún bò Huế", "price": 45000, "weight": 35},
            {"id": 89, "name": "Bún bò giò heo", "price": 50000, "weight": 25},
            {"id": 90, "name": "Bún bò đặc biệt", "price": 65000, "weight": 25},
            {"id": 91, "name": "Chả cua", "price": 20000, "weight": 25},
            {"id": 92, "name": "Giò heo thêm", "price": 25000, "weight": 20},
            {"id": 93, "name": "Trà đá", "price": 0, "weight": 30},
            {"id": 94, "name": "Nước mía", "price": 18000, "weight": 25},
        ]
    },
    {
        "id": 13,
        "name": "Cơm Nhà Demo - Chi nhánh Quận 1",
        "meal_weights": {"morning": 0.10, "lunch": 0.65, "afternoon": 0.05, "dinner": 0.20, "late_night": 0.00},
        "popularity": 1.20,
        "items": [
            {"id": 105, "name": "Cơm sườn nướng", "price": 45000, "weight": 35},
            {"id": 107, "name": "Cơm bò lúc lắc", "price": 65000, "weight": 25},
            {"id": 108, "name": "Cơm cá kho tộ", "price": 45000, "weight": 25},
            {"id": 109, "name": "Cơm chay thập cẩm", "price": 40000, "weight": 15},
            {"id": 110, "name": "Trứng ốp la", "price": 8000, "weight": 30},
            {"id": 111, "name": "Canh chua cá lóc", "price": 20000, "weight": 25},
            {"id": 112, "name": "Chả trứng hấp", "price": 12000, "weight": 20},
            {"id": 113, "name": "Trà đá", "price": 5000, "weight": 30},
            {"id": 114, "name": "Nước sâm bí đao", "price": 15000, "weight": 25},
            {"id": 115, "name": "Trà tắc", "price": 15000, "weight": 25},
        ]
    },
    {
        "id": 14,
        "name": "Cơm Nhà Demo - Chi nhánh Quận 3",
        "meal_weights": {"morning": 0.10, "lunch": 0.65, "afternoon": 0.05, "dinner": 0.20, "late_night": 0.00},
        "popularity": 1.15,
        "items": [
            {"id": 116, "name": "Cơm sườn nướng", "price": 45000, "weight": 35},
            {"id": 117, "name": "Cơm gà xối mỡ", "price": 50000, "weight": 25},
            {"id": 118, "name": "Cơm bò lúc lắc", "price": 69000, "weight": 25},
            {"id": 119, "name": "Cơm cá kho tộ", "price": 45000, "weight": 20},
            {"id": 120, "name": "Cơm chay thập cẩm", "price": 40000, "weight": 15},
            {"id": 121, "name": "Trứng ốp la", "price": 8000, "weight": 30},
            {"id": 122, "name": "Canh chua cá lóc", "price": 20000, "weight": 25},
            {"id": 123, "name": "Chả trứng hấp", "price": 12000, "weight": 20},
            {"id": 124, "name": "Trà đá", "price": 5000, "weight": 30},
            {"id": 125, "name": "Nước sâm bí đao", "price": 15000, "weight": 25},
            {"id": 126, "name": "Trà tắc", "price": 15000, "weight": 25},
        ]
    },
    {
        "id": 15,
        "name": "Bún Chả Demo",
        "meal_weights": {"morning": 0.15, "lunch": 0.55, "afternoon": 0.05, "dinner": 0.25, "late_night": 0.00},
        "popularity": 1.10,
        "items": [
            {"id": 127, "name": "Bún chả Hà Nội", "price": 50000, "weight": 40},
            {"id": 128, "name": "Bún chả đặc biệt", "price": 65000, "weight": 30},
            {"id": 129, "name": "Bún nem", "price": 45000, "weight": 20},
            {"id": 130, "name": "Bún thịt nướng", "price": 45000, "weight": 20},
            {"id": 131, "name": "Nem rán", "price": 20000, "weight": 30},
            {"id": 132, "name": "Chả viên thêm", "price": 20000, "weight": 25},
            {"id": 133, "name": "Rau sống thêm", "price": 5000, "weight": 15},
            {"id": 134, "name": "Trà đá", "price": 5000, "weight": 30},
            {"id": 135, "name": "Nước mía", "price": 15000, "weight": 25},
            {"id": 136, "name": "Sữa đậu nành", "price": 12000, "weight": 20},
        ]
    }
]

CUSTOMERS = [
    {"name": "Khách Demo Một", "phone": "0911000001", "email": "khach1@demo.test"},
    {"name": "Khách Demo Hai", "phone": "0911000002", "email": "khach2@demo.test"},
    {"name": "Nguyễn Văn An", "phone": "0981000001", "email": "mock.an@demo.test"},
    {"name": "Trần Thị Bình", "phone": "0981000002", "email": "mock.binh@demo.test"},
    {"name": "Lê Hoàng Cường", "phone": "0981000003", "email": "mock.cuong@demo.test"},
    {"name": "Phạm Minh Dũng", "phone": "0981000004", "email": "mock.dung@demo.test"},
    {"name": "Hoàng Thuỳ Linh", "phone": "0981000005", "email": "mock.linh@demo.test"},
    {"name": "Vũ Quốc Anh", "phone": "0981000006", "email": "mock.quocanh@demo.test"},
    {"name": "Đặng Mỹ Hạnh", "phone": "0981000007", "email": "mock.hanh@demo.test"},
    {"name": "Bùi Tấn Phát", "phone": "0981000008", "email": "mock.phat@demo.test"},
    {"name": "Đỗ Hải Yến", "phone": "0981000009", "email": "mock.yen@demo.test"},
    {"name": "Ngô Bảo Nam", "phone": "0981000010", "email": "mock.nam@demo.test"},
]

ADDRESSES = [
    {"address": "10 Lê Lợi, Phường Bến Nghé, Quận 1, TP.HCM", "lat": 10.7756, "lng": 106.7004},
    {"address": "200 Nguyễn Thị Minh Khai, Phường 6, Quận 3, TP.HCM", "lat": 10.7795, "lng": 106.6905},
    {"address": "15 Phan Xích Long, Phường 2, Quận Phú Nhuận, TP.HCM", "lat": 10.7990, "lng": 106.6870},
    {"address": "88 Nguyễn Đình Chiểu, Phường 6, Quận 3, TP.HCM", "lat": 10.7802, "lng": 106.6930},
    {"address": "45 Lê Duẩn, Phường Bến Nghé, Quận 1, TP.HCM", "lat": 10.7798, "lng": 106.7001},
    {"address": "123 Nam Kỳ Khởi Nghĩa, Phường 7, Quận 3, TP.HCM", "lat": 10.7850, "lng": 106.6890},
    {"address": "250 Điện Biên Phủ, Phường 15, Quận Bình Thạnh, TP.HCM", "lat": 10.8010, "lng": 106.7110},
    {"address": "78 Hai Bà Trưng, Phường Bến Nghé, Quận 1, TP.HCM", "lat": 10.7770, "lng": 106.7020},
    {"address": "32 Cao Thắng, Phường 5, Quận 3, TP.HCM", "lat": 10.7715, "lng": 106.6830},
    {"address": "105 Trần Hưng Đạo, Phường Cầu Ông Lãnh, Quận 1, TP.HCM", "lat": 10.7640, "lng": 106.6920},
    {"address": "18 Võ Văn Tần, Phường 6, Quận 3, TP.HCM", "lat": 10.7788, "lng": 106.6945},
    {"address": "55 Pasteur, Phường Bến Nghé, Quận 1, TP.HCM", "lat": 10.7765, "lng": 106.7015},
    {"address": "92 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP.HCM", "lat": 10.7738, "lng": 106.7032},
    {"address": "64 Trương Định, Phường 9, Quận 3, TP.HCM", "lat": 10.7750, "lng": 106.6885},
    {"address": "142 Đinh Tiên Hoàng, Phường Đa Kao, Quận 1, TP.HCM", "lat": 10.7910, "lng": 106.6960},
]

CANCEL_REASONS = [
    "Khách hàng đổi ý không muốn đặt nữa",
    "Quán thông báo hết món trong thực đơn",
    "Thời gian giao hàng dự kiến quá lâu",
    "Không thể liên lạc được với người nhận",
    "Khách hàng nhập nhầm địa chỉ giao",
    "Khách hàng muốn đặt lại đơn khác",
]

NOTES = [
    None,
    None,
    None,
    None,
    "Giao tận cửa giúp em",
    "Không cay",
    "Nhiều ớt",
    "Để trước cửa nhà",
    "Gọi trước khi tới 5 phút",
    "Cho nhiều quẩy / rau sống",
    "Ít đá ít ngọt",
    "Giao nhanh giúp mình đang vội",
]

def random_time_in_slot(slot: str, date_obj: datetime.date) -> datetime.datetime:
    """Generate a random datetime within the specified slot on the given date."""
    if slot == "morning":
        # 07:00 - 09:30 (150 mins)
        minute_offset = random.randint(0, 150)
        dt = datetime.datetime.combine(date_obj, datetime.time(7, 0, 0)) + datetime.timedelta(minutes=minute_offset, seconds=random.randint(0, 59))
    elif slot == "lunch":
        # 11:00 - 13:30 (150 mins)
        minute_offset = random.randint(0, 150)
        dt = datetime.datetime.combine(date_obj, datetime.time(11, 0, 0)) + datetime.timedelta(minutes=minute_offset, seconds=random.randint(0, 59))
    elif slot == "afternoon":
        # 14:30 - 17:00 (150 mins)
        minute_offset = random.randint(0, 150)
        dt = datetime.datetime.combine(date_obj, datetime.time(14, 30, 0)) + datetime.timedelta(minutes=minute_offset, seconds=random.randint(0, 59))
    elif slot == "dinner":
        # 18:00 - 21:30 (210 mins)
        minute_offset = random.randint(0, 210)
        dt = datetime.datetime.combine(date_obj, datetime.time(18, 0, 0)) + datetime.timedelta(minutes=minute_offset, seconds=random.randint(0, 59))
    else:  # late_night
        # 21:30 - 23:30 (120 mins)
        minute_offset = random.randint(0, 120)
        dt = datetime.datetime.combine(date_obj, datetime.time(21, 30, 0)) + datetime.timedelta(minutes=minute_offset, seconds=random.randint(0, 59))
    return dt


def generate_mock_orders(total_orders: int = 3000):
    start_date = datetime.date(2026, 10, 11)
    end_date = datetime.date(2026, 11, 21)
    num_days = (end_date - start_date).days + 1  # 42 days

    # 1. Distribute orders per day
    # Weekend multiplier (Sat, Sun = 1.35x, Fri = 1.15x, Mon, Tue = 0.9x)
    # Slight upward trend over time (e.g. from 0.85x at start to 1.15x at end)
    day_weights = []
    current_d = start_date
    for i in range(num_days):
        weekday = current_d.weekday() # 0 = Mon, 6 = Sun
        w = 1.0
        if weekday in (5, 6):
            w *= 1.35
        elif weekday == 4:
            w *= 1.15
        elif weekday in (0, 1):
            w *= 0.90
        # Time trend: 0.85 -> 1.15
        trend = 0.85 + 0.30 * (i / (num_days - 1))
        day_weights.append(w * trend)
        current_d += datetime.timedelta(days=1)

    sum_w = sum(day_weights)
    orders_per_day = [int(round(w / sum_w * total_orders)) for w in day_weights]
    diff = total_orders - sum(orders_per_day)
    # Adjust difference on recent days
    for k in range(abs(diff)):
        idx = num_days - 1 - (k % num_days)
        orders_per_day[idx] += 1 if diff > 0 else -1

    assert sum(orders_per_day) == total_orders

    # 2. Assign statuses
    # Over the entire dataset: ~85% DELIVERED, ~10% CANCELLED, ~5% other
    # On the final day (2026-11-21), we put recent in-flight orders:
    # PLACED, ACCEPTED, PREPARING, READY_FOR_PICKUP, DELIVERING, REJECTED
    orders = []
    order_items = []
    order_counter = 1

    # Meal slot overall weights
    slots = ["morning", "lunch", "afternoon", "dinner", "late_night"]
    slot_weights = [0.12, 0.36, 0.08, 0.38, 0.06]

    curr_date = start_date
    for day_idx in range(num_days):
        count_for_day = orders_per_day[day_idx]
        is_today = (curr_date == end_date)

        for _ in range(count_for_day):
            order_code = f"MOCK-ORD-{order_counter:06d}"

            # Pick meal slot
            slot = random.choices(slots, weights=slot_weights, k=1)[0]
            created_at = random_time_in_slot(slot, curr_date)

            # Pick restaurant based on meal slot compatibility and restaurant popularity
            rest_choices = []
            rest_weights = []
            for r in RESTAURANTS_DATA:
                slot_w = r["meal_weights"].get(slot, 0.01)
                pop_w = r["popularity"]
                rest_choices.append(r)
                rest_weights.append(slot_w * pop_w)

            restaurant = random.choices(rest_choices, weights=rest_weights, k=1)[0]

            # Pick customer
            customer = random.choice(CUSTOMERS)
            address_info = random.choice(ADDRESSES)

            # Determine status
            if is_today:
                # Distribution on current day: some delivered, some cancelled, some in progress
                # 65% DELIVERED, 10% CANCELLED, 25% active/in-progress
                status_roll = random.random()
                if status_roll < 0.65:
                    status = "DELIVERED"
                elif status_roll < 0.75:
                    status = "CANCELLED"
                elif status_roll < 0.80:
                    status = "PLACED"
                elif status_roll < 0.85:
                    status = "ACCEPTED"
                elif status_roll < 0.90:
                    status = "PREPARING"
                elif status_roll < 0.94:
                    status = "READY_FOR_PICKUP"
                elif status_roll < 0.98:
                    status = "DELIVERING"
                else:
                    status = "REJECTED"
            else:
                # Past days: 88.5% DELIVERED, 10.5% CANCELLED, 1.0% REJECTED
                status_roll = random.random()
                if status_roll < 0.885:
                    status = "DELIVERED"
                elif status_roll < 0.990:
                    status = "CANCELLED"
                else:
                    status = "REJECTED"

            # Set timing fields and cancel reason
            accepted_at = None
            delivered_at = None
            cancelled_at = None
            cancel_reason = None

            if status == "DELIVERED":
                accepted_at = created_at + datetime.timedelta(minutes=random.randint(2, 5))
                delivered_at = accepted_at + datetime.timedelta(minutes=random.randint(18, 40))
            elif status == "CANCELLED":
                if random.random() < 0.4:
                    accepted_at = created_at + datetime.timedelta(minutes=random.randint(1, 3))
                cancelled_at = created_at + datetime.timedelta(minutes=random.randint(3, 15))
                cancel_reason = random.choice(CANCEL_REASONS)
            elif status == "REJECTED":
                cancelled_at = created_at + datetime.timedelta(minutes=random.randint(2, 6))
                cancel_reason = "Quán đang quá tải đơn, không kịp phục vụ"
            elif status in ("ACCEPTED", "PREPARING", "READY_FOR_PICKUP", "DELIVERING"):
                accepted_at = created_at + datetime.timedelta(minutes=random.randint(2, 4))

            # Pick items from this restaurant (1 to 4 items)
            # 1 item: 40%, 2 items: 40%, 3 items: 15%, 4 items: 5%
            num_items = random.choices([1, 2, 3, 4], weights=[40, 40, 15, 5], k=1)[0]
            rest_items = restaurant["items"]
            item_weights = [it["weight"] for it in rest_items]

            # Choose unique items for this order
            k_items = min(num_items, len(rest_items))
            chosen_items = []
            available_indices = list(range(len(rest_items)))
            chosen_indices = set()
            for _ in range(k_items):
                remaining_indices = [idx for idx in available_indices if idx not in chosen_indices]
                if not remaining_indices:
                    break
                rem_weights = [item_weights[idx] for idx in remaining_indices]
                sel = random.choices(remaining_indices, weights=rem_weights, k=1)[0]
                chosen_indices.add(sel)
                chosen_items.append(rest_items[sel])

            # Ensure at least one item has price > 0
            if all(it["price"] == 0 for it in chosen_items):
                paid_items = [it for it in rest_items if it["price"] > 0]
                if paid_items:
                    chosen_items[0] = random.choice(paid_items)

            # Generate item quantities and compute subtotal
            order_subtotal = 0
            order_items_records = []
            for it in chosen_items:
                # Quantity 1 to 4: 1 (68%), 2 (24%), 3 (6%), 4 (2%)
                qty = random.choices([1, 2, 3, 4], weights=[68, 24, 6, 2], k=1)[0]
                item_price = it["price"]
                order_subtotal += qty * item_price
                note = random.choice([None, None, None, None, "Ít cay", "Không hành", "Cho thêm muỗng đũa"])
                order_items_records.append({
                    "order_code": order_code,
                    "menu_item_id": it["id"],
                    "item_name": it["name"],
                    "unit_price": item_price,
                    "quantity": qty,
                    "note": note
                })

            # Calculate delivery fee & distance
            # Distance 1.0 to 6.5 km
            distance_km = round(random.uniform(1.0, 6.5), 1)
            # Base fee: 15k for <= 2.0 km, +5k per additional km
            if distance_km <= 2.0:
                delivery_fee = 15000
            else:
                extra_km = int((distance_km - 2.0) + 0.999) # ceiling
                delivery_fee = 15000 + extra_km * 5000

            # Calculate discount
            # 80% no discount, 12% freeship (15k), 5% 20k discount, 3% 10% discount
            disc_roll = random.random()
            if disc_roll < 0.80:
                discount_amount = 0
            elif disc_roll < 0.92:
                discount_amount = min(delivery_fee, order_subtotal)
            elif disc_roll < 0.97:
                discount_amount = min(20000, order_subtotal)
            else:
                raw_disc = int(round(order_subtotal * 0.10, -3)) # round to thousand
                discount_amount = min(50000, raw_disc, order_subtotal)

            total_amount = order_subtotal + delivery_fee - discount_amount
            assert total_amount >= 0, f"Total amount cannot be negative: {total_amount}"
            assert order_subtotal == sum(rec["unit_price"] * rec["quantity"] for rec in order_items_records)

            # Payment method: COD (60%), VNPAY (25%), MOMO (15%)
            payment_method = random.choices(["COD", "VNPAY", "MOMO"], weights=[60, 25, 15], k=1)[0]
            estimated_minutes = random.randint(25, 50)
            order_note = random.choice(NOTES)

            # Order record
            orders.append({
                "code": order_code,
                "customer_phone": customer["phone"],
                "restaurant_name": restaurant["name"],
                "restaurant_id": restaurant["id"],
                "status": status,
                "receiver_name": customer["name"],
                "receiver_phone": customer["phone"],
                "delivery_address": address_info["address"],
                "delivery_lat": address_info["lat"],
                "delivery_lng": address_info["lng"],
                "note": order_note,
                "subtotal": order_subtotal,
                "delivery_fee": delivery_fee,
                "discount_amount": discount_amount,
                "total_amount": total_amount,
                "payment_method": payment_method,
                "distance_km": distance_km,
                "estimated_minutes": estimated_minutes,
                "cancel_reason": cancel_reason,
                "created_at": created_at,
                "accepted_at": accepted_at,
                "delivered_at": delivered_at,
                "cancelled_at": cancelled_at
            })

            order_items.extend(order_items_records)
            order_counter += 1

        curr_date += datetime.timedelta(days=1)

    return orders, order_items


def format_sql_value(val):
    if val is None:
        return "NULL"
    elif isinstance(val, (int, float)):
        return str(val)
    elif isinstance(val, datetime.datetime):
        return f"'{val.strftime('%Y-%m-%d %H:%M:%S')}'"
    elif isinstance(val, str):
        escaped = val.replace("'", "''")
        return f"'{escaped}'"
    return str(val)


def build_sql_script(orders, order_items, output_path: str):
    """Write the complete seed_mock_orders.sql file."""
    lines = []
    lines.append("-- =====================================================================")
    lines.append("-- seed_mock_orders.sql")
    lines.append("-- Realistic mock order dataset for Food Delivery platform")
    lines.append(f"-- Total Orders: {len(orders)}, Total Order Items: {len(order_items)}")
    lines.append("-- Date range: 2026-10-11 to 2026-11-21 (42 days)")
    lines.append("-- Safe & Idempotent: deletes previous code LIKE 'MOCK-%' orders.")
    lines.append("-- =====================================================================")
    lines.append("")
    lines.append("START TRANSACTION;")
    lines.append("")
    lines.append("-- ---------------------------------------------------------------------")
    lines.append("-- 1. Dọn dẹp đơn mock cũ (idempotent: xóa đơn MOCK cũ mà không chạm đơn thật)")
    lines.append("--    order_items tự động bị xóa theo nhờ ràng buộc ON DELETE CASCADE")
    lines.append("-- ---------------------------------------------------------------------")
    lines.append("DELETE FROM orders WHERE code LIKE 'MOCK-%';")
    lines.append("")

    lines.append("-- ---------------------------------------------------------------------")
    lines.append("-- 2. Đảm bảo các tài khoản khách hàng mock tồn tại (vai trò CUSTOMER)")
    lines.append("-- ---------------------------------------------------------------------")
    lines.append("INSERT INTO users (full_name, email, phone, password_hash, role, status) VALUES")
    cust_vals = []
    for c in CUSTOMERS:
        name_esc = c['name'].replace("'", "''")
        email_esc = c['email'].replace("'", "''")
        phone_esc = c['phone']
        # Password hash for 'Demo@12345'
        cust_vals.append(f"('{name_esc}', '{email_esc}', '{phone_esc}', '$2a$10$fzWKyu8V19NEeord90ek9ueuFKnb57BFpPozpUUwYC/mgkSE0CHWu', 'CUSTOMER', 'ACTIVE')")
    lines.append(",\n".join(cust_vals))
    lines.append("ON DUPLICATE KEY UPDATE status = 'ACTIVE';")
    lines.append("")

    lines.append("-- ---------------------------------------------------------------------")
    lines.append("-- 3. Tạo bảng tạm nạp orders (tránh xung đột auto_increment ID)")
    lines.append("-- ---------------------------------------------------------------------")
    lines.append("DROP TEMPORARY TABLE IF EXISTS tmp_mock_orders;")
    lines.append("""CREATE TEMPORARY TABLE tmp_mock_orders (
    code VARCHAR(20) NOT NULL PRIMARY KEY,
    customer_phone VARCHAR(20) NOT NULL,
    restaurant_name VARCHAR(150) NOT NULL,
    status ENUM('PENDING_PAYMENT','PLACED','ACCEPTED','PREPARING','READY_FOR_PICKUP',
                'PICKED_UP','DELIVERING','DELIVERED','CANCELLED','REJECTED') NOT NULL,
    receiver_name VARCHAR(100) NOT NULL,
    receiver_phone VARCHAR(20) NOT NULL,
    delivery_address VARCHAR(255) NOT NULL,
    delivery_lat DECIMAL(10,7) NOT NULL,
    delivery_lng DECIMAL(11,7) NOT NULL,
    note VARCHAR(255) NULL,
    subtotal DECIMAL(12,0) NOT NULL,
    delivery_fee DECIMAL(12,0) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(12,0) NOT NULL DEFAULT 0,
    total_amount DECIMAL(12,0) NOT NULL,
    payment_method ENUM('COD','VNPAY','MOMO') NOT NULL DEFAULT 'COD',
    distance_km DECIMAL(6,2) NULL,
    estimated_minutes SMALLINT UNSIGNED NULL,
    cancel_reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL,
    accepted_at DATETIME NULL,
    delivered_at DATETIME NULL,
    cancelled_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;""")
    lines.append("")

    lines.append("-- Nạp dữ liệu vào tmp_mock_orders (chia chunk 500 dòng)")
    chunk_size = 500
    for chunk_start in range(0, len(orders), chunk_size):
        chunk = orders[chunk_start:chunk_start + chunk_size]
        lines.append("INSERT INTO tmp_mock_orders (code, customer_phone, restaurant_name, status, receiver_name, receiver_phone,")
        lines.append("                            delivery_address, delivery_lat, delivery_lng, note, subtotal, delivery_fee,")
        lines.append("                            discount_amount, total_amount, payment_method, distance_km, estimated_minutes,")
        lines.append("                            cancel_reason, created_at, accepted_at, delivered_at, cancelled_at) VALUES")
        val_rows = []
        for o in chunk:
            row_str = (
                f"({format_sql_value(o['code'])}, {format_sql_value(o['customer_phone'])}, "
                f"{format_sql_value(o['restaurant_name'])}, {format_sql_value(o['status'])}, "
                f"{format_sql_value(o['receiver_name'])}, {format_sql_value(o['receiver_phone'])}, "
                f"{format_sql_value(o['delivery_address'])}, {format_sql_value(o['delivery_lat'])}, {format_sql_value(o['delivery_lng'])}, "
                f"{format_sql_value(o['note'])}, {format_sql_value(o['subtotal'])}, {format_sql_value(o['delivery_fee'])}, "
                f"{format_sql_value(o['discount_amount'])}, {format_sql_value(o['total_amount'])}, {format_sql_value(o['payment_method'])}, "
                f"{format_sql_value(o['distance_km'])}, {format_sql_value(o['estimated_minutes'])}, {format_sql_value(o['cancel_reason'])}, "
                f"{format_sql_value(o['created_at'])}, {format_sql_value(o['accepted_at'])}, "
                f"{format_sql_value(o['delivered_at'])}, {format_sql_value(o['cancelled_at'])})"
            )
            val_rows.append(row_str)
        lines.append(",\n".join(val_rows) + ";\n")

    lines.append("-- ---------------------------------------------------------------------")
    lines.append("-- 4. Chuyển orders từ bảng tạm vào bảng orders thật")
    lines.append("--    Ghép nối linh hoạt customer_id qua phone và restaurant_id qua name")
    lines.append("-- ---------------------------------------------------------------------")
    lines.append("""INSERT INTO orders (code, customer_id, restaurant_id, driver_id, status,
                    receiver_name, receiver_phone, delivery_address, delivery_lat, delivery_lng,
                    note, subtotal, delivery_fee, discount_amount, total_amount,
                    payment_method, distance_km, estimated_minutes, cancel_reason,
                    created_at, accepted_at, delivered_at, cancelled_at, updated_at)
SELECT t.code, u.id, r.id, NULL, t.status,
       t.receiver_name, t.receiver_phone, t.delivery_address, t.delivery_lat, t.delivery_lng,
       t.note, t.subtotal, t.delivery_fee, t.discount_amount, t.total_amount,
       t.payment_method, t.distance_km, t.estimated_minutes, t.cancel_reason,
       t.created_at, t.accepted_at, t.delivered_at, t.cancelled_at,
       COALESCE(t.delivered_at, t.cancelled_at, t.accepted_at, t.created_at)
FROM tmp_mock_orders t
JOIN users u ON u.phone = t.customer_phone
JOIN restaurants r ON r.name = t.restaurant_name;
""")

    lines.append("-- ---------------------------------------------------------------------")
    lines.append("-- 5. Tạo bảng tạm nạp order_items")
    lines.append("-- ---------------------------------------------------------------------")
    lines.append("DROP TEMPORARY TABLE IF EXISTS tmp_mock_order_items;")
    lines.append("""CREATE TEMPORARY TABLE tmp_mock_order_items (
    order_code VARCHAR(20) NOT NULL,
    fallback_menu_item_id BIGINT UNSIGNED NOT NULL,
    item_name VARCHAR(150) NOT NULL,
    unit_price DECIMAL(12,0) NOT NULL,
    quantity SMALLINT UNSIGNED NOT NULL,
    note VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;""")
    lines.append("")

    lines.append("-- Nạp dữ liệu vào tmp_mock_order_items (chia chunk 500 dòng)")
    for chunk_start in range(0, len(order_items), chunk_size):
        chunk = order_items[chunk_start:chunk_start + chunk_size]
        lines.append("INSERT INTO tmp_mock_order_items (order_code, fallback_menu_item_id, item_name, unit_price, quantity, note) VALUES")
        val_rows = []
        for oi in chunk:
            row_str = (
                f"({format_sql_value(oi['order_code'])}, {format_sql_value(oi['menu_item_id'])}, "
                f"{format_sql_value(oi['item_name'])}, {format_sql_value(oi['unit_price'])}, "
                f"{format_sql_value(oi['quantity'])}, {format_sql_value(oi['note'])})"
            )
            val_rows.append(row_str)
        lines.append(",\n".join(val_rows) + ";\n")

    lines.append("-- ---------------------------------------------------------------------")
    lines.append("-- 6. Chuyển order_items từ bảng tạm vào bảng order_items thật")
    lines.append("--    order_id lấy theo orders.id (khớp qua mã đơn code)")
    lines.append("--    menu_item_id lấy chính xác qua join menu_items theo quán và tên món")
    lines.append("-- ---------------------------------------------------------------------")
    lines.append("""INSERT INTO order_items (order_id, menu_item_id, item_name, unit_price, quantity, note)
SELECT o.id,
       COALESCE(m.id, t.fallback_menu_item_id),
       t.item_name,
       t.unit_price,
       t.quantity,
       t.note
FROM tmp_mock_order_items t
JOIN orders o ON o.code = t.order_code
LEFT JOIN menu_categories c ON c.restaurant_id = o.restaurant_id
LEFT JOIN menu_items m ON m.category_id = c.id AND m.name = t.item_name;
""")

    lines.append("-- ---------------------------------------------------------------------")
    lines.append("-- 7. Thu dọn bảng tạm và hoàn tất transaction")
    lines.append("-- ---------------------------------------------------------------------")
    lines.append("DROP TEMPORARY TABLE IF EXISTS tmp_mock_order_items;")
    lines.append("DROP TEMPORARY TABLE IF EXISTS tmp_mock_orders;")
    lines.append("")
    lines.append("COMMIT;")
    lines.append("")
    lines.append("-- =====================================================================")
    lines.append("-- KẾT THÚC SEED MOCK ORDERS THÀNH CÔNG")
    lines.append("-- =====================================================================")

    with open(output_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_path = os.path.join(script_dir, "seed_mock_orders.sql")
    print(f"Generating 3,000 mock orders into {output_path}...")
    orders, order_items = generate_mock_orders(3000)
    print(f"Generated {len(orders)} orders and {len(order_items)} order items.")

    # Status breakdown
    from collections import Counter
    status_counts = Counter(o["status"] for o in orders)
    print("Status Breakdown:")
    for st, cnt in sorted(status_counts.items(), key=lambda x: -x[1]):
        pct = (cnt / len(orders)) * 100
        print(f"  {st:20s}: {cnt:5d} ({pct:5.2f}%)")

    # Delivered order revenue
    delivered_revenue = sum(o["total_amount"] for o in orders if o["status"] == "DELIVERED")
    print(f"Total delivered revenue: {delivered_revenue:,.0f} VND")

    build_sql_script(orders, order_items, output_path)
    print(f"Successfully written {os.path.getsize(output_path):,} bytes to {output_path}")


if __name__ == "__main__":
    main()
