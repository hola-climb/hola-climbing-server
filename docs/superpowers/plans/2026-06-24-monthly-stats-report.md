# Monthly Stats Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a monthly climbing report under the stats domain that combines log-first monthly metrics, video-analysis style insights, optional gym-specific max grade, LLM-generated Korean narrative, and technique-based gym recommendations.

**Architecture:** Keep the feature inside `domain/stats` because the existing `/api/reports` path and `R` error prefix belong to content reports. Store monthly report snapshots in PostgreSQL so the app reads a cached artifact, while batch and on-demand generation both use the same aggregation service. The server owns all numeric metrics and gym recommendations; the LLM client only produces short Korean narrative text from server-computed inputs.

**Tech Stack:** Java 25, Spring Boot 4, MyBatis XML mapper, PostgreSQL JSONB, Flyway, RestClient for optional LLM calls, MockMvc integration tests with Testcontainers.

---

## File Structure

- Create: `src/main/resources/db/migration/V12__monthly_reports.sql`
  - Adds `monthly_reports` table and a `NULLS NOT DISTINCT` unique constraint for `(user_id, period, selected_gym_id)`.
- Create: `src/test/resources/sql/monthly-reports-schema.sql`
  - Test fixture for the new table.
- Create: `src/main/java/com/holaclimbing/server/domain/stats/MonthlyReportController.java`
  - Authenticated API controller for monthly report reads and available periods.
- Create: `src/main/java/com/holaclimbing/server/domain/stats/domain/MonthlyReport.java`
  - Stored report projection.
- Create: `src/main/java/com/holaclimbing/server/domain/stats/domain/MonthlyReportAggregate.java`
  - Aggregated source metrics before persistence.
- Create: `src/main/java/com/holaclimbing/server/domain/stats/domain/MonthlyReportStatus.java`
  - `READY`, `INSUFFICIENT_DATA`, `GENERATING`, `FAILED`.
- Create: `src/main/java/com/holaclimbing/server/domain/stats/domain/MonthlyReportSource.java`
  - `LOG`, `VIDEO_FALLBACK`, `NONE`.
- Create: `src/main/java/com/holaclimbing/server/domain/stats/domain/MonthlyReportNarrative.java`
  - LLM or fallback narrative value object.
- Create: `src/main/java/com/holaclimbing/server/domain/stats/domain/MonthlyReportRecommendedGym.java`
  - Query projection for target-technique gym recommendation.
- Create: `src/main/java/com/holaclimbing/server/domain/stats/dto/response/MonthlyReportResponse.java`
  - Public API response DTO.
- Create: `src/main/java/com/holaclimbing/server/domain/stats/dto/response/MonthlyReportAvailablePeriodsResponse.java`
  - Public API response DTO for available period list.
- Create: `src/main/java/com/holaclimbing/server/domain/stats/mapper/MonthlyReportMapper.java`
  - Mapper for stored reports, source aggregation, and recommended gyms.
- Create: `src/main/resources/mapper/stats/MonthlyReportMapper.xml`
  - SQL for log-first metrics, video fallback metrics, dynamic/static, technique counts, gym-specific grades, and recommendation candidates.
- Create: `src/main/java/com/holaclimbing/server/domain/stats/service/MonthlyReportService.java`
  - Service interface used by controller and scheduler.
- Create: `src/main/java/com/holaclimbing/server/domain/stats/service/MonthlyReportServiceImpl.java`
  - Coordinates retrieval, generation, persistence, insufficient-data gating, and DTO mapping.
- Create: `src/main/java/com/holaclimbing/server/domain/stats/service/MonthlyReportNarrativeClient.java`
  - Interface for narrative generation.
- Create: `src/main/java/com/holaclimbing/server/domain/stats/service/RuleBasedMonthlyReportNarrativeClient.java`
  - Deterministic fallback and test client.
- Create: `src/main/java/com/holaclimbing/server/domain/stats/service/OpenAiMonthlyReportNarrativeClient.java`
  - Optional LLM client enabled by configuration.
- Create: `src/main/java/com/holaclimbing/server/domain/stats/service/MonthlyReportScheduler.java`
  - Monthly batch generator for previous month.
- Create: `src/main/java/com/holaclimbing/server/domain/stats/MonthlyReportProperties.java`
  - `@ConfigurationProperties` for generation thresholds, on-demand behavior, scheduler, and LLM settings.
- Create: `src/main/java/com/holaclimbing/server/common/config/MonthlyReportConfig.java`
  - Registers `MonthlyReportProperties` and enables Spring scheduling.
- Modify: `src/main/java/com/holaclimbing/server/common/exception/error/ErrorCode.java`
  - Adds stats-domain monthly report error codes with `T` prefix.
- Modify: `src/main/resources/application.yaml`
  - Adds `app.monthly-report` configuration and enables scheduling.
- Modify: `src/test/java/com/holaclimbing/server/domain/stats/MonthlyReportIntegrationTest.java`
  - New integration tests for all policies.
- Modify: `db/schema.sql`
  - Snapshot update matching the Flyway migration.
- Optional Modify: `/Users/minjoun/Workspace/projects/Hola-Climbing/api 명세서/hola-climbing-api-spec.md`
  - Add the new stats endpoints after the existing monthly calendar section.

## API Contract

Primary endpoint:

```http
GET /api/stats/me/monthly-reports?month=2026-05&gymId=1
```

Query rules:

- `month`: optional `YYYY-MM`; when omitted, use the previous completed month in Asia/Seoul.
- `gymId`: optional. Only this selected gym is used for `grade`. When omitted, `grade` is `null`.

Success response:

```json
{
  "isSuccess": true,
  "code": "OK",
  "data": {
    "period": "2026-05",
    "status": "ready",
    "source": "log",
    "generatedAt": "2026-06-01T00:12:03+09:00",
    "metrics": {
      "sessions": 9,
      "videos": 14,
      "problemsSolved": 63,
      "gymsVisited": 3,
      "primaryGymId": 1,
      "primaryGymName": "손상원클라이밍짐 강남점",
      "dynamicCount": 10,
      "staticCount": 4,
      "dynamicRatio": 0.71,
      "staticRatio": 0.29,
      "techniqueCounts": {
        "high_step": 18,
        "flagging": 12,
        "toe_hook": 7
      }
    },
    "grade": {
      "gymId": 1,
      "gymName": "손상원클라이밍짐 강남점",
      "maxGrade": "V5",
      "maxGradePrevMonth": "V4"
    },
    "tip": {
      "type": "underusedTechnique",
      "techniqueKeys": ["heel_hook", "lock_off"],
      "message": "훅과 락오프 움직임이 적었어요. 다음 달엔 발과 버티는 힘을 쓰는 문제를 골라보면 좋아요."
    },
    "nextMonthGoal": {
      "title": "부족했던 기술 문제 5개 도전",
      "metric": "specificTechnique",
      "target": 5,
      "techniqueKeys": ["heel_hook", "lock_off"]
    },
    "recommendedGyms": [
      {
        "gymId": 12,
        "name": "더클라임 강남",
        "matchedTechniqueKeys": ["heel_hook", "lock_off"],
        "matchingVideoCount": 8,
        "reason": "최근 공개 영상에서 부족했던 기술이 자주 나온 암장이에요."
      }
    ],
    "narrative": {
      "headline": "볼륨과 스타일이 함께 쌓인 달",
      "summary": "꾸준한 기록과 영상 분석으로 다음 성장 포인트가 뚜렷해졌어요.",
      "highlights": ["기록 기준 볼륨이 안정적으로 쌓였어요"]
    },
    "requirement": null
  },
  "timestamp": "2026-06-24T12:00:00Z"
}
```

Insufficient-data response:

```json
{
  "period": "2026-05",
  "status": "insufficientData",
  "source": "videoFallback",
  "metrics": {
    "sessions": 1,
    "videos": 2,
    "problemsSolved": 2,
    "gymsVisited": 1,
    "dynamicCount": 1,
    "staticCount": 1,
    "dynamicRatio": 0.5,
    "staticRatio": 0.5,
    "techniqueCounts": {
      "dyno": 1
    }
  },
  "grade": null,
  "tip": null,
  "nextMonthGoal": null,
  "recommendedGyms": [],
  "narrative": null,
  "requirement": {
    "minVideos": 3,
    "minProblems": 10
  }
}
```

Available periods endpoint:

```http
GET /api/stats/me/monthly-reports/available
```

Response data:

```json
{
  "periods": ["2026-05", "2026-04"]
}
```

## Aggregation Rules

Use `YearMonth` and KST date boundaries:

```java
LocalDate from = month.atDay(1);
LocalDate to = month.atEndOfMonth();
```

Metric source policy:

