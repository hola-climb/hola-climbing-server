-- Chat 도메인 통합 테스트용 스키마 (chat_rooms / chat_room_members / chat_messages).
-- users-schema.sql, gyms-schema.sql 다음에 실행되어야 한다 (FK 대상).
-- 운영 schema.sql에서 별도 인덱스를 제외한 버전.
DROP TABLE IF EXISTS chat_messages CASCADE;
DROP TABLE IF EXISTS chat_room_members CASCADE;
DROP TABLE IF EXISTS chat_rooms CASCADE;

CREATE TABLE chat_rooms (
    id          BIGSERIAL PRIMARY KEY,
    gym_id      BIGINT NOT NULL UNIQUE REFERENCES gyms(id) ON DELETE CASCADE,
    name        VARCHAR(100),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE chat_room_members (
    id                      BIGSERIAL PRIMARY KEY,
    room_id                 BIGINT NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id                 BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    last_read_message_id    BIGINT,
    left_at                 TIMESTAMP,
    UNIQUE (room_id, user_id)
);

CREATE TABLE chat_messages (
    id          BIGSERIAL PRIMARY KEY,
    room_id     BIGINT NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content     TEXT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMP
);
