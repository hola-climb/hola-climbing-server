-- Video 도메인 통합 테스트용 스키마 (videos / comments / likes).
-- users-schema.sql, gyms-schema.sql 다음에 실행되어야 한다 (FK 대상).
-- 운영 schema.sql에서 별도 인덱스를 제외한 버전.
DROP TABLE IF EXISTS likes CASCADE;
DROP TABLE IF EXISTS comments CASCADE;
DROP TABLE IF EXISTS videos CASCADE;

CREATE TABLE videos (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    gym_id              BIGINT NOT NULL,
    gym_grade_id        BIGINT NOT NULL,
    title               VARCHAR(100),
    description         TEXT,
    gcs_path            VARCHAR(500) NOT NULL,
    gcs_streaming_path  VARCHAR(500),
    thumbnail_path      VARCHAR(500),
    duration_seconds    INTEGER,
    recorded_date       DATE NOT NULL,
    file_size_bytes     BIGINT,
    mime_type           VARCHAR(50),
    view_count          INTEGER NOT NULL DEFAULT 0,
    like_count          INTEGER NOT NULL DEFAULT 0,
    comment_count       INTEGER NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'pending',
    is_public           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT fk_videos_gym
        FOREIGN KEY (gym_id) REFERENCES gyms(id),
    CONSTRAINT fk_videos_gym_grade_same_gym
        FOREIGN KEY (gym_id, gym_grade_id) REFERENCES gym_grades(gym_id, id)
);

CREATE TABLE comments (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    video_id    BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    parent_id   BIGINT REFERENCES comments(id) ON DELETE CASCADE,
    content     TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ
);

CREATE TABLE likes (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    video_id    BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, video_id)
);
