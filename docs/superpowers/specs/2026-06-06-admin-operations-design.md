# 2026-06-06 Admin Operations Design

## Goal

Hola 운영자가 암장 데이터, 회원 상태, 신고 처리를 실수 없이 처리할 수 있는 관리자 백엔드 계약을 만든다. 이 저장소는 Spring 서버이므로 1차 산출물은 `/api/admin/**` API, 권한 모델, 감사 로그, 테스트이며, 프론트 관리자 화면은 이 API를 사용하는 별도 클라이언트의 정보 구조로 정의한다.

## Current State

- 관리자 도메인과 `/api/admin/**` API가 없다.
- `users` 테이블에는 `role`이나 계정 운영 상태 컬럼이 없다.
- JWT 인증 필터는 모든 인증 사용자를 `ROLE_USER`로 등록한다.
- `gyms.status`는 `active`, `pending`, `closed`를 표현하지만 승인/반려 API가 없다.
- `reports.status`, `reviewed_by`, `reviewed_at` 컬럼은 있지만 신고 처리 API가 없다.
- `UserTokenRevoker`가 이미 있어 계정 정지나 강제 로그아웃 시 기존 토큰 무효화에 재사용할 수 있다.
- 암장 난이도는 `gym_grades` 마스터로 정규화되어 있어 관리자 편집 대상으로 삼기 좋다.

## Product And UX Principles

관리자 화면의 첫 번째 목적은 통계 감상이 아니라 처리 대기 업무를 줄이는 것이다. 첫 화면은 "오늘 확인해야 할 것"을 기준으로 설계한다.

- 우선순위: 신고 대기, 암장 제안 대기, 분석 실패 영상, 정지/탈퇴 이슈.
- 화면 밀도: 운영 도구는 카드형 랜딩보다 표 중심이 적합하다.
- 상세 탐색: 목록에서 행을 선택하면 우측 drawer나 상세 페이지로 맥락을 이어간다.
- 위험 액션: 회원 정지, 암장 폐쇄, 콘텐츠 숨김은 확인 메시지와 사유 입력을 요구한다.
- 일괄 입력: 업로드 직후 바로 저장하지 않고, 미리보기와 오류 행 확인을 거친다.
- 추적성: 모든 관리자 변경은 `admin_audit_logs`에 남긴다.

## Approved V1 Scope

V1은 운영에 바로 필요한 흐름으로 제한한다.

1. 관리자 권한 모델
   - `USER`, `ADMIN` 두 역할만 둔다.
   - `ACTIVE`, `SUSPENDED`, `DELETED` 세 계정 상태를 둔다.
   - 관리자 계정은 DB seed 또는 수동 SQL로 생성할 수 있게 한다.

2. 운영 홈
   - 대기 중인 암장 제안 수
   - 대기 중인 신고 수
   - 분석 실패 영상 수
   - 오늘 가입한 회원 수

3. 암장 관리
   - 상태별 암장 목록 조회
   - 암장 상세 조회
   - 운영자 직접 암장 생성
   - 사용자 제안 암장 승인/반려
   - 암장 정보 수정
   - 암장 폐쇄
   - 암장별 난이도 목록 교체
   - JSON 기반 일괄 입력 미리보기와 적용

4. 회원 관리
   - 회원 목록 검색
   - 회원 상세 조회
   - 계정 상태 변경
   - 강제 토큰 무효화

5. 신고 처리
   - 신고 목록 조회
   - 신고 상세 조회
   - 신고 처리 상태 변경
   - 처리 액션으로 영상 삭제, 댓글 삭제, 사용자 정지를 연결

6. 감사 로그
   - 모든 관리자 변경 액션 기록
   - 관리자, 액션, 대상 타입, 대상 ID, 사유, 변경 전후 JSON, 생성 시각 저장

## V2 Scope

V2는 V1 API가 안정화된 뒤 붙인다.

- AI 분석 실패 큐 상세와 DLQ 재처리 UI
- 약관 버전 생성/활성화
- 공지성 시스템 알림 발송
- 관리자 화면 프론트 구현
- 관리자 역할 세분화: `GYM_MANAGER`, `MODERATOR`, `SUPER_ADMIN`

## UX Information Architecture

관리자 클라이언트는 다음 내비게이션을 가진다.

```text
Admin
├── Home
├── Gyms
│   ├── All
│   ├── Pending
│   └── Bulk Import
├── Users
├── Reports
├── Audit Logs
└── Settings
```

### Home

Home은 네 개의 업무 큐 카운터와 최근 감사 로그를 보여준다.

- `Pending gyms`: 사용자 제안 암장 승인 대기
- `Pending reports`: 모더레이션 대기 신고
- `Failed analyses`: 재분석 판단이 필요한 영상
- `New users today`: 운영 상태 확인용 성장 지표

각 카운터는 클릭하면 해당 필터가 적용된 목록으로 이동한다.

### Gyms

암장 목록은 필터와 행 액션 중심이다.

- 필터: status, regionCode, keyword
- 주요 컬럼: id, name, address, regionCode, status, createdBy, ratingAvg, ratingCount, updatedAt
- 행 액션: 상세, 수정, 승인, 반려, 폐쇄, 난이도 관리

