-- =====================================================================
-- Hola Climbing Log - Database Schema v2 (명세서 v0.2 기준)
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;


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
    role                        VARCHAR(20) NOT NULL DEFAULT 'USER',
    status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    -- AI 추천
    style_embedding             vector(64),
    -- 메타
    last_login_at               TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at                  TIMESTAMPTZ,
    CONSTRAINT chk_user_auth CHECK (
        (email IS NOT NULL AND password_hash IS NOT NULL) OR
        (provider IS NOT NULL AND provider_id IS NOT NULL)
    ),
    CONSTRAINT chk_user_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    UNIQUE (provider, provider_id)
);
CREATE INDEX idx_users_email           ON users(email) WHERE email IS NOT NULL;
CREATE INDEX idx_users_provider        ON users(provider, provider_id) WHERE provider IS NOT NULL;
CREATE INDEX idx_users_style_embedding ON users USING ivfflat (style_embedding vector_cosine_ops);
CREATE INDEX idx_users_deleted_at      ON users(id) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_role_status     ON users(role, status);
CREATE INDEX idx_users_status_created_at ON users(status, created_at DESC);
COMMENT ON TABLE users IS '회원';


CREATE TABLE admin_audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    admin_id    BIGINT NOT NULL REFERENCES users(id),
    action      VARCHAR(80) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id   BIGINT,
    reason      TEXT,
    before_json JSONB,
    after_json  JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_admin_audit_logs_admin_created
    ON admin_audit_logs(admin_id, created_at DESC);
CREATE INDEX idx_admin_audit_logs_target_created
    ON admin_audit_logs(target_type, target_id, created_at DESC);
COMMENT ON TABLE admin_audit_logs IS '관리자 작업 감사 로그';


CREATE TABLE follows (
    id              BIGSERIAL PRIMARY KEY,
    follower_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    following_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
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
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);
CREATE INDEX idx_user_blocks_blocker ON user_blocks(blocker_id);


CREATE TABLE device_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token           VARCHAR(500) NOT NULL UNIQUE,
    platform        VARCHAR(20) NOT NULL,  -- 'ios' | 'android'
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
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
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


CREATE TABLE terms_versions (
    id              BIGSERIAL PRIMARY KEY,
    type            VARCHAR(30) NOT NULL,  -- 'service' | 'privacy' | 'marketing' | 'location'
    version         VARCHAR(20) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    content         TEXT NOT NULL,
    is_required     BOOLEAN NOT NULL DEFAULT TRUE,
    effective_at    TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (type, version)
);


