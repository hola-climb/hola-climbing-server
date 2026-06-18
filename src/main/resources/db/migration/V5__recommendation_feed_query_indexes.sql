CREATE INDEX IF NOT EXISTS idx_user_blocks_blocked
    ON user_blocks(blocked_id);

CREATE INDEX IF NOT EXISTS idx_videos_public_feed_recent
    ON videos(created_at DESC, id DESC)
    WHERE is_public = TRUE AND deleted_at IS NULL;