일괄 입력은 두 단계로 동작한다.

1. `POST /api/admin/gyms/import/preview`
   - 요청 JSON을 검증하고 `validRows`, `invalidRows`를 반환한다.
2. `POST /api/admin/gyms/import`
   - 동일 payload를 실제 DB에 upsert하고 결과 건수를 반환한다.

### Users

회원 목록은 운영 리스크를 빠르게 판단하는 데 집중한다.

- 필터: status, role, email/nickname keyword, emailVerified
- 주요 컬럼: id, email, nickname, role, status, emailVerified, lastLoginAt, createdAt
- 상세: 프로필, 가입일, 마지막 로그인, 영상 수, 신고 받은 수, 신고한 수
- 액션: 상태 변경, 토큰 무효화

### Reports

신고 목록은 처리 대기 큐다.

- 필터: status, targetType, category
- 주요 컬럼: id, targetType, targetId, category, reporterId, status, createdAt
- 상세: 신고자, 대상 소유자, 대상 요약, 사유, 처리 이력
- 액션: 반려, 해결, 영상 삭제, 댓글 삭제, 사용자 정지

### Audit Logs

감사 로그는 문제 추적용 읽기 전용 화면이다.

- 필터: adminId, action, targetType, targetId, date range
- 주요 컬럼: id, adminId, action, targetType, targetId, reason, createdAt
- 상세: beforeJson, afterJson

## Data Model

### users

`users`에 역할과 운영 상태를 추가한다.

```sql
ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER',
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

CREATE INDEX idx_users_role_status ON users(role, status);
CREATE INDEX idx_users_status_created_at ON users(status, created_at DESC);
```

상태 의미:

- `ACTIVE`: 로그인 가능
- `SUSPENDED`: 로그인과 인증 API 사용 불가
- `DELETED`: 탈퇴 또는 관리자 삭제 상태

역할 의미:

- `USER`: 일반 사용자
- `ADMIN`: 관리자 API 접근 가능

### admin_audit_logs

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

### Existing Tables Reused

- `gyms.status`: 암장 승인/반려/폐쇄 흐름에 사용한다.
- `reports.status`, `reviewed_by`, `reviewed_at`: 신고 처리 흐름에 사용한다.
- `gym_grades.is_active`: 난이도 삭제 대신 비활성화에 사용한다.
- `videos.deleted_at`, `comments.deleted_at`: 관리자 모더레이션 삭제에 사용한다.

## API Design

모든 관리자 API는 `ROLE_ADMIN`이 필요하다.

### Dashboard

`GET /api/admin/dashboard`

```json
{
  "pendingGymCount": 3,
  "pendingReportCount": 7,
  "failedAnalysisVideoCount": 2,
  "newUserCountToday": 12
}
```

### Gyms

`GET /api/admin/gyms?status=pending&keyword=강남&page=0&size=20`

`GET /api/admin/gyms/{gymId}`

`POST /api/admin/gyms`

`PATCH /api/admin/gyms/{gymId}`

`POST /api/admin/gyms/{gymId}/approve`

`POST /api/admin/gyms/{gymId}/reject`

`POST /api/admin/gyms/{gymId}/close`

`PUT /api/admin/gyms/{gymId}/grades`

`POST /api/admin/gyms/import/preview`

`POST /api/admin/gyms/import`

암장 일괄 입력 요청은 JSON 배열을 기준으로 한다. CSV 파싱은 프론트가 맡기거나 V2에서 서버 multipart로 확장한다.

```json
{
  "rows": [
    {
      "externalKey": "theclimb-sillim",
      "name": "더클라임 신림",
      "address": "서울 관악구 신원로 35 3층",
      "lat": 37.4826,
      "lng": 126.9295,
      "regionCode": "seoul",
      "phone": "02-0000-0000",
      "website": "https://example.com",
      "businessHours": {
        "mon": {"open": "10:00", "close": "23:00"}
      },
      "grades": [
        {"label": "노랑", "difficultyOrder": 10},
        {"label": "주황", "difficultyOrder": 20}
      ]
    }
  ]
}
```

### Users

`GET /api/admin/users?status=ACTIVE&keyword=climber&page=0&size=20`

`GET /api/admin/users/{userId}`

`PATCH /api/admin/users/{userId}/status`

```json
{
  "status": "SUSPENDED",
  "reason": "반복 신고 처리"
}
```

`POST /api/admin/users/{userId}/revoke-tokens`

```json
{
  "reason": "계정 보안 조치"
}
```

### Reports

`GET /api/admin/reports?status=pending&targetType=video&page=0&size=20`

`GET /api/admin/reports/{reportId}`

`PATCH /api/admin/reports/{reportId}/status`

```json
{
  "status": "resolved",
  "resolutionAction": "delete_video",
  "reason": "부적절한 영상으로 판단"
}
```

`resolutionAction` 값:

- `none`
- `delete_video`
- `delete_comment`
- `suspend_user`

### Audit Logs

`GET /api/admin/audit-logs?targetType=gym&targetId=1&page=0&size=20`

