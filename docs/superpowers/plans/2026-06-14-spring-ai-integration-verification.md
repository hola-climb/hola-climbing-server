# Spring AI Integration Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Spring 서버와 Python AI 워커를 동시에 띄운 상태에서 `Spring -> Redis Streams -> AI worker -> Spring callback -> DB/SSE` 전체 경로가 실제로 맞물리는지 반복 가능하게 검증한다.

**Architecture:** Spring 서버가 연동 계약의 SSOT다. Spring은 영상 등록 후 `analysis:requests` Redis Stream에 `videoId`, `gcsPath`, `callbackUrl`을 적재하고, Python 워커는 이를 consumer group으로 소비한 뒤 `analysis:progress` Pub/Sub과 `POST /api/analysis/videos/{id}` callback으로 결과를 돌려준다. 최종 판정은 Spring DB `videos.status`, `analysis_results`, `/api/videos/{id}/analysis`, `/api/videos/{id}/analysis/stream`, Redis PEL/DLQ 상태로 한다.

**Tech Stack:** Spring Boot 4.0.x, Java 25, Maven, PostgreSQL pgvector, Redis Streams/Pub/Sub, Python 3.11, FastAPI, uv, MediaPipe/OpenCV, Google Cloud Storage.

---

## File Structure

검증 계획 문서:
- Created: `hola-climbing-server/docs/superpowers/plans/2026-06-14-spring-ai-integration-verification.md`

주요 Spring 연동 파일:
- Read: `hola-climbing-server/src/main/java/com/holaclimbing/server/domain/video/service/VideoServiceImpl.java`
- Read: `hola-climbing-server/src/main/java/com/holaclimbing/server/infrastructure/ai/AnalysisDispatcher.java`
- Read: `hola-climbing-server/src/main/java/com/holaclimbing/server/infrastructure/ai/RedisStreamAnalysisJobQueue.java`
- Read: `hola-climbing-server/src/main/java/com/holaclimbing/server/infrastructure/ai/AnalysisProgressListener.java`
- Read: `hola-climbing-server/src/main/java/com/holaclimbing/server/domain/analysis/service/AnalysisServiceImpl.java`
- Read: `hola-climbing-server/src/main/java/com/holaclimbing/server/common/security/AiCallbackSecretFilter.java`

주요 Python 연동 파일:
- Read: `hola-climbing-ai/app/models/stream.py`
- Read: `hola-climbing-ai/app/workers/stream_consumer.py`
- Read: `hola-climbing-ai/app/services/pipeline/orchestrator.py`
- Read: `hola-climbing-ai/app/services/callback/client.py`
- Read: `hola-climbing-ai/app/models/callback.py`

기존 회귀 테스트:
- Spring: `hola-climbing-server/src/test/java/com/holaclimbing/server/domain/analysis/AnalysisIntegrationTest.java`
- Spring: `hola-climbing-server/src/test/java/com/holaclimbing/server/infrastructure/ai/AnalysisInfraIntegrationTest.java`
- Spring: `hola-climbing-server/src/test/java/com/holaclimbing/server/infrastructure/ai/AnalysisDispatcherTest.java`
- Python: `hola-climbing-ai/tests/integration/test_stream_consumer.py`
- Python: `hola-climbing-ai/tests/integration/test_callback_payload_shape.py`
- Python: `hola-climbing-ai/tests/unit/test_callback_retry.py`

## Verification Contract

| Boundary | Expected contract | Evidence |
|---|---|---|
| Spring dispatch | Redis Stream `analysis:requests`, fields `videoId`, `gcsPath`, `callbackUrl` in camelCase | `RedisStreamAnalysisJobQueue` |
| Worker consume | `StreamRequest` accepts camelCase and coerces `videoId` from string/bytes to int | `app/models/stream.py` |
| Worker progress | Redis Pub/Sub `analysis:progress`, JSON fields `video_id`, `stage`, `message`, `updated_at` | `ProgressEvent`, `AnalysisProgress` |
| Worker callback | `POST {callbackUrl}`, header `X-AI-Callback-Secret`, body fields `status`, `model_version`, `segments` | `post_callback`, `AnalysisIngestRequest` |
| Spring ingest | Accepts `status=done|failed`, replaces previous rows with delete-then-insert, updates `videos.status` | `AnalysisServiceImpl.ingestResult` |
| Client observation | `/api/videos/{id}/analysis/stream`, `/api/videos/{id}/status`, `/api/videos/{id}/analysis` | `VideoController`, `AnalysisController` |

