-- GymReview 도메인 통합 테스트용 gym_reviews 테이블.
-- users-schema.sql, gyms-schema.sql 다음에 실행되어야 한다 (FK 대상).
DROP TABLE IF EXISTS gym_reviews CASCADE;

CREATE TABLE gym_reviews (
    id          BIGSERIAL PRIMARY KEY,
    gym_id      BIGINT NOT NULL REFERENCES gyms(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating      SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content     TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (gym_id, user_id)
);
