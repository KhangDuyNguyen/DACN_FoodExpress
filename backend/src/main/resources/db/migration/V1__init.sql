-- =====================================================================
-- V1__init.sql  |  Food delivery (do an chuyen nganh)  |  MySQL 8.x
-- 41 bang, chi tao cau truc (khong co du lieu mau).
-- Dat file vao: src/main/resources/db/migration/V1__init.sql
-- Tien te: VND (DECIMAL(12,0)). Toa do: DECIMAL(10,7) / DECIMAL(11,7).
-- Thu tu tao bang theo phu thuoc khoa ngoai, khong dao thu tu.
-- =====================================================================

-- ---------------------------------------------------------------------
-- NHOM 1: TAI KHOAN VA CUA HANG
-- ---------------------------------------------------------------------

CREATE TABLE users (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  full_name     VARCHAR(100) NOT NULL,
  email         VARCHAR(150) NULL,
  phone         VARCHAR(20)  NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role          ENUM('CUSTOMER','MERCHANT','DRIVER','ADMIN') NOT NULL DEFAULT 'CUSTOMER',
  status        ENUM('ACTIVE','LOCKED','PENDING','DELETED') NOT NULL DEFAULT 'ACTIVE',
  avatar_url    VARCHAR(500) NULL,
  last_login_at DATETIME NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_email (email),
  UNIQUE KEY uq_users_phone (phone),
  KEY idx_users_role_status (role, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE addresses (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id       BIGINT UNSIGNED NOT NULL,
  label         VARCHAR(50)  NULL,
  receiver_name VARCHAR(100) NOT NULL,
  receiver_phone VARCHAR(20) NOT NULL,
  address_line  VARCHAR(255) NOT NULL,
  lat           DECIMAL(10,7) NULL,
  lng           DECIMAL(11,7) NULL,
  is_default    TINYINT(1) NOT NULL DEFAULT 0,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_addresses_user (user_id),
  CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE merchants (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  owner_user_id  BIGINT UNSIGNED NOT NULL,
  business_name  VARCHAR(150) NOT NULL,
  tax_code       VARCHAR(30)  NULL,
  contact_phone  VARCHAR(20)  NULL,
  status         ENUM('PENDING','APPROVED','REJECTED','SUSPENDED') NOT NULL DEFAULT 'PENDING',
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_merchants_owner (owner_user_id),
  KEY idx_merchants_status (status),
  CONSTRAINT fk_merchants_owner FOREIGN KEY (owner_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE restaurants (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  merchant_id        BIGINT UNSIGNED NOT NULL,
  name               VARCHAR(150) NOT NULL,
  description        TEXT NULL,
  phone              VARCHAR(20) NULL,
  address            VARCHAR(255) NOT NULL,
  lat                DECIMAL(10,7) NOT NULL,
  lng                DECIMAL(11,7) NOT NULL,
  logo_url           VARCHAR(500) NULL,
  cover_url          VARCHAR(500) NULL,
  open_status        ENUM('OPEN','CLOSED','TEMP_CLOSED') NOT NULL DEFAULT 'CLOSED',
  status             ENUM('PENDING','ACTIVE','HIDDEN','SUSPENDED') NOT NULL DEFAULT 'PENDING',
  min_order_amount   DECIMAL(12,0) NOT NULL DEFAULT 0,
  prep_time_minutes  SMALLINT UNSIGNED NOT NULL DEFAULT 15,
  avg_rating         DECIMAL(2,1) NOT NULL DEFAULT 0.0,
  rating_count       INT UNSIGNED NOT NULL DEFAULT 0,
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_restaurants_merchant (merchant_id),
  KEY idx_restaurants_name (name),
  KEY idx_restaurants_status (status, open_status),
  KEY idx_restaurants_geo (lat, lng),
  CONSTRAINT fk_restaurants_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE restaurant_hours (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  day_of_week   TINYINT UNSIGNED NOT NULL COMMENT '1=Thu 2 ... 7=Chu nhat',
  open_time     TIME NOT NULL,
  close_time    TIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_hours_slot (restaurant_id, day_of_week, open_time),
  CONSTRAINT chk_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
  CONSTRAINT fk_hours_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cuisines (
  id       INT UNSIGNED NOT NULL AUTO_INCREMENT,
  name     VARCHAR(80) NOT NULL,
  icon_url VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_cuisines_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE restaurant_cuisines (
  restaurant_id BIGINT UNSIGNED NOT NULL,
  cuisine_id    INT UNSIGNED NOT NULL,
  PRIMARY KEY (restaurant_id, cuisine_id),
  KEY idx_rc_cuisine (cuisine_id),
  CONSTRAINT fk_rc_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE,
  CONSTRAINT fk_rc_cuisine FOREIGN KEY (cuisine_id) REFERENCES cuisines (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE favorites (
  user_id       BIGINT UNSIGNED NOT NULL,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, restaurant_id),
  KEY idx_favorites_restaurant (restaurant_id),
  CONSTRAINT fk_favorites_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_favorites_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- NHOM 2: THUC DON
-- ---------------------------------------------------------------------

CREATE TABLE menu_categories (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  name          VARCHAR(100) NOT NULL,
  sort_order    INT NOT NULL DEFAULT 0,
  is_active     TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  KEY idx_menu_categories_restaurant (restaurant_id, sort_order),
  CONSTRAINT fk_menu_categories_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE menu_items (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  category_id  BIGINT UNSIGNED NOT NULL,
  name         VARCHAR(150) NOT NULL,
  description  TEXT NULL,
  price        DECIMAL(12,0) NOT NULL,
  image_url    VARCHAR(500) NULL,
  is_available TINYINT(1) NOT NULL DEFAULT 1,
  sort_order   INT NOT NULL DEFAULT 0,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_menu_items_category (category_id, sort_order),
  KEY idx_menu_items_name (name),
  CONSTRAINT chk_menu_items_price CHECK (price >= 0),
  CONSTRAINT fk_menu_items_category FOREIGN KEY (category_id) REFERENCES menu_categories (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE option_groups (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  menu_item_id BIGINT UNSIGNED NOT NULL,
  name         VARCHAR(100) NOT NULL,
  is_required  TINYINT(1) NOT NULL DEFAULT 0,
  min_select   TINYINT UNSIGNED NOT NULL DEFAULT 0,
  max_select   TINYINT UNSIGNED NOT NULL DEFAULT 1,
  sort_order   INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_option_groups_item (menu_item_id),
  CONSTRAINT chk_option_groups_select CHECK (min_select <= max_select),
  CONSTRAINT fk_option_groups_item FOREIGN KEY (menu_item_id) REFERENCES menu_items (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE options (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  group_id     BIGINT UNSIGNED NOT NULL,
  name         VARCHAR(100) NOT NULL,
  extra_price  DECIMAL(12,0) NOT NULL DEFAULT 0,
  is_available TINYINT(1) NOT NULL DEFAULT 1,
  sort_order   INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_options_group (group_id),
  CONSTRAINT chk_options_price CHECK (extra_price >= 0),
  CONSTRAINT fk_options_group FOREIGN KEY (group_id) REFERENCES option_groups (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE combos (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  name          VARCHAR(150) NOT NULL,
  description   TEXT NULL,
  price         DECIMAL(12,0) NOT NULL,
  image_url     VARCHAR(500) NULL,
  is_available  TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  KEY idx_combos_restaurant (restaurant_id),
  CONSTRAINT chk_combos_price CHECK (price >= 0),
  CONSTRAINT fk_combos_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE combo_items (
  combo_id     BIGINT UNSIGNED NOT NULL,
  menu_item_id BIGINT UNSIGNED NOT NULL,
  quantity     SMALLINT UNSIGNED NOT NULL DEFAULT 1,
  PRIMARY KEY (combo_id, menu_item_id),
  KEY idx_combo_items_item (menu_item_id),
  CONSTRAINT chk_combo_items_qty CHECK (quantity > 0),
  CONSTRAINT fk_combo_items_combo FOREIGN KEY (combo_id) REFERENCES combos (id) ON DELETE CASCADE,
  CONSTRAINT fk_combo_items_item FOREIGN KEY (menu_item_id) REFERENCES menu_items (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- NHOM 5 (tao truoc vi orders tham chieu drivers): TAI XE
-- ---------------------------------------------------------------------

CREATE TABLE drivers (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id          BIGINT UNSIGNED NOT NULL,
  vehicle_type     ENUM('MOTORBIKE','BICYCLE','CAR') NOT NULL DEFAULT 'MOTORBIKE',
  license_plate    VARCHAR(20) NULL,
  status           ENUM('PENDING','APPROVED','REJECTED','SUSPENDED') NOT NULL DEFAULT 'PENDING',
  is_online        TINYINT(1) NOT NULL DEFAULT 0,
  current_lat      DECIMAL(10,7) NULL,
  current_lng      DECIMAL(11,7) NULL,
  last_location_at DATETIME NULL,
  avg_rating       DECIMAL(2,1) NOT NULL DEFAULT 0.0,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_drivers_user (user_id),
  KEY idx_drivers_available (status, is_online),
  CONSTRAINT fk_drivers_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- NHOM 3: GIO HANG VA DON HANG
-- ---------------------------------------------------------------------

CREATE TABLE carts (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id       BIGINT UNSIGNED NOT NULL,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_carts_user (user_id),
  KEY idx_carts_restaurant (restaurant_id),
  CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_carts_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cart_items (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  cart_id      BIGINT UNSIGNED NOT NULL,
  menu_item_id BIGINT UNSIGNED NOT NULL,
  quantity     SMALLINT UNSIGNED NOT NULL DEFAULT 1,
  note         VARCHAR(255) NULL,
  PRIMARY KEY (id),
  KEY idx_cart_items_cart (cart_id),
  KEY idx_cart_items_item (menu_item_id),
  CONSTRAINT chk_cart_items_qty CHECK (quantity > 0),
  CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
  CONSTRAINT fk_cart_items_item FOREIGN KEY (menu_item_id) REFERENCES menu_items (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cart_item_options (
  cart_item_id BIGINT UNSIGNED NOT NULL,
  option_id    BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (cart_item_id, option_id),
  KEY idx_cio_option (option_id),
  CONSTRAINT fk_cio_cart_item FOREIGN KEY (cart_item_id) REFERENCES cart_items (id) ON DELETE CASCADE,
  CONSTRAINT fk_cio_option FOREIGN KEY (option_id) REFERENCES options (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE orders (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code              VARCHAR(20) NOT NULL,
  customer_id       BIGINT UNSIGNED NOT NULL,
  restaurant_id     BIGINT UNSIGNED NOT NULL,
  driver_id         BIGINT UNSIGNED NULL,
  status            ENUM('PENDING_PAYMENT','PLACED','ACCEPTED','PREPARING','READY_FOR_PICKUP',
                         'PICKED_UP','DELIVERING','DELIVERED','CANCELLED','REJECTED')
                    NOT NULL DEFAULT 'PLACED',
  receiver_name     VARCHAR(100) NOT NULL,
  receiver_phone    VARCHAR(20)  NOT NULL,
  delivery_address  VARCHAR(255) NOT NULL,
  delivery_lat      DECIMAL(10,7) NOT NULL,
  delivery_lng      DECIMAL(11,7) NOT NULL,
  note              VARCHAR(255) NULL,
  subtotal          DECIMAL(12,0) NOT NULL,
  delivery_fee      DECIMAL(12,0) NOT NULL DEFAULT 0,
  discount_amount   DECIMAL(12,0) NOT NULL DEFAULT 0,
  total_amount      DECIMAL(12,0) NOT NULL,
  payment_method    ENUM('COD','VNPAY','MOMO') NOT NULL DEFAULT 'COD',
  distance_km       DECIMAL(6,2) NULL,
  estimated_minutes SMALLINT UNSIGNED NULL,
  cancel_reason     VARCHAR(255) NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  accepted_at       DATETIME NULL,
  delivered_at      DATETIME NULL,
  cancelled_at      DATETIME NULL,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_orders_code (code),
  KEY idx_orders_customer (customer_id, created_at),
  KEY idx_orders_restaurant (restaurant_id, status, created_at),
  KEY idx_orders_driver (driver_id, status),
  KEY idx_orders_status (status, created_at),
  CONSTRAINT chk_orders_amounts CHECK (subtotal >= 0 AND delivery_fee >= 0 AND discount_amount >= 0 AND total_amount >= 0),
  CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES users (id),
  CONSTRAINT fk_orders_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id),
  CONSTRAINT fk_orders_driver FOREIGN KEY (driver_id) REFERENCES drivers (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE order_items (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id     BIGINT UNSIGNED NOT NULL,
  menu_item_id BIGINT UNSIGNED NULL,
  item_name    VARCHAR(150) NOT NULL,
  unit_price   DECIMAL(12,0) NOT NULL,
  quantity     SMALLINT UNSIGNED NOT NULL,
  note         VARCHAR(255) NULL,
  PRIMARY KEY (id),
  KEY idx_order_items_order (order_id),
  KEY idx_order_items_item (menu_item_id),
  CONSTRAINT chk_order_items_qty CHECK (quantity > 0),
  CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
  CONSTRAINT fk_order_items_item FOREIGN KEY (menu_item_id) REFERENCES menu_items (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE order_item_options (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_item_id BIGINT UNSIGNED NOT NULL,
  option_id     BIGINT UNSIGNED NULL,
  option_name   VARCHAR(100) NOT NULL,
  extra_price   DECIMAL(12,0) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_oio_order_item (order_item_id),
  KEY idx_oio_option (option_id),
  CONSTRAINT fk_oio_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id) ON DELETE CASCADE,
  CONSTRAINT fk_oio_option FOREIGN KEY (option_id) REFERENCES options (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE order_status_history (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id   BIGINT UNSIGNED NOT NULL,
  status     ENUM('PENDING_PAYMENT','PLACED','ACCEPTED','PREPARING','READY_FOR_PICKUP',
                  'PICKED_UP','DELIVERING','DELIVERED','CANCELLED','REJECTED') NOT NULL,
  changed_by BIGINT UNSIGNED NULL,
  changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  note       VARCHAR(255) NULL,
  PRIMARY KEY (id),
  KEY idx_osh_order (order_id, changed_at),
  KEY idx_osh_changed_by (changed_by),
  CONSTRAINT fk_osh_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
  CONSTRAINT fk_osh_user FOREIGN KEY (changed_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- NHOM 4: THANH TOAN VA KHUYEN MAI
-- ---------------------------------------------------------------------

CREATE TABLE payments (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id        BIGINT UNSIGNED NOT NULL,
  method          ENUM('COD','VNPAY','MOMO') NOT NULL,
  amount          DECIMAL(12,0) NOT NULL,
  status          ENUM('PENDING','SUCCESS','FAILED','REFUNDED') NOT NULL DEFAULT 'PENDING',
  transaction_ref VARCHAR(100) NULL,
  gateway_payload JSON NULL,
  paid_at         DATETIME NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_payments_order (order_id),
  KEY idx_payments_txn (transaction_ref),
  CONSTRAINT chk_payments_amount CHECK (amount >= 0),
  CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE refunds (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  payment_id   BIGINT UNSIGNED NOT NULL,
  amount       DECIMAL(12,0) NOT NULL,
  reason       VARCHAR(255) NULL,
  status       ENUM('PENDING','COMPLETED','REJECTED') NOT NULL DEFAULT 'PENDING',
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  processed_at DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_refunds_payment (payment_id),
  CONSTRAINT chk_refunds_amount CHECK (amount > 0),
  CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE vouchers (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code           VARCHAR(30) NOT NULL,
  title          VARCHAR(150) NOT NULL,
  description    VARCHAR(500) NULL,
  discount_type  ENUM('PERCENT','FIXED','FREE_SHIP') NOT NULL,
  discount_value DECIMAL(12,2) NOT NULL DEFAULT 0,
  min_order      DECIMAL(12,0) NOT NULL DEFAULT 0,
  max_discount   DECIMAL(12,0) NULL,
  start_at       DATETIME NOT NULL,
  end_at         DATETIME NOT NULL,
  usage_limit    INT UNSIGNED NULL,
  per_user_limit INT UNSIGNED NOT NULL DEFAULT 1,
  used_count     INT UNSIGNED NOT NULL DEFAULT 0,
  restaurant_id  BIGINT UNSIGNED NULL COMMENT 'NULL = ap dung toan san',
  created_by     BIGINT UNSIGNED NULL,
  status         ENUM('ACTIVE','PAUSED','EXPIRED') NOT NULL DEFAULT 'ACTIVE',
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_vouchers_code (code),
  KEY idx_vouchers_restaurant (restaurant_id),
  KEY idx_vouchers_active (status, start_at, end_at),
  CONSTRAINT chk_vouchers_time CHECK (end_at > start_at),
  CONSTRAINT chk_vouchers_value CHECK (discount_value >= 0),
  CONSTRAINT fk_vouchers_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE,
  CONSTRAINT fk_vouchers_creator FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE voucher_usages (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  voucher_id BIGINT UNSIGNED NOT NULL,
  user_id    BIGINT UNSIGNED NOT NULL,
  order_id   BIGINT UNSIGNED NOT NULL,
  used_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_voucher_usages_order (order_id),
  KEY idx_voucher_usages_voucher_user (voucher_id, user_id),
  KEY idx_voucher_usages_user (user_id),
  CONSTRAINT fk_vu_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers (id),
  CONSTRAINT fk_vu_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_vu_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- NHOM 5: GIAO HANG
-- ---------------------------------------------------------------------

CREATE TABLE driver_locations (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  driver_id   BIGINT UNSIGNED NOT NULL,
  order_id    BIGINT UNSIGNED NULL,
  lat         DECIMAL(10,7) NOT NULL,
  lng         DECIMAL(11,7) NOT NULL,
  recorded_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_dl_driver_time (driver_id, recorded_at),
  KEY idx_dl_order_time (order_id, recorded_at),
  CONSTRAINT fk_dl_driver FOREIGN KEY (driver_id) REFERENCES drivers (id) ON DELETE CASCADE,
  CONSTRAINT fk_dl_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE order_assignments (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id      BIGINT UNSIGNED NOT NULL,
  driver_id     BIGINT UNSIGNED NOT NULL,
  status        ENUM('OFFERED','ACCEPTED','REJECTED','EXPIRED','CANCELLED') NOT NULL DEFAULT 'OFFERED',
  distance_km   DECIMAL(6,2) NULL,
  offered_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at    DATETIME NULL,
  responded_at  DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_oa_order (order_id, status),
  KEY idx_oa_driver (driver_id, status),
  CONSTRAINT fk_oa_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
  CONSTRAINT fk_oa_driver FOREIGN KEY (driver_id) REFERENCES drivers (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE driver_earnings (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  driver_id  BIGINT UNSIGNED NOT NULL,
  order_id   BIGINT UNSIGNED NOT NULL,
  amount     DECIMAL(12,0) NOT NULL,
  bonus      DECIMAL(12,0) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_de_order (order_id),
  KEY idx_de_driver_time (driver_id, created_at),
  CONSTRAINT fk_de_driver FOREIGN KEY (driver_id) REFERENCES drivers (id),
  CONSTRAINT fk_de_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE delivery_fee_rules (
  id               INT UNSIGNED NOT NULL AUTO_INCREMENT,
  name             VARCHAR(100) NOT NULL,
  base_fee         DECIMAL(12,0) NOT NULL,
  base_distance_km DECIMAL(5,2) NOT NULL DEFAULT 2.00,
  per_km_fee       DECIMAL(12,0) NOT NULL,
  peak_surcharge   DECIMAL(12,0) NOT NULL DEFAULT 0,
  is_active        TINYINT(1) NOT NULL DEFAULT 1,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_dfr_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- NHOM 6: DANH GIA, THONG BAO, HO TRO
-- ---------------------------------------------------------------------

CREATE TABLE reviews (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id          BIGINT UNSIGNED NOT NULL,
  customer_id       BIGINT UNSIGNED NOT NULL,
  restaurant_id     BIGINT UNSIGNED NOT NULL,
  restaurant_rating TINYINT UNSIGNED NOT NULL,
  driver_rating     TINYINT UNSIGNED NULL,
  `comment`         TEXT NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reviews_order (order_id),
  KEY idx_reviews_restaurant (restaurant_id, created_at),
  KEY idx_reviews_customer (customer_id),
  CONSTRAINT chk_reviews_restaurant_rating CHECK (restaurant_rating BETWEEN 1 AND 5),
  CONSTRAINT chk_reviews_driver_rating CHECK (driver_rating IS NULL OR driver_rating BETWEEN 1 AND 5),
  CONSTRAINT fk_reviews_order FOREIGN KEY (order_id) REFERENCES orders (id),
  CONSTRAINT fk_reviews_customer FOREIGN KEY (customer_id) REFERENCES users (id),
  CONSTRAINT fk_reviews_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE review_replies (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  review_id        BIGINT UNSIGNED NOT NULL,
  merchant_user_id BIGINT UNSIGNED NOT NULL,
  content          TEXT NOT NULL,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_rr_review (review_id),
  KEY idx_rr_user (merchant_user_id),
  CONSTRAINT fk_rr_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE,
  CONSTRAINT fk_rr_user FOREIGN KEY (merchant_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notifications (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id    BIGINT UNSIGNED NOT NULL,
  type       ENUM('ORDER','PROMOTION','SYSTEM') NOT NULL DEFAULT 'SYSTEM',
  title      VARCHAR(150) NOT NULL,
  body       VARCHAR(500) NOT NULL,
  data_json  JSON NULL,
  is_read    TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_notifications_user (user_id, is_read, created_at),
  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE device_tokens (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id    BIGINT UNSIGNED NOT NULL,
  fcm_token  VARCHAR(255) NOT NULL,
  platform   ENUM('ANDROID','IOS','WEB') NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_device_tokens_token (fcm_token),
  KEY idx_device_tokens_user (user_id),
  CONSTRAINT fk_device_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE support_tickets (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     BIGINT UNSIGNED NOT NULL,
  order_id    BIGINT UNSIGNED NULL,
  subject     VARCHAR(200) NOT NULL,
  description TEXT NULL,
  status      ENUM('OPEN','IN_PROGRESS','RESOLVED','CLOSED') NOT NULL DEFAULT 'OPEN',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_st_user (user_id),
  KEY idx_st_order (order_id),
  KEY idx_st_status (status, created_at),
  CONSTRAINT fk_st_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_st_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- NHOM 7: BANG PHUC VU AI
-- ---------------------------------------------------------------------

CREATE TABLE ai_chat_sessions (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id    BIGINT UNSIGNED NOT NULL,
  started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_acs_user (user_id, started_at),
  CONSTRAINT fk_acs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_chat_messages (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  session_id BIGINT UNSIGNED NOT NULL,
  role       ENUM('USER','ASSISTANT') NOT NULL,
  content    TEXT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_acm_session (session_id, created_at),
  CONSTRAINT fk_acm_session FOREIGN KEY (session_id) REFERENCES ai_chat_sessions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_item_interactions (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED NOT NULL,
  menu_item_id BIGINT UNSIGNED NOT NULL,
  type         ENUM('VIEW','ADD_TO_CART','ORDER','FAVORITE') NOT NULL,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_uii_user_time (user_id, created_at),
  KEY idx_uii_item_type (menu_item_id, type),
  CONSTRAINT fk_uii_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_uii_item FOREIGN KEY (menu_item_id) REFERENCES menu_items (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE review_analysis (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  review_id   BIGINT UNSIGNED NOT NULL,
  sentiment   ENUM('POSITIVE','NEUTRAL','NEGATIVE') NOT NULL,
  keywords    VARCHAR(500) NULL,
  analyzed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_review_analysis_review (review_id),
  CONSTRAINT fk_ra_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE delivery_time_predictions (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id          BIGINT UNSIGNED NOT NULL,
  predicted_minutes SMALLINT UNSIGNED NOT NULL,
  actual_minutes    SMALLINT UNSIGNED NULL,
  model_version     VARCHAR(50) NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_dtp_order (order_id),
  CONSTRAINT fk_dtp_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE demand_forecasts (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  restaurant_id     BIGINT UNSIGNED NOT NULL,
  forecast_date     DATE NOT NULL,
  predicted_orders  INT UNSIGNED NOT NULL,
  predicted_revenue DECIMAL(14,0) NOT NULL,
  model_version     VARCHAR(50) NOT NULL DEFAULT 'v1',
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_df_restaurant_date_model (restaurant_id, forecast_date, model_version),
  CONSTRAINT fk_df_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
