-- ClimbingLog 도메인 통합 테스트용 climbing_logs 테이블.
-- users-schema.sql, gyms-schema.sql 다음에 실행되어야 한다 (FK 대상).
DROP TABLE IF EXISTS climbing_logs CASCADE;

CREATE TABLE climbing_logs (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    gym_id        BIGINT NOT NULL REFERENCES gyms(id) ON DELETE CASCADE,
    climbed_on    DATE NOT NULL,
    grade_counts  JSONB NOT NULL DEFAULT '{}'::jsonb,
    memo          TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ
);
CREATE INDEX idx_climbing_logs_user_date ON climbing_logs(user_id, climbed_on)
    WHERE deleted_at IS NULL;
