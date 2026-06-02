-- Manual migration for the current schema.sql-driven dev database.
-- Apply with:
--   PGPASSWORD=... psql -h localhost -p 5432 -U hola_user -d hola_climbing -f db/manual-migrations/2026-06-03-video-recorded-date-and-schema-drift.sql

BEGIN;

ALTER TABLE videos
    ADD COLUMN IF NOT EXISTS recorded_date DATE;

UPDATE videos
SET recorded_date = created_at::date
WHERE recorded_date IS NULL;

ALTER TABLE videos
    ALTER COLUMN recorded_date SET NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'reports' AND column_name = 'reason_code'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'reports' AND column_name = 'category'
    ) THEN
        ALTER TABLE reports RENAME COLUMN reason_code TO category;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'reports' AND column_name = 'reason_detail'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'reports' AND column_name = 'reason'
    ) THEN
        ALTER TABLE reports RENAME COLUMN reason_detail TO reason;
    END IF;
END $$;

ALTER TABLE reports
    ALTER COLUMN category TYPE VARCHAR(50),
    ALTER COLUMN reason TYPE TEXT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'reports'::regclass
          AND contype = 'u'
          AND conkey = ARRAY[
              (SELECT attnum FROM pg_attribute WHERE attrelid = 'reports'::regclass AND attname = 'reporter_id'),
              (SELECT attnum FROM pg_attribute WHERE attrelid = 'reports'::regclass AND attname = 'target_type'),
              (SELECT attnum FROM pg_attribute WHERE attrelid = 'reports'::regclass AND attname = 'target_id')
          ]::smallint[]
    ) THEN
        ALTER TABLE reports
            ADD CONSTRAINT reports_reporter_target_unique UNIQUE (reporter_id, target_type, target_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_gym_reviews_gym
    ON gym_reviews(gym_id, created_at DESC);

COMMIT;