## Prerequisites

- Docker is running.
- `jq`, `redis-cli`, `psql`, `curl`, `gcloud` are available for manual smoke.
- Local GCS auth is available through `GOOGLE_APPLICATION_CREDENTIALS` or `gcloud auth application-default login`.
- Use the same shared secret on both processes: `AI_CALLBACK_SECRET=local-ai-secret`.
- Use a real short sample video already present locally: `/Users/minjoun/Workspace/projects/Hola-Climbing/hola-climbing-ai/data/gcs_cache/videos/original/IMG_0042.MOV`.
- Use the same bucket on both processes: `GCS_BUCKET=hola-climbing-log-videos`.
- On a fresh local DB, load test gym/grade seed data after Spring Flyway migrations run; the smoke commands below use `gymId=1` and `gymGradeId=1003`.
- Signup smoke requests must include required terms consent for term IDs `1` and `2`.

### Task 1: Run Existing Single-Side Contract Tests

**Files:**
- Test: `hola-climbing-server/src/test/java/com/holaclimbing/server/domain/analysis/AnalysisIntegrationTest.java`
- Test: `hola-climbing-server/src/test/java/com/holaclimbing/server/infrastructure/ai/AnalysisInfraIntegrationTest.java`
- Test: `hola-climbing-server/src/test/java/com/holaclimbing/server/infrastructure/ai/AnalysisDispatcherTest.java`
- Test: `hola-climbing-ai/tests/integration/test_stream_consumer.py`
- Test: `hola-climbing-ai/tests/integration/test_callback_payload_shape.py`
- Test: `hola-climbing-ai/tests/unit/test_callback_retry.py`

- [ ] **Step 1: Verify Spring analysis contract tests**

Run:

```bash
cd /Users/minjoun/Workspace/projects/Hola-Climbing/hola-climbing-server
./mvnw -Dtest=AnalysisIntegrationTest,AnalysisInfraIntegrationTest,AnalysisDispatcherTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 2: Verify Python worker contract tests**

Run:

```bash
cd /Users/minjoun/Workspace/projects/Hola-Climbing/hola-climbing-ai
uv run pytest tests/unit/test_callback_retry.py tests/integration/test_callback_payload_shape.py tests/integration/test_stream_consumer.py -q
```

Expected:

```text
passed
```

### Task 2: Start Shared Local Infrastructure

**Files:**
- Read: `hola-climbing-server/src/main/resources/application.yaml`
- Read: `hola-climbing-ai/.env.example`

- [ ] **Step 1: Start PostgreSQL and Redis**

Run:

```bash
docker run -d --name hola-it-postgres -e POSTGRES_DB=hola -e POSTGRES_USER=hola -e POSTGRES_PASSWORD=hola -p 5432:5432 pgvector/pgvector:pg16
docker run -d --name hola-it-redis -p 6379:6379 redis:7
```

Expected:

```text
container ids printed
```

- [ ] **Step 2: Confirm Redis and PostgreSQL readiness**

Run:

```bash
redis-cli -h 127.0.0.1 -p 6379 ping
psql postgresql://hola:hola@127.0.0.1:5432/hola -c "select 1;"
```

Expected:

```text
PONG
 ?column?
----------
        1
```

### Task 3: Start Spring and Python Together

**Files:**
- Read: `hola-climbing-server/src/main/resources/application.yaml`
- Read: `hola-climbing-ai/app/main.py`

- [ ] **Step 1: Start Spring server on port 8080**

Run in terminal A:

```bash
cd /Users/minjoun/Workspace/projects/Hola-Climbing/hola-climbing-server
SPRING_PROFILES_ACTIVE=local \
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/hola \
SPRING_DATASOURCE_USERNAME=hola \
SPRING_DATASOURCE_PASSWORD=hola \
SPRING_DATA_REDIS_HOST=127.0.0.1 \
SPRING_DATA_REDIS_PORT=6379 \
GCS_BUCKET=hola-climbing-log-videos \
AI_CALLBACK_SECRET=local-ai-secret \
APP_BASE_URL=http://localhost:8080 \
./mvnw spring-boot:run
```

Expected:

```text
Tomcat started on port 8080
```

- [ ] **Step 2: Check Spring health**

Run:

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

Expected:

```json
{
  "status": "UP"
}
```

- [ ] **Step 2.5: Seed gym and grade data when using a fresh DB**

Run after Spring has applied Flyway migrations:

```bash
psql postgresql://hola:hola@127.0.0.1:5432/hola \
  -f /Users/minjoun/Workspace/projects/Hola-Climbing/hola-climbing-server/src/test/resources/sql/gyms-data.sql
