#!/usr/bin/env python3
"""Collect Korean indoor climbing gyms from Kakao Local and emit seed SQL.

The script keeps secrets out of argv. Prefer KAKAO_REST_API_KEY in the
environment; otherwise it prompts on stdin without echo.
"""

from __future__ import annotations

import argparse
import csv
import getpass
import json
import math
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


KAKAO_KEYWORD_URL = "https://dapi.kakao.com/v2/local/search/keyword.json"
KAKAO_MAX_RADIUS_M = 20_000
KAKAO_MAX_PAGE = 45
KAKAO_PAGE_SIZE = 15

SEARCH_KEYWORDS = (
    "볼더링",
    "클라이밍",
    "클라이밍장",
    "클라이밍짐",
    "암벽등반",
)

INCLUDE_HINTS = (
    "클라이밍",
    "볼더",
    "암벽",
    "boulder",
    "climb",
    "더클라임",
    "더플라스틱",
    "피커스",
    "온사이트",
    "비블럭",
    "담장",
)

INDOOR_NAME_HINTS = (
    "클라이밍",
    "클라임",
    "볼더",
    "암벽",
    "홀드",
    "짐",
    "센터",
    "피커스",
    "담장",
    "시소",
    "오르자",
    "오르멍",
    "오르명",
    "서울숲",
    "디스커버리",
    "비블럭",
    "온사이트",
    "더월",
    "더클라임",
    "손상원",
    "빅월",
    "락트리",
    "락오디세이",
    "웨이브락",
    "리버스락",
    "야크돔",
    "몽키즈",
    "바위오름",
    "클라이머스",
)

EXCLUDE_HINTS = (
    "산악회",
    "동호회",
    "연맹",
    "협회",
    "용품",
    "쇼핑",
    "공사",
    "공장",
    "대학교",
    "초등학교",
    "중학교",
    "고등학교",
    "유치원",
    "등산로",
    "자연암장",
    "인공암벽",  # public lead wall / outdoor facilities are usually not bouldering gyms.
    "암벽등반장",
    "공원",
    "체육공원",
    "호수공원",
    "자연학습공원",
    "산악문화",
    "문화센터",
    "문화체험센터",
    "청소년수련시설",
    "테마파크",
    "하이로프",
    "아이스파크",
    "아이스클라이밍",
    "월드컵",
    "주차장",
    "페스티벌",
    "축구",
    "조경시공",
    "제조업",
    "예정",
)

REGION_CODE_BY_PREFIX = {
    "서울": "seoul",
    "서울특별시": "seoul",
    "경기": "gyeonggi",
    "경기도": "gyeonggi",
    "인천": "incheon",
    "인천광역시": "incheon",
    "강원": "gangwon",
    "강원도": "gangwon",
    "강원특별자치도": "gangwon",
    "충북": "chungbuk",
    "충청북도": "chungbuk",
    "충남": "chungnam",
    "충청남도": "chungnam",
    "대전": "daejeon",
    "대전광역시": "daejeon",
    "세종": "sejong",
    "세종특별자치시": "sejong",
    "경북": "gyeongbuk",
    "경상북도": "gyeongbuk",
    "대구": "daegu",
    "대구광역시": "daegu",
    "경남": "gyeongnam",
    "경상남도": "gyeongnam",
    "울산": "ulsan",
    "울산광역시": "ulsan",
    "부산": "busan",
    "부산광역시": "busan",
    "전북": "jeonbuk",
    "전라북도": "jeonbuk",
    "전북특별자치도": "jeonbuk",
    "전남": "jeonnam",
    "전라남도": "jeonnam",
    "광주": "gwangju",
    "광주광역시": "gwangju",
    "제주": "jeju",
    "제주도": "jeju",
    "제주특별자치도": "jeju",
}

DEFAULT_GRADES = (
    ("흰색", 10),
    ("노랑", 20),
    ("주황", 30),
    ("초록", 40),
    ("파랑", 50),
    ("빨강", 60),
    ("보라", 70),
    ("갈색", 80),
    ("회색", 90),
    ("검정", 100),
)

