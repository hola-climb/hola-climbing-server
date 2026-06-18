\set ON_ERROR_STOP on

EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
WITH viewer AS (
    SELECT style_embedding
    FROM users
    WHERE id = :viewer_id
),
blocked_users AS (
    SELECT ub.blocked_id AS user_id
    FROM user_blocks ub
    WHERE ub.blocker_id = :viewer_id
    UNION
    SELECT ub.blocker_id AS user_id
    FROM user_blocks ub
    WHERE ub.blocked_id = :viewer_id
),
feed AS (
    SELECT v.id, v.user_id, v.gym_id, v.gym_grade_id,
           g.name AS gym_name,
           gg.label AS gym_grade_label,
           gg.difficulty_order AS gym_grade_difficulty_order,
           v.title, v.description,
           v.gcs_path, v.gcs_streaming_path, v.thumbnail_path,
           v.duration_seconds, v.recorded_date, v.file_size_bytes, v.mime_type,
           v.view_count, v.like_count, v.comment_count, v.status, v.is_public,
           v.created_at, v.updated_at, v.deleted_at,
           CASE WHEN f.id IS NOT NULL THEN 1 ELSE 0 END AS following_rank,
           CASE
               WHEN viewer.style_embedding IS NOT NULL AND g.style_embedding IS NOT NULL
               THEN (viewer.style_embedding <=> g.style_embedding)
                    - CASE WHEN f.id IS NOT NULL THEN 0.25 ELSE 0 END
               ELSE NULL
           END AS ranking_distance
    FROM videos v
    JOIN gyms g ON g.id = v.gym_id
    JOIN gym_grades gg ON gg.id = v.gym_grade_id AND gg.gym_id = v.gym_id
    CROSS JOIN viewer
    LEFT JOIN follows f ON f.following_id = v.user_id AND f.follower_id = :viewer_id
    WHERE v.is_public = TRUE AND v.deleted_at IS NULL
      AND v.user_id <> :viewer_id
      AND NOT EXISTS (
          SELECT 1
          FROM blocked_users bu
          WHERE bu.user_id = v.user_id
      )
),
ranked AS (
    SELECT *,
           CASE WHEN ranking_distance IS NULL THEN 1 ELSE 0 END AS distance_null_rank
    FROM feed
)
SELECT id, user_id, gym_id, gym_grade_id,
       gym_name, gym_grade_label, gym_grade_difficulty_order,
       title, description,
       gcs_path, gcs_streaming_path, thumbnail_path,
       duration_seconds, recorded_date, file_size_bytes, mime_type,
       view_count, like_count, comment_count, status, is_public,
       distance_null_rank, ranking_distance, following_rank,
       created_at, updated_at, deleted_at
FROM ranked
ORDER BY distance_null_rank ASC, ranking_distance ASC,
         following_rank DESC, created_at DESC, id DESC
LIMIT :page_size;
