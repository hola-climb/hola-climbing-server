# Performance E2E Test Design

## Goal

Build a repeatable performance test workflow for Hola that can be used both for engineering decisions and portfolio storytelling.

The primary story is recommendation feed optimization: reproduce latency under API load, identify the database bottleneck with PostgreSQL execution plans, improve query or index behavior, and compare p95/p99 before and after the change.

The secondary stories are key user-flow regression checks and the AI analysis pipeline. They are intentionally measured separately because their bottlenecks have different causes.

## Scope

Primary target:

- `GET /api/recommendations/videos?size=20`

Secondary targets:

- Video detail screen fan-out:
  - `GET /api/videos/{id}`
  - `GET /api/users/{userId}`
  - `GET /api/gyms/{gymId}`
  - `GET /api/videos/{id}/analysis`
  - `GET /api/videos/{id}/comments`
- Like/comment concurrency smoke:
  - `POST /api/videos/{id}/like`
  - `DELETE /api/videos/{id}/like`
  - `POST /api/videos/{id}/comments`
  - `GET /api/videos/{id}`
- Upload and AI worker pipeline:
  - upload URL issue
  - GCS PUT
  - video registration
  - Redis Stream enqueue
  - worker claim
  - GCS download
  - pose extraction
  - classification
  - Spring callback
  - DB persistence
  - status completion

## Non-goals

- Do not performance-test every API deeply.
- Do not put large fake performance data into the production `hola` database.
- Do not upload 100,000 real video objects to GCS for the recommendation feed test.
- Do not treat AI worker total latency as a single number without phase breakdown.
- Do not tune Cloud Run autoscaling before the single-instance database bottleneck is understood.

## Test Environment

Use a separate performance environment that mirrors production topology without mixing data:

```text
hola-backend          -> PostgreSQL database: hola
hola-backend-perf     -> PostgreSQL database: hola_perf
```

The performance Cloud Run service should use the same backend Docker image as production, but different runtime configuration.

Recommended settings:

```text
Service name: hola-backend-perf
Database: hola_perf
SPRING_FLYWAY_ENABLED=true
APP_MAIL_MODE=log
FCM_ENABLED=false
min-instances=0
initial max-instances=1
post-optimization max-instances=2..3
```

The first run uses `max-instances=1` to expose query and DB behavior without Cloud Run scaling noise. After the recommendation query is understood and improved, rerun with `max-instances=2..3` to measure API throughput under limited autoscaling.

## Cost And Safety Controls

Configure GCP budget alerts before running the GCP test:

- 5,000 KRW
- 10,000 KRW
- 20,000 KRW

Expected cost for a one- to two-hour portfolio-scale run is low if fake GCS paths are used for feed data. The main risks are leaving Compute Engine, Serverless VPC Access, or Cloud Run load running after the test.

After each run:

- stop or scale down temporary test load generators
- keep `hola-backend-perf` at `min-instances=0`
- confirm no long-running local load process remains
- keep performance data isolated in `hola_perf`

## Seed Data

Use deterministic seed data so local and GCP runs are comparable.

Target size:

| table/domain | volume |
|---|---:|
| users | 10,000 |
| gyms | 1,000 |
| videos | 100,000 |
| follows | hundreds of thousands |
| user_blocks | small but non-zero blocker/blocked graph |
| likes/comments | enough for video detail and concurrency smoke |
| analysis_video_results | subset of `done` videos |

Recommendation feed videos should use fake `gcsPath`, `gcsStreamingPath`, and `thumbnailPath` values. This avoids unnecessary Cloud Storage object creation while preserving the API response shape.

Embeddings should be mixed:

- some users have `style_embedding`
- some gyms have `style_embedding`
- some users or gyms have `NULL` embeddings

This preserves both ranking paths:

- pgvector cosine ranking when both embeddings exist
- fallback ranking when embeddings are missing

## Tooling

Use three tool classes:

| tool | role |
|---|---|
| k6 | generate API load and record latency, throughput, and error rate |
| SQL report script | collect PostgreSQL row counts, relation sizes, and `EXPLAIN ANALYZE` output |
| Prometheus/Grafana | observe Spring, Tomcat, JVM, Cloud Run, VM, and Redis behavior during load |

k6 is preferred over JMeter for this scope because the test scenario is API-centric, scriptable, easy to review in Git, and can be reused locally and in GCP. Grafana remains part of the workflow as the observability surface.

## Phase 1: Recommendation Feed Performance

### Scenario

The k6 scenario calls:

```text
GET /api/recommendations/videos?size=20
```

It should:

- use a pool of authenticated test users
- randomize users across requests
- request the first page frequently
- follow `nextCursor` for a subset of requests to page 2 or 3
- run the same script against local and GCP performance environments

Recommended operating range:

```text
Total requests: 500,000..1,000,000
Duration: 1..2 hours
```

### Metrics

k6 records:

- `http_req_duration` p50, p95, p99
- `http_req_failed`
- `http_reqs`
- throughput
- status code distribution

Grafana observes:

- Spring HTTP latency
- Tomcat thread usage
- JVM heap and GC behavior
- process CPU and memory
- Cloud Run instance count
- database connection pressure
- Redis health, if relevant

The first success gate:

- no 5xx responses
- error rate below 1%
- test completes without saturating the load generator

The optimization target should be set after the first baseline. A good portfolio target is at least 30% p95 improvement when the baseline reveals a concrete query/index bottleneck.

### SQL Report

For each run, collect:

- row counts for `users`, `gyms`, `videos`, `follows`, `user_blocks`
- table and index sizes
- current indexes relevant to the recommendation query
- `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` for representative feed queries

The report should call out:

