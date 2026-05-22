-- Gym 도메인 통합 테스트용 스키마.
-- 운영 schema.sql의 gyms/gym_photos에서 pgvector 컬럼(style_embedding)과
-- users 참조 FK를 제외한 버전.
DROP TABLE IF EXISTS gym_photos CASCADE;
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
    region_code     VARCHAR(20),
    rating_avg      NUMERIC(3,2) NOT NULL DEFAULT 0,
    rating_count    INTEGER NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'active',
    created_by      BIGINT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP
);

CREATE TABLE gym_photos (
    id              BIGSERIAL PRIMARY KEY,
    gym_id          BIGINT NOT NULL REFERENCES gyms(id) ON DELETE CASCADE,
    uploaded_by     BIGINT,
    gcs_path        VARCHAR(500) NOT NULL,
    caption         VARCHAR(200),
    display_order   INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