BRAND_GRADES = (
    (("손상원",), (
        ("흰색", 10),
        ("노랑", 20),
        ("초록", 30),
        ("파랑", 40),
        ("빨강", 50),
        ("검정", 60),
        ("회색", 70),
        ("갈색", 80),
        ("핑크", 90),
    )),
    (("더클라임", "theclimb", "the climb"), (
        ("흰색", 10),
        ("노랑", 20),
        ("주황", 30),
        ("초록", 40),
        ("파랑", 50),
        ("빨강", 60),
        ("핑크", 65),
        ("보라", 70),
        ("회색", 80),
        ("갈색", 90),
        ("검정", 100),
    )),
    (("클라이밍파크",), (
        ("노랑", 10),
        ("핑크", 20),
        ("파랑", 30),
        ("빨강", 40),
        ("보라", 50),
        ("갈색", 60),
        ("회색", 70),
        ("검정", 80),
        ("흰색", 90),
    )),
)

APP_TABLES = (
    "admin_audit_logs",
    "analysis_results",
    "analysis_video_results",
    "chat_messages",
    "chat_room_members",
    "chat_rooms",
    "climbing_logs",
    "comments",
    "device_tokens",
    "favorites",
    "follows",
    "gym_board_posts",
    "gym_grades",
    "gym_reviews",
    "gyms",
    "labels",
    "likes",
    "notifications",
    "reports",
    "terms_versions",
    "user_blocks",
    "user_notification_settings",
    "user_stats",
    "user_term_agreements",
    "users",
    "videos",
)


@dataclass(frozen=True)
class Place:
    kakao_id: str
    name: str
    address: str
    lat: float
    lng: float
    region_code: str
    phone: str
    place_url: str
    category_name: str
    score: int


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Collect Kakao Local climbing gym data and generate SQL.")
    parser.add_argument("--output-dir", default="ops/gym-import/output", help="Directory for JSON/CSV/SQL outputs.")
    parser.add_argument("--sleep-seconds", type=float, default=0.08, help="Delay between Kakao API calls.")
    parser.add_argument("--min-score", type=int, default=3, help="Minimum inclusion score for curated gyms.")
    parser.add_argument("--limit", type=int, default=0, help="Optional max curated rows, for smoke tests.")
    parser.add_argument("--skip-collect", action="store_true", help="Reuse raw JSON from output-dir.")
    parser.add_argument(
        "--known-grade-only",
        action="store_true",
        help="Keep only gyms whose route grade order is encoded in BRAND_GRADES.",
    )
    return parser.parse_args()


def kakao_api_key() -> str:
    key = os.environ.get("KAKAO_REST_API_KEY")
    if key:
        return key.strip()
    return getpass.getpass("KAKAO_REST_API_KEY: ").strip()


def grid_points() -> list[tuple[float, float]]:
    """Return WGS84 (lng, lat) centers covering mainland Korea and Jeju."""
    boxes = [
        # lon_min, lon_max, lat_min, lat_max
        (126.00, 129.65, 34.20, 38.55),  # mainland urban belt
        (125.80, 126.85, 33.15, 33.65),  # Jeju
    ]
    points: list[tuple[float, float]] = []
    for lon_min, lon_max, lat_min, lat_max in boxes:
        lat = lat_min
        while lat <= lat_max:
            lon = lon_min
            # 0.28 degrees lon is about 24-26 km in Korea; paired with 20 km radius
            # and overlapping keywords, this keeps urban gyms covered without huge quota use.
            while lon <= lon_max:
                points.append((round(lon, 6), round(lat, 6)))
                lon += 0.28
            lat += 0.24
    return points


def request_keyword(api_key: str, keyword: str, lng: float, lat: float, page: int) -> dict:
    params = {
        "query": keyword,
        "x": str(lng),
        "y": str(lat),
        "radius": str(KAKAO_MAX_RADIUS_M),
        "page": str(page),
        "size": str(KAKAO_PAGE_SIZE),
        "sort": "distance",
    }
    url = f"{KAKAO_KEYWORD_URL}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"Authorization": f"KakaoAK {api_key}"})
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Kakao API HTTP {exc.code}: {body}") from exc