## Security Design

`JwtTokenProvider`는 access token에 `role` claim을 넣는다. `JwtAuthenticationFilter`는 이 값을 읽어 `ROLE_USER` 또는 `ROLE_ADMIN` 권한을 생성한다.

로그인 시 다음 조건을 적용한다.

- `deleted_at IS NULL`
- `status = 'ACTIVE'`
- `email_verified = TRUE`

관리자가 회원 상태를 `SUSPENDED` 또는 `DELETED`로 바꾸면 `UserTokenRevoker.revokeAllFor(userId)`를 호출한다.

`SecurityConfig`는 다음 정책을 가진다.

- `/api/auth/**`: 공개
- Swagger와 Actuator: 운영 프로필에서는 제한하거나 별도 네트워크에서만 노출
- `/api/admin/**`: `hasRole("ADMIN")`
- 그 외 보호 대상은 명시적으로 `authenticated`
- 마지막 fallback은 `denyAll` 또는 `authenticated` 중 하나로 둔다. V1에서는 기존 공개 GET API를 명시한 뒤 `.anyRequest().authenticated()`를 사용한다.

## Error Handling

기존 `ErrorCode` prefix 체계를 유지한다.

- 권한 없음: `C003`
- 관리자 대상 없음: 기존 도메인 에러 사용
  - 사용자: `U001`
  - 암장: `G001`
  - 영상: `V001`
- 관리자 입력 오류: `C001`
- 정지 계정 로그인: `U012 USER_SUSPENDED`

새 에러는 최소화한다. 관리자 전용 세분 에러보다 기존 도메인 에러를 우선 재사용한다.

## Testing Strategy

테스트는 통합 테스트 중심으로 작성한다.

- 일반 사용자 또는 비로그인 사용자는 `/api/admin/**` 접근 시 401/403.
- 관리자는 `/api/admin/dashboard` 조회 가능.
- 암장 제안 승인 시 `gyms.status`가 `active`로 바뀐다.
- 암장 반려 또는 폐쇄 시 공개 조회에서 제외된다.
- 난이도 교체 시 기존 활성 난이도는 비활성화되고 새 목록이 활성화된다.
- JSON 일괄 입력 preview는 오류 행을 저장하지 않는다.
- JSON 일괄 입력 apply는 유효 행만 저장하고 결과 카운트를 반환한다.
- 회원 정지 후 기존 access token은 거부된다.
- 신고 처리 시 `reports.status`, `reviewed_by`, `reviewed_at`이 갱신된다.
- 신고 처리 액션이 영상/댓글 삭제 또는 회원 정지를 수행한다.
- 모든 관리자 변경은 `admin_audit_logs`에 남는다.

## Alternatives Considered

### A. DB 수동 관리

좋은 점: 가장 빠르다.  
나쁜 점: 권한, 검증, 감사 로그, 실수 방지가 없다.  
되돌리기 비용: 낮다. 코드가 없으므로 폐기하면 된다.  
판단: 발표 전 임시 데이터 보정에는 가능하지만 운영 도구로 부적합하다.

### B. Backend Admin API First

좋은 점: Swagger/Postman으로 바로 운영 가능하고 프론트 관리자 화면도 이 계약 위에 붙일 수 있다. 기존 Spring/MyBatis 구조와 잘 맞는다.  
나쁜 점: UI 완성 전에는 운영자가 API 도구를 써야 한다.  
되돌리기 비용: 중간이다. `/api/admin/**`와 DB 컬럼은 남지만 역할 세분화로 확장 가능하다.  
판단: V1 추천안이다.

### C. Full Admin Console

좋은 점: 운영자 경험이 가장 좋다.  
나쁜 점: 현재 서버 저장소 범위를 넘어 프론트 작업까지 필요하고 일정 부담이 크다.  
되돌리기 비용: 높다. 백엔드와 프론트 양쪽 설계를 같이 바꿔야 한다.  
판단: V1 이후에 진행한다.

## Recommendation

V1은 Backend Admin API First로 구현한다. Minjoun의 현재 Hola 상황은 실사용 전 운영 하드닝과 데이터 정합 관리가 급하므로, 관리자 프론트를 먼저 그리는 것보다 안전한 권한 모델과 운영 API를 먼저 만드는 편이 낫다.

## Acceptance Criteria

- 최초 관리자 계정이 생성 가능하다.
- 관리자만 `/api/admin/**`에 접근할 수 있다.
- 운영자는 암장 제안을 승인/반려할 수 있다.
- 운영자는 암장 정보를 직접 생성/수정/폐쇄할 수 있다.
- 운영자는 암장별 난이도를 교체할 수 있다.
- 운영자는 JSON 일괄 입력 전에 오류 행을 확인할 수 있다.
- 운영자는 회원을 검색하고 상태를 바꿀 수 있다.
- 정지된 회원은 기존 토큰과 새 로그인 모두 막힌다.
- 운영자는 신고를 처리하고 필요한 모더레이션 액션을 수행할 수 있다.
- 관리자 변경 이력은 감사 로그로 조회 가능하다.