```

Expected:

```text
INSERT
```

- [ ] **Step 3: Start Python AI worker on port 8000**

Run in terminal B:

```bash
cd /Users/minjoun/Workspace/projects/Hola-Climbing/hola-climbing-ai
WORKER_HOST=0.0.0.0 \
WORKER_PORT=8000 \
REDIS_HOST=127.0.0.1 \
REDIS_PORT=6379 \
REDIS_PASSWORD= \
REDIS_DB=0 \
REDIS_STREAM_KEY=analysis:requests \
REDIS_CONSUMER_GROUP=hola-ai-worker \
REDIS_CONSUMER_NAME=worker-local-1 \
REDIS_PROGRESS_CHANNEL=analysis:progress \
GCS_BUCKET=hola-climbing-log-videos \
GOOGLE_APPLICATION_CREDENTIALS=/Users/minjoun/Workspace/projects/Hola-Climbing/keys/hola-storage-key.json \
AI_CALLBACK_SECRET=local-ai-secret \
MODEL_VERSION=rule_v3 \
FLOW_GATE_MODEL_PATH=models/flow_qa_rf_v2.joblib \
uv run uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Expected:

```text
Uvicorn running on http://0.0.0.0:8000
consumer starting
```

- [ ] **Step 4: Check worker health**

Run:

```bash
curl -s http://localhost:8000/health | jq .
curl -s http://localhost:8000/health/ready | jq .
```

Expected:

```json
{
  "is_success": true
}
```

Readiness must show Redis and GCS as `ok`. If GCS is unavailable, continue only with failure-path verification.

### Task 4: Prepare a Verified User and Uploaded GCS Object

**Files:**
- Read: `hola-climbing-server/src/main/java/com/holaclimbing/server/domain/user/AuthController.java`
- Read: `hola-climbing-server/src/main/java/com/holaclimbing/server/domain/video/VideoController.java`

- [ ] **Step 1: Create a smoke user through Spring**

Run:

```bash
curl -s -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"ai-smoke-20260614@hola.test","password":"password123","nickname":"ai-smoke-20260614","termsAgreed":[{"termId":1,"agreed":true},{"termId":2,"agreed":true}]}' | jq .
```

Expected:

```json
{
  "isSuccess": true
}
```

- [ ] **Step 2: Read the verification token from local DB**

Run:

```bash
TOKEN_TO_VERIFY=$(psql -qtAX postgresql://hola:hola@127.0.0.1:5432/hola -c "select email_verification_token from users where email = 'ai-smoke-20260614@hola.test';")
echo "$TOKEN_TO_VERIFY"
```

Expected: one non-empty token string.

- [ ] **Step 3: Verify email and login**

Run:

```bash
curl -s -X POST http://localhost:8080/api/auth/email/verify \
  -H "Content-Type: application/json" \
  -d "{\"token\":\"$TOKEN_TO_VERIFY\"}" | jq .

ACCESS_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"ai-smoke-20260614@hola.test","password":"password123"}' | jq -r '.data.accessToken')
echo "$ACCESS_TOKEN"
```

Expected: `ACCESS_TOKEN` is a non-empty JWT.

- [ ] **Step 4: Request a GCS signed upload URL**

Run:

```bash
UPLOAD_JSON=$(curl -s -X POST http://localhost:8080/api/videos/upload-url \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fileName":"IMG_0042.MOV","mimeType":"video/quicktime","fileSize":63137987}')
echo "$UPLOAD_JSON" | jq .
UPLOAD_URL=$(echo "$UPLOAD_JSON" | jq -r '.data.uploadUrl')
OBJECT_PATH=$(echo "$UPLOAD_JSON" | jq -r '.data.objectPath')
echo "$OBJECT_PATH"
```

Expected: `OBJECT_PATH` starts with `videos/uploads/{userId}/`.

