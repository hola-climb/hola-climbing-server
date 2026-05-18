-- =====================================================================
-- Hola Climbing Log - Database Schema v2 (명세서 v0.2 기준)
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS vector;


-- =====================================================================
-- 1. USER DOMAIN (F-01)
-- =====================================================================

CREATE TABLE users (
    id                          BIGSERIAL PRIMARY KEY,
    -- 자체 로그인 (MVP 0순위)
    email                       VARCHAR(255) UNIQUE,
    password_hash               VARCHAR(255),
    email_verified              BOOLEAN NOT NULL DEFAULT FALSE,
    email_verification_token    VARCHAR(255),
    -- 소셜 로그인
    provider                    VARCHAR(20),
    provider_id                 VARCHAR(100),
    -- 프로필
    nickname                    VARCHAR(50) NOT NULL UNIQUE,
    profile_image               VARCHAR(500),
    bio                         TEXT,
    -- AI 추천
    style_embedding             vector(64),
    -- 메타
    last_login_at               TIMESTAMP,
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at                  TIMESTAMP,
    CONSTRAINT chk_user_auth CHECK (
        (email IS NOT NULL AND password_hash IS NOT NULL) OR
        (provider IS NOT NULL AND provider_id IS NOT NULL)
    ),
    UNIQUE (provider, provider_id)
);
CREATE INDEX idx_users_email           ON users(email) WHERE email IS NOT NULL;
CREATE INDEX idx_users_provider        ON users(provider, provider_id) WHERE provider IS NOT NULL;
CREATE INDEX idx_users_style_embedding ON users USING ivfflat (style_embedding vector_cosine_ops);
CREATE INDEX idx_users_deleted_at      ON users(id) WHERE deleted_at IS NULL;
COMMENT ON TABLE users IS '회원';


CREATE TABLE follows (
    id              BIGSERIAL PRIMARY KEY,
    follower_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    following_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (follower_id, following_id),
    CHECK (follower_id <> following_id)
);
CREATE INDEX idx_follows_follower  ON follows(follower_id);
CREATE INDEX idx_follows_following ON follows(following_id);


CREATE TABLE user_blocks (
    id              BIGSERIAL PRIMARY KEY,
    blocker_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason          VARCHAR(200),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);
CREATE INDEX idx_user_blocks_blocker ON user_blocks(blocker_id);


CREATE TABLE device_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token           VARCHAR(500) NOT NULL UNIQUE,
    platform        VARCHAR(20) NOT NULL,  -- 'ios' | 'android'
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_device_tokens_user ON device_tokens(user_id);


CREATE TABLE user_notification_settings (
    user_id         BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    notify_comment  BOOLEAN NOT NULL DEFAULT TRUE,
    notify_reply    BOOLEAN NOT NULL DEFAULT TRUE,
    notify_like     BOOLEAN NOT NULL DEFAULT TRUE,
    notify_follow   BOOLEAN NOT NULL DEFAULT TRUE,
    notify_chat     BOOLEAN NOT NULL DEFAULT TRUE,
    notify_system   BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);


CREATE TABLE terms_versions (
    id              BIGSERIAL PRIMARY KEY,
    type            VARCHAR(30) NOT NULL,  -- 'service' | 'privacy' | 'marketing'
    version         VARCHAR(20) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    content         TEXT NOT NULL,
    is_required     BOOLEAN NOT NULL DEFAULT TRUE,
    effective_at    TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (type, version)
);


CREATE TABLE user_term_agreements (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    term_version_id BIGINT NOT NULL REFERENCES terms_versions(id),
    agreed          BOOLEAN NOT NULL,
    agreed_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, term_version_id)
);
CREATE INDEX idx_user_term_agreements_user ON user_term_agreements(user_id);


-- =====================================================================
-- 2. GYM DOMAIN (F-04)
-- =====================================================================

