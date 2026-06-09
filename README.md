# Hola (올라) — AI 클라이밍 영상 SNS · Backend Server

> AI 동작 분석 기반 클라이밍 영상 SNS의 백엔드 서버
> SSAFY 자율 프로젝트 · 2026.05.15 ~ 2026.06.25

---

## 프로젝트 소개

**Hola(올라)** 는 클라이밍을 즐기는 사람들을 위한 **AI 동작 분석 기반 영상 SNS** 입니다.

클라이밍은 "잘 오르는 법"을 글이나 말로 배우기 어려운 운동입니다. 같은 문제(루트)를
풀어도 사람마다 무브가 다르고, 본인이 어디서 힘을 낭비하는지, 어떤 동작이 비효율적인지를
스스로 알아채기 어렵습니다. 기존의 클라이밍 커뮤니티는 단순히 영상을 올리고 댓글을 다는
수준에 머물러 있어, "내 클라이밍이 어떻게 보이는지"에 대한 객관적인 피드백을 주지 못합니다.

Hola는 이 지점을 파고듭니다. 사용자가 자신의 클라이밍 영상을 업로드하면, 영상은
**AI 동작 분석 파이프라인**을 거쳐 어떤 기술(하이스텝, 플래깅, 데드포인트 등)을 몇 번
사용했는지, 동작 구간이 어떻게 나뉘는지 등을 분석합니다. 사용자는 단순히 영상을 공유하는
것을 넘어, **자신의 클라이밍을 데이터로 되돌아보고 성장의 흐름을 추적**할 수 있습니다.

여기에 SNS의 핵심 경험 — 팔로우·피드·댓글·좋아요·실시간 채팅 — 을 결합하고,
**암장(클라이밍짐) 정보, 리뷰, 즐겨찾기, 통계, 달력형 클라이밍 기록**까지 더해,
"클라이머의 일상을 담는 공간"을 지향합니다.

이 저장소(`hola-climbing-server`)는 그 중 **백엔드 서버**를 담당합니다. 회원·영상·암장·
채팅·통계·알림 등 모든 도메인 API를 제공하며, 영상 바이너리는 서버를 경유하지 않고
**GCS Signed URL로 클라이언트가 직접 업로드**하도록 설계해 서버 부하를 최소화했습니다.
AI 분석은 별도의 Python 워커가 수행하며, 백엔드는 분석 요청을 디스패치하고 결과를
수신하는 역할을 맡습니다.

### 팀 구성

| 이름 | 역할 |
|------|------|
| 김민준 | Backend (Spring Boot) · AI 파이프라인 (Python) |
| 곽예경 | Frontend (Vue 3 / Capacitor) · AI 파이프라인 (Python) |

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| Language / Runtime | Java 25 |
| Framework | Spring Boot 4.0.x, Spring Security 7, Spring WebSocket |
| Persistence | MyBatis 4.0, PostgreSQL + pgvector |
| Cache / Session | Redis (Lettuce) — JWT 블랙리스트 |
| Auth | JWT (jjwt 0.12.x), BCrypt |
| Storage | Google Cloud Storage (v4 Signed URL) |
| Realtime | WebSocket + STOMP |
| Docs | springdoc-openapi (Swagger UI) |
| Build | Maven |
| Test | JUnit 5, Testcontainers (PostgreSQL · Redis) |

### 설계 원칙

- **영상 바이너리는 서버를 거치지 않는다** — GCS Signed URL로 클라이언트가 직접 업로드/재생
- **WebSocket은 Spring Boot 전담** — 실시간 채팅은 백엔드에서 처리, Python은 분석만
- 모든 응답은 `ApiResponse<T>` 래퍼로 통일, 페이지 응답은 `PageResponse<T>`
- 모든 오류는 `ErrorCode` enum + `BusinessException` + `GlobalExceptionHandler`로 일원화
- 도메인 단위 패키지 구조 (`domain/{도메인}` → Controller · Service · Mapper · DTO · Domain)
- JSON 직렬화는 `camelCase`, 식별자는 `Long`(BIGSERIAL)

---

