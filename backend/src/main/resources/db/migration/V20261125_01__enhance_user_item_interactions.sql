-- =====================================================================
-- V20261125_01__enhance_user_item_interactions.sql (Diep, tuan 7)
-- Bo sung so luong, order_id va event_id cho bang user_item_interactions
-- de ghi nhan hanh vi day du va chong trung su kien khi retry.
-- =====================================================================

ALTER TABLE user_item_interactions
  ADD COLUMN quantity INT UNSIGNED NOT NULL DEFAULT 1 AFTER type,
  ADD COLUMN order_id BIGINT UNSIGNED NULL AFTER quantity,
  ADD COLUMN event_id VARCHAR(64) NULL AFTER order_id,
  ADD UNIQUE KEY uq_uii_event_id (event_id),
  ADD UNIQUE KEY uq_uii_order_item_type (order_id, menu_item_id, type),
  ADD KEY idx_uii_created_at (created_at),
  ADD CONSTRAINT fk_uii_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE SET NULL;

