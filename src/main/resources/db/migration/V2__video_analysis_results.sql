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

CREATE INDEX idx_analysis_video_results_model_feedback
    ON analysis_video_results(model_version, feedback_applied);
CREATE INDEX idx_analysis_video_results_corrected_by
    ON analysis_video_results(corrected_by)
    WHERE corrected_by IS NOT NULL;
