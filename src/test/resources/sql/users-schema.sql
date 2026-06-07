-- User 도메인 통합 테스트용 스키마.
-- 운영 schema.sql의 users/follows/user_blocks에서 pgvector 의존 컬럼(style_embedding)과
-- 별도 인덱스를 제외한 버전 — plain postgres 컨테이너에서 동작하도록 함.
-- terms_versions/user_term_agreements도 포함 — 회원가입이 약관 검증을 수행하므로
-- signup을 호출하는 모든 통합 테스트에서 약관 테이블이 존재해야 한다.
DROP TABLE IF EXISTS device_tokens CASCADE;
DROP TABLE IF EXISTS user_term_agreements CASCADE;
DROP TABLE IF EXISTS terms_versions CASCADE;
DROP TABLE IF EXISTS user_blocks CASCADE;
DROP TABLE IF EXISTS follows CASCADE;
DROP TABLE IF EXISTS admin_audit_logs CASCADE;
DROP TABLE IF EXISTS users CASCADE;

CREATE TABLE users (
    id                          BIGSERIAL PRIMARY KEY,
    email                       VARCHAR(255) UNIQUE,
    password_hash               VARCHAR(255),
    email_verified              BOOLEAN NOT NULL DEFAULT FALSE,
    email_verification_token    VARCHAR(255),
    provider                    VARCHAR(20),
    provider_id                 VARCHAR(100),
    nickname                    VARCHAR(50) NOT NULL UNIQUE,
    profile_image               VARCHAR(500),
    bio                         TEXT,
    role                        VARCHAR(20) NOT NULL DEFAULT 'USER',
    status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login_at               TIMESTAMP,
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at                  TIMESTAMP,
    CONSTRAINT chk_user_auth CHECK (
        (email IS NOT NULL AND password_hash IS NOT NULL) OR
        (provider IS NOT NULL AND provider_id IS NOT NULL)
    ),
    CONSTRAINT chk_user_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    UNIQUE (provider, provider_id)
);

CREATE INDEX idx_users_role_status    ON users(role, status);
CREATE INDEX idx_users_status_created_at ON users(status, created_at DESC);

CREATE TABLE admin_audit_logs (
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
CREATE INDEX idx_admin_audit_logs_admin_created
    ON admin_audit_logs(admin_id, created_at DESC);
CREATE INDEX idx_admin_audit_logs_target_created
    ON admin_audit_logs(target_type, target_id, created_at DESC);

CREATE TABLE follows (
    id              BIGSERIAL PRIMARY KEY,
    follower_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    following_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (follower_id, following_id),
    CHECK (follower_id <> following_id)
);

CREATE TABLE user_blocks (
    id              BIGSERIAL PRIMARY KEY,
    blocker_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason          VARCHAR(200),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);

CREATE TABLE terms_versions (
    id              BIGSERIAL PRIMARY KEY,
    type            VARCHAR(30) NOT NULL,
    version         VARCHAR(20) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    content         TEXT NOT NULL,
    is_required     BOOLEAN NOT NULL DEFAULT TRUE,
    effective_at    TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (type, version)
);

CREATE TABLE user_term_agreements (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    term_version_id BIGINT NOT NULL REFERENCES terms_versions(id),
    agreed          BOOLEAN NOT NULL,
    agreed_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, term_version_id)
);

-- FCM 디바이스 토큰. 영상 분석 완료/실패 푸시 알림 대상.
CREATE TABLE device_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token           VARCHAR(500) NOT NULL UNIQUE,
    platform        VARCHAR(20) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
