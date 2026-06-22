# 2026-06-22 Gym Grade Audit

## Scope
- Target: production `gyms` active rows after Flyway V9.
- Production snapshot at audit time: `active_gyms=75`, `active_grades=628`.
- Method: compare production grades with `output-researched/kakao-gyms-researched.csv`, then search public blog snippets for gyms added after the original researched seed.
- Status: audit only. `2026-06-22-fix-audited-gym-grades.sql` is a draft and has not been applied to production.

## Existing Researched Seed Check
- Existing researched rows matched: 54.
- Direct mismatches: 3, all Son Sangwon branches.
- Son Sangwon is confirmed as the newer 10-step order after manual review: `흰노초파빨검회갈핑보`.

## Confirmed Fix Candidates
| Gym / Brand | Current | Candidate | Evidence |
|---|---|---|---|
| 알레클라이밍 혜화/강동/영등포 | `흰색 > 노랑 > 주황 > 초록 > 파랑 > 빨강 > 보라 > 갈색 > 회색 > 검정` | `흰색 > 노랑 > 연두 > 초록 > 파랑 > 빨강 > 회색 > 갈색 > 검정` | Manual review: official/base setting ends with `검정`; event pink is not the standard grade tag |
| 락트리클라이밍 분당 | default rainbow with white first | `빨강 > 주황 > 노랑 > 초록 > 파랑 > 남색 > 보라 > 흰색 > 회색 > 검정` | Naver blogs: `224296119270`, `223912349325` |
| 스톤즈클라이밍 | default rainbow with white first | `빨강 > 주황 > 노랑 > 초록 > 파랑 > 남색 > 보라 > 흰색 > 검정` | Naver blogs: `224240756939`, `223891312414`, `223583482403` |
| 피크닉클라이밍 | default rainbow with white first | `빨강 > 주황 > 노랑 > 초록 > 파랑 > 남색 > 보라 > 갈색 > 검정 > 핑크` | Naver blog: `224314067903` |
| 킨디클라이밍 | `빨강 > 주황 > 노랑 > 초록 > 파랑 > 남색 > 보라 > 갈색 > 검정` | add final `흰색` | Naver blogs: `224305744878`, `223819356450`, `223969973083` |
| 오프더월클라이밍 | default rainbow with white first | `빨강 > 주황 > 노랑 > 초록 > 파랑 > 남색 > 보라 > 회색 > 갈색 > 검정 > 흰색` | Naver blogs: `224241942219`, `223697533197`, `223729127654`, `224119905442`, `224086525548` |
| 서울숲클라이밍 전체 4개 지점 | old mixed order, some `핑크` first | `빨강 > 주황 > 노랑 > 초록 > 하늘색 > 남색 > 보라 > 갈색 > 검정 > 핑크` | Naver blogs: `224308182286`, `224182401073`, `224249078287`, `224210684820`, `224315108881` |
| 신촌담장 / 을지로 담장 | `빨강 > 주황 > 노랑 > 초록 > 보라 > 흰색 > 검정` | `빨강 > 주황 > 노랑 > 초록 > 파랑 > 남색 > 보라 > 흰색 > 검정` | Naver blogs: `224260367033`, `224032002491`, `223760339065`, `224215150227` |
| 손상원클라이밍짐 판교/을지로/강남 | `흰색 > 노랑 > 초록 > 파랑 > 빨강 > 검정 > 회색 > 갈색 > 핑크` | `흰색 > 노랑 > 초록 > 파랑 > 빨강 > 검정 > 회색 > 갈색 > 핑크 > 보라` | Manual review: recent/current system adds final `보라`; supporting blogs: `224196741158`, `224172667569`, `224056282907` |

## No Change / Keep
| Gym / Brand | Reason |
|---|---|
| 더클라임 전체 | Current V9 order matches refreshed public search: `흰색 > 노랑 > 주황 > 초록 > 파랑 > 빨강 > 핑크 > 보라 > 회색 > 갈색 > 검정`. |
| 클라이밍파크 전체 | Current order matches strong sources for 강남/성수/한티. 종로 snippets sometimes call final step `벽색`; current `흰색` is the closest stored label. |
| 피커스 전체 | Current order matches brand source. |
| 클라임투게더 수원영통센터 | Existing manual source/order retained; fresh search did not produce a stronger replacement. |

## Manual Review Needed
| Gym / Brand | Issue |
|---|---|
| 오프더월 `파랑` vs `하늘색` label | Sources use both `파` and `하`. Draft SQL stores `파랑` intentionally for service-side normalization. |

## Draft SQL
- `ops/gym-import/2026-06-22-fix-audited-gym-grades.sql`
- Applies only confirmed fix candidates above.
- Uses idempotent upsert and deactivates obsolete active grades for target gyms.