def collect_raw(api_key: str, sleep_seconds: float) -> list[dict]:
    seen_request_keys: set[tuple[str, float, float, int]] = set()
    rows: list[dict] = []
    points = grid_points()
    total_requests = 0
    for idx, (lng, lat) in enumerate(points, start=1):
        for keyword in SEARCH_KEYWORDS:
            for page in range(1, KAKAO_MAX_PAGE + 1):
                request_key = (keyword, lng, lat, page)
                if request_key in seen_request_keys:
                    continue
                seen_request_keys.add(request_key)
                payload = request_keyword(api_key, keyword, lng, lat, page)
                total_requests += 1
                for doc in payload.get("documents", []):
                    doc["_search_keyword"] = keyword
                    doc["_search_lng"] = lng
                    doc["_search_lat"] = lat
                    rows.append(doc)
                if payload.get("meta", {}).get("is_end", True):
                    break
                time.sleep(sleep_seconds)
        if idx % 25 == 0:
            print(f"scanned {idx}/{len(points)} grid points, raw_docs={len(rows)}, requests={total_requests}", file=sys.stderr)
    print(f"finished grid scan: points={len(points)}, raw_docs={len(rows)}, requests={total_requests}", file=sys.stderr)
    return rows


def normalize(text: str) -> str:
    return re.sub(r"\s+", "", text or "").lower()


def first_address(place: dict) -> str:
    return (place.get("road_address_name") or place.get("address_name") or "").strip()


def region_code(address: str) -> str:
    first = (address.split() or [""])[0]
    return REGION_CODE_BY_PREFIX.get(first, first.lower()[:20] if first else "unknown")


def score_place(place: dict) -> int:
    name = place.get("place_name", "")
    category = place.get("category_name", "")
    address = first_address(place)
    name_category = normalize(" ".join([name, category]))
    haystack = normalize(" ".join([name, category, address]))
    if any(normalize(hint) in name_category for hint in EXCLUDE_HINTS):
        return -100
    score = 0
    if any(normalize(hint) in haystack for hint in INCLUDE_HINTS):
        score += 3
    if "스포츠" in category or "레저" in category or "암벽등반" in category:
        score += 2
    if "볼더" in name or "볼더" in category:
        score += 2
    if address:
        score += 1
    if place.get("phone"):
        score += 1
    if "클라이밍" in name or "climb" in normalize(name):
        score += 1
    return score


def is_indoor_candidate(place: dict) -> bool:
    name = place.get("place_name", "")
    address = first_address(place)
    normalized_name = normalize(name)
    if re.search(r"(^|\s)산\s*\d", address):
        return False
    if any(hint in normalized_name for hint in ("빙장", "릿지", "구용원")):
        return False
    if "암장" in normalized_name and "실내" not in normalized_name:
        return False
    return any(normalize(hint) in normalized_name for hint in INDOOR_NAME_HINTS)


def has_known_grade_scheme(name: str) -> bool:
    normalized_name = normalize(name)
    if "손상원" in normalized_name:
        return True
    if "더클라임" in normalized_name:
        return True
    return normalized_name.startswith("클라이밍파크")