CREATE TABLE gyms (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    address         VARCHAR(200),
    lat             DOUBLE PRECISION,
    lng             DOUBLE PRECISION,
    description     TEXT,
    phone           VARCHAR(30),
    website         VARCHAR(300),
    thumbnail_url   VARCHAR(500),
    business_hours  JSONB,
    region_code     VARCHAR(20),  -- 'seoul' | 'gyeonggi' | ...
    -- 평점 캐싱
    rating_avg      NUMERIC(3,2) NOT NULL DEFAULT 0,
    rating_count    INTEGER NOT NULL DEFAULT 0,
    -- AI 추천
    style_embedding vector(64),
    -- 메타
    status          VARCHAR(20) NOT NULL DEFAULT 'active',  -- 'active' | 'pending' | 'closed'
    created_by      BIGINT REFERENCES users(id),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP
);
CREATE INDEX idx_gyms_name            ON gyms(name);
CREATE INDEX idx_gyms_region          ON gyms(region_code) WHERE region_code IS NOT NULL;
CREATE INDEX idx_gyms_location        ON gyms(lat, lng) WHERE lat IS NOT NULL;
CREATE INDEX idx_gyms_status          ON gyms(status);
CREATE INDEX idx_gyms_style_embedding ON gyms USING ivfflat (style_embedding vector_cosine_ops);


