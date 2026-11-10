-- =====================================================================
-- V20261107_03__dong_bo_trang_thai_quan.sql  (Khang, tuan 4)
-- Tu PR #16 (Diep), quan da duoc admin duyet co status = APPROVED, va V20261106_01 da doi ACTIVE -> APPROVED.
-- Nhung tren database moi, Flyway chay V20261106_01 TRUOC seed V20261107_01, nen 3 quan demo
-- (Com Nha Demo 2 chi nhanh, Bun Cha Demo) van con ACTIVE. Doi not sang APPROVED cho dong bo,
-- de trang duyet quan cua admin hien dung.
-- Database nao khong con dong ACTIVE thi cau lenh khong doi gi.
-- =====================================================================

UPDATE restaurants SET status = 'APPROVED' WHERE status = 'ACTIVE';
