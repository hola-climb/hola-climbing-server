# Evidence-First Recommendation Feed Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first runnable performance-test slice: deterministic recommendation-feed seed data, k6 load test, SQL execution-plan report, evidence directories, screenshot checklist, and local baseline report template.

**Architecture:** Keep the first implementation focused on `GET /api/recommendations/videos`. Use PostgreSQL SQL scripts for deterministic seed/reporting, k6 for API load, shell scripts for repeatable local commands, and Markdown report templates for evidence-first before/after documentation. This plan does not optimize the production query yet; it creates the measuring rig and local baseline path.

**Tech Stack:** Spring Boot 4, PostgreSQL + pgvector, Redis for auth blacklist at runtime, k6, psql, shell scripts, Markdown reports.

---

## Scope Check

The design also covers video detail fan-out, like/comment concurrency, and the AI worker pipeline. Those are separate flows and should get separate execution plans after the recommendation-feed measuring rig works. This plan implements only:

- evidence directory and screenshot naming rules
- recommendation-feed perf seed SQL
- recommendation-feed SQL report scripts
- recommendation-feed k6 scenario
- recommendation-feed report template
- evidence validation script
- local baseline run path

## File Structure

Create these files:

- `perf/README.md` - operator guide for local recommendation-feed performance runs.
- `perf/scripts/ensure_evidence_dirs.sh` - creates evidence directories with `.gitkeep`.
- `perf/scripts/report_recommendation_sql.sh` - captures row counts, relation sizes, index list, and execution plans.
- `perf/scripts/validate_recommendation_evidence.sh` - checks raw files and screenshots exist before a report is treated as complete.
- `perf/sql/seed_recommendation_perf.sql` - deterministic `hola_perf` seed data for users, gyms, videos, follows, blocks, likes, comments, and analysis video results.
- `perf/sql/recommendation_feed_counts.sql` - row-count and relation-size report.
- `perf/sql/recommendation_feed_explain_text.sql` - readable `EXPLAIN ANALYZE` for screenshots.
- `perf/sql/recommendation_feed_explain_json.sql` - JSON `EXPLAIN ANALYZE` for raw evidence.
- `perf/k6/recommendation-feed.js` - k6 scenario for recommendation feed first-page and cursor-follow load.
- `docs/performance/recommendation-feed-report.md` - before/after report template with screenshot links.

Modify these files:

- `.gitignore` - keep generated `.gitkeep` files while ignoring large raw result files only if they become too large. For this first slice, do not ignore `perf/results` so evidence can be intentionally committed.

## Task 1: Evidence Directory Skeleton

**Files:**
- Create: `perf/README.md`
- Create: `perf/scripts/ensure_evidence_dirs.sh`
- Create directories through script: `perf/results/recommendation-feed/before/screenshots`, `perf/results/recommendation-feed/after/screenshots`

- [ ] **Step 1: Create the evidence directory script**

Create `perf/scripts/ensure_evidence_dirs.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

paths=(
  "perf/results/recommendation-feed/before/screenshots"
  "perf/results/recommendation-feed/after/screenshots"
  "perf/results/recommendation-feed/local-baseline/screenshots"
)

for path in "${paths[@]}"; do
  mkdir -p "${ROOT_DIR}/${path}"
  touch "${ROOT_DIR}/${path}/.gitkeep"
done

touch "${ROOT_DIR}/perf/results/recommendation-feed/.gitkeep"

echo "Evidence directories are ready under ${ROOT_DIR}/perf/results/recommendation-feed"
```

- [ ] **Step 2: Make the script executable**

Run:

```bash
chmod +x perf/scripts/ensure_evidence_dirs.sh
```

Expected: no output and executable bit is set.

- [ ] **Step 3: Run the script**

Run:

```bash
./perf/scripts/ensure_evidence_dirs.sh
```

Expected output:

```text
Evidence directories are ready under /Users/minjoun/Workspace/projects/Hola-Climbing/hola-climbing-server/perf/results/recommendation-feed
```

- [ ] **Step 4: Create the perf README**

Create `perf/README.md`:

````markdown
# Hola Performance Test Assets

This directory contains repeatable performance-test assets for Hola.

## First Slice

The first implemented flow is the recommendation feed:

```text
GET /api/recommendations/videos?size=20
```

The purpose is to capture before/after evidence for portfolio-quality performance work:

- deterministic seed data
- k6 latency and error-rate results
- PostgreSQL `EXPLAIN ANALYZE` reports
- Grafana and Cloud Run screenshots
- code-state evidence

## Evidence Rule

A performance claim is incomplete unless it has both raw output and screenshots.

For recommendation feed, use:

```text
perf/results/recommendation-feed/before/
perf/results/recommendation-feed/after/
perf/results/recommendation-feed/local-baseline/
```

Each run directory should contain:

```text
k6-summary.json
k6-summary.txt
row-counts-and-sizes.txt
recommendation-feed-explain.txt
recommendation-feed-explain.json
code-state.txt
screenshots/
```

