# Admin Operations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build V1 admin operations APIs for dashboard, gym management, user management, report moderation, and audit logging.

**Architecture:** Add a focused `domain/admin` package that exposes `/api/admin/**` APIs and coordinates existing domain mappers. Add minimal role/status fields to `users`, derive Spring Security authorities from JWT role claims, and record every admin mutation in `admin_audit_logs`.

**Tech Stack:** Java 25, Spring Boot 4, Spring Security 7, MyBatis XML mappers, PostgreSQL, Redis token revocation, JUnit 5, MockMvc, Testcontainers.

---

## File Map

- Modify `db/schema.sql`: add `users.role`, `users.status`, `admin_audit_logs`, indexes.
- Create `db/manual-migrations/2026-06-06-admin-operations.sql`: idempotent migration for current dev/operating DB.
- Modify `src/test/resources/sql/users-schema.sql`: include role/status and audit log table for admin integration tests.
- Modify `src/test/resources/sql/gyms-schema.sql`, `reports-schema.sql`, `videos-schema.sql`: add data needed by admin tests when required.
- Modify `src/main/java/com/holaclimbing/server/domain/user/domain/User.java`: add `role`, `status`.
- Modify `src/main/java/com/holaclimbing/server/domain/user/mapper/UserMapper.java` and `src/main/resources/mapper/user/UserMapper.xml`: select role/status, admin search, status updates.
- Modify `src/main/java/com/holaclimbing/server/domain/user/service/UserServiceImpl.java`: reject suspended users at login.
- Modify `src/main/java/com/holaclimbing/server/common/security/JwtTokenProvider.java`: add role claim.
- Modify `src/main/java/com/holaclimbing/server/common/security/JwtAuthenticationFilter.java`: map role claim to authorities.
- Modify `src/main/java/com/holaclimbing/server/common/security/SecurityConfig.java`: protect `/api/admin/**`.
- Modify `src/main/java/com/holaclimbing/server/common/security/StompHandshakeInterceptor.java`: keep WebSocket aligned with token revocation; no admin-specific STOMP role required in V1.
- Modify `src/main/java/com/holaclimbing/server/common/exception/error/ErrorCode.java`: add `U012 USER_SUSPENDED`.
- Create `src/main/java/com/holaclimbing/server/domain/admin/**`: controllers, services, mappers, DTOs for admin APIs.
- Modify existing mapper XML files only where admin actions reuse domain soft-delete/update methods.
- Create `src/test/java/com/holaclimbing/server/domain/admin/AdminSecurityIntegrationTest.java`.
- Create `src/test/java/com/holaclimbing/server/domain/admin/AdminDashboardIntegrationTest.java`.
- Create `src/test/java/com/holaclimbing/server/domain/admin/AdminGymIntegrationTest.java`.
- Create `src/test/java/com/holaclimbing/server/domain/admin/AdminUserIntegrationTest.java`.
- Create `src/test/java/com/holaclimbing/server/domain/admin/AdminReportIntegrationTest.java`.

---

### Task 1: Admin Schema Foundation

**Files:**
- Modify: `db/schema.sql`
- Create: `db/manual-migrations/2026-06-06-admin-operations.sql`
- Modify: `src/test/resources/sql/users-schema.sql`

- [ ] **Step 1: Add schema to `db/schema.sql`**

Add `role` and `status` to the `users` table after `bio`:

```sql
    role                        VARCHAR(20) NOT NULL DEFAULT 'USER',
    status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
```

Add indexes after existing `idx_users_deleted_at`:

```sql
CREATE INDEX idx_users_role_status       ON users(role, status);
CREATE INDEX idx_users_status_created_at ON users(status, created_at DESC);
```

Add table after `users` indexes or near the common operations section:

```sql
CREATE TABLE admin_audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    admin_id    BIGINT NOT NULL REFERENCES users(id),
    action      VARCHAR(80) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id   BIGINT,
    reason      TEXT,
    before_json JSONB,
    after_json  JSONB,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_admin_audit_logs_admin_created
    ON admin_audit_logs(admin_id, created_at DESC);
CREATE INDEX idx_admin_audit_logs_target_created
    ON admin_audit_logs(target_type, target_id, created_at DESC);
```

- [ ] **Step 2: Create idempotent manual migration**

Create `db/manual-migrations/2026-06-06-admin-operations.sql`:

```sql
BEGIN;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'USER',
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_users_role_status
    ON users(role, status);
CREATE INDEX IF NOT EXISTS idx_users_status_created_at
    ON users(status, created_at DESC);

CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    admin_id    BIGINT NOT NULL REFERENCES users(id),
    action      VARCHAR(80) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id   BIGINT,
    reason      TEXT,
    before_json JSONB,
    after_json  JSONB,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_admin_created
    ON admin_audit_logs(admin_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_target_created
    ON admin_audit_logs(target_type, target_id, created_at DESC);

COMMIT;
```

- [ ] **Step 3: Update test users schema**

In `src/test/resources/sql/users-schema.sql`, add the same `role`, `status`, indexes, and `admin_audit_logs` table. Also ensure the file drops `admin_audit_logs` before `users`:

```sql
DROP TABLE IF EXISTS admin_audit_logs CASCADE;
```

- [ ] **Step 4: Run schema validation**

Run:

```bash
git diff --check
```

Expected: exit 0 with no whitespace errors.

- [ ] **Step 5: Commit**