- [ ] **Step 5: Upload the sample video to GCS**

Run:

```bash
curl -X PUT "$UPLOAD_URL" \
  -H "Content-Type: video/quicktime" \
  --upload-file /Users/minjoun/Workspace/projects/Hola-Climbing/hola-climbing-ai/data/gcs_cache/videos/original/IMG_0042.MOV
```

Expected:

```text
HTTP 200 or no error output
```

If signed upload fails, use the previously proven fallback:

```bash
gcloud storage cp /Users/minjoun/Workspace/projects/Hola-Climbing/hola-climbing-ai/data/gcs_cache/videos/original/IMG_0042.MOV "gs://hola-climbing-log-videos/$OBJECT_PATH"
```

Expected:

```text
Copying file://... to gs://hola-climbing-log-videos/videos/uploads/...
```

### Task 5: Trigger the Full Integration Flow

**Files:**
- Read: `hola-climbing-server/src/main/java/com/holaclimbing/server/domain/video/service/VideoServiceImpl.java`
- Read: `hola-climbing-ai/app/workers/stream_consumer.py`
- Read: `hola-climbing-ai/app/services/pipeline/orchestrator.py`

- [ ] **Step 1: Open an SSE stream before creating the video**

Run in terminal C after `VIDEO_ID` is known in Step 2 if preferred:

```bash
curl -N -H "Authorization: Bearer $ACCESS_TOKEN" "http://localhost:8080/api/videos/$VIDEO_ID/analysis/stream"
```

Expected after processing begins:

```text
event: progress
data: {"videoId":...,"stage":"PROCESSING",...}
```

- [ ] **Step 2: Create video metadata and dispatch analysis**

Run:

```bash
CREATE_VIDEO_JSON=$(curl -s -X POST http://localhost:8080/api/videos \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"gymId\":1,\"title\":\"AI smoke 2026-06-14\",\"description\":\"Spring AI live smoke\",\"gymGradeId\":1003,\"objectPath\":\"$OBJECT_PATH\",\"thumbnailPath\":null,\"durationSeconds\":45,\"recordedDate\":\"2026-06-14\",\"isPublic\":true}")
echo "$CREATE_VIDEO_JSON" | jq .
VIDEO_ID=$(echo "$CREATE_VIDEO_JSON" | jq -r '.data.id')
echo "$VIDEO_ID"
```

Expected:

```json
{
  "isSuccess": true,
  "data": {
    "status": "pending"
  }
}
```

- [ ] **Step 3: Confirm Redis Stream dispatch**

Run:

```bash
redis-cli -h 127.0.0.1 -p 6379 XRANGE analysis:requests - + COUNT 5
redis-cli -h 127.0.0.1 -p 6379 XINFO GROUPS analysis:requests
```

Expected: the latest stream record contains the same `VIDEO_ID`, `OBJECT_PATH`, and `http://localhost:8080/api/analysis/videos/$VIDEO_ID`.

- [ ] **Step 4: Wait for AI worker completion**

Run:

```bash
for i in $(seq 1 90); do
  curl -s -H "Authorization: Bearer $ACCESS_TOKEN" "http://localhost:8080/api/videos/$VIDEO_ID/status" | jq -r '.data.status'
  sleep 2
done
```

Expected: status eventually becomes `done`. If the sample cannot be downloaded or decoded, expected status is `failed` and the failure path must be inspected in Task 7.

- [ ] **Step 5: Verify Spring API and DB state**

Run:

```bash
curl -s -H "Authorization: Bearer $ACCESS_TOKEN" "http://localhost:8080/api/videos/$VIDEO_ID/analysis" | jq .
psql postgresql://hola:hola@127.0.0.1:5432/hola -c "select id, status from videos where id = $VIDEO_ID;"
psql postgresql://hola:hola@127.0.0.1:5432/hola -c "select count(*) as segment_count, min(sequence_index), max(sequence_index), max(model_version) from analysis_results where video_id = $VIDEO_ID;"
```

Expected for success path:

```text
videos.status = done
segment_count > 0
model_version = rule_v3 or rule_v3+flow_rf_v2
```

- [ ] **Step 6: Verify queue cleanup**

Run:

```bash
redis-cli -h 127.0.0.1 -p 6379 XPENDING analysis:requests hola-ai-worker
redis-cli -h 127.0.0.1 -p 6379 XLEN analysis:requests:dlq
redis-cli -h 127.0.0.1 -p 6379 XLEN analysis:dlq
```