Screenshot names use this pattern:

```text
01-before-recommendation-feed-k6-summary.png
02-before-recommendation-feed-sql-plan.png
03-before-recommendation-feed-grafana-http-latency.png
```
````

- [ ] **Step 5: Verify tracked files**

Run:

```bash
git status --short
```

Expected output includes:

```text
?? perf/README.md
?? perf/scripts/ensure_evidence_dirs.sh
?? perf/results/
```

- [ ] **Step 6: Commit the evidence skeleton**

Run:

```bash
git add perf/README.md perf/scripts/ensure_evidence_dirs.sh perf/results
git commit -m "test(perf): add recommendation evidence skeleton"
```

Expected: commit succeeds with only `perf` evidence skeleton files.

## Task 2: Deterministic Recommendation Seed SQL

**Files:**
- Create: `perf/sql/seed_recommendation_perf.sql`

- [ ] **Step 1: Create the seed SQL**

Create `perf/sql/seed_recommendation_perf.sql`:

```sql
\set ON_ERROR_STOP on

DO $$
BEGIN
    IF current_database() <> 'hola_perf' THEN
        RAISE EXCEPTION 'Refusing to seed database %. Use hola_perf only.', current_database();
    END IF;
END
$$;

CREATE OR REPLACE FUNCTION perf_vec(seed bigint)
RETURNS vector(64)
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT ('[' || string_agg(
        (((seed * 1103515245 + i * 12345) % 1000)::numeric / 1000)::text,
        ',' ORDER BY i
    ) || ']')::vector(64)
    FROM generate_series(1, 64) AS i;
$$;

TRUNCATE TABLE
    notifications,
    labels,
    analysis_video_results,
    analysis_results,
    likes,
    comments,
    videos,
    gym_reviews,
    gym_photos,
    gym_grades,
    gyms,
    user_blocks,
    follows,
    user_notification_settings,
    device_tokens,
    user_term_agreements,
    users
RESTART IDENTITY CASCADE;

INSERT INTO users (
    id,
    email,
    password_hash,
    email_verified,
    nickname,
    role,
    status,
    style_embedding,
    created_at,
    updated_at
)
SELECT
    id,
    format('perf_user_%s@hola.test', lpad(id::text, 5, '0')),
    '$2a$10$bllcflF6BnpYq2vkWimEUO.jLCQsTiBu.OhfuOLRQ8CE4Ko8pYlfu',
    true,
    format('perf_user_%s', lpad(id::text, 5, '0')),
    'USER',
    'ACTIVE',
    CASE WHEN id % 5 = 0 THEN NULL ELSE perf_vec(id) END,
    now() - ((10000 - id) || ' seconds')::interval,
    now()
FROM generate_series(1, 10000) AS id;

INSERT INTO gyms (
    id,
    name,
    address,
    lat,
    lng,
    description,
    region_code,
    rating_avg,
    rating_count,
    status,
    style_embedding,
    created_at,
    updated_at
)
SELECT
    id,
    format('Perf Gym %s', lpad(id::text, 4, '0')),
    format('서울 성능구 %s번길', id),
    37.45 + (id % 100)::double precision / 1000,
    127.00 + (id % 100)::double precision / 1000,
    'performance seed gym',
    CASE WHEN id % 3 = 0 THEN 'seoul' WHEN id % 3 = 1 THEN 'gyeonggi' ELSE 'incheon' END,
    round((3.5 + (id % 15)::numeric / 10)::numeric, 2),
    10 + (id % 500),
    'active',
    CASE WHEN id % 4 = 0 THEN NULL ELSE perf_vec(id + 20000) END,
    now() - ((1000 - id) || ' minutes')::interval,
    now()
FROM generate_series(1, 1000) AS id;

INSERT INTO gym_grades (
    id,
    gym_id,
    label,
    difficulty_order,
    is_active,
    created_at,
    updated_at
)
SELECT
    (gym_id - 1) * 3 + grade_no,
    gym_id,
    CASE grade_no WHEN 1 THEN 'V0' WHEN 2 THEN 'V1' ELSE 'V2' END,
    grade_no * 10,
    true,
    now(),
    now()
FROM generate_series(1, 1000) AS gym_id
CROSS JOIN generate_series(1, 3) AS grade_no;

INSERT INTO videos (
    id,
    user_id,
    gym_id,
    gym_grade_id,
    title,
    description,
    gcs_path,
    gcs_streaming_path,
    thumbnail_path,
    duration_seconds,
    recorded_date,
    file_size_bytes,
    mime_type,
    view_count,
    like_count,
    comment_count,
    status,
    is_public,
    created_at,
    updated_at
)
SELECT
    id,
    ((id * 17) % 10000) + 1 AS user_id,
    ((id * 13) % 1000) + 1 AS gym_id,
    ((((id * 13) % 1000)) * 3) + ((id % 3) + 1) AS gym_grade_id,
    format('Perf climbing clip %s', id),
    'performance seed video',
    format('videos/perf/%s.mp4', id),
    format('videos/perf/%s-stream.mp4', id),
    format('videos/perf/%s-thumb.jpg', id),
    20 + (id % 40),
    current_date - ((id % 365)::int),
    2000000 + (id % 50000000),
    'video/mp4',
    id % 1000,
    0,
    0,
    'done',
    true,
    now() - ((100000 - id) || ' seconds')::interval,
    now()
FROM generate_series(1, 100000) AS id;

INSERT INTO follows (follower_id, following_id, created_at)
SELECT
    follower_id,
    following_id,
    now() - ((follower_id + step_no) || ' seconds')::interval
FROM (
    SELECT
        follower_id,
        ((follower_id + step_no * 97 - 1) % 10000) + 1 AS following_id,
        step_no
    FROM generate_series(1, 10000) AS follower_id
    CROSS JOIN generate_series(1, 30) AS step_no
) AS candidates
WHERE follower_id <> following_id
ON CONFLICT DO NOTHING;

INSERT INTO user_blocks (blocker_id, blocked_id, reason, created_at)
SELECT
    blocker_id,
    blocked_id,
    'perf block graph',
    now()
FROM (
    SELECT
        blocker_id,
        ((blocker_id + step_no * 131 - 1) % 10000) + 1 AS blocked_id
    FROM generate_series(50, 10000, 50) AS blocker_id
    CROSS JOIN generate_series(1, 5) AS step_no
) AS candidates
WHERE blocker_id <> blocked_id
ON CONFLICT DO NOTHING;

INSERT INTO likes (user_id, video_id, created_at)
SELECT
    ((id * 19) % 10000) + 1,
    id,
    now() - ((id % 5000) || ' seconds')::interval
FROM generate_series(1, 20000) AS id
ON CONFLICT DO NOTHING;

INSERT INTO comments (user_id, video_id, content, created_at, updated_at)
SELECT
    ((id * 23) % 10000) + 1,
    ((id * 29) % 100000) + 1,
    format('perf comment %s', id),
    now() - ((id % 3000) || ' seconds')::interval,
    now()
FROM generate_series(1, 10000) AS id;

INSERT INTO analysis_video_results (
    video_id,
    model_version,
    ai_techniques,
    ai_is_dynamic,
    ai_dynamic_probability,
    final_techniques,
    final_is_dynamic,
    feedback_applied,
    created_at,
    updated_at
)
SELECT
    id,
    'rule_v3',
    '["highstep","flagging"]'::jsonb,
    id % 2 = 0,
    CASE WHEN id % 2 = 0 THEN 0.72 ELSE 0.28 END,
    '["highstep","flagging"]'::jsonb,
    id % 2 = 0,
    false,
    now(),
    now()
FROM generate_series(1, 5000) AS id;

UPDATE videos v
SET like_count = counts.like_count
FROM (
    SELECT video_id, count(*)::int AS like_count
    FROM likes
    GROUP BY video_id
) AS counts
WHERE counts.video_id = v.id;

UPDATE videos v
SET comment_count = counts.comment_count
FROM (
    SELECT video_id, count(*)::int AS comment_count
    FROM comments
    GROUP BY video_id
) AS counts
WHERE counts.video_id = v.id;

SELECT setval(pg_get_serial_sequence('users', 'id'), (SELECT max(id) FROM users));
SELECT setval(pg_get_serial_sequence('gyms', 'id'), (SELECT max(id) FROM gyms));
SELECT setval(pg_get_serial_sequence('gym_grades', 'id'), (SELECT max(id) FROM gym_grades));
SELECT setval(pg_get_serial_sequence('videos', 'id'), (SELECT max(id) FROM videos));
SELECT setval(pg_get_serial_sequence('follows', 'id'), (SELECT max(id) FROM follows));
SELECT setval(pg_get_serial_sequence('user_blocks', 'id'), (SELECT max(id) FROM user_blocks));
SELECT setval(pg_get_serial_sequence('likes', 'id'), (SELECT max(id) FROM likes));
SELECT setval(pg_get_serial_sequence('comments', 'id'), (SELECT max(id) FROM comments));

ANALYZE;

SELECT
    (SELECT count(*) FROM users) AS users,
    (SELECT count(*) FROM gyms) AS gyms,
    (SELECT count(*) FROM videos) AS videos,
    (SELECT count(*) FROM follows) AS follows,
    (SELECT count(*) FROM user_blocks) AS user_blocks,
    (SELECT count(*) FROM likes) AS likes,
    (SELECT count(*) FROM comments) AS comments,
    (SELECT count(*) FROM analysis_video_results) AS analysis_video_results;
```

