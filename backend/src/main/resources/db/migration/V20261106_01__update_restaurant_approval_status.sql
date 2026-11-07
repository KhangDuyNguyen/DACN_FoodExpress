-- =====================================================================
-- V20261106_01__update_restaurant_approval_status.sql
-- Tuan 4: Ho tro trang thai APPROVED va REJECTED cho bang restaurants
-- Nguoi thuc hien: Sam Xuan Diep
-- =====================================================================

ALTER TABLE restaurants 
  MODIFY COLUMN status ENUM('PENDING','APPROVED','REJECTED','ACTIVE','HIDDEN','SUSPENDED') NOT NULL DEFAULT 'PENDING';

-- Dong bo cac quan dang co trang thai ACTIVE sang APPROVED de thong nhat nghiep vu duyet
UPDATE restaurants SET status = 'APPROVED' WHERE status = 'ACTIVE';