Expected:

```text
pending count = 0
analysis:requests:dlq unchanged from before the run
analysis:dlq unchanged from before the run
```

### Task 6: Verify Retry and Idempotent Replacement

**Files:**
- Read: `hola-climbing-server/src/main/java/com/holaclimbing/server/domain/analysis/service/AnalysisServiceImpl.java`
- Read: `hola-climbing-ai/app/services/callback/client.py`

- [ ] **Step 1: Capture current segment count**

Run:

```bash
BEFORE_COUNT=$(psql -qtAX postgresql://hola:hola@127.0.0.1:5432/hola -c "select count(*) from analysis_results where video_id = $VIDEO_ID;")
echo "$BEFORE_COUNT"
```

Expected: a number greater than `0`.

- [ ] **Step 2: Request retry through Spring**

Run:

```bash
curl -s -X POST -H "Authorization: Bearer $ACCESS_TOKEN" "http://localhost:8080/api/videos/$VIDEO_ID/analysis/retry" | jq .
```

Expected:

```json
{
  "isSuccess": true,
  "data": {
    "status": "pending",
    "segments": []
  }
}
```

- [ ] **Step 3: Wait for completion and confirm replace-not-append**

Run:

```bash
for i in $(seq 1 90); do
  STATUS=$(curl -s -H "Authorization: Bearer $ACCESS_TOKEN" "http://localhost:8080/api/videos/$VIDEO_ID/status" | jq -r '.data.status')
  echo "$STATUS"
  test "$STATUS" = "done" && break
  sleep 2
done
psql postgresql://hola:hola@127.0.0.1:5432/hola -c "select count(*) as segment_count, min(sequence_index), max(sequence_index) from analysis_results where video_id = $VIDEO_ID;"
```

Expected:

```text
status = done
segment_count is not BEFORE_COUNT * 2
sequence_index starts at 0
```

### Task 7: Verify Failure and Security Paths

**Files:**
- Read: `hola-climbing-server/src/main/java/com/holaclimbing/server/common/security/AiCallbackSecretFilter.java`
- Read: `hola-climbing-ai/app/services/callback/client.py`
- Read: `hola-climbing-ai/app/workers/stream_consumer.py`

- [ ] **Step 1: Wrong callback secret lands in worker DLQ**

Run Python worker with `AI_CALLBACK_SECRET=wrong-secret`, then create a new video using Task 4 and Task 5.

Expected:

```text
Spring callback returns 401
redis XLEN analysis:requests:dlq increases by 1
redis XPENDING analysis:requests hola-ai-worker returns 0 pending
Spring video status does not become done
```

- [ ] **Step 2: Missing GCS object becomes Spring failed status**

Run:

```bash
MISSING_OBJECT="videos/uploads/$(psql -qtAX postgresql://hola:hola@127.0.0.1:5432/hola -c "select id from users where email = 'ai-smoke-20260614@hola.test';")/missing-20260614.mp4"
CREATE_MISSING_JSON=$(curl -s -X POST http://localhost:8080/api/videos \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"gymId\":1,\"title\":\"AI missing object smoke\",\"description\":\"Expected failed analysis\",\"gymGradeId\":1003,\"objectPath\":\"$MISSING_OBJECT\",\"thumbnailPath\":null,\"durationSeconds\":45,\"recordedDate\":\"2026-06-14\",\"isPublic\":true}")
MISSING_VIDEO_ID=$(echo "$CREATE_MISSING_JSON" | jq -r '.data.id')
echo "$MISSING_VIDEO_ID"
```

Expected after worker handles it:

```text
GET /api/videos/$MISSING_VIDEO_ID/status -> failed
analysis_results count for MISSING_VIDEO_ID -> 0
analysis:requests:dlq unchanged
```

- [ ] **Step 3: Malformed progress event lands in Spring progress DLQ**

Run:

```bash
BEFORE_PROGRESS_DLQ=$(redis-cli -h 127.0.0.1 -p 6379 XLEN analysis:dlq)
redis-cli -h 127.0.0.1 -p 6379 PUBLISH analysis:progress "{ this is not valid json"
sleep 2
AFTER_PROGRESS_DLQ=$(redis-cli -h 127.0.0.1 -p 6379 XLEN analysis:dlq)
echo "$BEFORE_PROGRESS_DLQ -> $AFTER_PROGRESS_DLQ"
```