- [ ] **Step 2: Run seed against a local perf database**

Run:

```bash
createdb hola_perf
psql postgresql://hola:hola@127.0.0.1:5432/hola_perf -f src/main/resources/db/migration/V1__init.sql
psql postgresql://hola:hola@127.0.0.1:5432/hola_perf -f src/main/resources/db/migration/V2__video_analysis_results.sql
psql postgresql://hola:hola@127.0.0.1:5432/hola_perf -f src/main/resources/db/migration/V3__drop_unused_efficiency_columns.sql
psql postgresql://hola:hola@127.0.0.1:5432/hola_perf -f src/main/resources/db/migration/V4__gym_profile_image.sql
psql postgresql://hola:hola@127.0.0.1:5432/hola_perf -f perf/sql/seed_recommendation_perf.sql
```

Expected final row count summary:

```text
 users | gyms | videos | follows | user_blocks | likes | comments | analysis_video_results
-------+------+--------+---------+-------------+-------+----------+-----------------------
 10000 | 1000 | 100000 | 300000  | 1000        | 20000 | 10000    | 5000
```

`follows` can be slightly below 300000 only if deterministic self-follows were removed; the current formula avoids self-follows for the selected step values.

- [ ] **Step 3: Verify seeded login works**

Start the Spring app against `hola_perf`, then run:

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"perf_user_00001@hola.test","password":"Password1!"}'
```

Expected: JSON response has `isSuccess: true` and `data.accessToken`.

- [ ] **Step 4: Commit seed SQL**

Run:

```bash
git add perf/sql/seed_recommendation_perf.sql
git commit -m "test(perf): add recommendation feed seed data"
```

Expected: commit succeeds with only the seed SQL file.

## Task 3: SQL Report Scripts

**Files:**
- Create: `perf/sql/recommendation_feed_counts.sql`
- Create: `perf/sql/recommendation_feed_explain_text.sql`
- Create: `perf/sql/recommendation_feed_explain_json.sql`
- Create: `perf/scripts/report_recommendation_sql.sh`

- [ ] **Step 1: Create row count and size SQL**

Create `perf/sql/recommendation_feed_counts.sql`:

```sql
\set ON_ERROR_STOP on