```bash
git add db/schema.sql db/manual-migrations/2026-06-06-admin-operations.sql src/test/resources/sql/users-schema.sql
git commit -m "feat(admin): add admin role status and audit schema"
```

---

### Task 2: Role And Status In User/Auth

**Files:**
- Modify: `src/main/java/com/holaclimbing/server/domain/user/domain/User.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/user/mapper/UserMapper.java`
- Modify: `src/main/resources/mapper/user/UserMapper.xml`
- Modify: `src/main/java/com/holaclimbing/server/domain/user/service/UserServiceImpl.java`
- Modify: `src/main/java/com/holaclimbing/server/common/exception/error/ErrorCode.java`

- [ ] **Step 1: Write failing login status test**

Add to `src/test/java/com/holaclimbing/server/domain/user/UserAuthIntegrationTest.java`:

```java
@Test
@DisplayName("로그인 실패 - 정지 회원은 403 U012")
void login_suspendedUser_returns403() throws Exception {
    signup(EMAIL, PASSWORD, NICKNAME).andExpect(status().isCreated());
    verifyEmailOf(EMAIL);
    var user = userMapper.findByEmail(EMAIL);
    userMapper.updateStatus(user.getId(), "SUSPENDED");

    login(EMAIL, PASSWORD)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("U012"));
}
```

Add mapper method used by the test:

```java
int updateStatus(@Param("id") Long id, @Param("status") String status);
```

- [ ] **Step 2: Run failing test**

```bash
./mvnw -Dtest=UserAuthIntegrationTest#login_suspendedUser_returns403 test
```

Expected: compile failure until `updateStatus` and `User.status` exist, then behavior failure until login rejects suspended users.

- [ ] **Step 3: Add fields and mapper columns**

In `User.java`, add:

```java
private String role;
private String status;
```

In `UserMapper.xml` columns, include:

```sql
role, status,
```

In `UserMapper.xml`, add:

```xml
<update id="updateStatus">
    UPDATE users
    SET status = #{status}, updated_at = NOW()
    WHERE id = #{id} AND deleted_at IS NULL
</update>
```

- [ ] **Step 4: Add suspended error**

In `ErrorCode.java`, add after `INVALID_RESET_TOKEN`:

```java
USER_SUSPENDED(HttpStatus.FORBIDDEN, "U012", "정지된 계정입니다."),
```

- [ ] **Step 5: Reject suspended login**

In `UserServiceImpl.login`, after loading the user and before password checks, add:

```java
if (!"ACTIVE".equals(user.getStatus())) {
    throw new BusinessException(ErrorCode.USER_SUSPENDED);
}
```

- [ ] **Step 6: Run test**

```bash
./mvnw -Dtest=UserAuthIntegrationTest#login_suspendedUser_returns403 test
```

Expected: pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/holaclimbing/server/domain/user src/main/resources/mapper/user/UserMapper.xml src/main/java/com/holaclimbing/server/common/exception/error/ErrorCode.java src/test/java/com/holaclimbing/server/domain/user/UserAuthIntegrationTest.java
git commit -m "feat(auth): support user role and status"
```

---

### Task 3: JWT Role Claim And Admin Security

**Files:**
- Modify: `src/main/java/com/holaclimbing/server/common/security/JwtTokenProvider.java`
- Modify: `src/main/java/com/holaclimbing/server/common/security/JwtAuthenticationFilter.java`
- Modify: `src/main/java/com/holaclimbing/server/common/security/SecurityConfig.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/user/service/UserServiceImpl.java`
- Create: `src/test/java/com/holaclimbing/server/domain/admin/AdminSecurityIntegrationTest.java`

- [ ] **Step 1: Write failing admin security tests**

Create `AdminSecurityIntegrationTest`:

```java
package com.holaclimbing.server.domain.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
import com.holaclimbing.server.domain.user.dto.request.LoginRequest;
import com.holaclimbing.server.domain.user.dto.request.SignupRequest;
import com.holaclimbing.server.domain.user.dto.request.VerifyEmailRequest;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = "classpath:sql/users-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminSecurityIntegrationTest {
    private static final String PASSWORD = "password123";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserMapper userMapper;

    @Test
    @DisplayName("관리자 API - 비로그인은 401")
    void adminApi_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("관리자 API - 일반 사용자는 403")
    void adminApi_userRole_returns403() throws Exception {
        String token = registerAndLogin("user@hola.com", "normaluser", "USER");

        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자 API - 관리자는 접근 가능")
    void adminApi_adminRole_returnsOk() throws Exception {
        String token = registerAndLogin("admin@hola.com", "adminuser", "ADMIN");

        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String registerAndLogin(String email, String nickname, String role) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(email, PASSWORD, nickname))))
                .andExpect(status().isCreated());
        var user = userMapper.findByEmail(email);
        userMapper.updateRole(user.getId(), role);
        mockMvc.perform(post("/api/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest(user.getEmailVerificationToken()))))
                .andExpect(status().isOk());
        return objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }
}
```

- [ ] **Step 2: Run failing security tests**

```bash
./mvnw -Dtest=AdminSecurityIntegrationTest test
```

Expected: compile failure until admin dashboard endpoint and `updateRole` exist, then 403/200 behavior failure until JWT role authorities are implemented.

- [ ] **Step 3: Add role update mapper for tests and admin service**

In `UserMapper.java`:

```java
int updateRole(@Param("id") Long id, @Param("role") String role);
```

In `UserMapper.xml`:

```xml
<update id="updateRole">
    UPDATE users
    SET role = #{role}, updated_at = NOW()
    WHERE id = #{id} AND deleted_at IS NULL