```text
If the user has at least one climbing_logs row in the month:
  sessions = count(climbing_logs rows)
  problemsSolved = sum(jsonb values from climbing_logs.grade_counts)
  gymsVisited = count(distinct climbing_logs.gym_id)
  primaryGym = gym with the most log rows, tie by latest climbed_on desc, gym_id asc
  source = log

If the user has no climbing_logs row but has videos in the month:
  sessions = count(distinct videos.recorded_date, videos.gym_id)
  problemsSolved = count(videos rows)
  gymsVisited = count(distinct videos.gym_id)
  primaryGym = gym with the most videos, tie by latest recorded_date desc, gym_id asc
  source = videoFallback

If neither logs nor videos exist:
  source = none
  status = insufficientData
```

Video count policy:

```text
videos = count of non-deleted videos in the month, regardless of log source
```

Grade policy:

```text
If gymId query parameter is absent:
  grade = null

If gymId is present:
  First calculate maxGrade from climbing_logs at that gym in the selected month.
  Match climbing_logs.grade_counts keys to gym_grades.label and ignore labels with count <= 0.
  If no log grade exists at that gym, calculate maxGrade from videos.gym_grade_id at that gym in the selected month.
  If neither exists, grade = null.
  Compare grades with gym_grades.difficulty_order, not label text.
  Repeat the same policy for previous month to populate maxGradePrevMonth.
```

Dynamic/static policy:

```text
Use every non-deleted video in the month that has analysis_video_results.final_is_dynamic.
dynamicRatio = dynamicCount / (dynamicCount + staticCount)
staticRatio = staticCount / (dynamicCount + staticCount)
If denominator is 0, both ratios are null.
```

Technique policy:

```text
Use every non-deleted monthly video and analysis_video_results.final_techniques.
Normalize through AnalysisTechniqueCatalog.
Count occurrences per canonical technique.
Underused techniques are canonical techniques with count 0 first, then low positive count ascending.
Use at most two underused techniques for the tip, goal, and gym recommendation query.
```

Cold-start gate:

```text
status = insufficientData when videos < minVideos OR problemsSolved < minProblems
Default minVideos = 3
Default minProblems = 10
LLM is not called for insufficientData.
```

## LLM Guardrails

The LLM input contains only server-computed metrics, underused technique keys, and Korean technique labels. The model must never invent numbers.

System prompt:

```text
너는 클라이밍 앱의 월간 리포트 문장을 작성하는 데이터 애널리스트다.
서버가 계산한 JSON만 근거로 한국어 존댓말 문장을 만든다.
숫자, 횟수, 퍼센트, 난이도 값은 새로 쓰지 않는다.
부상 위험이 있는 공격적 무브 처방, 의학적 조언, 신체 상태 판단을 하지 않는다.
기술 다양성, 등반 볼륨, 기록 습관 중심으로만 제안한다.
출력은 headline, summary, highlights, tipMessage, goalTitle, goalRationale 키를 가진 JSON 객체만 허용한다.
```

Fallback narrative is always available through `RuleBasedMonthlyReportNarrativeClient`, so metrics can still be served if the LLM provider fails.

---

### Task 1: Add Monthly Report Storage

**Files:**
- Create: `src/main/resources/db/migration/V12__monthly_reports.sql`
- Create: `src/test/resources/sql/monthly-reports-schema.sql`
- Modify: `db/schema.sql`

- [ ] **Step 1: Write the Flyway migration**

Create `src/main/resources/db/migration/V12__monthly_reports.sql`:

```sql
CREATE TABLE monthly_reports (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    period              CHAR(7) NOT NULL,
    selected_gym_id     BIGINT REFERENCES gyms(id) ON DELETE SET NULL,
    status              VARCHAR(30) NOT NULL,
    source              VARCHAR(30) NOT NULL,
    metrics             JSONB NOT NULL DEFAULT '{}'::jsonb,
    grade               JSONB,
    tip                 JSONB,
    next_month_goal     JSONB,
    recommended_gyms    JSONB NOT NULL DEFAULT '[]'::jsonb,
    narrative           JSONB,
    requirement         JSONB,
    model               VARCHAR(100),
    prompt_version      VARCHAR(50),
    generated_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_monthly_reports_period
        CHECK (period ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    CONSTRAINT chk_monthly_reports_status
        CHECK (status IN ('ready', 'insufficientData', 'generating', 'failed')),
    CONSTRAINT chk_monthly_reports_source
        CHECK (source IN ('log', 'videoFallback', 'none')),
    CONSTRAINT uq_monthly_reports_user_period_gym
        UNIQUE NULLS NOT DISTINCT (user_id, period, selected_gym_id)
);

CREATE INDEX idx_monthly_reports_user_period
    ON monthly_reports(user_id, period DESC);
```

- [ ] **Step 2: Add the test fixture table**

Create `src/test/resources/sql/monthly-reports-schema.sql` with the same table and indexes as the migration, preceded by:

```sql
DROP TABLE IF EXISTS monthly_reports CASCADE;
```

- [ ] **Step 3: Mirror the schema snapshot**

Add the same `monthly_reports` table to `db/schema.sql` under the stats domain section, after `climbing_logs`.

- [ ] **Step 4: Verify migrations still load**

Run:

```bash
./mvnw -Dtest=FlywayMigrationIntegrationTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V12__monthly_reports.sql src/test/resources/sql/monthly-reports-schema.sql db/schema.sql
git commit -m "feat(stats): add monthly report storage"
```

---

### Task 2: Add DTOs, Domain Objects, and Properties

**Files:**
- Create: `src/main/java/com/holaclimbing/server/domain/stats/domain/MonthlyReportStatus.java`
- Create: `src/main/java/com/holaclimbing/server/domain/stats/domain/MonthlyReportSource.java`
- Create: `src/main/java/com/holaclimbing/server/domain/stats/domain/MonthlyReportNarrative.java`
- Create: `src/main/java/com/holaclimbing/server/domain/stats/domain/MonthlyReportAggregate.java`
- Create: `src/main/java/com/holaclimbing/server/domain/stats/domain/MonthlyReportRecommendedGym.java`
- Create: `src/main/java/com/holaclimbing/server/domain/stats/domain/MonthlyReport.java`
- Create: `src/main/java/com/holaclimbing/server/domain/stats/dto/response/MonthlyReportResponse.java`
- Create: `src/main/java/com/holaclimbing/server/domain/stats/dto/response/MonthlyReportAvailablePeriodsResponse.java`
- Create: `src/main/java/com/holaclimbing/server/domain/stats/MonthlyReportProperties.java`
- Create: `src/main/java/com/holaclimbing/server/common/config/MonthlyReportConfig.java`
- Modify: `src/main/resources/application.yaml`

- [ ] **Step 1: Add status and source enums**

Create `MonthlyReportStatus.java`:

```java
package com.holaclimbing.server.domain.stats.domain;

public enum MonthlyReportStatus {
    READY("ready"),
    INSUFFICIENT_DATA("insufficientData"),
    GENERATING("generating"),
    FAILED("failed");

    private final String value;

    MonthlyReportStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
```

Create `MonthlyReportSource.java`:

```java
package com.holaclimbing.server.domain.stats.domain;

public enum MonthlyReportSource {
    LOG("log"),
    VIDEO_FALLBACK("videoFallback"),
    NONE("none");

    private final String value;

    MonthlyReportSource(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
```

- [ ] **Step 2: Add the public response record**

Create `MonthlyReportNarrative.java`:

```java
package com.holaclimbing.server.domain.stats.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyReportNarrative {
    private String headline;
    private String summary;
    private List<String> highlights;
}
```

Create `MonthlyReportAggregate.java`:

```java
package com.holaclimbing.server.domain.stats.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyReportAggregate {
    private int sessions;
    private int videos;
    private int problemsSolved;
    private int gymsVisited;
    private Long primaryGymId;
    private String primaryGymName;
    private int dynamicCount;
    private int staticCount;
    private Long gradeGymId;
    private String gradeGymName;
    private String maxGrade;
    private Integer maxGradeOrder;
    private MonthlyReportSource source;

    public boolean hasSessions() {
        return sessions > 0;
    }

    public MonthlyReportAggregate withSource(MonthlyReportSource newSource) {
        this.source = newSource;
        return this;
    }

    public Map<String, Object> toPromptMap() {
        return Map.of(
                "sessions", sessions,
                "videos", videos,
                "problemsSolved", problemsSolved,
                "gymsVisited", gymsVisited,
                "primaryGymName", primaryGymName == null ? "" : primaryGymName,
                "dynamicCount", dynamicCount,
                "staticCount", staticCount
        );
    }
}
```

Create `MonthlyReportRecommendedGym.java`:

```java
package com.holaclimbing.server.domain.stats.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyReportRecommendedGym {
    private Long gymId;
    private String name;
    private int matchingVideoCount;
    private String matchedTechniques;
}
```

Create `MonthlyReport.java`:

