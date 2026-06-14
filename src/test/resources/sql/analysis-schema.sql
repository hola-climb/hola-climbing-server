-- Analysis 도메인 통합 테스트용 analysis_results / analysis_video_results / labels 테이블.
-- users-schema.sql, gyms-schema.sql, videos-schema.sql 다음에 실행되어야 한다 (FK 대상).
-- 운영 schema.sql에서 별도 인덱스를 제외한 버전.
DROP TABLE IF EXISTS labels CASCADE;
DROP TABLE IF EXISTS analysis_video_results CASCADE;
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
    model_version   VARCHAR(50),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE analysis_video_results (
    video_id                BIGINT PRIMARY KEY REFERENCES videos(id) ON DELETE CASCADE,
    model_version           VARCHAR(50),
    ai_techniques           JSONB NOT NULL DEFAULT '[]'::jsonb,
    ai_is_dynamic           BOOLEAN,
    ai_dynamic_probability  REAL,
    final_techniques        JSONB NOT NULL DEFAULT '[]'::jsonb,
    final_is_dynamic        BOOLEAN,
    feedback_applied        BOOLEAN NOT NULL DEFAULT FALSE,
    feedback_note           TEXT,
    corrected_by            BIGINT REFERENCES users(id) ON DELETE SET NULL,
    corrected_at            TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_analysis_video_dynamic_probability
        CHECK (ai_dynamic_probability IS NULL OR (ai_dynamic_probability >= 0 AND ai_dynamic_probability <= 1))
);

CREATE TABLE labels (
    id          BIGSERIAL PRIMARY KEY,
    video_id    BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    technique   VARCHAR(50),
    is_correct  BOOLEAN,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