CREATE TABLE user_term_agreements (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    term_version_id BIGINT NOT NULL REFERENCES terms_versions(id),
    agreed          BOOLEAN NOT NULL,
    agreed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
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
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX idx_gyms_name            ON gyms(name);
CREATE INDEX idx_gyms_name_trgm       ON gyms USING gin (name gin_trgm_ops);
CREATE INDEX idx_gyms_region          ON gyms(region_code) WHERE region_code IS NOT NULL;
CREATE INDEX idx_gyms_location        ON gyms(lat, lng) WHERE lat IS NOT NULL;
CREATE INDEX idx_gyms_status          ON gyms(status);
CREATE INDEX idx_gyms_style_embedding ON gyms USING ivfflat (style_embedding vector_cosine_ops);


CREATE TABLE gym_grades (
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
CREATE INDEX idx_gym_grades_gym_order
    ON gym_grades(gym_id, difficulty_order, id)
    WHERE is_active = TRUE;


CREATE TABLE gym_reviews (
    id          BIGSERIAL PRIMARY KEY,
    gym_id      BIGINT NOT NULL REFERENCES gyms(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating      SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (gym_id, user_id)
);
CREATE INDEX idx_gym_reviews_gym ON gym_reviews(gym_id, created_at DESC);


-- =====================================================================
-- 3. VIDEO DOMAIN (F-02)
-- =====================================================================

CREATE TABLE videos (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    gym_id              BIGINT NOT NULL,
    gym_grade_id        BIGINT NOT NULL,
    title               VARCHAR(100),
    description         TEXT,
    -- GCS 경로
    gcs_path            VARCHAR(500) NOT NULL,
    gcs_streaming_path  VARCHAR(500),
    thumbnail_path      VARCHAR(500),
    -- 영상 메타
    duration_seconds    INTEGER,
    recorded_date       DATE NOT NULL,
    file_size_bytes     BIGINT,
    mime_type           VARCHAR(50),
    -- 카운터 (캐싱)
    view_count          INTEGER NOT NULL DEFAULT 0,
    like_count          INTEGER NOT NULL DEFAULT 0,
    comment_count       INTEGER NOT NULL DEFAULT 0,
    -- 상태
    status              VARCHAR(20) NOT NULL DEFAULT 'pending',  -- 'pending' | 'done' | 'failed'
    is_public           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT fk_videos_gym
        FOREIGN KEY (gym_id) REFERENCES gyms(id),
    CONSTRAINT fk_videos_gym_grade_same_gym
        FOREIGN KEY (gym_id, gym_grade_id) REFERENCES gym_grades(gym_id, id)
);
CREATE INDEX idx_videos_user        ON videos(user_id, recorded_date DESC, id DESC);
CREATE INDEX idx_videos_gym         ON videos(gym_id);
CREATE INDEX idx_videos_status      ON videos(status);
-- 커서 피드는 recorded_date DESC, id DESC keyset로 페이징한다.
CREATE INDEX idx_videos_public_feed ON videos(recorded_date DESC, id DESC) WHERE is_public = TRUE AND deleted_at IS NULL;


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
CREATE INDEX idx_comments_video  ON comments(video_id, created_at);
CREATE INDEX idx_comments_parent ON comments(parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_comments_user   ON comments(user_id);


CREATE TABLE likes (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    video_id    BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
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
    -- 메타
    model_version   VARCHAR(50),  -- 'rule_v1' | 'lstm_v1' | 'videomae_v1'
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_analysis_video     ON analysis_results(video_id, sequence_index);
CREATE INDEX idx_analysis_technique ON analysis_results(technique) WHERE technique IS NOT NULL;


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
    corrected_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_analysis_video_dynamic_probability
        CHECK (ai_dynamic_probability IS NULL OR (ai_dynamic_probability >= 0 AND ai_dynamic_probability <= 1))
);
CREATE INDEX idx_analysis_video_results_model_feedback
    ON analysis_video_results(model_version, feedback_applied);
CREATE INDEX idx_analysis_video_results_corrected_by
    ON analysis_video_results(corrected_by)
    WHERE corrected_by IS NOT NULL;


CREATE TABLE labels (
    id          BIGSERIAL PRIMARY KEY,
    video_id    BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    technique   VARCHAR(50),
    is_correct  BOOLEAN,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
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
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
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
    reviewed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- 동일 신고자가 같은 대상을 중복 신고 불가 (서비스의 existsByReporterAndTarget 검사와 race가 나도 DB에서 차단).
    UNIQUE (reporter_id, target_type, target_id)
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
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
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
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ
);
CREATE INDEX idx_board_posts_gym ON gym_board_posts(gym_id, created_at DESC) WHERE deleted_at IS NULL;


CREATE TABLE chat_rooms (
    id          BIGSERIAL PRIMARY KEY,
    gym_id      BIGINT NOT NULL UNIQUE REFERENCES gyms(id) ON DELETE CASCADE,
    name        VARCHAR(100),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


CREATE TABLE chat_room_members (
    id                      BIGSERIAL PRIMARY KEY,
    room_id                 BIGINT NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id                 BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_read_message_id    BIGINT,  -- FK 생략 (메시지 삭제돼도 안전)
    left_at                 TIMESTAMPTZ,
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
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX idx_chat_messages_room ON chat_messages(room_id, created_at DESC);


-- =====================================================================
-- 9. STATS DOMAIN (F-03)
-- =====================================================================

CREATE TABLE user_stats (
    user_id                 BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    total_videos            INTEGER NOT NULL DEFAULT 0,
    total_climbing_seconds  BIGINT NOT NULL DEFAULT 0,
    -- 동작별 빈도: {"highstep": 12, "flagging": 8, ...}
    technique_counts        JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_climbed_at         TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 사용자가 직접 기록하는 클라이밍 로그 (달력 기능의 원천 데이터, F-03-03).
-- climbed_on 기준 날짜는 Asia/Seoul(KST) 로컬 날짜로 클라이언트가 전달한다.
CREATE TABLE climbing_logs (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    gym_id        BIGINT NOT NULL REFERENCES gyms(id) ON DELETE CASCADE,
    climbed_on    DATE NOT NULL,
    -- 난이도별 푼 문제 수: {"빨강": 3, "파랑": 5, ...}
    grade_counts  JSONB NOT NULL DEFAULT '{}'::jsonb,
    memo          TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ
);
CREATE INDEX idx_climbing_logs_user_date ON climbing_logs(user_id, climbed_on)
    WHERE deleted_at IS NULL;


-- =====================================================================
-- 10. INITIAL DATA
-- =====================================================================

INSERT INTO terms_versions (type, version, title, content, is_required, effective_at) VALUES
('service', '1.0', '서비스 이용약관', $terms$
제1조 목적
이 약관은 Hola Climbing이 제공하는 클라이밍 영상 공유, 암장 정보, 채팅, 댓글, 통계, AI 동작 분석 및 관련 서비스의 이용 조건과 절차, 회원과 운영자의 권리와 의무를 정합니다.

제2조 서비스의 내용
서비스는 회원가입과 로그인, 프로필 관리, 영상 업로드와 피드, 댓글과 좋아요, 암장 검색과 리뷰, 암장별 채팅, 클라이밍 기록과 통계, AI 기반 동작 분석 결과 조회 기능을 제공합니다. 운영자는 서비스 개선, 보안, 운영상 필요에 따라 기능을 변경하거나 중단할 수 있으며 중요한 변경은 합리적인 방법으로 안내합니다.

제3조 회원의 의무
회원은 정확한 정보를 제공해야 하며 타인의 계정, 영상, 위치, 기기 정보를 도용해서는 안 됩니다. 불법 콘텐츠, 타인의 권리 침해, 욕설과 혐오 표현, 스팸, 허위 위치 인증, 서비스 장애를 유발하는 행위는 금지됩니다.

제4조 게시물과 이용 제한
회원이 등록한 영상, 댓글, 리뷰, 채팅 메시지의 권리는 회원에게 있습니다. 다만 서비스 운영, 노출, 공유, 신고 처리, 분석 결과 제공을 위해 필요한 범위에서 운영자가 이를 저장, 표시, 변환, 전송할 수 있습니다. 운영자는 약관 위반 또는 신고가 접수된 콘텐츠를 숨김, 삭제하거나 회원 이용을 제한할 수 있습니다.

제5조 AI 분석과 책임 범위
AI 분석 결과와 통계는 운동 자세 개선을 돕는 참고 정보이며 의학적 진단, 부상 예방 보장, 전문 코칭을 대체하지 않습니다. 회원은 자신의 신체 상태와 주변 환경을 고려해 안전하게 운동해야 합니다.

제6조 탈퇴와 분쟁
회원은 언제든지 탈퇴할 수 있으며 탈퇴 후 법령상 보관이 필요한 정보와 부정 이용 방지에 필요한 최소 정보를 제외하고 관련 정보는 처리방침에 따라 삭제됩니다. 서비스 이용과 관련한 분쟁에는 대한민국 법령을 적용합니다.
$terms$, TRUE, TIMESTAMPTZ '2026-01-01 00:00:00'),
('privacy', '1.0', '개인정보 처리방침', $terms$
1. 수집하는 개인정보
Hola Climbing은 회원가입과 서비스 제공을 위해 이메일, 닉네임, 비밀번호 해시, 프로필 이미지, 회원 상태, 약관 동의 내역을 처리합니다. 서비스 이용 과정에서 영상, 썸네일, 댓글, 리뷰, 채팅 메시지, 클라이밍 기록, 신고 내용, 알림 설정, 기기 토큰, 접속 로그, 요청 식별자, IP, User-Agent가 생성될 수 있습니다. 위치 기반 기능을 사용할 때는 위도, 경도, 요청 시각, 암장과의 거리 또는 위치 인증 결과가 처리될 수 있습니다.

2. 이용 목적
개인정보는 회원 식별과 인증, 이메일 인증과 비밀번호 재설정, 콘텐츠 제공, 암장 위치 인증, 주변 암장 조회, 댓글과 채팅, 알림 발송, AI 분석 요청과 결과 제공, 신고와 부정 이용 방지, 서비스 보안과 장애 대응, 통계 산출을 위해 사용합니다.

3. 보유 기간
회원 정보는 회원 탈퇴 시까지 보관하며, 관계 법령 준수, 분쟁 대응, 부정 이용 방지를 위해 필요한 정보는 목적 달성 후 최소 범위로 별도 보관할 수 있습니다. 영상, 댓글, 채팅, 리뷰, 신고, 로그 데이터는 서비스 운영 정책과 법령상 보존 필요에 따라 보관 또는 삭제됩니다.

4. 제3자 제공과 처리 위탁
운영자는 원칙적으로 회원의 개인정보를 동의 없이 제3자에게 제공하지 않습니다. 다만 법령에 근거가 있거나 수사기관의 적법한 요청이 있는 경우 예외가 있을 수 있습니다. 서비스 운영을 위해 Google Cloud Platform, Google Cloud Storage, Firebase Cloud Messaging, 이메일 발송 인프라 등 외부 인프라를 사용할 수 있으며, 필요한 범위에서 개인정보 처리가 위탁될 수 있습니다.

5. 이용자의 권리
회원은 자신의 개인정보에 대해 열람, 정정, 삭제, 처리정지, 동의 철회를 요청할 수 있습니다. 앱 내 기능 또는 운영자가 고지한 문의 채널을 통해 요청할 수 있으며, 운영자는 본인 확인 후 법령이 정한 범위에서 처리합니다.

6. 안전성 확보 조치
운영자는 비밀번호 단방향 암호화, 접근 권한 관리, 전송 구간 보호, 로그와 모니터링, 불필요한 개인정보 최소화 등 합리적인 보호 조치를 적용합니다.
$terms$, TRUE, TIMESTAMPTZ '2026-01-01 00:00:00'),
('marketing', '1.0', '마케팅 정보 수신 동의', $terms$
Hola Climbing은 이벤트, 신규 기능, 서비스 업데이트, 프로모션, 설문, 혜택 안내를 위해 이메일 또는 앱 푸시 알림을 발송할 수 있습니다.

수집 및 이용 항목은 이메일, 닉네임, 기기 토큰, 알림 수신 설정, 서비스 이용 이력 중 안내 대상 선별에 필요한 최소 정보입니다. 보유 및 이용 기간은 동의 철회 또는 회원 탈퇴 시까지입니다.

마케팅 정보 수신 동의는 선택 사항이며, 동의하지 않아도 회원가입과 기본 서비스 이용에는 제한이 없습니다. 회원은 앱의 알림 설정, 기기 설정 또는 운영자가 제공하는 방법을 통해 언제든지 수신 동의를 철회할 수 있습니다.
$terms$, FALSE, TIMESTAMPTZ '2026-01-01 00:00:00'),
('location', '1.0', '위치기반서비스 이용약관', $terms$
제1조 목적
이 약관은 Hola Climbing이 위치정보를 활용해 제공하는 주변 암장 조회, 암장 기반 채팅, GPS 기반 암장 인증, 위치 인증 메시지 표시 등 위치기반서비스의 이용 조건을 정합니다.

제2조 위치정보의 이용
서비스는 회원의 현재 위치를 사용해 가까운 암장을 조회하거나, 특정 암장 반경 내에 있는지 확인해 채팅 메시지에 암장 인증 상태를 표시할 수 있습니다. 위치정보는 요청한 기능을 수행하기 위한 범위에서만 사용하며, 원칙적으로 원본 위도와 경도를 장기 저장하지 않습니다. 다만 부정 이용 방지, 장애 대응, 분쟁 처리를 위해 위치 인증 결과와 요청 로그가 일정 기간 보관될 수 있습니다.

제3조 회원의 권리
회원은 단말기 운영체제 또는 앱 설정에서 위치 권한을 거부하거나 철회할 수 있습니다. 위치 권한을 거부하면 주변 암장 조회, 암장 인증, 위치 기반 채팅 등 일부 기능 이용이 제한될 수 있으나 기본 회원 기능은 이용할 수 있습니다.

제4조 금지행위와 책임
회원은 GPS 조작, 허위 위치 전송, 타인의 위치정보 도용 등 위치기반서비스의 신뢰를 해치는 행위를 해서는 안 됩니다. 운영자는 허위 위치 인증이 의심되는 경우 해당 기능 이용을 제한하거나 관련 콘텐츠를 조치할 수 있습니다.
$terms$, FALSE, TIMESTAMPTZ '2026-01-01 00:00:00');