```java
package com.holaclimbing.server.domain.stats.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyReport {
    private Long id;
    private Long userId;
    private String period;
    private Long selectedGymId;
    private String status;
    private String source;
    private String metrics;
    private String grade;
    private String tip;
    private String nextMonthGoal;
    private String recommendedGyms;
    private String narrative;
    private String requirement;
    private String model;
    private String promptVersion;
    private OffsetDateTime generatedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
```

Create `MonthlyReportResponse.java`:

```java
package com.holaclimbing.server.domain.stats.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MonthlyReportResponse(
        String period,
        String status,
        String source,
        OffsetDateTime generatedAt,
        Metrics metrics,
        Grade grade,
        Tip tip,
        Goal nextMonthGoal,
        List<RecommendedGym> recommendedGyms,
        Narrative narrative,
        Requirement requirement
) {
    public record Metrics(
            int sessions,
            int videos,
            int problemsSolved,
            int gymsVisited,
            Long primaryGymId,
            String primaryGymName,
            int dynamicCount,
            int staticCount,
            Double dynamicRatio,
            Double staticRatio,
            Map<String, Integer> techniqueCounts
    ) {
    }

    public record Grade(
            Long gymId,
            String gymName,
            String maxGrade,
            String maxGradePrevMonth
    ) {
    }

    public record Tip(
            String type,
            List<String> techniqueKeys,
            String message
    ) {
    }

    public record Goal(
            String title,
            String metric,
            int target,
            List<String> techniqueKeys,
            String rationale
    ) {
    }

    public record RecommendedGym(
            Long gymId,
            String name,
            List<String> matchedTechniqueKeys,
            int matchingVideoCount,
            String reason
    ) {
    }

    public record Narrative(
            String headline,
            String summary,
            List<String> highlights
    ) {
    }

    public record Requirement(
            int minVideos,
            int minProblems
    ) {
    }
}
```

- [ ] **Step 3: Add available periods response**

Create `MonthlyReportAvailablePeriodsResponse.java`:

```java
package com.holaclimbing.server.domain.stats.dto.response;

import java.util.List;

public record MonthlyReportAvailablePeriodsResponse(
        List<String> periods
) {
}
```

- [ ] **Step 4: Add configuration properties**

Create `MonthlyReportProperties.java`:

```java
package com.holaclimbing.server.domain.stats;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.monthly-report")
public record MonthlyReportProperties(
        int minVideos,
        int minProblems,
        boolean generateOnMiss,
        String promptVersion,
        Scheduler scheduler,
        Llm llm
) {
    public record Scheduler(
            boolean enabled,
            String cron
    ) {
    }

    public record Llm(
            String mode,
            String baseUrl,
            String apiKey,
            String model,
            int timeoutSeconds
    ) {
    }
}
```

- [ ] **Step 5: Wire properties in `application.yaml`**

Create `MonthlyReportConfig.java`:

```java
package com.holaclimbing.server.common.config;

import com.holaclimbing.server.domain.stats.MonthlyReportProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(MonthlyReportProperties.class)
public class MonthlyReportConfig {
}
```

- [ ] **Step 6: Wire properties in `application.yaml`**

Add under `app:`:

```yaml
  monthly-report:
    min-videos: ${MONTHLY_REPORT_MIN_VIDEOS:3}
    min-problems: ${MONTHLY_REPORT_MIN_PROBLEMS:10}
    generate-on-miss: ${MONTHLY_REPORT_GENERATE_ON_MISS:true}
    prompt-version: ${MONTHLY_REPORT_PROMPT_VERSION:monthly-report-v1}
    scheduler:
      enabled: ${MONTHLY_REPORT_SCHEDULER_ENABLED:false}
      cron: ${MONTHLY_REPORT_SCHEDULER_CRON:0 0 0 1 * *}
    llm:
      mode: ${MONTHLY_REPORT_LLM_MODE:rule}
      base-url: ${MONTHLY_REPORT_LLM_BASE_URL:https://api.openai.com/v1}
      api-key: ${MONTHLY_REPORT_LLM_API_KEY:}
      model: ${MONTHLY_REPORT_LLM_MODEL:gpt-4.1-mini}
      timeout-seconds: ${MONTHLY_REPORT_LLM_TIMEOUT_SECONDS:20}
```

- [ ] **Step 7: Compile**

Run:

```bash
./mvnw -DskipTests compile
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/holaclimbing/server/domain/stats/domain src/main/java/com/holaclimbing/server/domain/stats/dto/response src/main/java/com/holaclimbing/server/domain/stats/MonthlyReportProperties.java src/main/java/com/holaclimbing/server/common/config/MonthlyReportConfig.java src/main/resources/application.yaml
git commit -m "feat(stats): add monthly report contracts"
```

---

### Task 3: Write Integration Tests for Product Policies

**Files:**
- Create: `src/test/java/com/holaclimbing/server/domain/stats/MonthlyReportIntegrationTest.java`

- [ ] **Step 1: Add the test class skeleton**

Create `MonthlyReportIntegrationTest.java`:

```java
package com.holaclimbing.server.domain.stats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
import com.holaclimbing.server.domain.user.dto.request.LoginRequest;
import com.holaclimbing.server.domain.user.dto.request.VerifyEmailRequest;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Map;

import static com.holaclimbing.server.TestSignupRequests.signupRequest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.cors.allowed-origins=http://localhost:3000",
        "app.monthly-report.generate-on-miss=true",
        "app.monthly-report.llm.mode=rule"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = {
        "classpath:sql/users-schema.sql",
        "classpath:sql/terms-data.sql",
        "classpath:sql/gyms-schema.sql",
        "classpath:sql/gyms-data.sql",
        "classpath:sql/climbing-logs-schema.sql",
        "classpath:sql/videos-schema.sql",
        "classpath:sql/monthly-reports-schema.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MonthlyReportIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserMapper userMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("월간 리포트 — 기록이 있으면 완등·세션·방문암장은 기록 기준으로 계산한다")
    void monthlyReport_prefersLogsForTotals() throws Exception {
        String token = register("a@hola.com", "climberone");
        long userId = userMapper.findByEmail("a@hola.com").getId();
        insertLog(userId, 1L, LocalDate.of(2026, 5, 10), Map.of("V3", 4, "V4", 2));
        insertLog(userId, 2L, LocalDate.of(2026, 5, 20), Map.of("V2", 3));
        insertVideo(userId, 1L, 1003L, LocalDate.of(2026, 5, 10), true, "[\"dyno\"]");
        insertVideo(userId, 1L, 1004L, LocalDate.of(2026, 5, 11), false, "[\"flagging\"]");

        mockMvc.perform(get("/api/stats/me/monthly-reports")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2026-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("insufficientData"))
                .andExpect(jsonPath("$.data.source").value("log"))
                .andExpect(jsonPath("$.data.metrics.sessions").value(2))
                .andExpect(jsonPath("$.data.metrics.problemsSolved").value(9))
                .andExpect(jsonPath("$.data.metrics.gymsVisited").value(2))
                .andExpect(jsonPath("$.data.metrics.videos").value(2));
    }

    @Test
    @DisplayName("월간 리포트 — 기록이 없으면 영상으로 세션·완등·방문암장을 추정한다")
    void monthlyReport_fallsBackToVideosWhenLogsAreMissing() throws Exception {
        String token = register("b@hola.com", "climbertwo");
        long userId = userMapper.findByEmail("b@hola.com").getId();
        insertVideo(userId, 1L, 1003L, LocalDate.of(2026, 5, 10), true, "[\"dyno\"]");
        insertVideo(userId, 1L, 1004L, LocalDate.of(2026, 5, 10), true, "[\"dyno\"]");
        insertVideo(userId, 2L, 1005L, LocalDate.of(2026, 5, 11), false, "[\"flagging\"]");

        mockMvc.perform(get("/api/stats/me/monthly-reports")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2026-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("videoFallback"))
                .andExpect(jsonPath("$.data.metrics.sessions").value(2))
                .andExpect(jsonPath("$.data.metrics.problemsSolved").value(3))
                .andExpect(jsonPath("$.data.metrics.gymsVisited").value(2));
    }

    @Test
    @DisplayName("월간 리포트 — 선택 암장 기준 최고난이도는 difficulty_order로 계산한다")
    void monthlyReport_gradeUsesSelectedGymOnly() throws Exception {
        String token = register("c@hola.com", "climberthree");
        long userId = userMapper.findByEmail("c@hola.com").getId();
        insertLog(userId, 3L, LocalDate.of(2026, 5, 10), Map.of("V3", 1, "V5", 1));
        insertLog(userId, 2L, LocalDate.of(2026, 5, 11), Map.of("노랑", 1));
        insertLog(userId, 3L, LocalDate.of(2026, 4, 10), Map.of("V4", 1));

        mockMvc.perform(get("/api/stats/me/monthly-reports")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2026-05")
                        .param("gymId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grade.gymId").value(3))
                .andExpect(jsonPath("$.data.grade.maxGrade").value("V5"))
                .andExpect(jsonPath("$.data.grade.maxGradePrevMonth").value("V4"));
    }

    @Test
    @DisplayName("월간 리포트 — 선택 암장 기록과 영상이 없으면 grade는 null이다")
    void monthlyReport_gradeIsNullWhenSelectedGymHasNoData() throws Exception {
        String token = register("d@hola.com", "climberfour");
        long userId = userMapper.findByEmail("d@hola.com").getId();
        insertLog(userId, 1L, LocalDate.of(2026, 5, 10), Map.of("V3", 3));

        mockMvc.perform(get("/api/stats/me/monthly-reports")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2026-05")
                        .param("gymId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grade").doesNotExist());
    }

    @Test
    @DisplayName("월간 리포트 — dynamic/static과 기술 통계는 그 달 모든 영상 분석 결과 기준이다")
    void monthlyReport_usesAllMonthlyVideosForStyle() throws Exception {
        String token = register("e@hola.com", "climberfive");
        long userId = userMapper.findByEmail("e@hola.com").getId();
        insertLog(userId, 1L, LocalDate.of(2026, 5, 10), Map.of("V3", 10));
        insertVideo(userId, 1L, 1003L, LocalDate.of(2026, 5, 10), true, "[\"dyno\", \"toe_hook\"]");
        insertVideo(userId, 2L, 1005L, LocalDate.of(2026, 5, 11), false, "[\"flagging\"]");
        insertVideo(userId, 2L, 1005L, LocalDate.of(2026, 5, 12), true, "[\"dyno\"]");

        mockMvc.perform(get("/api/stats/me/monthly-reports")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2026-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics.dynamicCount").value(2))
                .andExpect(jsonPath("$.data.metrics.staticCount").value(1))
                .andExpect(jsonPath("$.data.metrics.dynamicRatio").value(0.67))
                .andExpect(jsonPath("$.data.metrics.techniqueCounts.dyno").value(2))
                .andExpect(jsonPath("$.data.metrics.techniqueCounts.flagging").value(1));
    }

    @Test
    @DisplayName("월간 리포트 — 적게 한 기술을 목표로 만들고 해당 기술이 많은 암장을 추천한다")
    void monthlyReport_recommendsGymsForUnderusedTechniques() throws Exception {
        String token = register("f@hola.com", "climbersix");
        long userId = userMapper.findByEmail("f@hola.com").getId();
        insertLog(userId, 1L, LocalDate.of(2026, 5, 10), Map.of("V3", 12));
        insertVideo(userId, 1L, 1003L, LocalDate.of(2026, 5, 10), true, "[\"dyno\"]");
        insertPublicTechniqueVideo(2L, 1005L, LocalDate.of(2026, 5, 9), "[\"heel_hook\", \"lock_off\"]");
        insertPublicTechniqueVideo(2L, 1005L, LocalDate.of(2026, 5, 10), "[\"heel_hook\"]");

        mockMvc.perform(get("/api/stats/me/monthly-reports")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2026-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ready"))
                .andExpect(jsonPath("$.data.tip.type").value("underusedTechnique"))
                .andExpect(jsonPath("$.data.nextMonthGoal.metric").value("specificTechnique"))
                .andExpect(jsonPath("$.data.recommendedGyms[0].gymId").value(2));
    }
}
```

