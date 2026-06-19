-- Report 도메인 통합 테스트용 reports 테이블.
-- users-schema.sql 다음에 실행되어야 한다 (FK 대상).
-- 운영 schema.sql에서 별도 인덱스를 제외한 버전.
DROP TABLE IF EXISTS reports CASCADE;

CREATE TABLE reports (
    id              BIGSERIAL PRIMARY KEY,
    reporter_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_type     VARCHAR(30) NOT NULL,
    target_id       BIGINT NOT NULL,
    category        VARCHAR(50) NOT NULL,
    reason          TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    reviewed_by     BIGINT REFERENCES users(id),
    reviewed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (reporter_id, target_type, target_id)
);
