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

## Local Recommendation Baseline Run

Use this sequence after PostgreSQL, Redis, and Spring are available locally.

### 1. Start local dependencies

PostgreSQL must include pgvector.

```bash
docker run -d --name hola-postgres \
  -e POSTGRES_DB=hola \
  -e POSTGRES_USER=hola \
  -e POSTGRES_PASSWORD=hola \
  -p 5432:5432 \
  pgvector/pgvector:pg16

docker run -d --name hola-redis \
  -p 6379:6379 \
  redis:7
```

### 2. Prepare database

```bash
PGPASSWORD=hola createdb -h 127.0.0.1 -U hola hola_perf

psql postgresql://hola:hola@127.0.0.1:5432/hola_perf -f src/main/resources/db/migration/V1__init.sql
psql postgresql://hola:hola@127.0.0.1:5432/hola_perf -f src/main/resources/db/migration/V2__video_analysis_results.sql
psql postgresql://hola:hola@127.0.0.1:5432/hola_perf -f src/main/resources/db/migration/V3__drop_unused_efficiency_columns.sql
psql postgresql://hola:hola@127.0.0.1:5432/hola_perf -f src/main/resources/db/migration/V4__gym_profile_image.sql
psql postgresql://hola:hola@127.0.0.1:5432/hola_perf -f perf/sql/seed_recommendation_perf.sql
```

Expected seed scale:

```text
users=10000
gyms=1000
videos=100000
follows=300000
user_blocks=1000
likes=20000
comments=10000
analysis_video_results=5000
```

### 3. Start Spring against `hola_perf`

The local profile initializes the GCS signer even when this recommendation-feed
test does not upload videos. Provide `GOOGLE_APPLICATION_CREDENTIALS` with a
local test service-account JSON or another disposable credential file.

```bash
GOOGLE_APPLICATION_CREDENTIALS=/path/to/local-gcs-credentials.json \
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

### 4. Capture SQL report

```bash
DATABASE_URL=postgresql://hola:hola@127.0.0.1:5432/hola_perf \
RUN_LABEL=local-baseline \
VIEWER_ID=1 \
PAGE_SIZE=20 \
./perf/scripts/report_recommendation_sql.sh
```

### 5. Run k6 smoke

Use this when `k6` is installed on the host:

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

If host `k6` is unavailable, use Docker k6 from a temporary work directory. Do
not mount the full repository into the third-party k6 container.

```bash
mkdir -p /private/tmp/hola-k6-work/perf/k6
mkdir -p /private/tmp/hola-k6-work/perf/results/recommendation-feed/local-baseline
cp perf/k6/recommendation-feed.js /private/tmp/hola-k6-work/perf/k6/recommendation-feed.js

docker run --rm \
  -v /private/tmp/hola-k6-work:/work \
  -w /work \
  -e BASE_URL=http://host.docker.internal:8080 \
  -e RUN_LABEL=local-baseline \
  -e TOKEN_USER_COUNT=5 \
  -e VUS=2 \
  -e RAMP_UP=10s \
  -e STEADY=20s \
  -e RAMP_DOWN=5s \
  grafana/k6 run perf/k6/recommendation-feed.js

cp /private/tmp/hola-k6-work/perf/results/recommendation-feed/local-baseline/k6-summary.json \
  perf/results/recommendation-feed/local-baseline/k6-summary.json
cp /private/tmp/hola-k6-work/perf/results/recommendation-feed/local-baseline/k6-summary.txt \
  perf/results/recommendation-feed/local-baseline/k6-summary.txt
```

### 6. Save screenshots

Save at least these screenshots before treating a run as complete:

```text
perf/results/recommendation-feed/local-baseline/screenshots/01-local-recommendation-feed-code-state.png
perf/results/recommendation-feed/local-baseline/screenshots/02-local-recommendation-feed-k6-summary.png
perf/results/recommendation-feed/local-baseline/screenshots/03-local-recommendation-feed-sql-plan.png
perf/results/recommendation-feed/local-baseline/screenshots/04-local-recommendation-feed-k6-script.png
```

For cloud before/after runs, also save Grafana and Cloud Run metric screenshots
next to the raw outputs. When code changes are part of the performance claim,
save a code screenshot next to the metric screenshots.

### 7. Validate evidence

```bash
./perf/scripts/validate_recommendation_evidence.sh local-baseline
```