- [ ] **Step 2: Add helper methods to the test class**

Append these methods inside the class:

```java
private String register(String email, String nickname) throws Exception {
    mockMvc.perform(post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(signupRequest(email, PASSWORD, nickname))))
            .andExpect(status().isCreated());
    var user = userMapper.findByEmail(email);
    mockMvc.perform(post("/api/auth/email/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new VerifyEmailRequest(user.getEmailVerificationToken()))))
            .andExpect(status().isOk());
    String body = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
            .andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(body).path("data").path("accessToken").asText();
}

private void insertLog(long userId, long gymId, LocalDate climbedOn, Map<String, Integer> gradeCounts) throws Exception {
    jdbcTemplate.update("""
            INSERT INTO climbing_logs (user_id, gym_id, climbed_on, grade_counts, memo)
            VALUES (?, ?, ?, ?::jsonb, NULL)
            """, userId, gymId, climbedOn, objectMapper.writeValueAsString(gradeCounts));
}

private void insertVideo(long userId, long gymId, long gymGradeId, LocalDate recordedDate,
                         Boolean isDynamic, String techniquesJson) {
    Long videoId = jdbcTemplate.queryForObject("""
            INSERT INTO videos (user_id, gym_id, gym_grade_id, title, gcs_path,
                                duration_seconds, recorded_date, mime_type, status, is_public)
            VALUES (?, ?, ?, 'monthly clip', ?, 30, ?, 'video/mp4', 'done', TRUE)
            RETURNING id
            """, Long.class, userId, gymId, gymGradeId,
            "videos/uploads/" + userId + "/monthly-" + recordedDate + "-" + gymGradeId + ".mp4",
            recordedDate);
    jdbcTemplate.update("""
            INSERT INTO analysis_video_results (
                video_id, model_version, ai_techniques, ai_is_dynamic,
                ai_dynamic_probability, final_techniques, final_is_dynamic, feedback_applied
            )
            VALUES (?, 'rule_v3', ?::jsonb, ?, 0.5, ?::jsonb, ?, FALSE)
            """, videoId, techniquesJson, isDynamic, techniquesJson, isDynamic);
}

private void insertPublicTechniqueVideo(long gymId, long gymGradeId, LocalDate recordedDate, String techniquesJson) {
    Long ownerId = jdbcTemplate.queryForObject("""
            INSERT INTO users (email, password, nickname, is_email_verified, created_at, updated_at)
            VALUES (?, 'pw', ?, TRUE, NOW(), NOW())
            RETURNING id
            """, Long.class,
            "public-" + gymId + "-" + recordedDate + "-" + Math.abs(techniquesJson.hashCode()) + "@hola.com",
            "public" + gymId + Math.abs(techniquesJson.hashCode()));
    insertVideo(ownerId, gymId, gymGradeId, recordedDate, true, techniquesJson);
}
```

- [ ] **Step 3: Run tests and verify they fail because endpoint is missing**

Run:

```bash
./mvnw -Dtest=MonthlyReportIntegrationTest test
```

Expected:

```text
Status expected:<200> but was:<404>
```

- [ ] **Step 4: Commit failing tests**

```bash
git add src/test/java/com/holaclimbing/server/domain/stats/MonthlyReportIntegrationTest.java
git commit -m "test(stats): specify monthly report policies"
```

---

### Task 4: Implement Mapper Aggregations

**Files:**
- Create: `src/main/java/com/holaclimbing/server/domain/stats/mapper/MonthlyReportMapper.java`
- Create: `src/main/resources/mapper/stats/MonthlyReportMapper.xml`

- [ ] **Step 1: Add mapper interface**

Create `MonthlyReportMapper.java`:

```java
package com.holaclimbing.server.domain.stats.mapper;

import com.holaclimbing.server.domain.stats.domain.MonthlyReport;
import com.holaclimbing.server.domain.stats.domain.MonthlyReportAggregate;
import com.holaclimbing.server.domain.stats.domain.MonthlyReportRecommendedGym;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface MonthlyReportMapper {

    MonthlyReport findReport(@Param("userId") Long userId,
                             @Param("period") String period,
                             @Param("gymId") Long gymId);

    List<String> findAvailablePeriods(@Param("userId") Long userId);

    void upsertReport(MonthlyReport report);

    MonthlyReportAggregate findLogAggregate(@Param("userId") Long userId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    MonthlyReportAggregate findVideoFallbackAggregate(@Param("userId") Long userId,
                                                      @Param("from") LocalDate from,
                                                      @Param("to") LocalDate to);

    String findTechniqueCountsJson(@Param("userId") Long userId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    MonthlyReportAggregate findDynamicStaticAggregate(@Param("userId") Long userId,
                                                      @Param("from") LocalDate from,
                                                      @Param("to") LocalDate to);

    MonthlyReportAggregate findGradeFromLogs(@Param("userId") Long userId,
                                             @Param("gymId") Long gymId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    MonthlyReportAggregate findGradeFromVideos(@Param("userId") Long userId,
                                               @Param("gymId") Long gymId,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    List<MonthlyReportRecommendedGym> findRecommendedGymsByTechniques(
            @Param("techniques") List<String> techniques,
            @Param("size") int size);
}
```

- [ ] **Step 2: Add core SQL for log-first and video fallback aggregates**

