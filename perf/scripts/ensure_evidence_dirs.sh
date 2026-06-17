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
