-- User 도메인 통합 테스트용 스키마.
-- 운영 schema.sql의 users/follows/user_blocks에서 pgvector 의존 컬럼(style_embedding)과
-- 별도 인덱스를 제외한 버전 — plain postgres 컨테이너에서 동작하도록 함.
-- terms_versions/user_term_agreements도 포함 — 회원가입이 약관 검증을 수행하므로
-- signup을 호출하는 모든 통합 테스트에서 약관 테이블이 존재해야 한다.
DROP TABLE IF EXISTS user_term_agreements CASCADE;
DROP TABLE IF EXISTS terms_versions CASCADE;
DROP TABLE IF EXISTS user_blocks CASCADE;
DROP TABLE IF EXISTS follows CASCADE;
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
    last_login_at               TIMESTAMP,
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at                  TIMESTAMP,
    CONSTRAINT chk_user_auth CHECK (
        (email IS NOT NULL AND password_hash IS NOT NULL) OR
        (provider IS NOT NULL AND provider_id IS NOT NULL)
    ),
    UNIQUE (provider, provider_id)
);

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
