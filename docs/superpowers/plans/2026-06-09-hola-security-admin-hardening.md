# Hola Security And Admin Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 배포 차단급 보안 공백을 닫고, admin 권한 생명주기와 운영 필수 연동을 production 기준으로 채운다.

**Architecture:** P0는 보안 경계부터 닫는다: AI 콜백은 shared-secret filter로 분리하고, video 하위 리소스는 공통 접근 정책을 통해 public-or-owner 규칙을 강제한다. Admin은 bootstrap runner와 role 변경 API를 분리해 최초 운영자 생성과 이후 권한 관리를 모두 감사 가능하게 만든다. P1은 운영 하드닝과 성능/UX quick win으로 묶는다.

**Tech Stack:** Java 25, Spring Boot 4, Spring Security, MyBatis, PostgreSQL, Redis Streams, JUnit 5, MockMvc, Testcontainers.

---

## Orchestration Strategy

승인 후 `multi_agent_v1` worker를 사용한다. 각 worker는 disjoint write set을 갖고, 같은 파일을 건드리는 작업은 순차 실행한다.

1. Worker A: AI 콜백 인증.
2. Worker B: 비공개 영상 접근 정책.
3. Worker C: admin bootstrap + role lifecycle.
4. Worker D: 운영 하드닝 이메일/JWT/Actuator.
5. Worker E: P1 성능·검증 quick wins.

Controller는 각 worker 결과를 직접 diff review하고, 전체 `./mvnw test`를 마지막에 한 번 더 실행한다.

## File Map

- Create: `src/main/java/com/holaclimbing/server/common/security/AiCallbackSecretFilter.java`
- Create: `src/main/java/com/holaclimbing/server/common/security/AiCallbackProperties.java`
- Modify: `src/main/java/com/holaclimbing/server/common/security/SecurityConfig.java`
- Modify: `src/main/resources/application.yaml`
- Modify/Test: `src/test/java/com/holaclimbing/server/domain/analysis/AnalysisIntegrationTest.java`

- Create: `src/main/java/com/holaclimbing/server/domain/video/service/VideoAccessPolicy.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/video/VideoController.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/video/service/VideoService.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/video/service/VideoServiceImpl.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/video/service/CommentServiceImpl.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/analysis/AnalysisController.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/analysis/service/AnalysisService.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/analysis/service/AnalysisServiceImpl.java`
- Modify/Test: `src/test/java/com/holaclimbing/server/domain/video/VideoIntegrationTest.java`
- Modify/Test: `src/test/java/com/holaclimbing/server/domain/analysis/AnalysisIntegrationTest.java`

- Create: `src/main/java/com/holaclimbing/server/domain/admin/dto/request/AdminUserRoleRequest.java`
- Create: `src/main/java/com/holaclimbing/server/domain/admin/AdminBootstrapProperties.java`
- Create: `src/main/java/com/holaclimbing/server/domain/admin/AdminBootstrapRunner.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/admin/AdminUserController.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/admin/service/AdminUserService.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/admin/service/AdminUserServiceImpl.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/user/mapper/UserMapper.java`
- Modify: `src/main/resources/mapper/user/UserMapper.xml`
- Modify/Test: `src/test/java/com/holaclimbing/server/domain/admin/AdminUserIntegrationTest.java`
- Modify/Test: `src/test/java/com/holaclimbing/server/domain/admin/AdminSecurityIntegrationTest.java`