Create `MonthlyReportMapper.xml` with these selects:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.holaclimbing.server.domain.stats.mapper.MonthlyReportMapper">

    <select id="findLogAggregate"
            resultType="com.holaclimbing.server.domain.stats.domain.MonthlyReportAggregate">
        WITH logs AS (
            SELECT l.*
            FROM climbing_logs l
            WHERE l.user_id = #{userId}
              AND l.deleted_at IS NULL
              AND l.climbed_on BETWEEN #{from} AND #{to}
        ),
        problem_totals AS (
            SELECT COALESCE(SUM((grade.value)::int), 0)::int AS problems_solved
            FROM logs l
            CROSS JOIN LATERAL jsonb_each_text(l.grade_counts) AS grade(label, value)
        ),
        primary_gym AS (
            SELECT l.gym_id, g.name AS gym_name
            FROM logs l
            JOIN gyms g ON g.id = l.gym_id
            GROUP BY l.gym_id, g.name
            ORDER BY COUNT(*) DESC, MAX(l.climbed_on) DESC, l.gym_id ASC
            LIMIT 1
        )
        SELECT
            COUNT(*)::int AS sessions,
            (SELECT problems_solved FROM problem_totals) AS problems_solved,
            COUNT(DISTINCT gym_id)::int AS gyms_visited,
            (SELECT gym_id FROM primary_gym) AS primary_gym_id,
            (SELECT gym_name FROM primary_gym) AS primary_gym_name
        FROM logs
    </select>

    <select id="findVideoFallbackAggregate"
            resultType="com.holaclimbing.server.domain.stats.domain.MonthlyReportAggregate">
        WITH active_videos AS (
            SELECT v.*
            FROM videos v
            WHERE v.user_id = #{userId}
              AND v.deleted_at IS NULL
              AND v.status != 'failed'
              AND v.recorded_date BETWEEN #{from} AND #{to}
        ),
        primary_gym AS (
            SELECT v.gym_id, g.name AS gym_name
            FROM active_videos v
            JOIN gyms g ON g.id = v.gym_id
            GROUP BY v.gym_id, g.name
            ORDER BY COUNT(*) DESC, MAX(v.recorded_date) DESC, v.gym_id ASC
            LIMIT 1
        )
        SELECT
            COUNT(DISTINCT (recorded_date, gym_id))::int AS sessions,
            COUNT(*)::int AS problems_solved,
            COUNT(DISTINCT gym_id)::int AS gyms_visited,
            COUNT(*)::int AS videos,
            (SELECT gym_id FROM primary_gym) AS primary_gym_id,
            (SELECT gym_name FROM primary_gym) AS primary_gym_name
        FROM active_videos
    </select>
</mapper>
```

- [ ] **Step 3: Add remaining SQL**

Extend the mapper XML with:

```xml
<select id="findReport" resultType="com.holaclimbing.server.domain.stats.domain.MonthlyReport">
    SELECT id, user_id, period, selected_gym_id, status, source,
           metrics::text AS metrics,
           grade::text AS grade,
           tip::text AS tip,
           next_month_goal::text AS next_month_goal,
           recommended_gyms::text AS recommended_gyms,
           narrative::text AS narrative,
           requirement::text AS requirement,
           model, prompt_version, generated_at, created_at, updated_at
    FROM monthly_reports
    WHERE user_id = #{userId}
      AND period = #{period}
      <choose>
          <when test="gymId == null">
              AND selected_gym_id IS NULL
          </when>
          <otherwise>
              AND selected_gym_id = #{gymId}
          </otherwise>
      </choose>
</select>

<select id="findAvailablePeriods" resultType="string">
    SELECT DISTINCT period
    FROM monthly_reports
    WHERE user_id = #{userId}
      AND status = 'ready'
    ORDER BY period DESC
</select>

<insert id="upsertReport" parameterType="com.holaclimbing.server.domain.stats.domain.MonthlyReport">
    INSERT INTO monthly_reports (
        user_id, period, selected_gym_id, status, source,
        metrics, grade, tip, next_month_goal, recommended_gyms,
        narrative, requirement, model, prompt_version, generated_at
    )
    VALUES (
        #{userId}, #{period}, #{selectedGymId}, #{status}, #{source},
        #{metrics}::jsonb, #{grade}::jsonb, #{tip}::jsonb,
        #{nextMonthGoal}::jsonb, #{recommendedGyms}::jsonb,
        #{narrative}::jsonb, #{requirement}::jsonb,
        #{model}, #{promptVersion}, #{generatedAt}
    )
    ON CONFLICT ON CONSTRAINT uq_monthly_reports_user_period_gym
    DO UPDATE SET
        status = EXCLUDED.status,
        source = EXCLUDED.source,
        metrics = EXCLUDED.metrics,
        grade = EXCLUDED.grade,
        tip = EXCLUDED.tip,
        next_month_goal = EXCLUDED.next_month_goal,
        recommended_gyms = EXCLUDED.recommended_gyms,
        narrative = EXCLUDED.narrative,
        requirement = EXCLUDED.requirement,
        model = EXCLUDED.model,
        prompt_version = EXCLUDED.prompt_version,
        generated_at = EXCLUDED.generated_at,
        updated_at = NOW()
</insert>

<select id="findTechniqueCountsJson" resultType="string">
    SELECT COALESCE(jsonb_object_agg(technique, technique_count ORDER BY technique), '{}'::jsonb)::text
    FROM (
        SELECT t.technique, COUNT(*)::int AS technique_count
        FROM videos v
        JOIN analysis_video_results r ON r.video_id = v.id
        CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(r.final_techniques, '[]'::jsonb)) AS t(technique)
        WHERE v.user_id = #{userId}
          AND v.deleted_at IS NULL
          AND v.status != 'failed'
          AND v.recorded_date BETWEEN #{from} AND #{to}
        GROUP BY t.technique
    ) counted
</select>

<select id="findDynamicStaticAggregate"
        resultType="com.holaclimbing.server.domain.stats.domain.MonthlyReportAggregate">
    SELECT
        COUNT(*)::int AS videos,
        COALESCE(SUM(CASE WHEN r.final_is_dynamic = TRUE THEN 1 ELSE 0 END), 0)::int AS dynamic_count,
        COALESCE(SUM(CASE WHEN r.final_is_dynamic = FALSE THEN 1 ELSE 0 END), 0)::int AS static_count
    FROM videos v
    LEFT JOIN analysis_video_results r ON r.video_id = v.id
    WHERE v.user_id = #{userId}
      AND v.deleted_at IS NULL
      AND v.status != 'failed'
      AND v.recorded_date BETWEEN #{from} AND #{to}
</select>

<select id="findGradeFromLogs"
        resultType="com.holaclimbing.server.domain.stats.domain.MonthlyReportAggregate">
    SELECT
        g.id AS grade_gym_id,
        g.name AS grade_gym_name,
        gg.label AS max_grade,
        gg.difficulty_order AS max_grade_order
    FROM climbing_logs l
    JOIN gyms g ON g.id = l.gym_id
    CROSS JOIN LATERAL jsonb_each_text(l.grade_counts) AS grade(label, value)
    JOIN gym_grades gg ON gg.gym_id = l.gym_id AND gg.label = grade.label
    WHERE l.user_id = #{userId}
      AND l.gym_id = #{gymId}
      AND l.deleted_at IS NULL
      AND l.climbed_on BETWEEN #{from} AND #{to}
      AND (grade.value)::int > 0
    ORDER BY gg.difficulty_order DESC, gg.id DESC
    LIMIT 1
</select>

<select id="findGradeFromVideos"
        resultType="com.holaclimbing.server.domain.stats.domain.MonthlyReportAggregate">
    SELECT
        g.id AS grade_gym_id,
        g.name AS grade_gym_name,
        gg.label AS max_grade,
        gg.difficulty_order AS max_grade_order
    FROM videos v
    JOIN gyms g ON g.id = v.gym_id
    JOIN gym_grades gg ON gg.id = v.gym_grade_id
    WHERE v.user_id = #{userId}
      AND v.gym_id = #{gymId}
      AND v.deleted_at IS NULL
      AND v.status != 'failed'
      AND v.recorded_date BETWEEN #{from} AND #{to}
    ORDER BY gg.difficulty_order DESC, gg.id DESC
    LIMIT 1
</select>

<select id="findRecommendedGymsByTechniques"
        resultType="com.holaclimbing.server.domain.stats.domain.MonthlyReportRecommendedGym">
    SELECT
        g.id AS gym_id,
        g.name,
        COUNT(*)::int AS matching_video_count,
        COALESCE(jsonb_agg(DISTINCT t.technique ORDER BY t.technique), '[]'::jsonb)::text AS matched_techniques
    FROM gyms g
    JOIN videos v ON v.gym_id = g.id
    JOIN analysis_video_results r ON r.video_id = v.id
    CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(r.final_techniques, '[]'::jsonb)) AS t(technique)
    WHERE g.status = 'active'
      AND v.deleted_at IS NULL
      AND v.is_public = TRUE
      AND v.status != 'failed'
      AND t.technique IN
      <foreach collection="techniques" item="technique" open="(" separator="," close=")">
          #{technique}
      </foreach>
    GROUP BY g.id, g.name
    ORDER BY matching_video_count DESC, g.id ASC
    LIMIT #{size}
