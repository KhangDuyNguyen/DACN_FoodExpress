-- =====================================================================
-- V20261107_02__seed_phi_giao_hang.sql  (Khang, Module C tuan 3)
-- Bang phi giao hang mac dinh: 15.000d cho 2 km dau, moi km tiep theo (phan le lam tron len) them 5.000d.
-- Chua tinh phu phi gio cao diem (peak_surcharge = 0). Man hinh admin sua phi ship lam o tuan sau.
-- =====================================================================

INSERT INTO delivery_fee_rules (name, base_fee, base_distance_km, per_km_fee, peak_surcharge, is_active) VALUES
('Phí giao hàng mặc định', 15000, 2.00, 5000, 0, 1);