- Create: `src/main/java/com/holaclimbing/server/infrastructure/mail/VerificationEmailSender.java` replacement interface or refactor existing class.
- Create: `src/main/java/com/holaclimbing/server/infrastructure/mail/LoggingVerificationEmailSender.java`
- Create: `src/main/java/com/holaclimbing/server/infrastructure/mail/SmtpVerificationEmailSender.java`
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/main/java/com/holaclimbing/server/common/security/JwtTokenProvider.java`
- Modify/Test: `src/test/java/com/holaclimbing/server/common/security/SecurityConfigIntegrationTest.java`

- Modify: `src/main/resources/mapper/recommendation/RecommendationMapper.xml`
- Modify: `src/main/resources/mapper/gym/GymMapper.xml`
- Modify: `db/schema.sql`
- Modify: `src/main/java/com/holaclimbing/server/domain/stats/StatsController.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/stats/dto/request/CreateClimbingLogRequest.java`
- Modify: `src/main/resources/mapper/admin/AdminDashboardMapper.xml`

---

### Task 1: Protect AI Worker Callback

**Files:**
- Create: `src/main/java/com/holaclimbing/server/common/security/AiCallbackProperties.java`
- Create: `src/main/java/com/holaclimbing/server/common/security/AiCallbackSecretFilter.java`
- Modify: `src/main/java/com/holaclimbing/server/common/security/SecurityConfig.java`
- Modify: `src/main/resources/application.yaml`
- Test: `src/test/java/com/holaclimbing/server/domain/analysis/AnalysisIntegrationTest.java`

- [ ] **Step 1: Write failing tests**

Add tests:

```java
@Test
@DisplayName("AI 결과 수신은 콜백 시크릿이 없으면 401")
void ingestResult_withoutCallbackSecret_returns401() throws Exception {
    Long videoId = createPendingVideo(ownerToken);

    mockMvc.perform(post("/api/analysis/videos/{videoId}", videoId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"status":"done","segments":[]}
                            """))
            .andExpect(status().isUnauthorized());
}

@Test
@DisplayName("AI 결과 수신은 올바른 콜백 시크릿이면 성공")
void ingestResult_withCallbackSecret_returnsOk() throws Exception {
    Long videoId = createPendingVideo(ownerToken);

    mockMvc.perform(post("/api/analysis/videos/{videoId}", videoId)
                    .header("X-AI-Callback-Secret", "test-ai-callback-secret")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"status":"done","segments":[]}
                            """))
            .andExpect(status().isOk());
}
```

Set test property on the class:

```java
@SpringBootTest(properties = {
        "app.cors.allowed-origins=http://localhost:3000",
        "ai.callback-secret=test-ai-callback-secret"
})
```

- [ ] **Step 2: Run RED**

Run:

```bash
./mvnw -Dtest=AnalysisIntegrationTest test
```

Expected: the missing-secret test currently returns 200/4xx mismatch because the callback endpoint is `permitAll`.

- [ ] **Step 3: Implement secret properties**

Create:

```java
@ConfigurationProperties(prefix = "ai")
public record AiCallbackProperties(String callbackSecret) {
    public boolean configured() {
        return callbackSecret != null && !callbackSecret.isBlank();
    }
}
```

Enable it from `SecurityConfig` or a security configuration class.

- [ ] **Step 4: Implement filter**

Create a `OncePerRequestFilter` that only applies to `POST /api/analysis/**`. It must:

- read `X-AI-Callback-Secret`;
- return 401 if configured secret is missing or header mismatches;
- compare bytes with `MessageDigest.isEqual`;
- never log the provided secret.

- [ ] **Step 5: Wire filter before JWT filter**

In `SecurityConfig`, add `AiCallbackSecretFilter` before `JwtAuthenticationFilter`. Keep GET analysis paths unaffected because user-facing analysis query is under `/api/videos/{videoId}/analysis`.

- [ ] **Step 6: Run GREEN**

Run:

```bash
./mvnw -Dtest=AnalysisIntegrationTest test
```

Expected: callback auth tests pass.

---

### Task 2: Enforce Private Video Access Across Subresources

**Files:**
- Create: `src/main/java/com/holaclimbing/server/domain/video/service/VideoAccessPolicy.java`
- Modify: `VideoController`, `VideoService`, `VideoServiceImpl`, `CommentServiceImpl`
- Modify: `AnalysisController`, `AnalysisService`, `AnalysisServiceImpl`
- Test: `VideoIntegrationTest`, `AnalysisIntegrationTest`

- [ ] **Step 1: Write failing video tests**

Add cases proving another authenticated user cannot:

- `GET /api/videos/{privateVideoId}/status`;
- `POST /api/videos/{privateVideoId}/like`;
- `POST /api/videos/{privateVideoId}/comments`;
- `GET /api/videos/{privateVideoId}/comments`;
- `GET /api/videos/{privateVideoId}/analysis/stream`.

Expected response: 403 with existing `VIDEO_NOT_ACCESSIBLE` or `FORBIDDEN` mapping.

- [ ] **Step 2: Write failing analysis tests**

Add cases proving another user cannot:

- `GET /api/videos/{privateVideoId}/analysis`;
- `POST /api/videos/{privateVideoId}/analysis/feedback`.

- [ ] **Step 3: Run RED**

Run:

```bash
./mvnw -Dtest=VideoIntegrationTest,AnalysisIntegrationTest test
```

Expected: at least one endpoint currently returns 200/201.

- [ ] **Step 4: Create `VideoAccessPolicy`**

Expected behavior:

```java
public void requireViewable(Video video, Long viewerId) {
    if (!video.isPublic() && (viewerId == null || !video.getUserId().equals(viewerId))) {
        throw new BusinessException(ErrorCode.VIDEO_NOT_ACCESSIBLE);
    }
}
```

- [ ] **Step 5: Apply policy**

Apply the policy to:

- `VideoServiceImpl.getStatus(videoId, viewerId)`;
- `VideoServiceImpl.likeVideo`;
- `VideoServiceImpl.unlikeVideo`;
- `CommentServiceImpl.addComment`;
- `CommentServiceImpl.getComments`;
- `AnalysisServiceImpl.getAnalysis(videoId, viewerId)`;
- `AnalysisServiceImpl.submitFeedback`;
- `VideoController.streamAnalysisProgress`.

- [ ] **Step 6: Run GREEN**

Run:

```bash
./mvnw -Dtest=VideoIntegrationTest,AnalysisIntegrationTest test
```

Expected: private subresource tests pass and existing public video tests still pass.

---

### Task 3: Implement Admin Bootstrap And Role Lifecycle

**Files:**
- Create: `AdminUserRoleRequest.java`
- Create: `AdminBootstrapProperties.java`
- Create: `AdminBootstrapRunner.java`
- Modify: `AdminUserController.java`
- Modify: `AdminUserService.java`
- Modify: `AdminUserServiceImpl.java`
- Modify: `UserMapper.java`
- Modify: `UserMapper.xml`
- Test: `AdminUserIntegrationTest.java`, `AdminSecurityIntegrationTest.java`

- [ ] **Step 1: Write failing role API tests**

Add tests:

- admin can promote a normal verified active user to `ADMIN`;
- promoted user can access `/api/admin/dashboard` after re-login;
- admin can demote another admin to `USER`;
- demoted admin token is revoked;
- last remaining admin cannot be demoted;
- admin cannot demote self.

- [ ] **Step 2: Run RED**

Run:

```bash
./mvnw -Dtest=AdminUserIntegrationTest,AdminSecurityIntegrationTest test
```

Expected: role endpoint is missing and tests fail with 404.

- [ ] **Step 3: Add request DTO**

Create request with:

- `role`: required, pattern `USER|ADMIN`;
- `reason`: required, max 500.

- [ ] **Step 4: Add mapper support**

Add:

```java
long countActiveAdmins();
long countActiveAdminsExcluding(@Param("userId") Long userId);
```

SQL must count `deleted_at IS NULL`, `status = 'ACTIVE'`, `role = 'ADMIN'`.

- [ ] **Step 5: Add service behavior**

`changeRole(adminId, userId, request)` must:

- reject self role change;
- require target user exists;
- reject unsupported role;
- if demoting `ADMIN -> USER`, block when target is last active admin;
- call `userMapper.updateRole`;
- call `revokeActiveSessions(userId)`;
- record audit action `USER_ROLE_CHANGE`.

- [ ] **Step 6: Add controller endpoint**

Add:

```java
@PatchMapping("/{userId}/role")
public ApiResponse<AdminUserDetailResponse> changeRole(...)
```

- [ ] **Step 7: Add bootstrap runner**

Properties:

```yaml
app:
  admin:
    bootstrap:
      enabled: ${ADMIN_BOOTSTRAP_ENABLED:false}
      email: ${ADMIN_BOOTSTRAP_EMAIL:}
```

Runner behavior:

- if disabled or email blank, no-op;
- find active verified user by email;
- if already admin, no-op;
- promote to admin and log only masked email;
- never create a password or user automatically.

- [ ] **Step 8: Run GREEN**

Run:

```bash
./mvnw -Dtest=AdminUserIntegrationTest,AdminSecurityIntegrationTest test
```

Expected: admin lifecycle tests pass.

---

### Task 4: Production Hardening For JWT, Actuator, Mail

**Files:**
- Modify: `JwtTokenProvider.java`
- Modify: `SecurityConfig.java`
- Modify: `application.yaml`, `application-prod.yml`
- Modify/Create: mail sender classes under `src/main/java/com/holaclimbing/server/infrastructure/mail`
- Modify: `pom.xml`
- Test: `SecurityConfigIntegrationTest.java`, auth integration tests.

- [ ] **Step 1: Write failing actuator tests**

Add tests:

- `GET /actuator/health` is public;
- `GET /actuator/prometheus` is 401 without token.

- [ ] **Step 2: Write JWT default-secret guard test**

Add a small context test that starts with `prod` profile and the current default secret. Expected: context fails with `IllegalStateException`.

- [ ] **Step 3: Refactor mail sender**

Convert `VerificationEmailSender` into an interface:

```java
public interface VerificationEmailSender {
    void send(String toEmail, String token);
    void sendPasswordReset(String toEmail, String token);
}
```

Create:

- `LoggingVerificationEmailSender`: active when `app.mail.mode=log`, not allowed in `prod`;
- `SmtpVerificationEmailSender`: active when `app.mail.mode=smtp`.

- [ ] **Step 4: Add dependency**

Add `spring-boot-starter-mail` to `pom.xml`.

- [ ] **Step 5: Harden config**

Set:

```yaml
app:
  mail:
    mode: ${APP_MAIL_MODE:log}
```

In `application-prod.yml`, require `APP_MAIL_MODE=smtp` through startup validation.

- [ ] **Step 6: Run GREEN**

Run:

```bash
./mvnw -Dtest=SecurityConfigIntegrationTest,UserAuthIntegrationTest test
```

Expected: health remains public, prometheus is protected, auth tests still pass.

---

### Task 5: Make AI Dispatch Failure Visible

**Files:**
- Modify: `RedisStreamAnalysisJobQueue.java`
- Modify: `AnalysisDispatcher.java`
- Modify: `AnalysisDispatcherTest.java`

- [ ] **Step 1: Write failing test**

Add test: when queue enqueue fails, video analysis progress becomes `FAILED` and message explains dispatch failure.

- [ ] **Step 2: Run RED**

Run:

```bash
./mvnw -Dtest=AnalysisDispatcherTest test
```

Expected: current code only logs warn and does not publish failed progress.

- [ ] **Step 3: Change queue contract**

Let `RedisStreamAnalysisJobQueue.enqueue` throw an unchecked exception after logging context, or remove catch and let caller handle.

- [ ] **Step 4: Handle failure in dispatcher**

In `AnalysisDispatcher`, catch enqueue failure and save/publish:

- stage: `FAILED`;
- message: `분석 대기열 등록 실패`;
- do not mark success state after failed enqueue.

- [ ] **Step 5: Run GREEN**

Run:

```bash
./mvnw -Dtest=AnalysisDispatcherTest test
```

Expected: failure is visible through status store/bus.

---

### Task 6: P1 Query, Validation, And Race Quick Wins

**Files:**
- Modify: `RecommendationMapper.xml`
- Modify: `StatsController.java`
- Modify: `CreateClimbingLogRequest.java`
- Modify: `AdminDashboardMapper.xml`
- Modify: `AdminGymImportRequest.java`
- Modify: `AdminGymGradeReplaceRequest.java`
- Modify: `GymMapper.xml`
- Modify: `db/schema.sql`
- Tests: related integration tests.

- [ ] **Step 1: Write failing tests for recommendation block filter**

Add test: after user A blocks uploader B, `GET /api/recommendations/videos` for A does not include B's public video.

- [ ] **Step 2: Add block exclusion SQL**

Add `NOT EXISTS` against `user_blocks` to `RecommendationMapper.findFeedVideos` and `countFeedVideos`.

- [ ] **Step 3: Validate stats calendar params**

Add `@Validated` to `StatsController`. Add:

```java
@RequestParam @Min(2000) @Max(2100) int year,
@RequestParam @Min(1) @Max(12) int month
```

- [ ] **Step 4: Validate grade count values**

Change request type to:

```java
@NotEmpty Map<@NotBlank String, @PositiveOrZero Integer> gradeCounts
```

- [ ] **Step 5: Add admin import limits**

Use:

```java
@NotEmpty @Size(max = 500) List<@Valid AdminGymImportRow> rows
@NotEmpty @Size(max = 100) List<@Valid AdminGymGradeRequest> grades
```

- [ ] **Step 6: Make dashboard date predicate sargable**

Replace `created_at::date = CURRENT_DATE` with range predicate.

- [ ] **Step 7: Add trigram index plan**

Add to `db/schema.sql`:

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_gyms_name_trgm ON gyms USING gin (name gin_trgm_ops);
```

Keep existing btree index for ordered name scans.

- [ ] **Step 8: Run GREEN**

Run:

```bash
./mvnw -Dtest=RecommendationIntegrationTest,StatsIntegrationTest,ClimbingLogIntegrationTest,AdminGymIntegrationTest test
```

Expected: block filter and validation tests pass.

---

## Final Verification

After all tasks:

```bash
./mvnw test
```

Required output:

- `BUILD SUCCESS`;
- zero failures/errors;
- no new secrets in logs;
- `git status --short` only contains intended files.

## Rollback Plan

- AI callback auth: remove filter bean and `ai.callback-secret` property.
- Video access policy: revert interface/controller signature changes together.
- Admin role lifecycle: remove endpoint/runner and keep existing status/revoke APIs.
- Mail hardening: switch `app.mail.mode=log` in non-prod only.
- SQL index changes: `DROP INDEX IF EXISTS idx_gyms_name_trgm;` and keep btree fallback.

