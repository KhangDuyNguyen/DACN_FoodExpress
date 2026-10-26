-- =====================================================================
-- V20261026_01__seed_tai_khoan_demo.sql  (Khang, tuan 3)
-- Tai khoan mau cho tung vai tro de demo va test. DU LIEU GIA, khong phai nguoi that.
-- Mat khau cua tat ca tai khoan duoi day: Demo@12345 (ghi o docs/week3-demo.md).
-- Cot password_hash la ket qua BCrypt (cost 10) cua mat khau tren, KHONG luu mat khau goc.
-- Khong ghi id co dinh: id do MySQL tu tang, cac file seed sau tim user theo so dien thoai.
-- Khong tao dong merchants / drivers o day: bang do thuoc module cua Duong / An.
-- =====================================================================

INSERT INTO users (full_name, email, phone, password_hash, role, status) VALUES
('Khách Demo Một', 'khach1@demo.test', '0911000001', '$2a$10$fzWKyu8V19NEeord90ek9ueuFKnb57BFpPozpUUwYC/mgkSE0CHWu', 'CUSTOMER', 'ACTIVE'),
('Khách Demo Hai', 'khach2@demo.test', '0911000002', '$2a$10$fzWKyu8V19NEeord90ek9ueuFKnb57BFpPozpUUwYC/mgkSE0CHWu', 'CUSTOMER', 'ACTIVE'),
('Chủ Quán Demo Một', 'chuquan1@demo.test', '0922000001', '$2a$10$fzWKyu8V19NEeord90ek9ueuFKnb57BFpPozpUUwYC/mgkSE0CHWu', 'MERCHANT', 'ACTIVE'),
('Chủ Quán Demo Hai', 'chuquan2@demo.test', '0922000002', '$2a$10$fzWKyu8V19NEeord90ek9ueuFKnb57BFpPozpUUwYC/mgkSE0CHWu', 'MERCHANT', 'ACTIVE'),
('Tài Xế Demo', 'taixe1@demo.test', '0933000001', '$2a$10$fzWKyu8V19NEeord90ek9ueuFKnb57BFpPozpUUwYC/mgkSE0CHWu', 'DRIVER', 'ACTIVE'),
('Quản Trị Demo', 'admin@demo.test', '0900000001', '$2a$10$fzWKyu8V19NEeord90ek9ueuFKnb57BFpPozpUUwYC/mgkSE0CHWu', 'ADMIN', 'ACTIVE'),
-- Tai khoan bi khoa: dung de test dang nhap dung mat khau van bi 403
('Khách Bị Khóa', 'khoa@demo.test', '0911000009', '$2a$10$fzWKyu8V19NEeord90ek9ueuFKnb57BFpPozpUUwYC/mgkSE0CHWu', 'CUSTOMER', 'LOCKED');
