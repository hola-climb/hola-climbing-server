-- Manual migration for gym-specific grade masters.
-- Apply with:
--   PGPASSWORD="$HOLA_DB_PASSWORD" psql -h localhost -p 5432 -U hola_user -d hola_climbing -f db/manual-migrations/2026-06-03-gym-grades.sql

BEGIN;

CREATE TABLE IF NOT EXISTS gym_grades (
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

CREATE INDEX IF NOT EXISTS idx_gym_grades_gym_order
    ON gym_grades(gym_id, difficulty_order, id)
    WHERE is_active = TRUE;

ALTER TABLE gym_grades
    DROP COLUMN IF EXISTS color_hex;

ALTER TABLE videos
    ADD COLUMN IF NOT EXISTS gym_grade_id BIGINT;

INSERT INTO gym_grades (id, gym_id, label, difficulty_order, is_active)
SELECT *
FROM (VALUES
    (9014::BIGINT, 9001::BIGINT, '흰색'::VARCHAR, 10, TRUE),
    (9015::BIGINT, 9001::BIGINT, '노랑'::VARCHAR, 20, TRUE),
    (9001::BIGINT, 9001::BIGINT, '초록'::VARCHAR, 30, TRUE),
    (9002::BIGINT, 9001::BIGINT, '파랑'::VARCHAR, 40, TRUE),
    (9003::BIGINT, 9001::BIGINT, '빨강'::VARCHAR, 50, TRUE),
    (9016::BIGINT, 9001::BIGINT, '검정'::VARCHAR, 60, TRUE),
    (9017::BIGINT, 9001::BIGINT, '회색'::VARCHAR, 70, TRUE),
    (9018::BIGINT, 9001::BIGINT, '갈색'::VARCHAR, 80, TRUE),
    (9019::BIGINT, 9001::BIGINT, '핑크'::VARCHAR, 90, TRUE),
    (9020::BIGINT, 9002::BIGINT, '흰색'::VARCHAR, 10, TRUE),
    (9004::BIGINT, 9002::BIGINT, '노랑'::VARCHAR, 20, TRUE),
    (9021::BIGINT, 9002::BIGINT, '주황'::VARCHAR, 30, TRUE),
    (9022::BIGINT, 9002::BIGINT, '초록'::VARCHAR, 40, TRUE),
    (9023::BIGINT, 9002::BIGINT, '파랑'::VARCHAR, 50, TRUE),
    (9024::BIGINT, 9002::BIGINT, '빨강'::VARCHAR, 60, TRUE),
    (9005::BIGINT, 9002::BIGINT, '보라'::VARCHAR, 70, TRUE),
    (9006::BIGINT, 9002::BIGINT, '회색'::VARCHAR, 80, TRUE),
    (9025::BIGINT, 9002::BIGINT, '갈색'::VARCHAR, 90, TRUE),
    (9026::BIGINT, 9002::BIGINT, '검정'::VARCHAR, 100, TRUE),
    (9007::BIGINT, 9003::BIGINT, '노랑'::VARCHAR, 10, TRUE),
    (9008::BIGINT, 9003::BIGINT, '핑크'::VARCHAR, 20, TRUE),
    (9009::BIGINT, 9003::BIGINT, '파랑'::VARCHAR, 30, TRUE),
    (9027::BIGINT, 9003::BIGINT, '빨강'::VARCHAR, 40, TRUE),
    (9028::BIGINT, 9003::BIGINT, '보라'::VARCHAR, 50, TRUE),
    (9029::BIGINT, 9003::BIGINT, '갈색'::VARCHAR, 60, TRUE),
    (9030::BIGINT, 9003::BIGINT, '회색'::VARCHAR, 70, TRUE),
    (9031::BIGINT, 9003::BIGINT, '검정'::VARCHAR, 80, TRUE),
    (9032::BIGINT, 9003::BIGINT, '흰색'::VARCHAR, 90, TRUE)
) AS seed(id, gym_id, label, difficulty_order, is_active)
WHERE EXISTS (SELECT 1 FROM gyms g WHERE g.id = seed.gym_id)
ON CONFLICT (id) DO UPDATE SET
    gym_id           = EXCLUDED.gym_id,
    label            = EXCLUDED.label,
    difficulty_order = EXCLUDED.difficulty_order,
    is_active        = EXCLUDED.is_active,
    updated_at       = NOW();

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'videos'
          AND column_name = 'grade'
    ) THEN
        EXECUTE '
            UPDATE videos v
            SET gym_grade_id = gg.id
            FROM gym_grades gg
            WHERE v.gym_grade_id IS NULL
              AND v.gym_id = gg.gym_id
              AND v.grade = gg.label
        ';
    END IF;
END $$;

UPDATE videos v
SET gym_grade_id = (
    SELECT gg.id
    FROM gym_grades gg
    WHERE gg.gym_id = v.gym_id
      AND gg.is_active = TRUE
    ORDER BY gg.difficulty_order, gg.id
    LIMIT 1
)
WHERE v.gym_grade_id IS NULL
  AND v.gym_id IS NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM videos
        WHERE gym_id IS NULL OR gym_grade_id IS NULL
    ) THEN
        RAISE EXCEPTION 'videos.gym_id and videos.gym_grade_id must be backfilled before applying NOT NULL constraints';
    END IF;
END $$;

ALTER TABLE videos
    DROP CONSTRAINT IF EXISTS videos_gym_id_fkey,
    DROP CONSTRAINT IF EXISTS fk_videos_gym,
    DROP CONSTRAINT IF EXISTS fk_videos_gym_grade_same_gym;

ALTER TABLE videos
    ALTER COLUMN gym_id SET NOT NULL,
    ALTER COLUMN gym_grade_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_videos_gym'
          AND conrelid = 'videos'::regclass
    ) THEN
        ALTER TABLE videos
            ADD CONSTRAINT fk_videos_gym
            FOREIGN KEY (gym_id)
            REFERENCES gyms(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_videos_gym_grade_same_gym'
          AND conrelid = 'videos'::regclass
    ) THEN
        ALTER TABLE videos
            ADD CONSTRAINT fk_videos_gym_grade_same_gym
            FOREIGN KEY (gym_id, gym_grade_id)
            REFERENCES gym_grades(gym_id, id);
    END IF;
END $$;

ALTER TABLE videos
    DROP COLUMN IF EXISTS grade;

COMMIT;
