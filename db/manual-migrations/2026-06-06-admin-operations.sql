-- Manual migration for admin operations foundation.
-- Apply with:
--   PGPASSWORD=... psql -h localhost -p 5432 -U hola_user -d hola_climbing -f db/manual-migrations/2026-06-06-admin-operations.sql

BEGIN;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS role VARCHAR(20),
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);

ALTER TABLE users
    ALTER COLUMN role SET DEFAULT 'USER',
    ALTER COLUMN status SET DEFAULT 'ACTIVE';

UPDATE users
SET role = 'USER'
WHERE role IS NULL;

UPDATE users
SET status = CASE
    WHEN deleted_at IS NOT NULL THEN 'DELETED'
    ELSE 'ACTIVE'
END
WHERE status IS NULL;

ALTER TABLE users
    ALTER COLUMN role SET NOT NULL,
    ALTER COLUMN status SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_user_role'
          AND conrelid = 'users'::regclass
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT chk_user_role CHECK (role IN ('USER', 'ADMIN'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_user_status'
          AND conrelid = 'users'::regclass
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_users_role_status
    ON users(role, status);
CREATE INDEX IF NOT EXISTS idx_users_status_created_at
    ON users(status, created_at DESC);

CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    admin_id    BIGINT NOT NULL REFERENCES users(id),
    action      VARCHAR(80) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id   BIGINT,
    reason      TEXT,
    before_json JSONB,
    after_json  JSONB,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_admin_created
    ON admin_audit_logs(admin_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_target_created
    ON admin_audit_logs(target_type, target_id, created_at DESC);

COMMENT ON TABLE admin_audit_logs IS '관리자 작업 감사 로그';

COMMIT;
