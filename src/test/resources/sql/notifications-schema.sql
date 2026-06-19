-- Notification 통합 테스트용 notifications / user_notification_settings 테이블.
-- users-schema.sql 다음에 실행되어야 한다 (FK 대상).
DROP TABLE IF EXISTS user_notification_settings CASCADE;
DROP TABLE IF EXISTS notifications CASCADE;

CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    recipient_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sender_id       BIGINT REFERENCES users(id) ON DELETE SET NULL,
    type            VARCHAR(30) NOT NULL,
    target_type     VARCHAR(30),
    target_id       BIGINT,
    title           VARCHAR(200),
    content         TEXT,
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_notification_settings (
    user_id         BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    notify_comment  BOOLEAN NOT NULL DEFAULT TRUE,
    notify_reply    BOOLEAN NOT NULL DEFAULT TRUE,
    notify_like     BOOLEAN NOT NULL DEFAULT TRUE,
    notify_follow   BOOLEAN NOT NULL DEFAULT TRUE,
    notify_chat     BOOLEAN NOT NULL DEFAULT TRUE,
    notify_system   BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