- sequential scans
- index scans
- sort nodes
- hash joins or nested loops
- estimated rows vs actual rows
- shared buffer hits and reads
- planning time
- execution time

Store baseline and after-optimization reports in the same structure so the comparison is mechanical.

## Phase 2: Video Detail Screen Fan-out

### Scenario

Model a video detail screen open as one grouped user flow:

```text
GET /api/videos/{id}
GET /api/users/{userId}
GET /api/gyms/{gymId}
GET /api/videos/{id}/analysis
GET /api/videos/{id}/comments
```

The goal is not to deeply optimize this path first. The goal is to quantify screen-level latency and request fan-out so future work can choose between:

- backend response composition
- frontend request reduction
- better caching for GCS read URLs
- asynchronous view count handling
- comments pagination tuning

### Metrics

Record:

- per-request latency
- grouped screen-open duration
- failure rate
- cache behavior for signed read URLs, if visible through metrics or logs
- increase in `view_count` under load

This phase is a secondary regression test. It should not block the recommendation feed optimization unless it reveals 5xx errors or severe contention.

## Phase 3: Like And Comment Concurrency Smoke

### Scenario

Run concurrent mutations against a controlled set of videos:

```text
POST /api/videos/{id}/like
DELETE /api/videos/{id}/like
POST /api/videos/{id}/comments
GET /api/videos/{id}
```

This phase tests correctness under concurrency more than raw latency.

Assertions:

- duplicate likes are rejected or handled consistently
- no 5xx responses under concurrent like/unlike attempts
- final `like_count` matches persisted likes
- final `comment_count` matches persisted comments
- notifications do not cause request failures

This gives portfolio support for "performance tests also checked write-path stability and counter consistency."

## Phase 4: Upload And AI Worker Pipeline

### Why This Is Separate

The AI pipeline should not be mixed with the recommendation feed test. Its latency depends on VM CPU, video duration, resolution, GCS download speed, MediaPipe/OpenCV configuration, worker concurrency, Redis queue delay, and callback persistence.

The correct story is phase-level latency analysis, not a single total time.

### Pipeline Phases

Measure:

```text
upload_url_issued_at
gcs_put_finished_at
video_registered_at
redis_enqueued_at
worker_claimed_at
gcs_download_finished_at
pose_extract_finished_at
classification_finished_at
callback_finished_at
spring_done_at
```

### Test Types

Use three subtests:

1. Actual user E2E smoke
   - 1 to 3 real videos
   - upload URL -> GCS PUT -> video register -> worker analysis -> callback -> status done

2. Worker analysis benchmark
   - fixed sample videos already in GCS
   - batch jobs created through video registration or direct Redis enqueue
   - upload time excluded
   - focus on queue wait, download, inference, callback

3. Upload benchmark
   - Signed URL PUT only
   - different file sizes
   - separate GCS upload/network time from AI processing time

### Metrics

Record:

- total pipeline latency
- queue wait time
- GCS download time
- pose extraction time
- classification time
- callback time
- terminal status success/failure
- Redis pending entries and DLQ length
- VM CPU/memory during worker processing

Expected portfolio framing:

```text
The AI pipeline was decomposed into upload, queue, download, inference, callback, and persistence phases.
The dominant latency came from the worker CPU-bound pose extraction phase, so the next improvements should target worker concurrency, VM sizing, frame sampling, and model settings rather than Spring API scaling.
```

## Artifacts

Create these implementation artifacts after this design is approved:

```text
perf/k6/recommendation-feed.js
perf/k6/video-detail-flow.js
perf/k6/like-comment-concurrency.js
perf/scripts/seed_perf_data.*
perf/scripts/report_recommendation_sql.*
perf/scripts/ai_pipeline_benchmark.*
perf/results/local-baseline/
perf/results/gcp-baseline/
perf/results/after-optimization/
docs/performance/recommendation-feed-report.md
docs/performance/ai-pipeline-report.md
```

## Portfolio Output

The final write-up should follow this structure:

1. Problem
   - recommendation feed is the first screen of the SNS experience
   - the query combines pgvector ranking, social graph, blocking rules, gyms, grades, and cursor pagination

2. Measurement
   - k6 load test
   - Prometheus/Grafana runtime metrics
   - PostgreSQL `EXPLAIN ANALYZE`

3. Bottleneck
   - concrete SQL plan findings from the baseline

4. Improvement
   - query or index changes
   - any application-level adjustments

5. Result
   - before/after p95 and p99
   - error rate
   - throughput
   - DB execution time comparison

6. Follow-up
   - video detail fan-out
   - like/comment concurrency smoke
   - AI pipeline phase-level latency

## Risks

- Test data can leak into user-facing views if inserted into the production DB. Use `hola_perf`.
- Cloud Run autoscaling can hide DB bottlenecks or overload the VM database. Start with `max-instances=1`.
- Load generator capacity can become the bottleneck. Track k6-side resource use.
- GCS fake paths are valid for feed tests but not for real video playback tests. Use a small real-video set for upload and AI smoke only.
- AI worker results are VM-dependent. Report phase timing and machine type together.

## Open Implementation Decisions

These decisions belong in the implementation plan, not this design:

- exact k6 stage profile
- exact token generation and storage format
- seed script language
- whether SQL reports run through `psql` shell scripts or a small typed script
- exact Cloud Run deploy command or GitHub Actions workflow shape for `hola-backend-perf`

## Rollback Cost

Low for test artifacts and documentation. The only operational changes are additive:

- create a separate `hola_perf` database
- deploy a separate `hola-backend-perf` Cloud Run service
- add performance scripts and reports

No production API contract changes are required for the test design itself.