</select>
```

- [ ] **Step 4: Run the monthly report tests**

Run:

```bash
./mvnw -Dtest=MonthlyReportIntegrationTest test
```

Expected after this task:

```text
Status expected:<200> but was:<404>
```

The mapper compiles; controller and service are still absent.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/holaclimbing/server/domain/stats/mapper/MonthlyReportMapper.java src/main/resources/mapper/stats/MonthlyReportMapper.xml
git commit -m "feat(stats): add monthly report aggregations"
```

---

### Task 5: Implement Service, Narrative Client, and Response Mapping

**Files:**
- Create: `src/main/java/com/holaclimbing/server/domain/stats/service/MonthlyReportService.java`
- Create: `src/main/java/com/holaclimbing/server/domain/stats/service/MonthlyReportServiceImpl.java`
- Create: `src/main/java/com/holaclimbing/server/domain/stats/service/MonthlyReportNarrativeClient.java`
- Create: `src/main/java/com/holaclimbing/server/domain/stats/service/RuleBasedMonthlyReportNarrativeClient.java`
- Modify: `src/main/java/com/holaclimbing/server/common/exception/error/ErrorCode.java`

- [ ] **Step 1: Add service interface**

Create `MonthlyReportService.java`:

```java
package com.holaclimbing.server.domain.stats.service;

import com.holaclimbing.server.domain.stats.dto.response.MonthlyReportAvailablePeriodsResponse;
import com.holaclimbing.server.domain.stats.dto.response.MonthlyReportResponse;

import java.time.YearMonth;

public interface MonthlyReportService {
    MonthlyReportResponse getMonthlyReport(Long userId, YearMonth month, Long gymId);
    MonthlyReportResponse generateMonthlyReport(Long userId, YearMonth month, Long gymId);
    MonthlyReportAvailablePeriodsResponse getAvailablePeriods(Long userId);
}
```

- [ ] **Step 2: Add narrative client interface and rule implementation**

Create `MonthlyReportNarrativeClient.java`:

```java
package com.holaclimbing.server.domain.stats.service;

import com.holaclimbing.server.domain.stats.domain.MonthlyReportAggregate;
import com.holaclimbing.server.domain.stats.domain.MonthlyReportNarrative;

import java.util.List;
import java.util.Map;

public interface MonthlyReportNarrativeClient {
    MonthlyReportNarrative generate(MonthlyReportAggregate aggregate,
                                    Map<String, Integer> techniqueCounts,
                                    List<String> underusedTechniques);
}
```

Create `RuleBasedMonthlyReportNarrativeClient.java`:

```java
package com.holaclimbing.server.domain.stats.service;

import com.holaclimbing.server.domain.stats.domain.MonthlyReportAggregate;
import com.holaclimbing.server.domain.stats.domain.MonthlyReportNarrative;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnMissingBean(MonthlyReportNarrativeClient.class)
public class RuleBasedMonthlyReportNarrativeClient implements MonthlyReportNarrativeClient {

    @Override
    public MonthlyReportNarrative generate(MonthlyReportAggregate aggregate,
                                           Map<String, Integer> techniqueCounts,
                                           List<String> underusedTechniques) {
        String headline = aggregate.getProblemsSolved() > 0
                ? "볼륨과 스타일이 함께 쌓인 달"
                : "기록을 쌓기 시작한 달";
        String summary = underusedTechniques.isEmpty()
                ? "꾸준한 등반 데이터가 쌓이고 있어요."
                : "이번 달 기록에서 다음 성장 포인트가 뚜렷해졌어요.";
        return new MonthlyReportNarrative(headline, summary,
                List.of("기록과 영상 분석을 함께 살펴봤어요"));
    }
}
```

- [ ] **Step 3: Add stats error codes**

Modify `ErrorCode.java` under the stats section:

```java
MONTHLY_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "T002", "월간 리포트를 찾을 수 없습니다."),
INVALID_MONTH(HttpStatus.BAD_REQUEST, "T003", "월 형식이 올바르지 않습니다."),
MONTHLY_REPORT_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "T004", "월간 리포트 생성에 실패했습니다."),
```

- [ ] **Step 4: Implement service orchestration**

Create `MonthlyReportServiceImpl.java` with these responsibilities:

```java
package com.holaclimbing.server.domain.stats.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.domain.analysis.domain.AnalysisTechniqueCatalog;
import com.holaclimbing.server.domain.stats.MonthlyReportProperties;
import com.holaclimbing.server.domain.stats.domain.MonthlyReportAggregate;
import com.holaclimbing.server.domain.stats.domain.MonthlyReportNarrative;
import com.holaclimbing.server.domain.stats.domain.MonthlyReportRecommendedGym;
import com.holaclimbing.server.domain.stats.domain.MonthlyReportSource;
import com.holaclimbing.server.domain.stats.domain.MonthlyReportStatus;
import com.holaclimbing.server.domain.stats.dto.response.MonthlyReportAvailablePeriodsResponse;
import com.holaclimbing.server.domain.stats.dto.response.MonthlyReportResponse;
import com.holaclimbing.server.domain.stats.mapper.MonthlyReportMapper;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MonthlyReportServiceImpl implements MonthlyReportService {

    private static final TypeReference<Map<String, Integer>> COUNTS_TYPE = new TypeReference<>() {};

    private final MonthlyReportMapper monthlyReportMapper;
    private final UserMapper userMapper;
    private final MonthlyReportProperties properties;
    private final MonthlyReportNarrativeClient narrativeClient;
    private final ObjectMapper objectMapper;

    @Override
    public MonthlyReportResponse getMonthlyReport(Long userId, YearMonth month, Long gymId) {
        requireUser(userId);
        var existing = monthlyReportMapper.findReport(userId, month.toString(), gymId);
        if (existing != null) {
            return toStoredResponse(existing);
        }
        if (!properties.generateOnMiss()) {
            return generatingResponse(month);
        }
        return generateMonthlyReport(userId, month, gymId);
    }

    @Override
    @Transactional
    public MonthlyReportResponse generateMonthlyReport(Long userId, YearMonth month, Long gymId) {
        requireUser(userId);
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        MonthlyReportAggregate logAggregate = monthlyReportMapper.findLogAggregate(userId, from, to);
        MonthlyReportAggregate videoAggregate = monthlyReportMapper.findVideoFallbackAggregate(userId, from, to);
        MonthlyReportAggregate sourceAggregate = logAggregate.hasSessions()
                ? logAggregate.withSource(MonthlyReportSource.LOG)
                : videoAggregate.withSource(videoAggregate.getVideos() > 0 ? MonthlyReportSource.VIDEO_FALLBACK : MonthlyReportSource.NONE);

        MonthlyReportAggregate styleAggregate = monthlyReportMapper.findDynamicStaticAggregate(userId, from, to);
        Map<String, Integer> techniqueCounts = parseCounts(monthlyReportMapper.findTechniqueCountsJson(userId, from, to));
        List<String> underused = underusedTechniques(techniqueCounts);
        boolean enoughData = styleAggregate.getVideos() >= properties.minVideos()
                && sourceAggregate.getProblemsSolved() >= properties.minProblems();

        MonthlyReportNarrative narrative = enoughData
                ? narrativeClient.generate(sourceAggregate, techniqueCounts, underused)
                : null;

        List<MonthlyReportRecommendedGym> gyms = enoughData && !underused.isEmpty()
                ? monthlyReportMapper.findRecommendedGymsByTechniques(underused, 3)
                : List.of();

        MonthlyReportResponse response = buildResponse(
                month, sourceAggregate, styleAggregate, techniqueCounts, underused, gyms,
                gymId == null ? null : findGrade(userId, gymId, month),
                narrative, enoughData);
        saveReport(userId, gymId, response);
        return response;
    }

    @Override
    public MonthlyReportAvailablePeriodsResponse getAvailablePeriods(Long userId) {
        requireUser(userId);
        return new MonthlyReportAvailablePeriodsResponse(monthlyReportMapper.findAvailablePeriods(userId));
    }

    private MonthlyReportResponse toStoredResponse(com.holaclimbing.server.domain.stats.domain.MonthlyReport report) {
        try {
            return new MonthlyReportResponse(
                    report.getPeriod(),
                    report.getStatus(),
                    report.getSource(),
                    report.getGeneratedAt(),
                    objectMapper.readValue(report.getMetrics(), MonthlyReportResponse.Metrics.class),
                    readNullable(report.getGrade(), MonthlyReportResponse.Grade.class),
                    readNullable(report.getTip(), MonthlyReportResponse.Tip.class),
                    readNullable(report.getNextMonthGoal(), MonthlyReportResponse.Goal.class),
                    objectMapper.readValue(report.getRecommendedGyms(), objectMapper.getTypeFactory()
                            .constructCollectionType(List.class, MonthlyReportResponse.RecommendedGym.class)),
                    readNullable(report.getNarrative(), MonthlyReportResponse.Narrative.class),
                    readNullable(report.getRequirement(), MonthlyReportResponse.Requirement.class)
            );
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.MONTHLY_REPORT_GENERATION_FAILED);
        }
    }

    private <T> T readNullable(String json, Class<T> type) throws Exception {
        if (json == null || json.isBlank()) {
            return null;
        }
        return objectMapper.readValue(json, type);
    }

    private void requireUser(Long userId) {
        if (userMapper.findById(userId) == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private Map<String, Integer> parseCounts(String json) {
        try {
            return objectMapper.readValue(json == null ? "{}" : json, COUNTS_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<String> underusedTechniques(Map<String, Integer> counts) {
        return AnalysisTechniqueCatalog.CANONICAL_TECHNIQUES.stream()
                .sorted(Comparator.comparingInt(key -> counts.getOrDefault(key, 0)))
                .limit(2)
                .toList();
    }

    private Double ratio(int numerator, int denominator) {
        if (denominator <= 0) {
            return null;
        }
        return BigDecimal.valueOf((double) numerator / denominator)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private MonthlyReportResponse generatingResponse(YearMonth month) {
        return new MonthlyReportResponse(month.toString(), "generating", "none", null,
                null, null, null, null, List.of(), null, null);
    }
}
```

