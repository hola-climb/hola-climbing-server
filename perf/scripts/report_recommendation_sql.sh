#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

DATABASE_URL="${DATABASE_URL:-postgresql://hola:hola@127.0.0.1:5432/hola_perf}"
RUN_LABEL="${RUN_LABEL:-local-baseline}"
VIEWER_ID="${VIEWER_ID:-1}"
PAGE_SIZE="${PAGE_SIZE:-20}"
CANDIDATE_WINDOW="${CANDIDATE_WINDOW:-5000}"
OUT_DIR="${ROOT_DIR}/perf/results/recommendation-feed/${RUN_LABEL}"

mkdir -p "${OUT_DIR}/screenshots"

git_commit="$(git -C "${ROOT_DIR}" rev-parse HEAD)"
sanitized_database_url="$(printf '%s' "${DATABASE_URL}" | sed -E 's#(://[^:/@]+):[^@]*@#\1:***@#')"

{
  echo "git_commit=${git_commit}"
  echo "git_status_short_start"
  git -C "${ROOT_DIR}" status --short
  echo "git_status_short_end"
  echo "captured_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "database_url=${sanitized_database_url}"
  echo "viewer_id=${VIEWER_ID}"
  echo "page_size=${PAGE_SIZE}"
  echo "candidate_window=${CANDIDATE_WINDOW}"
} > "${OUT_DIR}/code-state.txt"

psql "${DATABASE_URL}" \
  -v viewer_id="${VIEWER_ID}" \
  -v page_size="${PAGE_SIZE}" \
  -f "${ROOT_DIR}/perf/sql/recommendation_feed_counts.sql" \
  > "${OUT_DIR}/row-counts-and-sizes.txt"

psql "${DATABASE_URL}" \
  -v viewer_id="${VIEWER_ID}" \
  -v page_size="${PAGE_SIZE}" \
  -v candidate_window="${CANDIDATE_WINDOW}" \
  -f "${ROOT_DIR}/perf/sql/recommendation_feed_explain_text.sql" \
  > "${OUT_DIR}/recommendation-feed-explain.txt"

psql "${DATABASE_URL}" \
  -qAt \
  -v viewer_id="${VIEWER_ID}" \
  -v page_size="${PAGE_SIZE}" \
  -v candidate_window="${CANDIDATE_WINDOW}" \
  -f "${ROOT_DIR}/perf/sql/recommendation_feed_explain_json.sql" \
  > "${OUT_DIR}/recommendation-feed-explain.json"

echo "SQL report written to ${OUT_DIR}"
