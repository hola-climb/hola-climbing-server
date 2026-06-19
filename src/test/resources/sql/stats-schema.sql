-- Stats 도메인 통합 테스트용 user_stats 테이블.
-- users-schema.sql 다음에 실행되어야 한다 (FK 대상).
DROP TABLE IF EXISTS user_stats CASCADE;

CREATE TABLE user_stats (
    user_id                 BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    total_videos            INTEGER NOT NULL DEFAULT 0,
    total_climbing_seconds  BIGINT NOT NULL DEFAULT 0,
    technique_counts        JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_climbed_at         TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