Add these private helpers to the same service:

```java
private MonthlyReportResponse.Grade findGrade(Long userId, Long gymId, YearMonth month) {
    LocalDate from = month.atDay(1);
    LocalDate to = month.atEndOfMonth();
    MonthlyReportAggregate current = monthlyReportMapper.findGradeFromLogs(userId, gymId, from, to);
    if (current == null || current.getMaxGrade() == null) {
        current = monthlyReportMapper.findGradeFromVideos(userId, gymId, from, to);
    }
    if (current == null || current.getMaxGrade() == null) {
        return null;
    }

    YearMonth prev = month.minusMonths(1);
    MonthlyReportAggregate previous = monthlyReportMapper.findGradeFromLogs(
            userId, gymId, prev.atDay(1), prev.atEndOfMonth());
    if (previous == null || previous.getMaxGrade() == null) {
        previous = monthlyReportMapper.findGradeFromVideos(
                userId, gymId, prev.atDay(1), prev.atEndOfMonth());
    }

    return new MonthlyReportResponse.Grade(
            current.getGradeGymId(),
            current.getGradeGymName(),
            current.getMaxGrade(),
            previous == null ? null : previous.getMaxGrade()
    );
}

private MonthlyReportResponse buildResponse(YearMonth month,
                                            MonthlyReportAggregate totals,
                                            MonthlyReportAggregate style,
                                            Map<String, Integer> techniqueCounts,
                                            List<String> underused,
                                            List<MonthlyReportRecommendedGym> gyms,
                                            MonthlyReportResponse.Grade grade,
                                            MonthlyReportNarrative narrative,
                                            boolean enoughData) {
    int styleTotal = style.getDynamicCount() + style.getStaticCount();
    MonthlyReportResponse.Metrics metrics = new MonthlyReportResponse.Metrics(
            totals.getSessions(),
            style.getVideos(),
            totals.getProblemsSolved(),
            totals.getGymsVisited(),
            totals.getPrimaryGymId(),
            totals.getPrimaryGymName(),
            style.getDynamicCount(),
            style.getStaticCount(),
            ratio(style.getDynamicCount(), styleTotal),
            ratio(style.getStaticCount(), styleTotal),
            techniqueCounts
    );

    if (!enoughData) {
        return new MonthlyReportResponse(
                month.toString(),
                MonthlyReportStatus.INSUFFICIENT_DATA.value(),
                totals.getSource().value(),
                OffsetDateTime.now(),
                metrics,
                grade,
                null,
                null,
                List.of(),
                null,
                new MonthlyReportResponse.Requirement(properties.minVideos(), properties.minProblems())
        );
    }

    MonthlyReportResponse.Tip tip = new MonthlyReportResponse.Tip(
            "underusedTechnique",
            underused,
            "이번 달에 적게 나온 기술을 다음 달 문제 선택 기준으로 삼아보면 좋아요."
    );
    MonthlyReportResponse.Goal goal = new MonthlyReportResponse.Goal(
            "부족했던 기술 문제 5개 도전",
            "specificTechnique",
            5,
            underused,
            "적게 나온 기술을 의식적으로 고르면 스타일이 더 균형 잡혀요."
    );
    List<MonthlyReportResponse.RecommendedGym> recommended = gyms.stream()
            .map(gym -> new MonthlyReportResponse.RecommendedGym(
                    gym.getGymId(),
                    gym.getName(),
                    parseTechniqueList(gym.getMatchedTechniques()),
                    gym.getMatchingVideoCount(),
                    "최근 공개 영상에서 부족했던 기술이 자주 나온 암장이에요."
            ))
            .toList();
    MonthlyReportResponse.Narrative responseNarrative = new MonthlyReportResponse.Narrative(
            narrative.getHeadline(),
            narrative.getSummary(),
            narrative.getHighlights()
    );

    return new MonthlyReportResponse(
            month.toString(),
            MonthlyReportStatus.READY.value(),
            totals.getSource().value(),
            OffsetDateTime.now(),
            metrics,
            grade,
            tip,
            goal,
            recommended,
            responseNarrative,
            null
    );
}

private List<String> parseTechniqueList(String json) {
    try {
        return objectMapper.readValue(json == null ? "[]" : json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    } catch (Exception e) {
        return List.of();
    }
}

private void saveReport(Long userId, Long gymId, MonthlyReportResponse response) {
    try {
        monthlyReportMapper.upsertReport(com.holaclimbing.server.domain.stats.domain.MonthlyReport.builder()
                .userId(userId)
                .period(response.period())
                .selectedGymId(gymId)
                .status(response.status())
                .source(response.source())
                .metrics(objectMapper.writeValueAsString(response.metrics()))
                .grade(response.grade() == null ? null : objectMapper.writeValueAsString(response.grade()))
                .tip(response.tip() == null ? null : objectMapper.writeValueAsString(response.tip()))
                .nextMonthGoal(response.nextMonthGoal() == null ? null : objectMapper.writeValueAsString(response.nextMonthGoal()))
                .recommendedGyms(objectMapper.writeValueAsString(response.recommendedGyms()))
                .narrative(response.narrative() == null ? null : objectMapper.writeValueAsString(response.narrative()))
                .requirement(response.requirement() == null ? null : objectMapper.writeValueAsString(response.requirement()))
                .model(properties.llm().mode())
                .promptVersion(properties.promptVersion())
                .generatedAt(response.generatedAt())
                .build());
    } catch (Exception e) {
        throw new BusinessException(ErrorCode.MONTHLY_REPORT_GENERATION_FAILED);
    }
}
```

- [ ] **Step 5: Run monthly report tests**

Run:

```bash
./mvnw -Dtest=MonthlyReportIntegrationTest test
```

Expected after this task:

```text
Status expected:<200> but was:<404>
```

Service exists; controller is still absent.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/holaclimbing/server/domain/stats/service src/main/java/com/holaclimbing/server/common/exception/error/ErrorCode.java
git commit -m "feat(stats): generate monthly report snapshots"
```

---

### Task 6: Add Controller Endpoints

**Files:**
- Create: `src/main/java/com/holaclimbing/server/domain/stats/MonthlyReportController.java`

- [ ] **Step 1: Add controller**

Create `MonthlyReportController.java`:

```java
package com.holaclimbing.server.domain.stats;

import static com.holaclimbing.server.common.exception.error.ErrorCode.*;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.docs.ApiErrorCodes;
import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.domain.stats.dto.response.MonthlyReportAvailablePeriodsResponse;
import com.holaclimbing.server.domain.stats.dto.response.MonthlyReportResponse;
import com.holaclimbing.server.domain.stats.service.MonthlyReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/stats/me/monthly-reports")
@RequiredArgsConstructor
public class MonthlyReportController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MonthlyReportService monthlyReportService;

    @ApiErrorCodes({USER_NOT_FOUND, INVALID_MONTH, MONTHLY_REPORT_GENERATION_FAILED})
    @GetMapping
    public ApiResponse<MonthlyReportResponse> getMonthlyReport(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Long gymId) {
        return ApiResponse.success(monthlyReportService.getMonthlyReport(userId, parseMonth(month), gymId));
    }

    @ApiErrorCodes({USER_NOT_FOUND})
    @GetMapping("/available")
    public ApiResponse<MonthlyReportAvailablePeriodsResponse> getAvailablePeriods(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.success(monthlyReportService.getAvailablePeriods(userId));
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(KST).minusMonths(1);
        }
        try {
            return YearMonth.parse(month);
        } catch (Exception e) {
            throw new BusinessException(INVALID_MONTH);
        }
    }
}
```

- [ ] **Step 2: Run monthly report tests**

Run:

```bash
./mvnw -Dtest=MonthlyReportIntegrationTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 3: Run adjacent stats tests**

