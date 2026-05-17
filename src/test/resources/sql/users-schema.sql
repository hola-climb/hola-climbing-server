-- User 도메인 통합 테스트용 스키마.
-- 운영 schema.sql의 users 테이블에서 pgvector 의존 컬럼(style_embedding)과
-- 별도 인덱스를 제외한 버전 — plain postgres 컨테이너에서 동작하도록 함.
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
