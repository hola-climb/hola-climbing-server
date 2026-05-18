-- Analysis 도메인 통합 테스트용 analysis_results 테이블.
-- users-schema.sql, gyms-schema.sql, videos-schema.sql 다음에 실행되어야 한다 (FK 대상).
-- 운영 schema.sql에서 별도 인덱스를 제외한 버전.
DROP TABLE IF EXISTS analysis_results CASCADE;

CREATE TABLE analysis_results (
    id              BIGSERIAL PRIMARY KEY,
    video_id        BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    sequence_index  INTEGER NOT NULL DEFAULT 0,
    start_time_ms   INTEGER,
    end_time_ms     INTEGER,
    technique       VARCHAR(50),
    is_dynamic      BOOLEAN,
    confidence      REAL,
    e_trajectory    DOUBLE PRECISION,
    e_arm           DOUBLE PRECISION,
    model_version   VARCHAR(50),
    raw_data        JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