</update>
```

- [ ] **Step 4: Add role claim to token provider**

In `JwtTokenProvider.java`, add:

```java
public static final String CLAIM_ROLE = "role";
```

Change access token creation to accept role:

```java
public String createAccessToken(Long userId, String email, String role) {
    return createToken(userId, email, role, TYPE_ACCESS,
            Duration.ofMinutes(props.accessTokenValidityMinutes()));
}
```

Change `createToken` signature and set claim:

```java
private String createToken(Long userId, String email, String role, String type, Duration validity) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + validity.toMillis());

    var builder = Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(String.valueOf(userId))
            .issuer(props.issuer())
            .issuedAt(now)
            .expiration(expiry)
            .claim(CLAIM_TYPE, type);

    if (email != null) {
        builder.claim(CLAIM_EMAIL, email);
    }
    if (role != null) {
        builder.claim(CLAIM_ROLE, role);
    }

    return builder.signWith(key, Jwts.SIG.HS256).compact();
}
```

Refresh tokens keep no role:

```java
public String createRefreshToken(Long userId) {
    return createToken(userId, null, null, TYPE_REFRESH,
            Duration.ofDays(props.refreshTokenValidityDays()));
}
```

- [ ] **Step 5: Pass role from login/refresh**

In `UserServiceImpl`, change access token creation:

```java
String accessToken = tokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
```

For refresh, reload user and use its current role:

```java
User user = userMapper.findById(userId);
if (user == null) {
    throw new BusinessException(ErrorCode.USER_NOT_FOUND);
}
if (!"ACTIVE".equals(user.getStatus())) {
    throw new BusinessException(ErrorCode.USER_SUSPENDED);
}
String accessToken = tokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
```

- [ ] **Step 6: Map role claim to authorities**

In `JwtAuthenticationFilter`, replace fixed authority creation:

```java
String role = claims.get(JwtTokenProvider.CLAIM_ROLE, String.class);
String authority = "ADMIN".equals(role) ? "ROLE_ADMIN" : "ROLE_USER";
var auth = new UsernamePasswordAuthenticationToken(
        userId, null, List.of(new SimpleGrantedAuthority(authority)));
```

- [ ] **Step 7: Protect admin routes**

In `SecurityConfig`, add before user routes:

```java
.requestMatchers("/api/admin/**").hasRole("ADMIN")
```

Keep V1 fallback:

```java
.anyRequest().authenticated()
```

Only after all existing public GET routes are explicitly listed.

- [ ] **Step 8: Add temporary dashboard endpoint**

Create `AdminDashboardController` with a stub that returns zeros. The real service replaces this stub in Task 5.

```java
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {
    @GetMapping
    public ApiResponse<AdminDashboardResponse> getDashboard() {
        return ApiResponse.success(new AdminDashboardResponse(0, 0, 0, 0));
    }
}
```

Create response:

```java
public record AdminDashboardResponse(
        long pendingGymCount,
        long pendingReportCount,
        long failedAnalysisVideoCount,
        long newUserCountToday
) {
}
```

- [ ] **Step 9: Run admin security tests**

```bash
./mvnw -Dtest=AdminSecurityIntegrationTest test
```

Expected: pass.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/holaclimbing/server/common/security src/main/java/com/holaclimbing/server/domain/user src/main/resources/mapper/user/UserMapper.xml src/main/java/com/holaclimbing/server/domain/admin src/test/java/com/holaclimbing/server/domain/admin/AdminSecurityIntegrationTest.java
git commit -m "feat(admin): secure admin api with role claim"
```

---

### Task 4: Audit Logging Service

**Files:**
- Create: `src/main/java/com/holaclimbing/server/domain/admin/domain/AdminAuditLog.java`
- Create: `src/main/java/com/holaclimbing/server/domain/admin/mapper/AdminAuditLogMapper.java`
- Create: `src/main/resources/mapper/admin/AdminAuditLogMapper.xml`
- Create: `src/main/java/com/holaclimbing/server/domain/admin/service/AdminAuditService.java`
- Create: `src/main/java/com/holaclimbing/server/domain/admin/service/AdminAuditServiceImpl.java`
- Create: `src/main/java/com/holaclimbing/server/domain/admin/dto/response/AdminAuditLogResponse.java`
- Create: `src/main/java/com/holaclimbing/server/domain/admin/AdminAuditLogController.java`

- [ ] **Step 1: Write failing audit read test**

Add to a new `AdminAuditLogIntegrationTest`:

```java
@Test
@DisplayName("관리자 감사 로그 - 목록 조회")
void getAuditLogs_returnsLogs() throws Exception {
    String admin = registerAndLoginAdmin();
    adminAuditLogMapper.insert(AdminAuditLog.builder()
            .adminId(1L)
            .action("GYM_APPROVE")
            .targetType("gym")
            .targetId(1L)
            .reason("검수 완료")
            .beforeJson("{\"status\":\"pending\"}")
            .afterJson("{\"status\":\"active\"}")
            .build());

    mockMvc.perform(get("/api/admin/audit-logs")
                    .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].action").value("GYM_APPROVE"));
}
```

- [ ] **Step 2: Add domain**