def curate(raw_rows: Iterable[dict], min_score: int, limit: int, known_grade_only: bool) -> list[Place]:
    by_key: dict[str, dict] = {}
    for row in raw_rows:
        key = row.get("id") or f"{normalize(row.get('place_name', ''))}:{normalize(first_address(row))}"
        existing = by_key.get(key)
        if existing is None or score_place(row) > score_place(existing):
            by_key[key] = row

    places: list[Place] = []
    for row in by_key.values():
        address = first_address(row)
        try:
            lng = float(row.get("x") or "nan")
            lat = float(row.get("y") or "nan")
        except ValueError:
            continue
        if not (math.isfinite(lat) and math.isfinite(lng)):
            continue
        score = score_place(row)
        if score < min_score:
            continue
        if not is_indoor_candidate(row):
            continue
        if known_grade_only and not has_known_grade_scheme(row.get("place_name", "")):
            continue
        places.append(Place(
            kakao_id=str(row.get("id") or ""),
            name=(row.get("place_name") or "").strip(),
            address=address,
            lat=lat,
            lng=lng,
            region_code=region_code(address),
            phone=(row.get("phone") or "").strip(),
            place_url=(row.get("place_url") or "").strip(),
            category_name=(row.get("category_name") or "").strip(),
            score=score,
        ))
    places.sort(key=lambda p: (p.region_code, p.name, p.kakao_id))
    if limit > 0:
        return places[:limit]
    return places


def grades_for(name: str) -> tuple[tuple[str, int], ...]:
    haystack = normalize(name)
    for hints, grades in BRAND_GRADES:
        if "클라이밍파크" in hints and not haystack.startswith("클라이밍파크"):
            continue
        if any(normalize(hint) in haystack for hint in hints):
            return grades
    return DEFAULT_GRADES


def sql_literal(value: str | None) -> str:
    if value is None or value == "":
        return "NULL"
    return "'" + value.replace("'", "''") + "'"


def write_csv(path: Path, places: list[Place]) -> None:
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=[
            "id",
            "kakao_id",
            "name",
            "address",
            "lat",
            "lng",
            "region_code",
            "phone",
            "place_url",
            "category_name",
            "score",
        ])
        writer.writeheader()
        for idx, place in enumerate(places, start=1):
            writer.writerow({"id": idx, **place.__dict__})


def write_clear_sql(path: Path) -> None:
    table_list = ",\n    ".join(APP_TABLES)
    path.write_text(f"""-- Generated by ops/gym-import/collect_kakao_gyms.py
-- Purpose: clear application data before production gym reseed.
-- Keep flyway_schema_history intact.

BEGIN;

TRUNCATE TABLE
    {table_list}
RESTART IDENTITY CASCADE;

INSERT INTO terms_versions (id, type, version, title, content, is_required, effective_at) VALUES
(1, 'service', '1.0', '서비스 이용약관', '서비스 이용약관 v1.0 본문...', TRUE, NOW()),
(2, 'privacy', '1.0', '개인정보 처리방침', '개인정보 처리방침 v1.0 본문...', TRUE, NOW()),
(3, 'marketing', '1.0', '마케팅 정보 수신 동의', '마케팅 정보 수신 동의 v1.0 본문...', FALSE, NOW());

SELECT setval(pg_get_serial_sequence('terms_versions', 'id'), 3, true);

COMMIT;
""", encoding="utf-8")


def write_seed_sql(path: Path, places: list[Place]) -> None:
    lines: list[str] = [
        "-- Generated by ops/gym-import/collect_kakao_gyms.py",
        "-- Purpose: seed active nationwide indoor climbing gyms collected from Kakao Local.",
        "",
        "BEGIN;",
        "",
    ]
    if places:
        lines.append("INSERT INTO gyms (id, name, address, lat, lng, phone, website, region_code, status) VALUES")
        gym_values = []
        for idx, place in enumerate(places, start=1):
            gym_values.append(
                f"({idx}, {sql_literal(place.name)}, {sql_literal(place.address)}, "
                f"{place.lat:.8f}, {place.lng:.8f}, {sql_literal(place.phone)}, "
                f"{sql_literal(place.place_url)}, {sql_literal(place.region_code)}, 'active')"
            )
        lines.append(",\n".join(gym_values) + ";")
        lines.append("")
        lines.append("INSERT INTO gym_grades (id, gym_id, label, difficulty_order, is_active) VALUES")
        grade_values = []
        grade_id = 1
        for gym_id, place in enumerate(places, start=1):
            for label, difficulty_order in grades_for(place.name):
                grade_values.append(
                    f"({grade_id}, {gym_id}, {sql_literal(label)}, {difficulty_order}, TRUE)"
                )
                grade_id += 1
        lines.append(",\n".join(grade_values) + ";")
        lines.append("")
        lines.append("SELECT setval(pg_get_serial_sequence('gyms', 'id'), (SELECT MAX(id) FROM gyms), true);")
        lines.append("SELECT setval(pg_get_serial_sequence('gym_grades', 'id'), (SELECT MAX(id) FROM gym_grades), true);")
    lines.extend([
        "",
        "COMMIT;",
        "",
    ])
    path.write_text("\n".join(lines), encoding="utf-8")