Expected:

```text
AFTER_PROGRESS_DLQ is greater than BEFORE_PROGRESS_DLQ
```

### Task 8: Verify Small Concurrent Batch

**Files:**
- Read: `hola-climbing-ai/app/workers/stream_consumer.py`
- Read: `hola-climbing-server/src/main/java/com/holaclimbing/server/infrastructure/ai/AnalysisDispatcher.java`

- [ ] **Step 1: Start a second worker consumer**

Run in terminal D:

```bash
cd /Users/minjoun/Workspace/projects/Hola-Climbing/hola-climbing-ai
WORKER_HOST=0.0.0.0 \
WORKER_PORT=8001 \
REDIS_HOST=127.0.0.1 \
REDIS_PORT=6379 \
REDIS_PASSWORD= \
REDIS_DB=0 \
REDIS_STREAM_KEY=analysis:requests \
REDIS_CONSUMER_GROUP=hola-ai-worker \
REDIS_CONSUMER_NAME=worker-local-2 \
REDIS_PROGRESS_CHANNEL=analysis:progress \
GCS_BUCKET=hola-climbing-log-videos \
GOOGLE_APPLICATION_CREDENTIALS=/Users/minjoun/Workspace/projects/Hola-Climbing/keys/hola-storage-key.json \
AI_CALLBACK_SECRET=local-ai-secret \
MODEL_VERSION=rule_v3 \
FLOW_GATE_MODEL_PATH=models/flow_qa_rf_v2.joblib \
uv run uvicorn app.main:app --host 0.0.0.0 --port 8001
```

Expected:

```text
consumer starting
```

- [ ] **Step 2: Submit three videos using the same uploaded object**

Run Task 5 Step 2 three times with titles `AI batch 1`, `AI batch 2`, `AI batch 3`.

Expected:

```text
three distinct VIDEO_ID values
```

- [ ] **Step 3: Confirm all jobs are terminal and not duplicated**

Run:

```bash
redis-cli -h 127.0.0.1 -p 6379 XINFO GROUPS analysis:requests
redis-cli -h 127.0.0.1 -p 6379 XPENDING analysis:requests hola-ai-worker
psql postgresql://hola:hola@127.0.0.1:5432/hola -c "select id, status from videos where title in ('AI batch 1','AI batch 2','AI batch 3') order by id;"
psql postgresql://hola:hola@127.0.0.1:5432/hola -c "select video_id, count(*) from analysis_results where video_id in (select id from videos where title in ('AI batch 1','AI batch 2','AI batch 3')) group by video_id order by video_id;"
```

Expected:

```text
XPENDING count = 0
each video status is done or failed
no video has duplicated segment rows from repeated callbacks
```

## Final Acceptance Criteria

- Spring contract tests pass.
- Python contract tests pass.
- Spring and Python are both running against the same Redis and callback secret.
- `POST /api/videos` creates a Redis Stream record with correct `videoId`, `gcsPath`, `callbackUrl`.
- AI worker consumes and ACKs the record; Redis PEL returns `0`.
- AI worker publishes at least one `PROCESSING` event and Spring stores/replays it.
- AI callback succeeds with `X-AI-Callback-Secret`.
- Spring stores `videos.status=done` and `analysis_results` rows for the happy path.
- Retry deletes previous rows and inserts a fresh set instead of appending duplicates.
- Wrong callback secret and malformed progress are observable in the correct DLQ.
- Missing GCS object reports `failed` to Spring without leaving Redis pending entries.
- A 3-job batch reaches terminal states without duplicate analysis rows.

## Execution Notes

- The 2026-06-09 local E2E smoke already proved the real path once: GCS object upload workaround, Redis dispatch, AI processing, callback, Spring `done`, 13 stored segments. This plan turns that smoke into a repeatable checklist.
- If MediaPipe fails inside Codex sandbox with Metal service errors, run the worker command from a normal macOS terminal outside sandbox. The API/Redis/DB observations remain the same.
- If GCS auth is unavailable, finish Task 1, Task 2, Task 3 health, and Task 7 security/DLQ checks; mark happy-path GCS download as blocked by credentials rather than as integration failure.
