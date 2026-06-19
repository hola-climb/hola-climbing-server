-- Favorite 통합 테스트용 favorites 테이블.
-- users-schema.sql, gyms-schema.sql 다음에 실행되어야 한다 (FK 대상).
DROP TABLE IF EXISTS favorites CASCADE;

CREATE TABLE favorites (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    gym_id      BIGINT NOT NULL REFERENCES gyms(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, gym_id)
);
