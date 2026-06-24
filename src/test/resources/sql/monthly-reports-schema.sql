DROP TABLE IF EXISTS monthly_reports CASCADE;

CREATE TABLE monthly_reports (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    period              CHAR(7) NOT NULL,
    selected_gym_id     BIGINT REFERENCES gyms(id) ON DELETE SET NULL,
    status              VARCHAR(30) NOT NULL,
    source              VARCHAR(30) NOT NULL,
    metrics             JSONB NOT NULL DEFAULT '{}'::jsonb,
    grade               JSONB,
    tip                 JSONB,
    next_month_goal     JSONB,
    recommended_gyms    JSONB NOT NULL DEFAULT '[]'::jsonb,
    narrative           JSONB,
    requirement         JSONB,
    model               VARCHAR(100),
    prompt_version      VARCHAR(50),
    generated_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_monthly_reports_period
        CHECK (period ~ '^[0-9]{4}-[0-9]{2}$'),
    CONSTRAINT chk_monthly_reports_status
        CHECK (status IN ('ready', 'insufficientData', 'generating', 'failed')),
    CONSTRAINT chk_monthly_reports_source
        CHECK (source IN ('log', 'videoFallback', 'none'))
);

CREATE UNIQUE INDEX uq_monthly_reports_user_period_no_gym
    ON monthly_reports(user_id, period)
    WHERE selected_gym_id IS NULL;

CREATE UNIQUE INDEX uq_monthly_reports_user_period_gym
    ON monthly_reports(user_id, period, selected_gym_id)
    WHERE selected_gym_id IS NOT NULL;

CREATE INDEX idx_monthly_reports_user_period
    ON monthly_reports(user_id, period DESC);