## 실행 방법

### 사전 요구사항

- JDK 25
- Maven 3.9+
- Docker (PostgreSQL + pgvector, Redis 컨테이너 구동용)

### 1. 인프라 컨테이너 실행

```bash
# PostgreSQL (pgvector 확장 포함)
docker run -d --name hola-postgres \
  -e POSTGRES_DB=hola -e POSTGRES_USER=hola -e POSTGRES_PASSWORD=hola \
  -p 5432:5432 pgvector/pgvector:pg16

# Redis
docker run -d --name hola-redis -p 6379:6379 redis:7
```

### 2. 데이터베이스 스키마 적용

```bash
psql postgresql://hola:hola@localhost:5432/hola -f db/schema.sql
```

### 3. 환경 변수 설정

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `SPRING_PROFILES_ACTIVE` | 실행 프로파일 | `local` |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/hola` |
| `SPRING_DATASOURCE_USERNAME` | DB 사용자 | `hola` |
| `SPRING_DATASOURCE_PASSWORD` | DB 비밀번호 | `hola` |
| `SPRING_DATA_REDIS_HOST` | Redis 호스트 | `localhost` |
| `JWT_SECRET` | JWT 서명 키 (32바이트 이상, `prod`에서는 개발 기본값 금지) | 개발용 기본값 존재 |
| `GCS_BUCKET` | 영상 저장 GCS 버킷 | `hola-climbing-log-videos` |
| `AI_ANALYSIS_URL` | AI 분석 워커 URL (비우면 디스패치 비활성) | (없음) |
| `AI_CALLBACK_SECRET` | AI 워커 결과 콜백 공유 시크릿 | (없음) |
| `ADMIN_BOOTSTRAP_ENABLED` | 인증 완료 회원을 최초 관리자로 승격할지 여부 | `false` |
| `ADMIN_BOOTSTRAP_EMAIL` | 최초 관리자로 승격할 회원 이메일 | (없음) |
| `APP_MAIL_MODE` | 메일 발송 모드 (`log` 또는 `smtp`, `prod`는 `smtp` 필수) | `log` |
| `APP_MAIL_FROM` | SMTP 발송 From 주소 | `no-reply@hola.local` |
| `CORS_ALLOWED_ORIGINS` | 허용 Origin | `http://localhost:5173` |

> `JWT_SECRET`, `AI_ANALYSIS_URL`, `AI_CALLBACK_SECRET`, GCS 자격증명, SMTP 설정은 운영 환경에서 반드시 별도 설정해야 합니다.

### 4. 서버 실행

```bash
mvn spring-boot:run
# 또는
mvn clean package && java -jar target/server-*.jar
```

서버는 기본 `8080` 포트에서 동작합니다.

- API 문서(Swagger UI): `http://localhost:8080/swagger-ui.html`
- 헬스 체크: `http://localhost:8080/actuator/health`

### 5. 테스트

```bash
mvn test
```

통합 테스트는 **Testcontainers** 로 PostgreSQL·Redis 컨테이너를 자동 기동하므로,
Docker만 실행 중이면 별도 인프라 준비 없이 전체 스위트를 검증할 수 있습니다.

### 운영 배포 체크리스트

- 운영 DB에는 `db/schema.sql` 또는 필요한 수동 migration을 먼저 적용합니다.
  이미 운영 중인 DB라면 `db/manual-migrations/2026-06-06-admin-operations.sql`처럼
  idempotent migration을 사용합니다.
- `JWT_SECRET`, `SPRING_DATASOURCE_*`, `SPRING_DATA_REDIS_*`, `GCS_BUCKET`,
  GCS 자격증명, `CORS_ALLOWED_ORIGINS`, `AI_ANALYSIS_URL`, `AI_CALLBACK_SECRET`,
  `APP_MAIL_MODE=smtp`, SMTP 호스트/계정은 운영 값으로 분리합니다.
