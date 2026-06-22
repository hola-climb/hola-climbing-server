DROP INDEX IF EXISTS idx_videos_public_feed;
DROP INDEX IF EXISTS idx_videos_public_feed_recent;
DROP INDEX IF EXISTS idx_videos_user;

CREATE INDEX idx_videos_user
    ON videos(user_id, recorded_date DESC, id DESC);

CREATE INDEX idx_videos_public_feed
    ON videos(recorded_date DESC, id DESC)
    WHERE is_public = TRUE AND deleted_at IS NULL;
