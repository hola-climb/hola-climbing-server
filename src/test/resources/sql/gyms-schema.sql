-- Gym 도메인 통합 테스트용 스키마.
-- 운영 schema.sql의 gyms에서 users 참조 FK만 제외한 버전.
CREATE EXTENSION IF NOT EXISTS vector;

DROP TABLE IF EXISTS gym_grades CASCADE;
DROP TABLE IF EXISTS gyms CASCADE;

CREATE TABLE gyms (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    address         VARCHAR(200),
    lat             DOUBLE PRECISION,
    lng             DOUBLE PRECISION,
    description     TEXT,
    phone           VARCHAR(30),
    website         VARCHAR(300),
    thumbnail_url   VARCHAR(500),
    business_hours  JSONB,
    style_embedding vector(64),
    region_code     VARCHAR(20),
    rating_avg      NUMERIC(3,2) NOT NULL DEFAULT 0,
    rating_count    INTEGER NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'active',
    created_by      BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE TABLE gym_grades (
    id               BIGSERIAL PRIMARY KEY,
    gym_id           BIGINT NOT NULL REFERENCES gyms(id) ON DELETE CASCADE,
    label            VARCHAR(50) NOT NULL,
    difficulty_order INTEGER NOT NULL,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (gym_id, label),
    UNIQUE (gym_id, id)
);