- 최초 운영자 계정은 일반 회원가입과 이메일 인증을 완료한 뒤
  `ADMIN_BOOTSTRAP_ENABLED=true`, `ADMIN_BOOTSTRAP_EMAIL={인증 완료 이메일}`로 한 번 승격합니다.
  이후 운영자 변경은 `PATCH /api/admin/users/{userId}/role`을 사용하며,
  역할 변경 후에는 다시 로그인해야 새 role claim이 포함된 JWT가 발급됩니다.
- AI 워커 결과 콜백은 `POST /api/analysis/**` 요청에
  `X-AI-Callback-Secret: ${AI_CALLBACK_SECRET}` 헤더를 포함해야 합니다.
  Spring 서버와 Python 워커의 값이 다르면 분석 결과 저장이 401로 거부됩니다.

- `/actuator/health`를 로드밸런서 헬스 체크로 사용합니다.
  `/actuator/metrics` 등 세부 운영 지표는 `ADMIN` 토큰이 필요합니다.
  운영 로그에는 `requestId`를 함께 남깁니다.
- 외부 공개 환경에서는 HTTPS/reverse proxy, DB 백업, Redis 가용성, Swagger UI 공개 여부를 별도 점검합니다.

---

## 전체 기능 설명

기능은 **기본 / 추가 / 심화** 세 단계로 구분합니다.

### 🟢 기본 기능 — SNS 코어

클라이밍 영상 SNS가 갖춰야 할 핵심 사용자 경험입니다.

| 도메인 | 기능 |
|--------|------|
| 회원·인증 | 이메일 인증 회원가입, 로그인, JWT 토큰 재발급, 로그아웃, 비밀번호 재설정, 회원 탈퇴 |
| 약관 | 활성 약관 조회, 약관 동의 (필수 약관 검증) |
| 프로필 | 본인/타 사용자 프로필 조회, 본인 정보 수정 |
| 소셜 그래프 | 팔로우 / 언팔로우, 팔로워·팔로잉 목록 |
| 차단 | 사용자 차단 / 해제, 차단 목록 조회 |
| 영상 | 영상 등록, 목록·상세 조회, 수정, 삭제 |
| 영상 상호작용 | 댓글 작성·수정·삭제·목록, 좋아요 / 좋아요 취소 |
| 암장 | 암장 검색·목록, 상세 조회, 근처 암장(좌표 반경) 조회 |
| 즐겨찾기 | 암장 즐겨찾기 추가 / 해제, 즐겨찾기 목록 |

### 🟡 추가 기능 — 클라이밍 특화 경험

일반 SNS를 넘어, "클라이밍" 이라는 도메인에 맞춰 확장한 기능입니다.

| 도메인 | 기능 |
|--------|------|
| 알림 | 댓글·좋아요·팔로우 이벤트 알림, 알림 목록·읽음 처리, 미읽음 수, 알림 설정 조회/변경 |
| 신고 | 영상·사용자 등 콘텐츠 신고, 자기 신고·중복 신고 차단 |
| 통계 | 내 클라이밍 통계, 기술별(동작별) 사용 빈도 통계 |
| 암장 리뷰 | 리뷰 작성·목록·수정·삭제 (사용자당 암장 1개 리뷰), 평점 자동 재집계 |
| 암장 관리 | 사용자 암장 등록 제안(승인 대기), 암장 사진 업로드 / 조회 |
| 클라이밍 기록·달력 | "어느 암장에서 난이도별 몇 문제를 풀었다"는 기록 CRUD, 월간·일별 달력 조회 |
| 추천 피드 | 팔로우 + 최신 영상 기반 개인화 피드 (`following` / `recommended` 태그) |
| 실시간 채팅 | 암장별 채팅방 (WebSocket STOMP), 메시지별 GPS 암장 인증 |

### 🔴 심화 기능 — AI 동작 분석

Hola의 정체성을 만드는, 가장 차별화된 기능입니다.

- **GCS Signed URL 직접 업로드** — 영상 바이너리는 백엔드를 경유하지 않습니다.
  업로드 URL 발급 → 클라이언트가 GCS에 직접 업로드 → 영상 메타데이터만 서버에 등록.
- **AI 분석 디스패치** — 영상 등록 시 분석 상태를 `pending`으로 저장하고,
  Python AI 워커로 분석을 요청합니다.
