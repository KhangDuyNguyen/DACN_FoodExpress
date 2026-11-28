ALTER TABLE notifications
    ADD COLUMN event_key VARCHAR(150) NULL,
    ADD CONSTRAINT uk_notifications_user_event UNIQUE (user_id, event_key);
