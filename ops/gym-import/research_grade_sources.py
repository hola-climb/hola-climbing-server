#!/usr/bin/env python3
"""Find public blog snippets that mention each gym's grade color order.

This script consumes `kakao-gyms-curated.csv` from the broad collector output and
emits a narrower seed for gyms whose difficulty scale is discoverable from
Kakao Blog Search snippets.
"""

from __future__ import annotations

import argparse
import csv
import getpass
import html
import json
import os
import re
import time
import urllib.parse
import urllib.error
import urllib.request
from pathlib import Path

from collect_kakao_gyms import (
    APP_TABLES,
    BRAND_GRADES,
    DEFAULT_GRADES,
    grades_for,
    sql_literal,
    write_clear_sql,
    write_combined_sql,
    write_verify_sql,
)


KAKAO_BLOG_SEARCH_URL = "https://dapi.kakao.com/v2/search/blog"
COLOR_ALIASES = {
    "흰": "흰색",
    "흰색": "흰색",
    "하양": "흰색",
    "하얀": "흰색",
    "화이트": "흰색",
    "노": "노랑",
    "노랑": "노랑",
    "노란": "노랑",
    "노란색": "노랑",
    "옐로": "노랑",
    "주": "주황",
    "주황": "주황",
    "주황색": "주황",
    "오렌지": "주황",
    "초": "초록",
    "초록": "초록",
    "초록색": "초록",
    "그린": "초록",
    "파": "파랑",
    "파랑": "파랑",
    "파란": "파랑",
    "파란색": "파랑",
    "블루": "파랑",
    "빨": "빨강",
    "빨강": "빨강",
    "빨간": "빨강",
    "빨간색": "빨강",
    "레드": "빨강",
    "핑": "핑크",
    "핑크": "핑크",
    "분홍": "핑크",
    "보": "보라",
    "보라": "보라",
    "보라색": "보라",
    "퍼플": "보라",
    "갈": "갈색",
    "갈색": "갈색",
    "브라운": "갈색",
    "회": "회색",
    "회색": "회색",
    "그레이": "회색",
    "검": "검정",
    "검정": "검정",
    "검은": "검정",
    "검은색": "검정",
    "블랙": "검정",
}
COLOR_TERMS = [alias for alias in COLOR_ALIASES if len(alias) > 1]
COLOR_PATTERN = re.compile("|".join(sorted(map(re.escape, COLOR_TERMS), key=len, reverse=True)))
TRUSTED_CHAIN_BRANDS = (
    "더클라임",
    "손상원",
    "클라이밍파크",
    "서울숲클라이밍",
    "웨이브락",
    "락오디세이",
    "피커스",
    "더플라스틱",
    "디스커버리",
    "비블럭",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Research grade color order from Kakao Blog Search snippets.")
    parser.add_argument("--input-csv", default="ops/gym-import/output-broad/kakao-gyms-curated.csv")
    parser.add_argument("--output-dir", default="ops/gym-import/output-researched")
    parser.add_argument("--size", type=int, default=10)
    parser.add_argument("--sleep-seconds", type=float, default=0.12)
    parser.add_argument("--min-colors", type=int, default=5)
    parser.add_argument("--limit", type=int, default=0)
    return parser.parse_args()


def kakao_api_key() -> str:
    key = os.environ.get("KAKAO_REST_API_KEY")
    if key:
        return key.strip()
    return getpass.getpass("KAKAO_REST_API_KEY: ").strip()


def clean(text: str) -> str:
    text = re.sub(r"<[^>]+>", "", text or "")
    return html.unescape(text)


def blog_search(api_key: str, query: str, size: int) -> list[dict]:
    url = f"{KAKAO_BLOG_SEARCH_URL}?{urllib.parse.urlencode({'query': query, 'size': str(size)})}"
    req = urllib.request.Request(url, headers={"Authorization": f"KakaoAK {api_key}"})
    last_error: Exception | None = None
    for attempt in range(1, 4):
        try:
            with urllib.request.urlopen(req, timeout=20) as response:
                return json.loads(response.read().decode("utf-8")).get("documents", [])
        except urllib.error.HTTPError:
            raise
        except (TimeoutError, urllib.error.URLError) as exc:
            last_error = exc
            if attempt == 3:
                break
            time.sleep(attempt * 1.5)
    print(f"warning: skipped query after retries: {query} ({last_error})")
    return []


def brand_query(name: str) -> str:
    for token in TRUSTED_CHAIN_BRANDS:
        if token in name:
            return token
    return name


def normalize_for_match(text: str) -> str:
    return re.sub(r"\s+", "", text or "").lower()


def branch_tokens(name: str) -> list[str]:
    tokens = re.findall(r"[가-힣A-Za-z0-9]+점", name or "")
    return [token for token in tokens if len(token) > 1]


def location_tokens(address: str) -> list[str]:
    tokens = []
    for token in re.split(r"\s+", address or "")[:3]:
        normalized = re.sub(r"(광역시|특별시|특별자치시|특별자치도|도|시|군|구)$", "", token)
        if len(normalized) >= 2:
            tokens.append(normalized)
    return tokens


def source_mentions_location(address: str, text: str) -> bool:
    normalized_text = normalize_for_match(text)
    return any(normalize_for_match(token) in normalized_text for token in location_tokens(address))


def loose_term_in_text(term: str, text: str) -> bool:
    compact = re.sub(r"\s+", "", term or "")
    if not compact:
        return False
    pattern = r"(?<![가-힣A-Za-z0-9])" + r"\s*".join(map(re.escape, compact))
    pattern += r"(?:장|짐|센터|점)?(?![가-힣A-Za-z0-9])"
    return bool(re.search(pattern, text or "", flags=re.IGNORECASE))


def distinctive_name_tokens(name: str) -> list[str]:
    generic = {"클라이밍", "클라임", "볼더링", "짐", "센터", "암장"}
    return [
        token
        for token in re.findall(r"[가-힣A-Za-z0-9]+", name or "")
        if len(token) >= 2 and token not in generic
    ]


def title_mentions_independent_name(name: str, title: str) -> bool:
    if loose_term_in_text(name, title):
        return True
    return any(loose_term_in_text(token, title) for token in distinctive_name_tokens(name))


def source_mentions_gym(name: str, address: str, title: str, contents: str) -> bool:
    text = f"{title} {contents}"
    normalized_text = normalize_for_match(text)
    normalized_title = normalize_for_match(title)
    normalized_name = normalize_for_match(name)
    brand = brand_query(name)
    normalized_brand = normalize_for_match(brand)
    if brand in TRUSTED_CHAIN_BRANDS and normalized_brand in normalized_title:
        return True
    if not title_mentions_independent_name(name, title):
        return False
    if loose_term_in_text(name, title):
        return True
    if normalized_name and normalized_name in normalized_text:
        return source_mentions_location(address, text)
    return False


def extract_color_order(text: str) -> list[str]:
    normalized = clean(text)
    if "난이도" not in normalized and "색상" not in normalized and "순서" not in normalized:
        return []
    matches = []
    for match in COLOR_PATTERN.finditer(normalized):
        color = COLOR_ALIASES[match.group(0)]
        if not matches or matches[-1] != color:
            matches.append(color)
    deduped = []
    for color in matches:
        if color not in deduped:
            deduped.append(color)
    return deduped


def best_source(api_key: str, row: dict, size: int, min_colors: int, sleep_seconds: float) -> dict | None:
    name = row["name"]
    queries = [
        f'"{name}" 난이도 색상',
        f'"{name}" 난이도 순서',
        f'"{brand_query(name)}" 난이도 색상',
    ]
    seen_urls: set[str] = set()
    best = None
    for query in queries:
        docs = blog_search(api_key, query, size)
        time.sleep(sleep_seconds)
        for doc in docs:
            url = doc.get("url", "")
            if url in seen_urls:
                continue
            seen_urls.add(url)
            title = clean(doc.get("title", ""))
            contents = clean(doc.get("contents", ""))
            combined = f"{title}\n{contents}"
            colors = extract_color_order(combined)
            title_or_body = f"{title} {contents}"
            if (
                len(colors) >= min_colors
                and ("클라이밍" in title_or_body or "클라임" in title_or_body)
                and source_mentions_gym(name, row["address"], title, contents)
            ):
                candidate = {
                    "query": query,
                    "title": title,
                    "url": url,
                    "blogname": doc.get("blogname", ""),
                    "datetime": doc.get("datetime", ""),
                    "contents": contents,
                    "colors": colors,
                }
                if best is None or len(colors) > len(best["colors"]):
                    best = candidate
    return best


def grade_order_for(name: str, colors: list[str]) -> list[tuple[str, int]]:
    # If a brand-specific order is already encoded, prefer it once public search
    # proves the gym/brand exposes a grade scale at all.
    if any(hint in name for hints, _ in BRAND_GRADES for hint in hints):
        return list(grades_for(name))
    return [(color, (idx + 1) * 10) for idx, color in enumerate(colors)]


def write_csv(path: Path, rows: list[dict]) -> None:
    with path.open("w", encoding="utf-8", newline="") as f:
        fieldnames = [
            "id", "kakao_id", "name", "address", "lat", "lng", "region_code",
            "phone", "place_url", "category_name", "score",
            "source_title", "source_url", "source_query", "source_colors",
        ]
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for idx, row in enumerate(rows, start=1):
            writer.writerow({
                "id": idx,
                "kakao_id": row["kakao_id"],
                "name": row["name"],
                "address": row["address"],
                "lat": row["lat"],
                "lng": row["lng"],
                "region_code": row["region_code"],
                "phone": row["phone"],
                "place_url": row["place_url"],
                "category_name": row["category_name"],
                "score": row["score"],
                "source_title": row["source"]["title"],
                "source_url": row["source"]["url"],
                "source_query": row["source"]["query"],
                "source_colors": " > ".join(row["source"]["colors"]),
            })


def write_seed_sql(path: Path, rows: list[dict]) -> None:
    lines = [
        "-- Generated by ops/gym-import/research_grade_sources.py",
        "-- Purpose: seed gyms whose grade color order was found via blog search snippets.",
        "",
        "BEGIN;",
        "",
    ]
    if rows:
        lines.append("INSERT INTO gyms (id, name, address, lat, lng, phone, website, region_code, status) VALUES")
        gym_values = []
        grade_values = []
        grade_id = 1
        for gym_id, row in enumerate(rows, start=1):
            gym_values.append(
                f"({gym_id}, {sql_literal(row['name'])}, {sql_literal(row['address'])}, "
                f"{float(row['lat']):.8f}, {float(row['lng']):.8f}, {sql_literal(row['phone'])}, "
                f"{sql_literal(row['place_url'])}, {sql_literal(row['region_code'])}, 'active')"
            )
            for label, difficulty_order in grade_order_for(row["name"], row["source"]["colors"]):
                grade_values.append(
                    f"({grade_id}, {gym_id}, {sql_literal(label)}, {difficulty_order}, TRUE)"
                )
                grade_id += 1
        lines.append(",\n".join(gym_values) + ";")
        lines.append("")
        lines.append("INSERT INTO gym_grades (id, gym_id, label, difficulty_order, is_active) VALUES")
        lines.append(",\n".join(grade_values) + ";")
        lines.append("")
        lines.append("SELECT setval(pg_get_serial_sequence('gyms', 'id'), (SELECT MAX(id) FROM gyms), true);")
        lines.append("SELECT setval(pg_get_serial_sequence('gym_grades', 'id'), (SELECT MAX(id) FROM gym_grades), true);")
    lines.extend(["", "COMMIT;", ""])
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    args = parse_args()
    api_key = kakao_api_key()
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    input_rows = list(csv.DictReader(open(args.input_csv, encoding="utf-8")))
    if args.limit:
        input_rows = input_rows[:args.limit]

    kept = []
    rejected = []
    for index, row in enumerate(input_rows, start=1):
        source = best_source(api_key, row, args.size, args.min_colors, args.sleep_seconds)
        if source:
            row["source"] = source
            kept.append(row)
        else:
            rejected.append(row)
        if index % 25 == 0:
            print(f"searched {index}/{len(input_rows)} kept={len(kept)} rejected={len(rejected)}")

    write_csv(output_dir / "kakao-gyms-researched.csv", kept)
    clear_sql = output_dir / "01-clear-production-data.sql"
    seed_sql = output_dir / "02-seed-production-gyms.sql"
    write_clear_sql(clear_sql)
    write_seed_sql(seed_sql, kept)
    write_combined_sql(output_dir / "03-clear-and-seed-production-gyms.sql", clear_sql, seed_sql)
    write_verify_sql(output_dir / "04-verify-production-gyms.sql")
    summary = {
        "input_gyms": len(input_rows),
        "kept_gyms": len(kept),
        "rejected_gyms": len(rejected),
        "output_dir": str(output_dir),
    }
    (output_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