- **분석 결과 수신·조회** — AI 워커가 동작 분석 결과(기술 사용 빈도, 구간 분할 등)를
  `X-AI-Callback-Secret` 헤더가 포함된 콜백으로 전달하면 서버가 저장하고,
  사용자는 분석 결과·진행률을 조회할 수 있습니다.
- **분석 피드백 / 재시도** — 분석 결과에 대한 피드백 수집, 실패 시 재시도 요청.
- **GPS 암장 인증** — 채팅 메시지 작성 시 좌표를 검증해 암장 반경 300m 이내 인증 표시.
- **pgvector 기반 추천 (고도화 예정)** — 암장·영상의 `style_embedding` 벡터 유사도를
  활용한 추천. 현재는 휴리스틱 피드로 동작하며, AI 임베딩 데이터 축적 후 고도화 예정.

---

## 프로젝트 구조

```
hola-climbing-server/
├── db/
│   └── schema.sql                  # 전체 DB 스키마 (테이블 · 인덱스 · 초기 데이터)
├── src/main/java/com/holaclimbing/server/
│   ├── common/                     # 공통 — 응답 래퍼, 예외, 시큐리티, 설정
│   │   ├── response/               #   ApiResponse · PageResponse
│   │   ├── exception/              #   ErrorCode · BusinessException · GlobalExceptionHandler
│   │   ├── security/               #   JWT 필터 · SecurityConfig
│   │   └── config/                 #   Jackson · Swagger 등
│   ├── domain/                     # 도메인별 패키지
│   │   ├── user/                   #   회원 · 인증 · 프로필 · 팔로우 · 차단 · 약관
│   │   ├── video/                  #   영상 · 댓글 · 좋아요
│   │   ├── gym/                    #   암장 · 리뷰 · 사진
│   │   ├── favorite/               #   즐겨찾기
│   │   ├── chat/                   #   실시간 채팅 (WebSocket)
│   │   ├── notification/           #   알림
│   │   ├── stats/                  #   통계 · 클라이밍 기록 · 달력
│   │   ├── analysis/               #   AI 분석 결과 연동
│   │   ├── recommendation/         #   추천 피드
│   │   └── report/                 #   신고
│   └── infrastructure/             # GCS · 메일 등 외부 연동
└── src/test/                       # Testcontainers 기반 통합 테스트
```

각 도메인은 `Controller → Service / ServiceImpl → Mapper(+XML)` 구조를 따르며,
`*IntegrationTest` 와 `src/test/resources/sql/*-schema.sql` 테스트 픽스처를 함께 포함합니다.

---

## API 문서

서버 실행 후 Swagger UI에서 전체 API 명세와 도메인별 에러 코드를 확인할 수 있습니다.

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- 에러 코드 목록: `GET /api/docs/error-codes`

## 운영자 API

`/api/admin/**`는 `ROLE_ADMIN` 토큰이 필요합니다. 운영자가 회원 상태를 정지하거나
콘텐츠를 삭제하는 변경 작업은 `admin_audit_logs`에 기록됩니다.

- `GET /api/admin/dashboard`
- `GET /api/admin/audit-logs`
- `GET /api/admin/gyms`
- `GET /api/admin/gyms/{gymId}`
- `POST /api/admin/gyms`
- `PATCH /api/admin/gyms/{gymId}`
- `POST /api/admin/gyms/{gymId}/approve`
- `POST /api/admin/gyms/{gymId}/reject`
- `POST /api/admin/gyms/{gymId}/close`
- `PUT /api/admin/gyms/{gymId}/grades`
- `POST /api/admin/gyms/import/preview`
- `POST /api/admin/gyms/import`
- `GET /api/admin/users`
- `GET /api/admin/users/{userId}`
- `PATCH /api/admin/users/{userId}/status`
- `PATCH /api/admin/users/{userId}/role`
- `POST /api/admin/users/{userId}/revoke-tokens`
- `GET /api/admin/reports`
- `PATCH /api/admin/reports/{reportId}/status`