def write_verify_sql(path: Path) -> None:
    path.write_text("""-- Generated by ops/gym-import/collect_kakao_gyms.py
SELECT 'gyms' AS metric, COUNT(*) AS value FROM gyms
UNION ALL
SELECT 'gym_grades', COUNT(*) FROM gym_grades
UNION ALL
SELECT 'gyms_without_grades', COUNT(*)
FROM gyms g
WHERE NOT EXISTS (
    SELECT 1 FROM gym_grades gg WHERE gg.gym_id = g.id AND gg.is_active = TRUE
)
UNION ALL
SELECT 'invalid_coordinates', COUNT(*)
FROM gyms
WHERE lat IS NULL OR lng IS NULL OR lat NOT BETWEEN -90 AND 90 OR lng NOT BETWEEN -180 AND 180;

SELECT region_code, COUNT(*) AS gym_count
FROM gyms
GROUP BY region_code
ORDER BY gym_count DESC, region_code;

SELECT lower(trim(name)) AS normalized_name, COUNT(*) AS duplicate_count
FROM gyms
GROUP BY lower(trim(name))
HAVING COUNT(*) > 1
ORDER BY duplicate_count DESC, normalized_name
LIMIT 30;
""", encoding="utf-8")


def write_combined_sql(path: Path, clear_sql: Path, seed_sql: Path) -> None:
    def without_transaction_markers(sql: str) -> str:
        lines = []
        for line in sql.splitlines():
            if line.strip() in {"BEGIN;", "COMMIT;"}:
                continue
            lines.append(line)
        return "\n".join(lines).strip()

    path.write_text(
        "-- Generated by ops/gym-import/collect_kakao_gyms.py\n"
        "-- Purpose: atomically clear application data and seed nationwide gyms.\n\n"
        "BEGIN;\n\n"
        + without_transaction_markers(clear_sql.read_text(encoding="utf-8"))
        + "\n\n"
        + without_transaction_markers(seed_sql.read_text(encoding="utf-8"))
        + "\n\nCOMMIT;\n",
        encoding="utf-8",
    )


def main() -> int:
    args = parse_args()
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    raw_json = output_dir / "kakao-gyms-raw.json"

    if args.skip_collect:
        raw_rows = json.loads(raw_json.read_text(encoding="utf-8"))
    else:
        api_key = kakao_api_key()
        if not api_key:
            print("KAKAO_REST_API_KEY is required.", file=sys.stderr)
            return 2
        raw_rows = collect_raw(api_key, args.sleep_seconds)
        raw_json.write_text(json.dumps(raw_rows, ensure_ascii=False, indent=2), encoding="utf-8")

    places = curate(raw_rows, args.min_score, args.limit, args.known_grade_only)
    write_csv(output_dir / "kakao-gyms-curated.csv", places)
    clear_sql = output_dir / "01-clear-production-data.sql"
    seed_sql = output_dir / "02-seed-production-gyms.sql"
    write_clear_sql(clear_sql)
    write_seed_sql(seed_sql, places)
    write_combined_sql(output_dir / "03-clear-and-seed-production-gyms.sql", clear_sql, seed_sql)
    write_verify_sql(output_dir / "04-verify-production-gyms.sql")
    summary = {
        "raw_rows": len(raw_rows),
        "curated_gyms": len(places),
        "output_dir": str(output_dir),
        "regions": {},
    }
    for place in places:
        summary["regions"][place.region_code] = summary["regions"].get(place.region_code, 0) + 1
    (output_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