CREATE TABLE gym_photos (
    id              BIGSERIAL PRIMARY KEY,
    gym_id          BIGINT NOT NULL REFERENCES gyms(id) ON DELETE CASCADE,
    uploaded_by     BIGINT REFERENCES users(id),
    gcs_path        VARCHAR(500) NOT NULL,
    caption         VARCHAR(200),
    display_order   INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_gym_photos_gym ON gym_photos(gym_id, display_order);

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
CREATE INDEX idx_gym_reviews_gym ON gym_reviews(gym_id, created_at DESC);


-- =====================================================================
-- 3. VIDEO DOMAIN (F-02)
-- =====================================================================

CREATE TABLE videos (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    gym_id              BIGINT REFERENCES gyms(id) ON DELETE SET NULL,
    title               VARCHAR(100),
    description         TEXT,
    grade               VARCHAR(20),
    -- GCS 경로
    gcs_path            VARCHAR(500) NOT NULL,
    gcs_streaming_path  VARCHAR(500),
    thumbnail_path      VARCHAR(500),
    -- 영상 메타
    duration_seconds    INTEGER,
    file_size_bytes     BIGINT,
    mime_type           VARCHAR(50),
    -- 카운터 (캐싱)
    view_count          INTEGER NOT NULL DEFAULT 0,
    like_count          INTEGER NOT NULL DEFAULT 0,
    comment_count       INTEGER NOT NULL DEFAULT 0,
    -- 상태
    status              VARCHAR(20) NOT NULL DEFAULT 'uploaded',  -- 'uploaded' | 'analyzing' | 'analyzed' | 'failed'
    is_public           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP
);
CREATE INDEX idx_videos_user        ON videos(user_id, created_at DESC);
CREATE INDEX idx_videos_gym         ON videos(gym_id);
CREATE INDEX idx_videos_status      ON videos(status);
CREATE INDEX idx_videos_public_feed ON videos(created_at DESC) WHERE is_public = TRUE AND deleted_at IS NULL;


CREATE TABLE comments (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    video_id    BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    parent_id   BIGINT REFERENCES comments(id) ON DELETE CASCADE,
    content     TEXT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMP
);
CREATE INDEX idx_comments_video  ON comments(video_id, created_at);
CREATE INDEX idx_comments_parent ON comments(parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_comments_user   ON comments(user_id);


CREATE TABLE likes (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    video_id    BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, video_id)
);
CREATE INDEX idx_likes_video ON likes(video_id);
CREATE INDEX idx_likes_user  ON likes(user_id);


-- =====================================================================
-- 4. ANALYSIS DOMAIN (F-07)
-- =====================================================================

CREATE TABLE analysis_results (
    id              BIGSERIAL PRIMARY KEY,
    video_id        BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    -- segment 정보
    sequence_index  INTEGER NOT NULL DEFAULT 0,
    start_time_ms   INTEGER,
    end_time_ms     INTEGER,
    -- 분류 결과 (6동작: highstep/flagging/heel_hook/toe_hook/lock_off/dyno/coordination)
    technique       VARCHAR(50),
    is_dynamic      BOOLEAN,
    confidence      REAL,
    -- 효율 지표 (PoC, 발표 제외이지만 컬럼은 유지)
    e_trajectory    DOUBLE PRECISION,
    e_arm           DOUBLE PRECISION,
    -- 메타
    model_version   VARCHAR(50),  -- 'rule_v1' | 'lstm_v1' | 'videomae_v1'
    raw_data        JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_analysis_video     ON analysis_results(video_id, sequence_index);
CREATE INDEX idx_analysis_technique ON analysis_results(technique) WHERE technique IS NOT NULL;


CREATE TABLE labels (
    id          BIGSERIAL PRIMARY KEY,
    video_id    BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    technique   VARCHAR(50),
    is_correct  BOOLEAN,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_labels_video ON labels(video_id);
CREATE INDEX idx_labels_user  ON labels(user_id);


-- =====================================================================
-- 5. NOTIFICATION DOMAIN (F-08)
-- =====================================================================

CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    recipient_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sender_id       BIGINT REFERENCES users(id) ON DELETE SET NULL,
    type            VARCHAR(30) NOT NULL,  -- 'comment' | 'reply' | 'like' | 'follow' | 'chat' | 'system'
    target_type     VARCHAR(30),           -- 'video' | 'comment' | 'user' | 'gym' | NULL
    target_id       BIGINT,
    title           VARCHAR(200),
    content         TEXT,
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notif_recipient_created ON notifications(recipient_id, created_at DESC);
CREATE INDEX idx_notif_recipient_unread  ON notifications(recipient_id) WHERE is_read = FALSE;


-- =====================================================================
-- 6. REPORT DOMAIN (F-09)
-- =====================================================================

CREATE TABLE reports (
    id              BIGSERIAL PRIMARY KEY,
    reporter_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_type     VARCHAR(30) NOT NULL,  -- 'video' | 'comment' | 'user'
    target_id       BIGINT NOT NULL,
    category        VARCHAR(50) NOT NULL,  -- 'obscene' | 'copyright' | 'abuse' | 'spam' | 'etc'
    reason          TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',  -- 'pending' | 'reviewed' | 'resolved' | 'rejected'
    reviewed_by     BIGINT REFERENCES users(id),
    reviewed_at     TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_reports_target ON reports(target_type, target_id);
CREATE INDEX idx_reports_status ON reports(status, created_at DESC);


-- =====================================================================
-- 7. FAVORITE DOMAIN (F-06) - 암장 즐겨찾기
-- =====================================================================

CREATE TABLE favorites (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    gym_id      BIGINT NOT NULL REFERENCES gyms(id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, gym_id)
);
CREATE INDEX idx_favorites_user ON favorites(user_id);
CREATE INDEX idx_favorites_gym  ON favorites(gym_id);


-- =====================================================================
-- 8. CHAT DOMAIN (F-04-08) - 암장별 그룹 채팅 + 한줄게시판
-- =====================================================================

CREATE TABLE gym_board_posts (
    id          BIGSERIAL PRIMARY KEY,
    gym_id      BIGINT NOT NULL REFERENCES gyms(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content     VARCHAR(200) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMP
);
CREATE INDEX idx_board_posts_gym ON gym_board_posts(gym_id, created_at DESC) WHERE deleted_at IS NULL;


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
    last_read_message_id    BIGINT,  -- FK 생략 (메시지 삭제돼도 안전)
    left_at                 TIMESTAMP,
    UNIQUE (room_id, user_id)
);
CREATE INDEX idx_chat_members_user ON chat_room_members(user_id) WHERE left_at IS NULL;
CREATE INDEX idx_chat_members_room ON chat_room_members(room_id) WHERE left_at IS NULL;


CREATE TABLE chat_messages (
    id              BIGSERIAL PRIMARY KEY,
    room_id         BIGINT NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content         TEXT NOT NULL,
    verified_at_gym BOOLEAN NOT NULL DEFAULT FALSE,  -- 작성 시 암장 300m 반경 내 여부
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP
);
CREATE INDEX idx_chat_messages_room ON chat_messages(room_id, created_at DESC);


-- =====================================================================
-- 9. STATS DOMAIN (F-03)
-- =====================================================================

CREATE TABLE user_stats (
    user_id                 BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    total_videos            INTEGER NOT NULL DEFAULT 0,
    total_climbing_seconds  BIGINT NOT NULL DEFAULT 0,
    avg_e_trajectory        DOUBLE PRECISION,
    avg_e_arm               DOUBLE PRECISION,
    -- 동작별 빈도: {"highstep": 12, "flagging": 8, ...}
    technique_counts        JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_climbed_at         TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);


-- =====================================================================
-- 10. INITIAL DATA
-- =====================================================================

INSERT INTO terms_versions (type, version, title, content, is_required, effective_at) VALUES
('service',   '1.0', '서비스 이용약관',   '서비스 이용약관 v1.0 본문...',     TRUE,  NOW()),
('privacy',   '1.0', '개인정보 처리방침', '개인정보 처리방침 v1.0 본문...',   TRUE,  NOW()),
('marketing', '1.0', '마케팅 정보 수신',  '마케팅 정보 수신 동의 v1.0 본문...', FALSE, NOW());