```java
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditLog {
    private Long id;
    private Long adminId;
    private String action;
    private String targetType;
    private Long targetId;
    private String reason;
    private String beforeJson;
    private String afterJson;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: Add mapper**

```java
@Mapper
public interface AdminAuditLogMapper {
    void insert(AdminAuditLog log);
    List<AdminAuditLog> search(@Param("targetType") String targetType,
                                @Param("targetId") Long targetId,
                                @Param("adminId") Long adminId,
                                @Param("size") int size,
                                @Param("offset") int offset);
    long countSearch(@Param("targetType") String targetType,
                     @Param("targetId") Long targetId,
                     @Param("adminId") Long adminId);
}
```

XML:

```xml
<insert id="insert" parameterType="com.holaclimbing.server.domain.admin.domain.AdminAuditLog"
        useGeneratedKeys="true" keyProperty="id">
    INSERT INTO admin_audit_logs
        (admin_id, action, target_type, target_id, reason, before_json, after_json)
    VALUES
        (#{adminId}, #{action}, #{targetType}, #{targetId}, #{reason},
         #{beforeJson}::jsonb, #{afterJson}::jsonb)
</insert>

<select id="search" resultType="com.holaclimbing.server.domain.admin.domain.AdminAuditLog">
    SELECT id, admin_id, action, target_type, target_id, reason,
           before_json, after_json, created_at
    FROM admin_audit_logs
    WHERE 1 = 1
    <if test="targetType != null and targetType != ''">AND target_type = #{targetType}</if>
    <if test="targetId != null">AND target_id = #{targetId}</if>
    <if test="adminId != null">AND admin_id = #{adminId}</if>
    ORDER BY created_at DESC, id DESC
    LIMIT #{size} OFFSET #{offset}
</select>
```

- [ ] **Step 4: Add service helper**

```java
public interface AdminAuditService {
    void record(Long adminId, String action, String targetType, Long targetId,
                String reason, Object before, Object after);
    PageResponse<AdminAuditLogResponse> search(String targetType, Long targetId, Long adminId, int page, int size);
}
```

`AdminAuditServiceImpl.record` serializes `before` and `after` with `ObjectMapper.writeValueAsString`, using `"{}"` when value is null.

- [ ] **Step 5: Add controller**

```java
@GetMapping("/api/admin/audit-logs")
public ApiResponse<PageResponse<AdminAuditLogResponse>> search(
        @RequestParam(required = false) String targetType,
        @RequestParam(required = false) Long targetId,
        @RequestParam(required = false) Long adminId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
    return ApiResponse.success(adminAuditService.search(targetType, targetId, adminId, page, size));
}
```

- [ ] **Step 6: Run audit tests**

```bash
./mvnw -Dtest=AdminAuditLogIntegrationTest test
```

Expected: pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/holaclimbing/server/domain/admin src/main/resources/mapper/admin src/test/java/com/holaclimbing/server/domain/admin/AdminAuditLogIntegrationTest.java
git commit -m "feat(admin): add audit log service"
```

---

### Task 5: Admin Dashboard

**Files:**
- Create: `src/main/java/com/holaclimbing/server/domain/admin/mapper/AdminDashboardMapper.java`
- Create: `src/main/resources/mapper/admin/AdminDashboardMapper.xml`
- Create: `src/main/java/com/holaclimbing/server/domain/admin/service/AdminDashboardService.java`
- Create: `src/main/java/com/holaclimbing/server/domain/admin/service/AdminDashboardServiceImpl.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/admin/AdminDashboardController.java`
- Create: `src/test/java/com/holaclimbing/server/domain/admin/AdminDashboardIntegrationTest.java`

- [ ] **Step 1: Write failing dashboard test**

```java
@Test
@DisplayName("관리자 홈 - 처리 대기 카운터를 반환한다")
void getDashboard_returnsWorkQueueCounts() throws Exception {
    String admin = registerAndLoginAdmin();

    mockMvc.perform(get("/api/admin/dashboard")
                    .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.pendingGymCount").isNumber())
            .andExpect(jsonPath("$.data.pendingReportCount").isNumber())
            .andExpect(jsonPath("$.data.failedAnalysisVideoCount").isNumber())
            .andExpect(jsonPath("$.data.newUserCountToday").isNumber());
}
```

- [ ] **Step 2: Add mapper**

```java
@Mapper
public interface AdminDashboardMapper {
    long countPendingGyms();
    long countPendingReports();
    long countFailedAnalysisVideos();
    long countNewUsersToday();
}
```

XML:

```xml
<select id="countPendingGyms" resultType="long">
    SELECT COUNT(*) FROM gyms WHERE status = 'pending' AND deleted_at IS NULL
</select>
<select id="countPendingReports" resultType="long">
    SELECT COUNT(*) FROM reports WHERE status = 'pending'
</select>
<select id="countFailedAnalysisVideos" resultType="long">
    SELECT COUNT(*) FROM videos WHERE status = 'failed' AND deleted_at IS NULL
</select>
<select id="countNewUsersToday" resultType="long">
    SELECT COUNT(*) FROM users
    WHERE deleted_at IS NULL AND created_at::date = CURRENT_DATE
</select>
```

- [ ] **Step 3: Replace dashboard stub with service**

```java
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {
    private final AdminDashboardMapper mapper;

    @Override
    public AdminDashboardResponse getDashboard() {
        return new AdminDashboardResponse(
                mapper.countPendingGyms(),
                mapper.countPendingReports(),
                mapper.countFailedAnalysisVideos(),
                mapper.countNewUsersToday());
    }
}
```

- [ ] **Step 4: Run dashboard tests**

```bash
./mvnw -Dtest=AdminDashboardIntegrationTest,AdminSecurityIntegrationTest test
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/holaclimbing/server/domain/admin src/main/resources/mapper/admin src/test/java/com/holaclimbing/server/domain/admin/AdminDashboardIntegrationTest.java
git commit -m "feat(admin): add operations dashboard"
```

---

### Task 6: Admin Gym Management

**Files:**
- Create: `src/main/java/com/holaclimbing/server/domain/admin/AdminGymController.java`
- Create: `src/main/java/com/holaclimbing/server/domain/admin/service/AdminGymService.java`
- Create: `src/main/java/com/holaclimbing/server/domain/admin/service/AdminGymServiceImpl.java`
- Create: `src/main/java/com/holaclimbing/server/domain/admin/mapper/AdminGymMapper.java`
- Create: `src/main/resources/mapper/admin/AdminGymMapper.xml`
- Create DTOs under `src/main/java/com/holaclimbing/server/domain/admin/dto/request` and `response`
- Create: `src/test/java/com/holaclimbing/server/domain/admin/AdminGymIntegrationTest.java`

- [ ] **Step 1: Write failing gym tests**

Add tests:

```java
@Test
@DisplayName("관리자 암장 - pending 목록 조회")
void searchGyms_pending_returnsRows() throws Exception {
    String admin = registerAndLoginAdmin();

    mockMvc.perform(get("/api/admin/gyms")
                    .param("status", "pending")
                    .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray());
}

@Test
@DisplayName("관리자 암장 - 제안 승인")
void approveGym_changesStatusToActive() throws Exception {
    String admin = registerAndLoginAdmin();
    long pendingGymId = insertPendingGym();

    mockMvc.perform(post("/api/admin/gyms/" + pendingGymId + "/approve")
                    .header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"정보 확인 완료\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("active"));

    mockMvc.perform(get("/api/gyms/" + pendingGymId))
            .andExpect(status().isOk());
}
```

- [ ] **Step 2: Add request/response DTOs**

Create:

```java
public record AdminGymSearchResponse(
        Long id,
        String name,
        String address,
        String regionCode,
        String status,
        Long createdBy,
        BigDecimal ratingAvg,
        int ratingCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
```

```java
public record AdminReasonRequest(
        @Size(max = 500) String reason
) {
}
```

```java
public record AdminGymUpsertRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 200) String address,
        Double lat,
        Double lng,
        @Size(max = 30) String phone,
        @Size(max = 300) String website,
        String description,
        Map<String, DayHours> businessHours,
        @Size(max = 20) String regionCode
) {
}
```

- [ ] **Step 3: Add mapper methods**

```java
List<Gym> search(@Param("status") String status,
                 @Param("keyword") String keyword,
                 @Param("regionCode") String regionCode,
                 @Param("size") int size,
                 @Param("offset") int offset);
long countSearch(@Param("status") String status,
                 @Param("keyword") String keyword,
                 @Param("regionCode") String regionCode);
Gym findByIdAnyStatus(Long gymId);
int updateStatus(@Param("gymId") Long gymId, @Param("status") String status);
int updateGym(@Param("gymId") Long gymId, @Param("request") AdminGymUpsertRequest request,
              @Param("businessHours") String businessHours);
```

- [ ] **Step 4: Add service behavior**

`approveGym`:

```java
Gym before = requireGymAnyStatus(gymId);
adminGymMapper.updateStatus(gymId, "active");
Gym after = requireGymAnyStatus(gymId);
audit.record(adminId, "GYM_APPROVE", "gym", gymId, request.reason(), before, after);
return GymDetailResponse.of(after, parseBusinessHours(after.getBusinessHours()), photosOf(gymId));
```

`rejectGym` sets `closed`.  
`closeGym` sets `closed`.  
`createGym` inserts directly with `status = active` and `createdBy = adminId`.  
`updateGym` edits fields but keeps status.

- [ ] **Step 5: Add controller routes**

```java
@RestController
@RequestMapping("/api/admin/gyms")
@RequiredArgsConstructor
@Validated
public class AdminGymController {
    private final AdminGymService service;

    @GetMapping
    public ApiResponse<PageResponse<AdminGymSearchResponse>> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String regionCode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
        return ApiResponse.success(service.search(status, keyword, regionCode, page, size));
    }

    @PostMapping("/{gymId}/approve")
    public ApiResponse<GymDetailResponse> approve(@AuthenticationPrincipal Long adminId,
                                                  @PathVariable Long gymId,
                                                  @Valid @RequestBody AdminReasonRequest request) {
        return ApiResponse.success(service.approveGym(adminId, gymId, request));
    }
}
```

- [ ] **Step 6: Run gym admin tests**

```bash
./mvnw -Dtest=AdminGymIntegrationTest test
```

Expected: pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/holaclimbing/server/domain/admin src/main/resources/mapper/admin src/test/java/com/holaclimbing/server/domain/admin/AdminGymIntegrationTest.java
git commit -m "feat(admin): add gym management api"
```

---

### Task 7: Admin Gym Grades And JSON Import

**Files:**
- Modify: `src/main/java/com/holaclimbing/server/domain/admin/AdminGymController.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/admin/service/AdminGymService.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/admin/service/AdminGymServiceImpl.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/admin/mapper/AdminGymMapper.java`
- Modify: `src/main/resources/mapper/admin/AdminGymMapper.xml`
- Add DTOs under `src/main/java/com/holaclimbing/server/domain/admin/dto/request` and `response`
- Modify: `src/test/java/com/holaclimbing/server/domain/admin/AdminGymIntegrationTest.java`

- [ ] **Step 1: Write failing grade replacement test**

```java
@Test
@DisplayName("관리자 암장 - 난이도 목록 교체")
void replaceGrades_replacesActiveGrades() throws Exception {
    String admin = registerAndLoginAdmin();

    mockMvc.perform(put("/api/admin/gyms/1/grades")
                    .header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"grades":[
                          {"label":"노랑","difficultyOrder":10},
                          {"label":"파랑","difficultyOrder":20}
                        ],"reason":"운영 난이도 갱신"}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].label").value("노랑"));
}
```

- [ ] **Step 2: Write failing import preview/apply tests**

```java
@Test
@DisplayName("관리자 암장 일괄입력 - preview는 저장하지 않고 오류 행을 반환한다")
void previewImport_returnsInvalidRowsWithoutSaving() throws Exception {
    String admin = registerAndLoginAdmin();

    mockMvc.perform(post("/api/admin/gyms/import/preview")
                    .header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"rows":[
                          {"externalKey":"ok-1","name":"정상 암장","address":"서울","lat":37.1,"lng":127.1,
                           "regionCode":"seoul","grades":[{"label":"노랑","difficultyOrder":10}]},
                          {"externalKey":"bad-1","name":"","address":"서울","grades":[]}
                        ]}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.validCount").value(1))
            .andExpect(jsonPath("$.data.invalidRows[0].externalKey").value("bad-1"));
}
```

- [ ] **Step 3: Add DTOs**

```java
public record AdminGymGradeReplaceRequest(
        @NotEmpty List<AdminGymGradeRequest> grades,
        @Size(max = 500) String reason
) {
}