SELECT current_database() AS database_name, now() AS captured_at;

SELECT 'users' AS relation, count(*) AS rows FROM users
UNION ALL SELECT 'gyms', count(*) FROM gyms
UNION ALL SELECT 'gym_grades', count(*) FROM gym_grades
UNION ALL SELECT 'videos', count(*) FROM videos
UNION ALL SELECT 'follows', count(*) FROM follows
UNION ALL SELECT 'user_blocks', count(*) FROM user_blocks
UNION ALL SELECT 'likes', count(*) FROM likes
UNION ALL SELECT 'comments', count(*) FROM comments
UNION ALL SELECT 'analysis_video_results', count(*) FROM analysis_video_results
ORDER BY relation;

SELECT
    schemaname,
    relname,
    pg_size_pretty(pg_total_relation_size(format('%I.%I', schemaname, relname)::regclass)) AS total_size,
    pg_size_pretty(pg_relation_size(format('%I.%I', schemaname, relname)::regclass)) AS table_size,
    pg_size_pretty(pg_indexes_size(format('%I.%I', schemaname, relname)::regclass)) AS indexes_size
FROM pg_stat_user_tables
WHERE relname IN ('users', 'gyms', 'gym_grades', 'videos', 'follows', 'user_blocks', 'likes', 'comments', 'analysis_video_results')
ORDER BY pg_total_relation_size(format('%I.%I', schemaname, relname)::regclass) DESC;

SELECT
    tablename,
    indexname,
    indexdef
FROM pg_indexes
WHERE tablename IN ('users', 'gyms', 'gym_grades', 'videos', 'follows', 'user_blocks')
ORDER BY tablename, indexname;
```

- [ ] **Step 2: Create readable EXPLAIN SQL**

Create `perf/sql/recommendation_feed_explain_text.sql`:

```sql
\set ON_ERROR_STOP on