Run:

```bash
./mvnw -Dtest=StatsIntegrationTest,ClimbingLogIntegrationTest,MonthlyReportIntegrationTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/holaclimbing/server/domain/stats/MonthlyReportController.java
git commit -m "feat(stats): expose monthly report API"
```

---

### Task 7: Add Optional LLM Client

**Files:**
- Create: `src/main/java/com/holaclimbing/server/domain/stats/service/OpenAiMonthlyReportNarrativeClient.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/stats/MonthlyReportProperties.java`

- [ ] **Step 1: Add OpenAI-compatible RestClient implementation**

Create `OpenAiMonthlyReportNarrativeClient.java`:

```java
package com.holaclimbing.server.domain.stats.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.domain.stats.MonthlyReportProperties;
import com.holaclimbing.server.domain.stats.domain.MonthlyReportAggregate;
import com.holaclimbing.server.domain.stats.domain.MonthlyReportNarrative;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.monthly-report.llm", name = "mode", havingValue = "openai")
public class OpenAiMonthlyReportNarrativeClient implements MonthlyReportNarrativeClient {

    private final RestClient.Builder restClientBuilder;
    private final MonthlyReportProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public MonthlyReportNarrative generate(MonthlyReportAggregate aggregate,
                                           Map<String, Integer> techniqueCounts,
                                           List<String> underusedTechniques) {
        try {
            RestClient client = restClientBuilder.baseUrl(properties.llm().baseUrl()).build();
            Map<String, Object> request = Map.of(
                    "model", properties.llm().model(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt()),
                            Map.of("role", "user", "content", objectMapper.writeValueAsString(Map.of(
                                    "metrics", aggregate.toPromptMap(),
                                    "techniqueCounts", techniqueCounts,
                                    "underusedTechniques", underusedTechniques
                            )))
                    ),
                    "response_format", Map.of("type", "json_object")
            );
            String body = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.llm().apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
            JsonNode content = objectMapper.readTree(body)
                    .path("choices").path(0).path("message").path("content");
            JsonNode parsed = objectMapper.readTree(content.asText());
            return new MonthlyReportNarrative(
                    parsed.path("headline").asText("볼륨과 스타일이 함께 쌓인 달"),
                    parsed.path("summary").asText("이번 달 기록에서 다음 성장 포인트가 뚜렷해졌어요."),
                    objectMapper.convertValue(parsed.path("highlights"), objectMapper.getTypeFactory()
                            .constructCollectionType(List.class, String.class))
            );
        } catch (Exception e) {
            log.warn("monthly report LLM narrative generation failed: {}", e.getMessage());
            return new RuleBasedMonthlyReportNarrativeClient()
                    .generate(aggregate, techniqueCounts, underusedTechniques);
        }
    }

    private String systemPrompt() {
        return """
                너는 클라이밍 앱의 월간 리포트 문장을 작성하는 데이터 애널리스트다.
                서버가 계산한 JSON만 근거로 한국어 존댓말 문장을 만든다.
                숫자, 횟수, 퍼센트, 난이도 값은 새로 쓰지 않는다.
                부상 위험이 있는 공격적 무브 처방, 의학적 조언, 신체 상태 판단을 하지 않는다.
                기술 다양성, 등반 볼륨, 기록 습관 중심으로만 제안한다.
                출력은 headline, summary, highlights 키를 가진 JSON 객체만 허용한다.
                """;
    }
}
```

- [ ] **Step 2: Keep tests on rule mode**

No test should set `app.monthly-report.llm.mode=openai`. Existing tests continue to use:

```java
"app.monthly-report.llm.mode=rule"
```

- [ ] **Step 3: Run tests**

Run:

```bash
./mvnw -Dtest=MonthlyReportIntegrationTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/holaclimbing/server/domain/stats/service/OpenAiMonthlyReportNarrativeClient.java
git commit -m "feat(stats): add monthly report llm client"
```

---

### Task 8: Add Monthly Scheduler

**Files:**
- Create: `src/main/java/com/holaclimbing/server/domain/stats/service/MonthlyReportScheduler.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/user/mapper/UserMapper.java`
- Modify: `src/main/resources/mapper/user/UserMapper.xml`

- [ ] **Step 1: Add mapper method for active users**

Add to `UserMapper.java`:

```java
List<Long> findActiveUserIdsForMonthlyReport();
```

Add to `UserMapper.xml`:

```xml
<select id="findActiveUserIdsForMonthlyReport" resultType="long">
    SELECT id
    FROM users
    WHERE deleted_at IS NULL
      AND status = 'ACTIVE'
    ORDER BY id
</select>
```

- [ ] **Step 2: Add scheduler**

Create `MonthlyReportScheduler.java`:

```java
package com.holaclimbing.server.domain.stats.service;

import com.holaclimbing.server.domain.stats.MonthlyReportProperties;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.monthly-report.scheduler", name = "enabled", havingValue = "true")
public class MonthlyReportScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MonthlyReportService monthlyReportService;
    private final UserMapper userMapper;

    @Scheduled(cron = "${app.monthly-report.scheduler.cron}", zone = "Asia/Seoul")
    public void generatePreviousMonthReports() {
        YearMonth targetMonth = YearMonth.now(KST).minusMonths(1);
        for (Long userId : userMapper.findActiveUserIdsForMonthlyReport()) {
            try {
                monthlyReportService.generateMonthlyReport(userId, targetMonth, null);
            } catch (Exception e) {
                log.warn("monthly report generation failed. userId={}, period={}, error={}",
                        userId, targetMonth, e.getMessage());
            }
        }
    }
}
```

- [ ] **Step 3: Compile**

Run:

```bash
./mvnw -DskipTests compile
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/holaclimbing/server/domain/stats/service/MonthlyReportScheduler.java src/main/java/com/holaclimbing/server/domain/user/mapper/UserMapper.java src/main/resources/mapper/user/UserMapper.xml
git commit -m "feat(stats): schedule monthly report generation"
```

---

### Task 9: Update API Spec and Run Full Verification

**Files:**
- Modify: `/Users/minjoun/Workspace/projects/Hola-Climbing/api 명세서/hola-climbing-api-spec.md`
- Modify: `README.md` if the feature list needs a short mention.

- [ ] **Step 1: Add API spec rows**

Add rows under the stats API list:

```markdown
| 통계 | 월간 리포트 조회 | GET | /api/stats/me/monthly-reports | 기록 우선 월간 리포트 + 영상 분석 인사이트 | 1 | Done | Not started |
| 통계 | 월간 리포트 월 목록 | GET | /api/stats/me/monthly-reports/available | 생성된 월간 리포트 기간 목록 | 1 | Done | Not started |
```

- [ ] **Step 2: Add endpoint details after monthly calendar**

Add:

```markdown
### 5.6 월간 리포트 조회

**URL** `GET /api/stats/me/monthly-reports?month=2026-05&gymId=1`

**정책**
- 완등 수, 세션 수, 방문 암장 수는 `climbing_logs` 기록 우선.
- 해당 월 기록이 없고 영상이 있으면 영상 기준으로 fallback.
- `gymId`가 있으면 해당 암장의 `gym_grades.difficulty_order` 기준으로 최고 난이도를 계산.
- `gymId`가 없거나 선택 암장 데이터가 없으면 `grade`는 `null`.
- dynamic/static과 기술 통계는 해당 월 모든 영상의 `analysis_video_results` 기준.
- LLM은 숫자를 만들지 않고 짧은 한국어 문장만 생성.
```

- [ ] **Step 3: Run focused verification**

Run:

```bash
./mvnw -Dtest=StatsIntegrationTest,ClimbingLogIntegrationTest,MonthlyReportIntegrationTest,FlywayMigrationIntegrationTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 4: Run full test suite**

Run:

```bash
./mvnw test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 5: Commit docs and verification cleanup**

```bash
git add README.md "/Users/minjoun/Workspace/projects/Hola-Climbing/api 명세서/hola-climbing-api-spec.md"
git commit -m "docs(stats): document monthly report API"
```

---

## Implementation Order Summary

1. Storage and fixture.
2. Contracts and properties.
3. Failing integration tests.
4. Aggregation mapper.
5. Service and deterministic narrative.
6. Controller.
7. Optional LLM client.
8. Monthly scheduler.
9. API spec and full verification.

## Self-Review

- Spec coverage: The plan covers selected-gym max grade, log-first totals with video fallback, all-month video style statistics, underused technique tip, next-month goal, technique-based gym recommendations, LLM guardrails, and monthly batch generation.
- Scope: This is one backend feature within `domain/stats`. It does not require changes to `domain/report`.
- Type consistency: API response names use camelCase and are wrapped by `ApiResponse<T>`.
- Risk note: `V11__fix_audited_gym_grades.sql` already exists in the worktree, so this feature uses `V12__monthly_reports.sql`.