public record AdminGymGradeRequest(
        @NotBlank @Size(max = 50) String label,
        @NotNull Integer difficultyOrder
) {
}

public record AdminGymImportRequest(
        @NotEmpty List<AdminGymImportRow> rows
) {
}

public record AdminGymImportRow(
        @NotBlank String externalKey,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 200) String address,
        Double lat,
        Double lng,
        @Size(max = 20) String regionCode,
        @Size(max = 30) String phone,
        @Size(max = 300) String website,
        String description,
        Map<String, DayHours> businessHours,
        List<AdminGymGradeRequest> grades
) {
}
```

- [ ] **Step 4: Add mapper grade methods**

```java
int deactivateGrades(Long gymId);
void insertGrade(@Param("gymId") Long gymId,
                 @Param("label") String label,
                 @Param("difficultyOrder") int difficultyOrder);
```

XML:

```xml
<update id="deactivateGrades">
    UPDATE gym_grades
    SET is_active = FALSE, updated_at = NOW()
    WHERE gym_id = #{gymId} AND is_active = TRUE
</update>

<insert id="insertGrade">
    INSERT INTO gym_grades (gym_id, label, difficulty_order, is_active)
    VALUES (#{gymId}, #{label}, #{difficultyOrder}, TRUE)
    ON CONFLICT (gym_id, label)
    DO UPDATE SET difficulty_order = EXCLUDED.difficulty_order,
                  is_active = TRUE,
                  updated_at = NOW()
</insert>
```

- [ ] **Step 5: Implement preview validation**

Validation rules:

```java
if (row.name() == null || row.name().isBlank()) errors.add("name is required");
if (row.lat() != null && (row.lat() < -90 || row.lat() > 90)) errors.add("lat must be -90..90");
if (row.lng() != null && (row.lng() < -180 || row.lng() > 180)) errors.add("lng must be -180..180");
if (row.grades() == null || row.grades().isEmpty()) errors.add("at least one grade is required");
```

Preview returns:

```java
public record AdminGymImportPreviewResponse(
        int totalCount,
        int validCount,
        int invalidCount,
        List<AdminGymImportInvalidRowResponse> invalidRows
) {
}
```

- [ ] **Step 6: Implement apply import**

Apply rejects payload if any invalid row exists:

```java
if (!invalidRows.isEmpty()) {
    throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 암장 행이 있습니다.");
}
```

For each row, insert gym with `status = active`, replace grades, and record one audit action `GYM_IMPORT`.

- [ ] **Step 7: Run import tests**

```bash
./mvnw -Dtest=AdminGymIntegrationTest test
```

Expected: pass.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/holaclimbing/server/domain/admin src/main/resources/mapper/admin src/test/java/com/holaclimbing/server/domain/admin/AdminGymIntegrationTest.java
git commit -m "feat(admin): add gym grade and import operations"
```

---

### Task 8: Admin User Management

**Files:**
- Create: `src/main/java/com/holaclimbing/server/domain/admin/AdminUserController.java`
- Create: `src/main/java/com/holaclimbing/server/domain/admin/service/AdminUserService.java`
- Create: `src/main/java/com/holaclimbing/server/domain/admin/service/AdminUserServiceImpl.java`
- Create DTOs under `src/main/java/com/holaclimbing/server/domain/admin/dto/request` and `response`
- Modify: `src/main/java/com/holaclimbing/server/domain/user/mapper/UserMapper.java`
- Modify: `src/main/resources/mapper/user/UserMapper.xml`
- Create: `src/test/java/com/holaclimbing/server/domain/admin/AdminUserIntegrationTest.java`

- [ ] **Step 1: Write failing user management tests**

```java
@Test
@DisplayName("관리자 회원 - 목록 검색")
void searchUsers_returnsUsers() throws Exception {
    String admin = registerAndLoginAdmin();

    mockMvc.perform(get("/api/admin/users")
                    .param("keyword", "climber")
                    .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray());
}

@Test
@DisplayName("관리자 회원 - 정지하면 기존 토큰이 무효화된다")
void suspendUser_revokesExistingToken() throws Exception {
    String admin = registerAndLoginAdmin();
    String userToken = registerAndLoginUser("u@hola.com", "targetuser");
    long userId = userMapper.findByEmail("u@hola.com").getId();

    mockMvc.perform(patch("/api/admin/users/" + userId + "/status")
                    .header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"SUSPENDED\",\"reason\":\"신고 누적\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

    mockMvc.perform(get("/api/users/me")
                    .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isUnauthorized());
}
```

- [ ] **Step 2: Add DTOs**

```java
public record AdminUserSearchResponse(
        Long id,
        String email,
        String nickname,
        String role,
        String status,
        boolean emailVerified,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {
}

public record AdminUserStatusRequest(
        @NotBlank String status,
        @Size(max = 500) String reason
) {
}
```

- [ ] **Step 3: Add mapper search**

```java
List<User> searchAdminUsers(@Param("status") String status,
                            @Param("role") String role,
                            @Param("keyword") String keyword,
                            @Param("emailVerified") Boolean emailVerified,
                            @Param("size") int size,
                            @Param("offset") int offset);
long countAdminUsers(@Param("status") String status,
                     @Param("role") String role,
                     @Param("keyword") String keyword,
                     @Param("emailVerified") Boolean emailVerified);
```

XML:

```xml
<select id="searchAdminUsers" resultType="com.holaclimbing.server.domain.user.domain.User">
    SELECT <include refid="columns"/>
    FROM users
    WHERE deleted_at IS NULL
    <if test="status != null and status != ''">AND status = #{status}</if>
    <if test="role != null and role != ''">AND role = #{role}</if>
    <if test="emailVerified != null">AND email_verified = #{emailVerified}</if>
    <if test="keyword != null and keyword != ''">
        AND (email ILIKE '%' || #{keyword} || '%' OR nickname ILIKE '%' || #{keyword} || '%')
    </if>
    ORDER BY created_at DESC, id DESC
    LIMIT #{size} OFFSET #{offset}
</select>
```

- [ ] **Step 4: Implement service**

`changeStatus` validates:

```java
Set<String> statuses = Set.of("ACTIVE", "SUSPENDED", "DELETED");
if (!statuses.contains(request.status())) {
    throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 회원 상태입니다.");
}
if (adminId.equals(userId)) {
    throw new BusinessException(ErrorCode.INVALID_INPUT, "자기 자신의 상태는 변경할 수 없습니다.");
}
```

After update:

```java
if (!"ACTIVE".equals(request.status())) {
    userTokenRevoker.revokeAllFor(userId);
    deviceTokenMapper.deleteByUserId(userId);
}
audit.record(adminId, "USER_STATUS_CHANGE", "user", userId, request.reason(), before, after);
```

- [ ] **Step 5: Add controller**

```java
@PatchMapping("/{userId}/status")
public ApiResponse<AdminUserDetailResponse> changeStatus(
        @AuthenticationPrincipal Long adminId,
        @PathVariable Long userId,
        @Valid @RequestBody AdminUserStatusRequest request) {
    return ApiResponse.success(service.changeStatus(adminId, userId, request));
}
```

- [ ] **Step 6: Run user admin tests**

```bash
./mvnw -Dtest=AdminUserIntegrationTest test
```

Expected: pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/holaclimbing/server/domain/admin src/main/java/com/holaclimbing/server/domain/user src/main/resources/mapper/user/UserMapper.xml src/test/java/com/holaclimbing/server/domain/admin/AdminUserIntegrationTest.java
git commit -m "feat(admin): add user management api"
```

---

### Task 9: Admin Report Moderation

**Files:**
- Create: `src/main/java/com/holaclimbing/server/domain/admin/AdminReportController.java`
- Create: `src/main/java/com/holaclimbing/server/domain/admin/service/AdminReportService.java`
- Create: `src/main/java/com/holaclimbing/server/domain/admin/service/AdminReportServiceImpl.java`
- Create: `src/main/java/com/holaclimbing/server/domain/admin/mapper/AdminReportMapper.java`
- Create: `src/main/resources/mapper/admin/AdminReportMapper.xml`
- Create DTOs under `src/main/java/com/holaclimbing/server/domain/admin/dto/request` and `response`
- Modify existing mappers if delete/status methods are missing.
- Create: `src/test/java/com/holaclimbing/server/domain/admin/AdminReportIntegrationTest.java`

- [ ] **Step 1: Write failing report moderation tests**

```java
@Test
@DisplayName("관리자 신고 - pending 목록 조회")
void searchReports_pending_returnsRows() throws Exception {
    String admin = registerAndLoginAdmin();
    insertReport("video", 1L, "spam");

    mockMvc.perform(get("/api/admin/reports")
                    .param("status", "pending")
                    .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray());
}

@Test
@DisplayName("관리자 신고 - 영상 삭제 액션으로 해결 처리")
void resolveReport_deleteVideo_softDeletesVideo() throws Exception {
    String admin = registerAndLoginAdmin();
    long reportId = insertReport("video", 1L, "abuse");

    mockMvc.perform(patch("/api/admin/reports/" + reportId + "/status")
                    .header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"resolved\",\"resolutionAction\":\"delete_video\",\"reason\":\"정책 위반\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("resolved"));

    mockMvc.perform(get("/api/videos/1"))
            .andExpect(status().isNotFound());
}
```

- [ ] **Step 2: Add DTOs**

```java
public record AdminReportStatusRequest(
        @NotBlank String status,
        @NotBlank String resolutionAction,
        @Size(max = 500) String reason
) {
}
```

Allowed statuses:

```java
Set.of("reviewed", "resolved", "rejected")
```

Allowed actions:

```java
Set.of("none", "delete_video", "delete_comment", "suspend_user")
```

- [ ] **Step 3: Add mapper methods**

```java
List<Report> search(@Param("status") String status,
                    @Param("targetType") String targetType,
                    @Param("category") String category,
                    @Param("size") int size,
                    @Param("offset") int offset);
long countSearch(@Param("status") String status,
                 @Param("targetType") String targetType,
                 @Param("category") String category);
int updateStatus(@Param("reportId") Long reportId,
                 @Param("status") String status,
                 @Param("reviewedBy") Long reviewedBy);
```

XML update:

```xml
<update id="updateStatus">
    UPDATE reports
    SET status = #{status},
        reviewed_by = #{reviewedBy},
        reviewed_at = NOW()
    WHERE id = #{reportId}
</update>
```

- [ ] **Step 4: Implement resolution actions**

```java
switch (request.resolutionAction()) {
    case "none" -> { }
    case "delete_video" -> videoMapper.softDelete(report.getTargetId());
    case "delete_comment" -> commentMapper.softDelete(report.getTargetId());
    case "suspend_user" -> {
        Long targetUserId = resolveTargetUserId(report);
        userMapper.updateStatus(targetUserId, "SUSPENDED");
        userTokenRevoker.revokeAllFor(targetUserId);
        deviceTokenMapper.deleteByUserId(targetUserId);
    }
    default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 신고 처리 액션입니다.");
}
```

- [ ] **Step 5: Record audit log**

After updating report status:

```java
audit.record(adminId, "REPORT_STATUS_CHANGE", "report", reportId,
        request.reason(), before, after);
```

For destructive action, record extra action:

```java
audit.record(adminId, "MODERATION_DELETE_VIDEO", "video", report.getTargetId(),
        request.reason(), videoBefore, null);
```

- [ ] **Step 6: Run report admin tests**

```bash
./mvnw -Dtest=AdminReportIntegrationTest test
```

Expected: pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/holaclimbing/server/domain/admin src/main/resources/mapper/admin src/main/java/com/holaclimbing/server/domain/video src/main/resources/mapper/video src/test/java/com/holaclimbing/server/domain/admin/AdminReportIntegrationTest.java
git commit -m "feat(admin): add report moderation api"
```

---

### Task 10: API Documentation And Full Verification

**Files:**
- Modify: `README.md`
- Modify: `src/test/java/com/holaclimbing/server/common/exception/docs/ApiErrorCodesDocumentationTest.java` if documented error examples need updates.
- Modify: admin controllers with `@ApiErrorCodes`.

- [ ] **Step 1: Add README admin section**

Add an "운영자 API" section:

```markdown
## 운영자 API

`/api/admin/**`는 `ROLE_ADMIN` 토큰이 필요합니다.

- `GET /api/admin/dashboard`
- `GET /api/admin/gyms`
- `POST /api/admin/gyms/{gymId}/approve`
- `POST /api/admin/gyms/{gymId}/reject`
- `PUT /api/admin/gyms/{gymId}/grades`
- `POST /api/admin/gyms/import/preview`
- `POST /api/admin/gyms/import`
- `GET /api/admin/users`
- `PATCH /api/admin/users/{userId}/status`
- `GET /api/admin/reports`
- `PATCH /api/admin/reports/{reportId}/status`
- `GET /api/admin/audit-logs`
```

- [ ] **Step 2: Run targeted tests**

```bash
./mvnw -Dtest=AdminSecurityIntegrationTest,AdminDashboardIntegrationTest,AdminGymIntegrationTest,AdminUserIntegrationTest,AdminReportIntegrationTest,AdminAuditLogIntegrationTest,UserAuthIntegrationTest test
```

Expected: all selected tests pass.

- [ ] **Step 3: Run full test suite**

```bash
./mvnw test
```

Expected: all tests pass.

- [ ] **Step 4: Inspect status**

```bash
git status --short
```

Expected: only intended admin/security/docs files changed.

- [ ] **Step 5: Commit**

```bash
git add README.md src/main/java/com/holaclimbing/server/domain/admin src/test/java/com/holaclimbing/server/common/exception/docs/ApiErrorCodesDocumentationTest.java
git commit -m "docs(admin): document admin operations api"
```

---

## Self-Review Checklist

- Spec coverage: tasks cover RBAC, dashboard, gym management, JSON import, user management, report moderation, audit logs, tests, and docs.
- Placeholder scan: the plan gives concrete file paths, endpoints, and code shapes.
- Type consistency: role/status strings are uppercase on `users`; gym/report statuses preserve existing lowercase DB values.
- Scope check: V1 avoids frontend implementation, AI DLQ, terms administration, and role specialization.

## Execution Options

Plan complete and saved to `docs/superpowers/plans/2026-06-06-admin-operations.md`. Two execution options:

1. Subagent-Driven (recommended) - dispatch a fresh subagent per task, review between tasks, fast iteration.
2. Inline Execution - execute tasks in this session using executing-plans, batch execution with checkpoints.