EXPLAIN (ANALYZE, BUFFERS)
WITH viewer AS (
    SELECT style_embedding
    FROM users
    WHERE id = :viewer_id
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
          FROM user_blocks ub
          WHERE (ub.blocker_id = :viewer_id AND ub.blocked_id = v.user_id)
             OR (ub.blocker_id = v.user_id AND ub.blocked_id = :viewer_id)
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
```

- [ ] **Step 3: Create JSON EXPLAIN SQL**

Create `perf/sql/recommendation_feed_explain_json.sql` with the same query and JSON format:

```sql
\set ON_ERROR_STOP on

EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
WITH viewer AS (
    SELECT style_embedding
    FROM users
    WHERE id = :viewer_id
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
          FROM user_blocks ub
          WHERE (ub.blocker_id = :viewer_id AND ub.blocked_id = v.user_id)
             OR (ub.blocker_id = v.user_id AND ub.blocked_id = :viewer_id)
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
```

- [ ] **Step 4: Create SQL report shell script**

Create `perf/scripts/report_recommendation_sql.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

DATABASE_URL="${DATABASE_URL:-postgresql://hola:hola@127.0.0.1:5432/hola_perf}"
RUN_LABEL="${RUN_LABEL:-local-baseline}"
VIEWER_ID="${VIEWER_ID:-1}"
PAGE_SIZE="${PAGE_SIZE:-20}"
OUT_DIR="${ROOT_DIR}/perf/results/recommendation-feed/${RUN_LABEL}"

mkdir -p "${OUT_DIR}/screenshots"

{
  echo "git_commit=$(git -C "${ROOT_DIR}" rev-parse HEAD)"
  echo "git_status_short_start"
  git -C "${ROOT_DIR}" status --short
  echo "git_status_short_end"
  echo "captured_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "database_url=${DATABASE_URL}"
  echo "viewer_id=${VIEWER_ID}"
  echo "page_size=${PAGE_SIZE}"
} > "${OUT_DIR}/code-state.txt"

psql "${DATABASE_URL}" \
  -v viewer_id="${VIEWER_ID}" \
  -v page_size="${PAGE_SIZE}" \
  -f "${ROOT_DIR}/perf/sql/recommendation_feed_counts.sql" \
  > "${OUT_DIR}/row-counts-and-sizes.txt"

psql "${DATABASE_URL}" \
  -v viewer_id="${VIEWER_ID}" \
  -v page_size="${PAGE_SIZE}" \
  -f "${ROOT_DIR}/perf/sql/recommendation_feed_explain_text.sql" \
  > "${OUT_DIR}/recommendation-feed-explain.txt"

psql "${DATABASE_URL}" \
  -v viewer_id="${VIEWER_ID}" \
  -v page_size="${PAGE_SIZE}" \
  -f "${ROOT_DIR}/perf/sql/recommendation_feed_explain_json.sql" \
  > "${OUT_DIR}/recommendation-feed-explain.json"

echo "SQL report written to ${OUT_DIR}"
```

- [ ] **Step 5: Make the script executable**

Run:

```bash
chmod +x perf/scripts/report_recommendation_sql.sh
```

Expected: no output.

- [ ] **Step 6: Run SQL report locally**

Run:

```bash
DATABASE_URL=postgresql://hola:hola@127.0.0.1:5432/hola_perf \
RUN_LABEL=local-baseline \
VIEWER_ID=1 \
PAGE_SIZE=20 \
./perf/scripts/report_recommendation_sql.sh
```

Expected output:

```text
SQL report written to /Users/minjoun/Workspace/projects/Hola-Climbing/hola-climbing-server/perf/results/recommendation-feed/local-baseline
```

Expected files:

```text
perf/results/recommendation-feed/local-baseline/code-state.txt
perf/results/recommendation-feed/local-baseline/row-counts-and-sizes.txt
perf/results/recommendation-feed/local-baseline/recommendation-feed-explain.txt
perf/results/recommendation-feed/local-baseline/recommendation-feed-explain.json
```

- [ ] **Step 7: Commit SQL report scripts**

Run:

```bash
git add perf/sql/recommendation_feed_counts.sql perf/sql/recommendation_feed_explain_text.sql perf/sql/recommendation_feed_explain_json.sql perf/scripts/report_recommendation_sql.sh
git commit -m "test(perf): add recommendation sql reporting"
```

Expected: commit succeeds with SQL report assets.

## Task 4: k6 Recommendation Feed Scenario

**Files:**
- Create: `perf/k6/recommendation-feed.js`

- [ ] **Step 1: Create k6 scenario**

Create `perf/k6/recommendation-feed.js`:

```javascript
import http from 'k6/http'
import { check, group, sleep } from 'k6'
import { Trend, Rate } from 'k6/metrics'

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'
const RUN_LABEL = __ENV.RUN_LABEL || 'local-baseline'
const PERF_PASSWORD = __ENV.PERF_PASSWORD || 'Password1!'
const TOKEN_USER_COUNT = Number(__ENV.TOKEN_USER_COUNT || '50')
const PAGE_SIZE = Number(__ENV.PAGE_SIZE || '20')
const CURSOR_FOLLOW_RATE = Number(__ENV.CURSOR_FOLLOW_RATE || '0.35')
const MAX_CURSOR_DEPTH = Number(__ENV.MAX_CURSOR_DEPTH || '3')

const firstPageDuration = new Trend('recommendation_feed_first_page_duration', true)
const cursorPageDuration = new Trend('recommendation_feed_cursor_page_duration', true)
const feedFailureRate = new Rate('recommendation_feed_failed')

export const options = {
  scenarios: {
    recommendation_feed: {
      executor: 'ramping-vus',
      stages: [
        { duration: __ENV.RAMP_UP || '2m', target: Number(__ENV.VUS || '20') },
        { duration: __ENV.STEADY || '5m', target: Number(__ENV.VUS || '20') },
        { duration: __ENV.RAMP_DOWN || '1m', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    recommendation_feed_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
}

function perfEmail(index) {
  return `perf_user_${String(index).padStart(5, '0')}@hola.test`
}

function login(index) {
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: perfEmail(index), password: PERF_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { api: 'auth-login' } },
  )

  const ok = check(res, {
    'login status is 200': (r) => r.status === 200,
    'login has access token': (r) => Boolean(r.json('data.accessToken')),
  })

  if (!ok) {
    throw new Error(`Failed to login perf user ${index}: status=${res.status} body=${res.body}`)
  }

  return res.json('data.accessToken')
}

export function setup() {
  const tokens = []
  for (let i = 1; i <= TOKEN_USER_COUNT; i += 1) {
    tokens.push(login(i))
  }
  return { tokens }
}

function getFeed(token, cursor, depth) {
  const cursorParam = cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''
  const url = `${BASE_URL}/api/recommendations/videos?size=${PAGE_SIZE}${cursorParam}`
  const res = http.get(url, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { api: 'recommendation-feed', page_depth: String(depth) },
  })

  const ok = check(res, {
    'feed status is 200': (r) => r.status === 200,
    'feed response success': (r) => r.json('isSuccess') === true,
    'feed content is array': (r) => Array.isArray(r.json('data.content')),
  })

  feedFailureRate.add(!ok)
  if (depth === 1) {
    firstPageDuration.add(res.timings.duration)
  } else {
    cursorPageDuration.add(res.timings.duration)
  }

  return ok ? res.json('data.nextCursor') : null
}

export default function (data) {
  const token = data.tokens[(__VU + __ITER) % data.tokens.length]

  group('recommendation feed first page', () => {
    let cursor = getFeed(token, null, 1)

    if (cursor && Math.random() < CURSOR_FOLLOW_RATE) {
      for (let depth = 2; depth <= MAX_CURSOR_DEPTH; depth += 1) {
        cursor = getFeed(token, cursor, depth)
        if (!cursor) {
          break
        }
      }
    }
  })

  sleep(Number(__ENV.SLEEP_SECONDS || '0.2'))
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data),
    [`perf/results/recommendation-feed/${RUN_LABEL}/k6-summary.json`]: JSON.stringify(data, null, 2),
    [`perf/results/recommendation-feed/${RUN_LABEL}/k6-summary.txt`]: textSummary(data),
  }
}

function textSummary(data) {
  const metrics = data.metrics
  const duration = metrics.http_req_duration
  const failed = metrics.http_req_failed
  const requests = metrics.http_reqs
  const firstPage = metrics.recommendation_feed_first_page_duration
  const cursorPage = metrics.recommendation_feed_cursor_page_duration

  return [
    '# k6 recommendation feed summary',
    `run_label=${RUN_LABEL}`,
    `base_url=${BASE_URL}`,
    `http_reqs=${requests?.values?.count ?? 0}`,
    `http_req_failed_rate=${failed?.values?.rate ?? 'n/a'}`,
    `http_req_duration_p50=${duration?.values?.['p(50)'] ?? 'n/a'}`,
    `http_req_duration_p95=${duration?.values?.['p(95)'] ?? 'n/a'}`,
    `http_req_duration_p99=${duration?.values?.['p(99)'] ?? 'n/a'}`,
    `first_page_p95=${firstPage?.values?.['p(95)'] ?? 'n/a'}`,
    `cursor_page_p95=${cursorPage?.values?.['p(95)'] ?? 'n/a'}`,
    '',
  ].join('\n')
}
```

- [ ] **Step 2: Create k6 output directory**

Run:

```bash
./perf/scripts/ensure_evidence_dirs.sh
mkdir -p perf/results/recommendation-feed/local-baseline
```

Expected: directories exist.

- [ ] **Step 3: Run a short local smoke load**

Run with the Spring app already listening on `localhost:8080`:

```bash
BASE_URL=http://localhost:8080 \
RUN_LABEL=local-baseline \
TOKEN_USER_COUNT=5 \
VUS=2 \
RAMP_UP=10s \
STEADY=20s \
RAMP_DOWN=5s \
k6 run perf/k6/recommendation-feed.js
```

Expected:

- k6 exits with code 0
- `http_req_failed` is below 1%
- files are written:

```text
perf/results/recommendation-feed/local-baseline/k6-summary.json
perf/results/recommendation-feed/local-baseline/k6-summary.txt
```

- [ ] **Step 4: Commit k6 scenario**

Run:

```bash
git add perf/k6/recommendation-feed.js
git commit -m "test(perf): add recommendation feed k6 scenario"
```

Expected: commit succeeds with k6 scenario only.

## Task 5: Evidence Validation And Report Template

**Files:**
- Create: `perf/scripts/validate_recommendation_evidence.sh`
- Create: `docs/performance/recommendation-feed-report.md`

- [ ] **Step 1: Create evidence validation script**

Create `perf/scripts/validate_recommendation_evidence.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUN_LABEL="${1:-local-baseline}"
RUN_DIR="${ROOT_DIR}/perf/results/recommendation-feed/${RUN_LABEL}"
SCREENSHOT_DIR="${RUN_DIR}/screenshots"

required_files=(
  "code-state.txt"
  "row-counts-and-sizes.txt"
  "recommendation-feed-explain.txt"
  "recommendation-feed-explain.json"
  "k6-summary.txt"
  "k6-summary.json"
)

for file in "${required_files[@]}"; do
  if [[ ! -s "${RUN_DIR}/${file}" ]]; then
    echo "Missing required evidence file: ${RUN_DIR}/${file}" >&2
    exit 1
  fi
done

if [[ ! -d "${SCREENSHOT_DIR}" ]]; then
  echo "Missing screenshot directory: ${SCREENSHOT_DIR}" >&2
  exit 1
fi

screenshot_count="$(find "${SCREENSHOT_DIR}" -type f \( -name '*.png' -o -name '*.jpg' -o -name '*.jpeg' \) | wc -l | tr -d ' ')"

if [[ "${screenshot_count}" -lt 3 ]]; then
  echo "Expected at least 3 screenshots in ${SCREENSHOT_DIR}; found ${screenshot_count}" >&2
  exit 1
fi

echo "Recommendation feed evidence is complete for ${RUN_LABEL}"
```

- [ ] **Step 2: Make validation script executable**

Run:

```bash
chmod +x perf/scripts/validate_recommendation_evidence.sh
```

Expected: no output.

- [ ] **Step 3: Create report template**

Create `docs/performance/recommendation-feed-report.md`:

````markdown
# Recommendation Feed Performance Report

## Summary

Target API:

```text
GET /api/recommendations/videos?size=20
```

Goal:

```text
Measure baseline p95/p99, identify PostgreSQL bottlenecks with EXPLAIN ANALYZE,
apply query or index improvements, and compare before/after evidence.
```

## Environment

| item | before | after |
|---|---|---|
| git commit |  |  |
| database | hola_perf | hola_perf |
| Cloud Run service | hola-backend-perf | hola-backend-perf |
| Cloud Run max instances | 1 | 1, then 2..3 |
| seed users | 10,000 | 10,000 |
| seed gyms | 1,000 | 1,000 |
| seed videos | 100,000 | 100,000 |
| seed follows | about 300,000 | about 300,000 |

## Before Evidence

### Code State

![before code state](../../perf/results/recommendation-feed/before/screenshots/01-before-recommendation-feed-code-state.png)

### k6 Summary

![before k6 summary](../../perf/results/recommendation-feed/before/screenshots/02-before-recommendation-feed-k6-summary.png)

### SQL Plan

![before sql plan](../../perf/results/recommendation-feed/before/screenshots/03-before-recommendation-feed-sql-plan.png)

### Grafana HTTP Latency

![before grafana http latency](../../perf/results/recommendation-feed/before/screenshots/04-before-recommendation-feed-grafana-http-latency.png)

### Cloud Run Metrics

![before cloud run metrics](../../perf/results/recommendation-feed/before/screenshots/05-before-recommendation-feed-cloud-run-metrics.png)

## After Evidence

### Code State

![after code state](../../perf/results/recommendation-feed/after/screenshots/11-after-recommendation-feed-code-state.png)

### k6 Summary

![after k6 summary](../../perf/results/recommendation-feed/after/screenshots/12-after-recommendation-feed-k6-summary.png)

### SQL Plan

![after sql plan](../../perf/results/recommendation-feed/after/screenshots/13-after-recommendation-feed-sql-plan.png)

### Grafana HTTP Latency

![after grafana http latency](../../perf/results/recommendation-feed/after/screenshots/14-after-recommendation-feed-grafana-http-latency.png)

### Cloud Run Metrics

![after cloud run metrics](../../perf/results/recommendation-feed/after/screenshots/15-after-recommendation-feed-cloud-run-metrics.png)

## Result Table

| metric | before | after | change |
|---|---:|---:|---:|
| k6 p50 |  |  |  |
| k6 p95 |  |  |  |
| k6 p99 |  |  |  |
| error rate |  |  |  |
| total requests |  |  |  |
| SQL execution time |  |  |  |
| shared read buffers |  |  |  |

## Bottleneck

Describe the concrete SQL plan finding and link the screenshot next to the claim.

## Change

Describe the query, index, or application change and link the code screenshot next to the claim.

## Interpretation

Explain why the result changed and what remains risky.

## Evidence Files

Raw evidence directories:

```text
perf/results/recommendation-feed/before/
perf/results/recommendation-feed/after/
perf/results/recommendation-feed/local-baseline/
```
````

- [ ] **Step 4: Verify validation fails before screenshots**

Run:

```bash
./perf/scripts/validate_recommendation_evidence.sh local-baseline
```

Expected before adding screenshots:

```text
Expected at least 3 screenshots in .../perf/results/recommendation-feed/local-baseline/screenshots; found 0
```

- [ ] **Step 5: Commit validation and report template**

Run:

```bash
git add perf/scripts/validate_recommendation_evidence.sh docs/performance/recommendation-feed-report.md
git commit -m "docs(perf): add recommendation evidence report template"
```

Expected: commit succeeds.

## Task 6: Local Baseline Runbook

**Files:**
- Modify: `perf/README.md`

- [ ] **Step 1: Add local baseline commands to README**

Append this section to `perf/README.md`:

````markdown
## Local Recommendation Baseline Run

Use this sequence after PostgreSQL, Redis, and Spring are available locally.

### 1. Prepare database

```bash
createdb hola_perf
psql postgresql://hola:hola@127.0.0.1:5432/hola_perf -f src/main/resources/db/migration/V1__init.sql
psql postgresql://hola:hola@127.0.0.1:5432/hola_perf -f src/main/resources/db/migration/V2__video_analysis_results.sql
psql postgresql://hola:hola@127.0.0.1:5432/hola_perf -f src/main/resources/db/migration/V3__drop_unused_efficiency_columns.sql
psql postgresql://hola:hola@127.0.0.1:5432/hola_perf -f src/main/resources/db/migration/V4__gym_profile_image.sql
psql postgresql://hola:hola@127.0.0.1:5432/hola_perf -f perf/sql/seed_recommendation_perf.sql
```

### 2. Start Spring against `hola_perf`

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/hola_perf \
SPRING_DATASOURCE_USERNAME=hola \
SPRING_DATASOURCE_PASSWORD=hola \
SPRING_DATA_REDIS_HOST=127.0.0.1 \
SPRING_DATA_REDIS_PORT=6379 \
SPRING_FLYWAY_ENABLED=false \
APP_MAIL_MODE=log \
FCM_ENABLED=false \
./mvnw spring-boot:run
```

### 3. Capture SQL report

```bash
DATABASE_URL=postgresql://hola:hola@127.0.0.1:5432/hola_perf \
RUN_LABEL=local-baseline \
VIEWER_ID=1 \
PAGE_SIZE=20 \
./perf/scripts/report_recommendation_sql.sh
```

### 4. Run k6 smoke

```bash
BASE_URL=http://localhost:8080 \
RUN_LABEL=local-baseline \
TOKEN_USER_COUNT=5 \
VUS=2 \
RAMP_UP=10s \
STEADY=20s \
RAMP_DOWN=5s \
k6 run perf/k6/recommendation-feed.js
```

### 5. Save screenshots

Save at least these screenshots:

```text
perf/results/recommendation-feed/local-baseline/screenshots/01-local-recommendation-feed-code-state.png
perf/results/recommendation-feed/local-baseline/screenshots/02-local-recommendation-feed-k6-summary.png
perf/results/recommendation-feed/local-baseline/screenshots/03-local-recommendation-feed-sql-plan.png
```

### 6. Validate evidence

```bash
./perf/scripts/validate_recommendation_evidence.sh local-baseline
```
````

- [ ] **Step 2: Commit README runbook**

Run:

```bash
git add perf/README.md
git commit -m "docs(perf): document local recommendation baseline"
```

Expected: commit succeeds.

## Task 7: Final Verification

**Files:**
- Read all files created by Tasks 1-6.

- [ ] **Step 1: Run shell syntax checks**

Run:

```bash
bash -n perf/scripts/ensure_evidence_dirs.sh
bash -n perf/scripts/report_recommendation_sql.sh
bash -n perf/scripts/validate_recommendation_evidence.sh
```

Expected: no output.

- [ ] **Step 2: Check for unfinished markers in created files**

Run:

```bash
rg -n "TBD|TODO|FIXME|fill in|implement later" perf docs/performance
```

Expected: no matches.

- [ ] **Step 3: Run k6 script static import check**

Run:

```bash
k6 inspect perf/k6/recommendation-feed.js
```

Expected: k6 prints scenario configuration and exits with code 0.

- [ ] **Step 4: Inspect git status**

Run:

```bash
git status --short
```

Expected: clean working tree after all task commits.

## Self-Review

Spec coverage:

- Evidence directories: Task 1.
- Recommendation seed data: Task 2.
- SQL report: Task 3.
- k6 recommendation feed scenario: Task 4.
- Screenshot/evidence validation: Task 5.
- Local baseline run path: Task 6.
- Final verification: Task 7.

Completion scan:

- No plan step relies on incomplete generic directions such as "add appropriate handling", "write tests", or an unnamed script.
- Every new file has explicit content.

Type and path consistency:

- k6 writes to `perf/results/recommendation-feed/${RUN_LABEL}`.
- SQL report writes to the same run directory.
- validation reads from the same run directory.
- report template links to `perf/results/recommendation-feed/before` and `after`.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-17-performance-evidence-and-recommendation-feed.md`. Two execution options:

1. **Subagent-Driven (recommended)** - dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** - execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
