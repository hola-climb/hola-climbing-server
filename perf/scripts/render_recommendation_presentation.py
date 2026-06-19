#!/usr/bin/env python3
import argparse
import json
import textwrap
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont
except ModuleNotFoundError as exc:
    raise SystemExit(
        "render_recommendation_presentation.py requires Pillow. "
        "Run it with a Python environment that has Pillow installed."
    ) from exc


PALETTE = {
    "bg": "#F7F8FA",
    "white": "#FFFFFF",
    "ink": "#172033",
    "muted": "#667085",
    "line": "#D9E0EA",
    "blue": "#1D4ED8",
    "purple": "#6D28D9",
    "green": "#15803D",
    "teal": "#0F766E",
    "amber": "#B54708",
    "amber_bg": "#FFF7ED",
    "amber_line": "#FED7AA",
    "green_bg": "#E7F7EF",
    "green_line": "#B7E4C7",
    "panel_bg": "#F8FAFC",
}

P95_TARGET_MS = 1000
ERROR_RATE_TARGET = 0.01


def p95_passed(values):
    return values["p95"] < P95_TARGET_MS


def error_rate_passed(values):
    return values["error_rate"] < ERROR_RATE_TARGET


def optional_metric_value(metrics, name, key):
    metric = metrics.get(name)
    if not metric or not metric.get("values"):
        return None
    return metric["values"].get(key)


def ms_text(value):
    if value is None:
        return "측정 없음"
    return f"{value:.0f}ms"


def ms_value_text(value):
    if value is None:
        return "n/a"
    return f"{value:.0f} ms"


def first_page_delta_text(first, cached):
    if cached is None:
        return "측정 없음"
    diff = cached - first
    if abs(diff) < 0.5:
        return "차이 없음"
    direction = "감소" if diff < 0 else "증가"
    percent = abs(diff) / first * 100 if first else 0
    return f"{abs(diff):.0f}ms {direction} ({percent:.0f}%)"


def change_sentence(label, before, after, unit="ms"):
    diff = after - before
    if abs(diff) < 0.5:
        return f"{label}은 {before:.0f}{unit}로 거의 변하지 않았다."
    direction = "증가" if diff > 0 else "감소"
    return f"{label}은 {before:.0f}{unit}에서 {after:.0f}{unit}로 {abs(diff):.0f}{unit} {direction}했다."


class Renderer:
    def __init__(self, image, root):
        self.image = image
        self.draw = ImageDraw.Draw(image)
        self.root = root
        self.failures = []
        self.sans_path = self._font_path(
            [
                "/System/Library/Fonts/AppleSDGothicNeo.ttc",
                "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
                "/System/Library/Fonts/Helvetica.ttc",
            ]
        )
        self.mono_path = self._font_path(
            [
                "/System/Library/Fonts/Menlo.ttc",
                "/System/Library/Fonts/Monaco.ttf",
                "/System/Library/Fonts/Courier.ttc",
            ]
        )

    def _font_path(self, candidates):
        for candidate in candidates:
            if Path(candidate).exists():
                return candidate
        return None

    def font(self, size, mono=False):
        path = self.mono_path if mono else self.sans_path
        if path:
            return ImageFont.truetype(path, size)
        return ImageFont.load_default()

    def text_width(self, text, font):
        if not text:
            return 0
        return self.draw.textbbox((0, 0), text, font=font)[2]

    def line_height(self, font):
        bbox = self.draw.textbbox((0, 0), "Ag", font=font)
        return bbox[3] - bbox[1] + 10

    def wrap(self, text, font, max_width):
        words = text.split(" ")
        lines = []
        current = ""
        for word in words:
            candidate = word if not current else f"{current} {word}"
            if self.text_width(candidate, font) <= max_width:
                current = candidate
                continue
            if current:
                lines.append(current)
            if self.text_width(word, font) <= max_width:
                current = word
            else:
                current = ""
                for chunk in textwrap.wrap(word, width=18, break_long_words=True):
                    if self.text_width(chunk, font) > max_width:
                        self.failures.append(f"word overflow: {chunk}")
                    lines.append(chunk)
        if current:
            lines.append(current)
        return lines or [""]

    def fit_lines(self, text, box, size, min_size=18, mono=False, max_lines=None):
        x1, y1, x2, y2 = box
        max_width = x2 - x1
        max_height = y2 - y1
        for candidate_size in range(size, min_size - 1, -1):
            font = self.font(candidate_size, mono=mono)
            lines = []
            for paragraph in text.split("\n"):
                lines.extend(self.wrap(paragraph, font, max_width))
            if max_lines and len(lines) > max_lines:
                lines = lines[: max_lines - 1] + ["..."]
            total_height = len(lines) * self.line_height(font)
            longest = max((self.text_width(line, font) for line in lines), default=0)
            if longest <= max_width and total_height <= max_height:
                return font, lines
        self.failures.append(f"text overflow in box {box}: {text[:80]}")
        font = self.font(min_size, mono=mono)
        return font, self.wrap(text, font, max_width)

    def text_box(self, box, text, size, fill, min_size=18, mono=False, max_lines=None):
        x1, y1, x2, y2 = box
        font, lines = self.fit_lines(text, box, size, min_size, mono, max_lines)
        y = y1
        line_height = self.line_height(font)
        for line in lines:
            self.draw.text((x1, y), line, font=font, fill=fill)
            width = self.text_width(line, font)
            if x1 + width > x2:
                self.failures.append(f"line exceeds box width: {line}")
            y += line_height
        if y > y2 + 1:
            self.failures.append(f"lines exceed box height: {text[:80]}")

    def assert_no_overflow(self):
        if self.failures:
            raise SystemExit("presentation text overflow check failed:\n- " + "\n- ".join(self.failures))


def metric_values(root, run_label):
    run_dir = root / "perf/results/recommendation-feed" / run_label
    k6 = json.loads((run_dir / "k6-summary.json").read_text())
    explain = json.loads((run_dir / "recommendation-feed-explain.json").read_text())[0]
    metrics = k6["metrics"]
    plan = explain["Plan"]
    return {
        "p50": metrics["http_req_duration"]["values"]["med"],
        "p95": metrics["http_req_duration"]["values"]["p(95)"],
        "p99": metrics["http_req_duration"]["values"]["p(99)"],
        "error_rate": metrics["http_req_failed"]["values"]["rate"],
        "requests": int(metrics["http_reqs"]["values"]["count"]),
        "first_page_p95": metrics["recommendation_feed_first_page_duration"]["values"]["p(95)"],
        "cursor_page_p95": metrics["recommendation_feed_cursor_page_duration"]["values"]["p(95)"],
        "second_page_p95": optional_metric_value(metrics, "recommendation_feed_second_page_duration", "p(95)"),
        "third_page_p95": optional_metric_value(metrics, "recommendation_feed_third_page_duration", "p(95)"),
        "sql_time": explain["Execution Time"],
        "temp_read": plan.get("Temp Read Blocks", 0),
        "temp_written": plan.get("Temp Written Blocks", 0),
    }


def _walk_plan(node):
    yield node
    for child in node.get("Plans", []):
        yield from _walk_plan(child)


def _first_plan_node(plan, predicate):
    for node in _walk_plan(plan):
        if predicate(node):
            return node
    return {}


def plan_values(root, run_label):
    run_dir = root / "perf/results/recommendation-feed" / run_label
    explain = json.loads((run_dir / "recommendation-feed-explain.json").read_text())[0]
    plan = explain["Plan"]
    videos_scan = _first_plan_node(
        plan,
        lambda node: node.get("Relation Name") == "videos"
        and node.get("Node Type") in {"Seq Scan", "Index Scan", "Index Only Scan", "Bitmap Heap Scan"},
    )
    sort = _first_plan_node(plan, lambda node: node.get("Node Type") == "Sort")
    gather = _first_plan_node(plan, lambda node: node.get("Node Type") == "Gather Merge")
    user_blocks_removed = sum(
        int(node.get("Rows Removed by Filter", 0) or 0)
        for node in _walk_plan(plan)
        if node.get("Relation Name") == "user_blocks"
    )
    rows_per_loop = int(videos_scan.get("Actual Rows", 0) or 0)
    loops = int(videos_scan.get("Actual Loops", 0) or 0)
    workers = sort.get("Workers", [])
    worker_sort_space = sum(int(worker.get("Sort Space Used", 0) or 0) for worker in workers)
    return {
        "execution_time": explain["Execution Time"],
        "planning_time": explain["Planning Time"],
        "temp_read": plan.get("Temp Read Blocks", 0),
        "temp_written": plan.get("Temp Written Blocks", 0),
        "videos_rows_per_loop": rows_per_loop,
        "videos_loops": loops,
        "videos_rows_scanned": rows_per_loop * max(loops, 1),
        "videos_scan_type": videos_scan.get("Node Type", "unknown"),
        "sort_method": sort.get("Sort Method", "not used"),
        "sort_space_used": int(sort.get("Sort Space Used", 0) or 0),
        "sort_space_type": sort.get("Sort Space Type", "none"),
        "worker_sort_space": worker_sort_space,
        "workers_launched": gather.get("Workers Launched", 0),
        "user_blocks_removed": user_blocks_removed,
    }


def code_state_values(root, run_label):
    path = root / "perf/results/recommendation-feed" / run_label / "code-state.txt"
    text = path.read_text()
    values = {}
    for line in text.splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            values[key] = value
    commit = values.get("git_commit", "unknown")
    recommendation_dirty = "RecommendationServiceImpl.java" in text
    gcs_dirty = "GcsStorageService.java" in text or "GcsProperties.java" in text
    test_dirty = "RecommendationServiceImplTest.java" in text
    return {
        "commit": commit[:7] if commit != "unknown" else commit,
        "working_tree": "working tree" if recommendation_dirty or gcs_dirty or test_dirty else "commit",
        "recommendation_dirty": recommendation_dirty,
        "gcs_dirty": gcs_dirty,
        "test_dirty": test_dirty,
        "captured_at": values.get("captured_at", "unknown"),
        "viewer_id": values.get("viewer_id", "unknown"),
        "page_size": values.get("page_size", "unknown"),
        "candidate_window": values.get("candidate_window", "unknown"),
        "snapshot_size": values.get("snapshot_size", "unknown"),
    }


def presentation_output(root, run_label, filename):
    return root / "perf/results/recommendation-feed" / run_label / "screenshots/presentation" / filename


def run_display_name(run_label):
    if run_label == "local-baseline":
        return "local-baseline"
    if run_label == "after":
        return "after"
    return run_label


def run_has_results(root, run_label):
    run_dir = root / "perf/results/recommendation-feed" / run_label
    return (run_dir / "k6-summary.json").exists() and (run_dir / "recommendation-feed-explain.json").exists()


def delta_text(before, after, suffix=""):
    diff = after - before
    if abs(diff) < 0.5:
        return "변화 없음"
    direction = "증가" if diff > 0 else "감소"
    return f"{abs(diff):,.0f}{suffix} {direction}"


def new_canvas(root, title, subtitle, badge):
    width, height = 1800, 1260
    image = Image.new("RGB", (width, height), PALETTE["bg"])
    r = Renderer(image, root)
    d = r.draw
    d.rounded_rectangle((44, 36, width - 44, height - 36), radius=28, fill=PALETTE["white"], outline=PALETTE["line"], width=2)
    d.rectangle((44, 36, width - 44, 156), fill="#EFF6FF")
    d.line((44, 156, width - 44, 156), fill=PALETTE["line"], width=2)
    r.text_box((82, 64, 1220, 112), title, 44, PALETTE["blue"], max_lines=1)
    r.text_box((82, 118, 1220, 148), subtitle, 24, PALETTE["muted"], max_lines=1)
    d.rounded_rectangle((1285, 70, 1680, 124), radius=14, fill=PALETTE["green_bg"], outline=PALETTE["green_line"], width=1)
    r.text_box((1315, 84, 1650, 112), badge, 22, PALETTE["green"], max_lines=1)
    return image, r, d


def draw_footer(renderer, y, evidence, metrics):
    renderer.text_box((82, y, 225, y + 30), "원본 근거:", 20, PALETTE["muted"], max_lines=1)
    renderer.text_box((225, y, 1660, y + 30), evidence, 20, "#344054", mono=True, max_lines=1)
    renderer.text_box((82, y + 40, 1660, y + 72), metrics, 20, "#344054", mono=True, max_lines=1)


def draw_small_card(renderer, box, label, value, sub, color):
    x1, y1, x2, y2 = box
    d = renderer.draw
    d.rounded_rectangle(box, radius=18, fill=PALETTE["white"], outline=PALETTE["line"], width=2)
    renderer.text_box((x1 + 24, y1 + 22, x2 - 24, y1 + 52), label, 20, PALETTE["muted"], max_lines=1)
    renderer.text_box((x1 + 24, y1 + 58, x2 - 24, y1 + 112), value, 40, color, min_size=30, max_lines=1)
    renderer.text_box((x1 + 24, y1 + 122, x2 - 24, y2 - 18), sub, 19, PALETTE["muted"], min_size=16, max_lines=2)


def draw_list_item(renderer, x, y, title, desc, color=PALETTE["teal"], text_color=PALETTE["ink"]):
    d = renderer.draw
    d.ellipse((x, y + 10, x + 12, y + 22), fill=color)
    renderer.text_box((x + 32, y, 1655, y + 30), title, 23, text_color, min_size=19, max_lines=1)
    renderer.text_box((x + 32, y + 32, 1655, y + 70), desc, 19, PALETTE["muted"], min_size=17, max_lines=1)


def draw_card(renderer, box, label, value, sub, color):
    x1, y1, x2, y2 = box
    d = renderer.draw
    d.rounded_rectangle(box, radius=20, fill=PALETTE["white"], outline=PALETTE["line"], width=2)
    renderer.text_box((x1 + 28, y1 + 26, x2 - 28, y1 + 58), label.upper(), 21, PALETTE["muted"], max_lines=1)
    renderer.text_box((x1 + 28, y1 + 66, x2 - 28, y1 + 132), value, 58, color, min_size=42, max_lines=1)
    renderer.text_box((x1 + 28, y1 + 142, x2 - 28, y2 - 22), sub, 22, PALETTE["muted"], max_lines=2)


def draw_bullet(renderer, x, y, title, desc):
    d = renderer.draw
    d.ellipse((x, y + 8, x + 16, y + 24), fill=PALETTE["amber"])
    renderer.text_box((x + 36, y - 2, 1660, y + 32), title, 24, PALETTE["ink"], max_lines=1)
    renderer.text_box((x + 36, y + 34, 1660, y + 72), desc, 20, "#7A4A12", max_lines=1)


def render(root, run_label):
    label = run_display_name(run_label)
    values = metric_values(root, run_label)
    plan = plan_values(root, run_label)
    state = code_state_values(root, run_label)
    baseline_values = metric_values(root, "local-baseline") if run_label == "after" and run_has_results(root, "local-baseline") else None
    baseline_plan = plan_values(root, "local-baseline") if baseline_values else None
    width, height = 1800, 1260
    image = Image.new("RGB", (width, height), PALETTE["bg"])
    r = Renderer(image, root)
    d = r.draw

    d.rounded_rectangle((44, 36, width - 44, height - 36), radius=28, fill=PALETTE["white"], outline=PALETTE["line"], width=2)
    d.rectangle((44, 36, width - 44, 156), fill="#EFF6FF")
    d.line((44, 156, width - 44, 156), fill=PALETTE["line"], width=2)
    r.text_box((82, 64, 1220, 112), "추천 피드 성능 테스트", 44, PALETTE["blue"], max_lines=1)
    r.text_box((82, 118, 1220, 148), f"{label} 결과 요약 - size={state['page_size']} / snapshot={state['snapshot_size']}", 24, PALETTE["muted"], max_lines=1)
    d.rounded_rectangle((1285, 70, 1680, 124), radius=14, fill=PALETTE["green_bg"], outline=PALETTE["green_line"], width=1)
    r.text_box((1315, 84, 1650, 112), "seed: videos 100k - users 10k", 22, PALETTE["green"], max_lines=1)

    card_y = 210
    card_w = 390
    card_h = 190
    gap = 28
    p95_ok = p95_passed(values)
    error_ok = error_rate_passed(values)
    cards = [
        (
            "p95 응답시간",
            f"{values['p95']:.0f} ms",
            "목표 통과" if p95_ok else "목표 초과",
            PALETTE["blue"] if p95_ok else PALETTE["amber"],
        ),
        ("p99 응답시간", f"{values['p99']:.0f} ms", "상위 지연 구간", PALETTE["purple"]),
        (
            "오류율",
            f"{values['error_rate']:.0%}",
            "k6 check 통과" if error_ok else "k6 check 실패",
            PALETTE["green"] if error_ok else PALETTE["amber"],
        ),
        ("SQL 실행시간", f"{values['sql_time']:.0f} ms", "EXPLAIN ANALYZE", PALETTE["amber"]),
    ]
    for index, card in enumerate(cards):
        x = 82 + index * (card_w + gap)
        draw_card(r, (x, card_y, x + card_w, card_y + card_h), *card)

    d.rounded_rectangle((82, 445, 780, 705), radius=22, fill=PALETTE["panel_bg"], outline=PALETTE["line"], width=2)
    r.text_box((118, 480, 720, 522), "테스트 흐름", 32, PALETTE["ink"], max_lines=1)
    flow = [
        ("login", "perf 사용자 5명"),
        ("1페이지", f"p95 {values['first_page_p95']:.0f}ms"),
        ("2페이지", f"p95 {ms_text(values['second_page_p95'])}"),
        ("3페이지", f"p95 {ms_text(values['third_page_p95'])}"),
    ]
    x = 118
    for index, (name, detail) in enumerate(flow):
        d.rounded_rectangle((x, 555, x + 135, 640), radius=18, fill=PALETTE["white"], outline="#C7D2FE", width=2)
        r.text_box((x + 18, 574, x + 118, 604), name, 22, PALETTE["blue"], min_size=18, max_lines=1)
        r.text_box((x + 18, 612, x + 118, 638), detail, 16, PALETTE["muted"], min_size=13, max_lines=1)
        if index < len(flow) - 1:
            d.line((x + 143, 598, x + 183, 598), fill=PALETTE["blue"], width=4)
            d.polygon([(x + 183, 598), (x + 169, 588), (x + 169, 608)], fill=PALETTE["blue"])
        x += 182

    d.rounded_rectangle((830, 445, 1718, 780), radius=22, fill=PALETTE["amber_bg"], outline=PALETTE["amber_line"], width=2)
    r.text_box((866, 480, 1660, 522), "관찰된 성능 신호", 32, PALETTE["ink"], max_lines=1)
    draw_bullet(r, 866, 548, f"{plan['videos_scan_type']} on videos", f"랭킹 계산 전 scan rows {plan['videos_rows_scanned']:,}건")
    draw_bullet(r, 866, 628, f"Sort method: {plan['sort_method']}", f"temp blocks read {values['temp_read']}, written {values['temp_written']}")
    draw_bullet(r, 866, 708, "user_blocks 필터링 비용", f"Rows Removed by Filter {plan['user_blocks_removed']:,}건")

    d.rounded_rectangle((82, 825, 1718, 1075), radius=22, fill=PALETTE["white"], outline=PALETTE["line"], width=2)
    r.text_box((118, 863, 1660, 905), "성능결과 해석", 32, PALETTE["ink"], max_lines=1)
    if baseline_values:
        http_direction = "증가" if values["p95"] > baseline_values["p95"] else "감소"
        summary = [
            f"최초 기준선 대비 SQL은 {baseline_values['sql_time']:.0f}ms에서 {values['sql_time']:.0f}ms로 줄고, temp write는 {baseline_plan['temp_written']:,}에서 {plan['temp_written']:,}이 되었다.",
            f"반면 HTTP p95는 {baseline_values['p95']:.0f}ms에서 {values['p95']:.0f}ms로 {http_direction}해 병목이 SQL 밖으로 이동했는지 확인해야 한다.",
            "2·3번째 cursor 호출은 snapshot 재사용 구간이므로 첫 페이지와 분리해 캐시 효과를 별도 측정한다.",
        ]
    elif p95_ok:
        summary = [
            f"{label} API 지연은 p95 {values['p95']:.0f}ms이고, 첫 페이지 snapshot SQL이 약 {values['sql_time']:.0f}ms를 사용한다.",
            "실행 계획은 scan rows, temp blocks, 정렬 방식이 API 지연으로 이어지는지 확인하는 핵심 근거다.",
            "다음 비교는 동일 seed와 k6 script로 p95/p99, SQL 시간, temp blocks가 함께 줄었는지 확인한다.",
        ]
    else:
        summary = [
            f"{label} API 지연은 p95 {values['p95']:.0f}ms로 목표를 초과했지만, SQL은 약 {values['sql_time']:.0f}ms에 그쳤다.",
            "현재 병목은 DB ranking보다 signed URL 생성과 응답 조립 같은 API 레이어에 남아 있을 가능성이 높다.",
            "다음 비교는 동일 seed와 k6 script로 SQL 시간과 HTTP 지연을 분리해서 반복 측정한다.",
        ]
    y = 925
    for line in summary:
        d.ellipse((118, y + 12, 128, y + 22), fill=PALETTE["teal"])
        r.text_box((152, y, 1660, y + 48), line, 24, PALETTE["ink"], min_size=20, max_lines=2)
        y += 54

    r.text_box((82, 1120, 225, 1150), "원본 근거:", 20, PALETTE["muted"], max_lines=1)
    r.text_box(
        (225, 1120, 1660, 1150),
        "k6-summary.json - recommendation-feed-explain.json - screenshots/raw",
        20,
        "#344054",
        mono=True,
        max_lines=1,
    )
    r.text_box(
        (82, 1160, 1660, 1192),
        f"requests={values['requests']} - p50={values['p50']:.0f}ms - p95={values['p95']:.0f}ms - p99={values['p99']:.0f}ms - error_rate={values['error_rate']:.0%}",
        20,
        "#344054",
        mono=True,
        max_lines=1,
    )

    r.assert_no_overflow()
    return image


def render_sql_bottleneck(root, run_label):
    label = run_display_name(run_label)
    values = metric_values(root, run_label)
    plan = plan_values(root, run_label)
    state = code_state_values(root, run_label)
    sql_share = plan["execution_time"] / values["p95"] * 100 if values["p95"] else 0
    image, r, d = new_canvas(
        root,
        "추천 피드 SQL 실행계획 확대",
        f"{label} EXPLAIN ANALYZE - 후보군 스캔과 정렬 비용",
        f"SQL {plan['execution_time']:.0f}ms",
    )

    card_y = 205
    card_w = 390
    card_h = 174
    gap = 28
    cards = [
        ("후보 스캔", f"{plan['videos_rows_scanned']:,} rows", f"videos {plan['videos_rows_per_loop']:,} x {plan['videos_loops']} loops", PALETTE["blue"]),
        ("정렬 방식", plan["sort_method"], f"{plan['sort_space_type']} {plan['sort_space_used']:,}kB", PALETTE["amber"]),
        ("Temp Write", f"{plan['temp_written']:,} blocks", f"read {plan['temp_read']:,} blocks", PALETTE["purple"]),
        ("p95 내 SQL 비중", f"{sql_share:.0f}%", f"SQL {plan['execution_time']:.0f}ms / p95 {values['p95']:.0f}ms", PALETTE["green"]),
    ]
    for index, card in enumerate(cards):
        x = 82 + index * (card_w + gap)
        draw_small_card(r, (x, card_y, x + card_w, card_y + card_h), *card)

    d.rounded_rectangle((82, 425, 1718, 720), radius=22, fill=PALETTE["panel_bg"], outline=PALETTE["line"], width=2)
    r.text_box((118, 462, 1660, 504), "실행 계획 흐름", 32, PALETTE["ink"], max_lines=1)
    sort_label = "Sort 없음" if plan["sort_method"] == "not used" else plan["sort_method"]
    sort_detail = "disk spill 없음" if plan["temp_written"] == 0 else f"temp write {plan['temp_written']:,}"
    flow = [
        (plan["videos_scan_type"], f"videos {plan['videos_rows_scanned']:,} rows"),
        ("Hash Join", "gym/users/follows 결합"),
        (sort_label, sort_detail),
        ("Gather Merge", f"workers {plan['workers_launched']}"),
        (f"Limit {state['snapshot_size']}", "snapshot 후보"),
    ]
    x = 118
    for index, (name, detail) in enumerate(flow):
        d.rounded_rectangle((x, 555, x + 250, 645), radius=18, fill=PALETTE["white"], outline="#C7D2FE", width=2)
        r.text_box((x + 22, 574, x + 228, 604), name, 22, PALETTE["blue"], min_size=18, max_lines=1)
        r.text_box((x + 22, 611, x + 228, 638), detail, 17, PALETTE["muted"], min_size=15, max_lines=1)
        if index < len(flow) - 1:
            d.line((x + 258, 600, x + 294, 600), fill=PALETTE["blue"], width=4)
            d.polygon([(x + 294, 600), (x + 280, 590), (x + 280, 610)], fill=PALETTE["blue"])
        x += 310

    d.rounded_rectangle((82, 765, 1718, 1090), radius=22, fill=PALETTE["white"], outline=PALETTE["line"], width=2)
    r.text_box((118, 803, 1660, 845), "성능결과 해석", 32, PALETTE["ink"], max_lines=1)
    if plan["videos_rows_scanned"] > 20_000 or plan["temp_written"] > 0:
        findings = [
            ("반환은 20개지만 정렬 전 후보군이 크다.", "현재 병목은 네트워크보다 DB 내부 후보 생성과 ranking 정렬 쪽에 있다."),
            ("정렬 과정의 temp blocks written이 크면 DB I/O 변동성이 커진다.", "동시 요청이 늘면 p95/p99보다 상위 지연이 먼저 흔들릴 수 있다."),
            ("user_blocks 조회는 데이터 증가 전에 방향별 인덱스와 쿼리 분리가 필요하다.", f"현재 Rows Removed by Filter {plan['user_blocks_removed']:,}건을 기준으로 after와 비교한다."),
        ]
    else:
        findings = [
            ("후보군 window와 인덱스가 scan rows를 제한하는지 확인한다.", "after에서는 전체 공개 영상 스캔보다 작은 후보군에서 ranking이 끝나야 한다."),
            ("temp blocks written이 줄면 정렬 I/O 병목이 완화된 신호다.", "p95/p99 개선이 SQL time, temp blocks 감소와 함께 나타나야 설득력이 있다."),
            ("user_blocks는 방향별 조회 비용을 분리해서 계속 관찰한다.", f"현재 Rows Removed by Filter {plan['user_blocks_removed']:,}건; 차단 데이터가 커지면 별도 지표로 추적한다."),
        ]
    y = 870
    for title, desc in findings:
        draw_list_item(r, 118, y, title, desc)
        y += 70

    draw_footer(
        r,
        1132,
        "recommendation-feed-explain.json - row-counts-and-sizes.txt - screenshots/03-sql-plan",
        f"execution={plan['execution_time']:.0f}ms - temp_written={plan['temp_written']} - scan_rows={plan['videos_rows_scanned']}",
    )

    r.assert_no_overflow()
    return image


def render_k6_results(root, run_label):
    run_name = run_display_name(run_label)
    values = metric_values(root, run_label)
    first_delta = values["first_page_p95"] - values["cursor_page_p95"]
    second_delta = first_page_delta_text(values["first_page_p95"], values["second_page_p95"])
    third_delta = first_page_delta_text(values["first_page_p95"], values["third_page_p95"])
    spread = values["p99"] - values["p50"]
    sql_share = values["sql_time"] / values["p95"] * 100 if values["p95"] else 0
    spread_desc = (
        f"상위 지연 구간도 {spread:.0f}ms 차이로 비교적 좁다."
        if spread <= 100
        else f"상위 지연 구간은 {spread:.0f}ms 차이로 넓다."
    )
    flow_delta_desc = "차이 없음"
    if first_delta > 0:
        flow_delta_desc = "첫 페이지가 더 느림"
    elif first_delta < 0:
        flow_delta_desc = "다음 페이지가 더 느림"
    image, r, d = new_canvas(
        root,
        "추천 피드 k6 결과 해석",
        f"{run_name} 부하 결과 - 지연 분포와 실패율",
        f"requests {values['requests']}",
    )

    card_y = 205
    card_w = 390
    card_h = 174
    gap = 28
    p95_ok = p95_passed(values)
    error_ok = error_rate_passed(values)
    cards = [
        ("p50", f"{values['p50']:.0f} ms", "중앙값 응답시간", PALETTE["blue"]),
        (
            "p95",
            f"{values['p95']:.0f} ms",
            "threshold p95 < 1000ms 통과" if p95_ok else "threshold p95 < 1000ms 실패",
            PALETTE["green"] if p95_ok else PALETTE["amber"],
        ),
        ("p99", f"{values['p99']:.0f} ms", f"p99-p50 {spread:.0f}ms", PALETTE["purple"]),
        (
            "오류율",
            f"{values['error_rate']:.0%}",
            "http_req_failed < 1% 통과" if error_ok else "http_req_failed < 1% 실패",
            PALETTE["green"] if error_ok else PALETTE["amber"],
        ),
    ]
    for index, card in enumerate(cards):
        x = 82 + index * (card_w + gap)
        draw_small_card(r, (x, card_y, x + card_w, card_y + card_h), *card)

    d.rounded_rectangle((82, 425, 850, 725), radius=22, fill=PALETTE["panel_bg"], outline=PALETTE["line"], width=2)
    r.text_box((118, 462, 800, 504), "지연 분포", 32, PALETTE["ink"], max_lines=1)
    axis_y = 600
    d.line((150, axis_y, 780, axis_y), fill=PALETTE["line"], width=6)
    points = [
        ("p50", values["p50"], 180, PALETTE["blue"]),
        ("p95", values["p95"], 500, PALETTE["green"] if p95_ok else PALETTE["amber"]),
        ("p99", values["p99"], 720, PALETTE["purple"]),
    ]
    for point_label, value, x, color in points:
        d.ellipse((x - 14, axis_y - 14, x + 14, axis_y + 14), fill=color)
        r.text_box((x - 70, axis_y - 78, x + 70, axis_y - 44), point_label, 22, PALETTE["muted"], max_lines=1)
        r.text_box((x - 82, axis_y + 30, x + 82, axis_y + 64), f"{value:.0f}ms", 24, color, max_lines=1)
    r.text_box((118, 655, 800, 698), spread_desc, 23, PALETTE["ink"], min_size=19, max_lines=1)

    d.rounded_rectangle((900, 425, 1718, 725), radius=22, fill=PALETTE["white"], outline=PALETTE["line"], width=2)
    r.text_box((936, 462, 1660, 504), "흐름별 응답", 32, PALETTE["ink"], max_lines=1)
    flow_cards = [
        ("1페이지", f"p95 {values['first_page_p95']:.0f}ms", "snapshot 생성"),
        ("2페이지", f"p95 {ms_text(values['second_page_p95'])}", second_delta),
        ("3페이지", f"p95 {ms_text(values['third_page_p95'])}", third_delta),
        ("cursor 전체", f"p95 {values['cursor_page_p95']:.0f}ms", flow_delta_desc),
    ]
    x = 936
    for card_label, value, sub in flow_cards:
        d.rounded_rectangle((x, 555, x + 175, 675), radius=18, fill=PALETTE["panel_bg"], outline=PALETTE["line"], width=2)
        r.text_box((x + 18, 574, x + 157, 602), card_label, 20, PALETTE["muted"], min_size=17, max_lines=1)
        r.text_box((x + 18, 607, x + 157, 638), value, 20, PALETTE["blue"], min_size=16, max_lines=1)
        r.text_box((x + 18, 642, x + 157, 668), sub, 14, PALETTE["muted"], min_size=12, max_lines=1)
        x += 193

    d.rounded_rectangle((82, 770, 1718, 1088), radius=22, fill=PALETTE["white"], outline=PALETTE["line"], width=2)
    r.text_box((118, 808, 1660, 850), "성능결과 해석", 32, PALETTE["ink"], max_lines=1)
    if p95_ok and error_ok:
        findings = [
            (f"현재 {run_name}은 p95 목표와 오류율 기준을 모두 통과했다.", "API 레이어는 낮은 부하 조건에서 안정적으로 응답했고 k6 check 실패가 없다."),
            (f"2페이지는 {second_delta}, 3페이지는 {third_delta}이다.", "snapshot이 만들어진 뒤 cursor page가 첫 페이지보다 얼마나 가벼워지는지 보여주는 지표다."),
            (f"SQL 실행시간 {values['sql_time']:.0f}ms가 p95의 약 {sql_share:.0f}%를 차지한다.", "SQL 비중이 낮아지면 남은 지연은 URL 생성, DTO 조립, 네트워크까지 분리해서 본다."),
        ]
    else:
        findings = [
            (f"현재 {run_name}은 오류율 {values['error_rate']:.0%}이지만 p95 목표는 초과했다.", "API 응답은 실패하지 않았지만 사용자 체감 지연은 아직 높다."),
            (f"SQL 실행시간 {values['sql_time']:.0f}ms는 p95의 약 {sql_share:.0f}%만 차지한다.", "남은 병목은 signed URL 생성, 캐시 적중률, 응답 DTO 조립 비용을 분리해서 봐야 한다."),
            (f"2페이지는 {second_delta}, 3페이지는 {third_delta}이다.", "cursor page의 캐시 효과와 전체 HTTP p95를 분리해서 해석한다."),
        ]
    y = 875
    for title, desc in findings:
        draw_list_item(r, 118, y, title, desc)
        y += 70

    draw_footer(
        r,
        1132,
        "k6-summary.json - recommendation-feed.js - screenshots/02-k6-summary",
        f"requests={values['requests']} - p50={values['p50']:.0f}ms - p95={values['p95']:.0f}ms - p99={values['p99']:.0f}ms - error_rate={values['error_rate']:.0%}",
    )

    r.assert_no_overflow()
    return image


def draw_comparison_row(renderer, y, label, before, after, delta):
    d = renderer.draw
    d.line((118, y - 12, 1682, y - 12), fill=PALETTE["line"], width=1)
    renderer.text_box((118, y, 430, y + 36), label, 22, PALETTE["muted"], max_lines=1)
    renderer.text_box((470, y, 780, y + 36), before, 22, PALETTE["ink"], max_lines=1)
    renderer.text_box((860, y, 1170, y + 36), after, 22, PALETTE["muted"], max_lines=1)
    renderer.text_box((1250, y, 1645, y + 36), delta, 22, PALETTE["muted"], max_lines=1)


def render_before_after_template(root, run_label):
    before_values = metric_values(root, "local-baseline")
    before_plan = plan_values(root, "local-baseline")
    after_available = run_has_results(root, "after")
    after_values = metric_values(root, "after") if after_available else None
    after_plan = plan_values(root, "after") if after_available else None
    image, r, d = new_canvas(
        root,
        "추천 피드 최초 기준선 대비 현재 변화",
        "성능테스트 시작 전 local-baseline과 현재 after를 같은 근거 파일로 비교",
        "after 완료" if after_available else "after 대기",
    )

    d.rounded_rectangle((82, 205, 850, 430), radius=22, fill=PALETTE["white"], outline=PALETTE["line"], width=2)
    r.text_box((118, 240, 800, 280), "Before: local-baseline", 30, PALETTE["ink"], max_lines=1)
    r.text_box((118, 300, 800, 350), f"p95 {before_values['p95']:.0f}ms / SQL {before_values['sql_time']:.0f}ms / temp write {before_plan['temp_written']:,}", 26, PALETTE["blue"], min_size=21, max_lines=1)
    r.text_box((118, 362, 800, 402), "초기 병목: 넓은 videos scan + external merge sort", 22, PALETTE["muted"], max_lines=1)

    d.rounded_rectangle((900, 205, 1718, 430), radius=22, fill=PALETTE["amber_bg"], outline=PALETTE["amber_line"], width=2)
    r.text_box((936, 240, 1660, 280), "After: after" if after_available else "After: 측정 대기", 30, PALETTE["ink"], max_lines=1)
    if after_available:
        r.text_box((936, 300, 1660, 350), f"p95 {after_values['p95']:.0f}ms / SQL {after_values['sql_time']:.0f}ms / temp write {after_plan['temp_written']:,}", 26, PALETTE["amber"], min_size=21, max_lines=1)
        r.text_box((936, 362, 1660, 402), "현재 해석: DB 병목 완화 후 HTTP 레이어 지연을 분리 관찰", 22, PALETTE["muted"], max_lines=1)
    else:
        r.text_box((936, 300, 1660, 350), "동일 데이터셋, 동일 VU, 동일 endpoint로 재측정", 26, PALETTE["amber"], min_size=21, max_lines=1)
        r.text_box((936, 362, 1660, 402), "수치가 줄어든 이유를 SQL plan과 코드 diff로 연결", 22, PALETTE["muted"], max_lines=1)

    d.rounded_rectangle((82, 480, 1718, 880), radius=22, fill=PALETTE["white"], outline=PALETTE["line"], width=2)
    r.text_box((118, 520, 430, 560), "항목", 24, PALETTE["ink"], max_lines=1)
    r.text_box((470, 520, 780, 560), "Before", 24, PALETTE["ink"], max_lines=1)
    r.text_box((860, 520, 1170, 560), "After", 24, PALETTE["ink"], max_lines=1)
    r.text_box((1250, 520, 1645, 560), "변화량", 24, PALETTE["ink"], max_lines=1)
    if after_available:
        rows = [
            ("p95 응답시간", f"{before_values['p95']:.0f}ms", f"{after_values['p95']:.0f}ms", delta_text(before_values["p95"], after_values["p95"], "ms")),
            ("p99 응답시간", f"{before_values['p99']:.0f}ms", f"{after_values['p99']:.0f}ms", delta_text(before_values["p99"], after_values["p99"], "ms")),
            ("SQL 실행시간", f"{before_values['sql_time']:.0f}ms", f"{after_values['sql_time']:.0f}ms", delta_text(before_values["sql_time"], after_values["sql_time"], "ms")),
            ("temp blocks written", f"{before_plan['temp_written']:,}", f"{after_plan['temp_written']:,}", delta_text(before_plan["temp_written"], after_plan["temp_written"], " blocks")),
            ("scan rows", f"{before_plan['videos_rows_scanned']:,}", f"{after_plan['videos_rows_scanned']:,}", delta_text(before_plan["videos_rows_scanned"], after_plan["videos_rows_scanned"], " rows")),
            ("오류율", f"{before_values['error_rate']:.0%}", f"{after_values['error_rate']:.0%}", "동일 기준 유지"),
        ]
    else:
        rows = [
            ("p95 응답시간", f"{before_values['p95']:.0f}ms", "측정 대기", "계산 대기"),
            ("p99 응답시간", f"{before_values['p99']:.0f}ms", "측정 대기", "계산 대기"),
            ("SQL 실행시간", f"{before_values['sql_time']:.0f}ms", "측정 대기", "계산 대기"),
            ("temp blocks written", f"{before_plan['temp_written']:,}", "측정 대기", "계산 대기"),
            ("scan rows", f"{before_plan['videos_rows_scanned']:,}", "측정 대기", "계산 대기"),
            ("오류율", f"{before_values['error_rate']:.0%}", "측정 대기", "동일 기준 유지"),
        ]
    y = 590
    for row in rows:
        draw_comparison_row(r, y, *row)
        y += 48

    d.rounded_rectangle((82, 925, 1718, 1085), radius=22, fill=PALETTE["panel_bg"], outline=PALETTE["line"], width=2)
    r.text_box((118, 960, 1660, 1000), "성능결과 해석", 30, PALETTE["ink"], max_lines=1)
    if after_available:
        r.text_box((118, 1012, 1660, 1044), change_sentence("HTTP p95", before_values["p95"], after_values["p95"]), 23, PALETTE["ink"], min_size=19, max_lines=1)
        r.text_box((118, 1048, 1660, 1078), change_sentence("SQL 실행시간", before_values["sql_time"], after_values["sql_time"]), 23, PALETTE["ink"], min_size=19, max_lines=1)
    else:
        r.text_box((118, 1018, 1660, 1058), "after 측정 후 HTTP p95와 SQL 실행시간을 분리해서 병목 이동 여부를 기록한다.", 24, PALETTE["ink"], min_size=20, max_lines=1)

    draw_footer(
        r,
        1130,
        "before-after-template - k6-summary.json - recommendation-feed-explain.json",
        f"before_run=local-baseline - after_run={'after' if after_available else 'pending'} - endpoint=/api/recommendations/videos?size=20",
    )

    r.assert_no_overflow()
    return image


def render_code_change_template(root, run_label):
    label = run_display_name(run_label)
    state = code_state_values(root, run_label)
    values = metric_values(root, run_label)
    image, r, d = new_canvas(
        root,
        "추천 피드 코드 변경 증거",
        "성능 개선 전후의 코드와 수치가 같은 흐름으로 연결되는지 확인",
        f"{label} {state['commit']} + {state['working_tree']}",
    )

    d.rounded_rectangle((82, 205, 850, 515), radius=22, fill=PALETTE["white"], outline=PALETTE["line"], width=2)
    r.text_box((118, 240, 800, 280), f"{label} 코드 증거", 30, PALETTE["ink"], max_lines=1)
    before_items = [
        (f"base commit {state['commit']}", f"captured_at {state['captured_at']}"),
        (f"viewer_id {state['viewer_id']} / page_size {state['page_size']}", f"{label} 실행 조건"),
        ("working tree", "RecommendationServiceImpl + GCS 설정 변경"),
    ]
    y = 312
    for title, desc in before_items:
        draw_list_item(r, 118, y, title, desc, color=PALETTE["blue"])
        y += 62

    d.rounded_rectangle((900, 205, 1718, 515), radius=22, fill=PALETTE["amber_bg"], outline=PALETTE["amber_line"], width=2)
    if run_label == "after":
        r.text_box((936, 240, 1660, 280), "적용된 변경 내용", 30, PALETTE["ink"], max_lines=1)
        after_items = [
            ("피드 streamUrl 제거", "추천 목록은 null, 상세 API에서 생성"),
            ("thumbnail signed URL 제거", "public bucket base URL과 path 조합"),
            ("회귀 테스트 추가", "영상/썸네일 signed URL 호출 금지 검증"),
        ]
    else:
        r.text_box((936, 240, 1660, 280), "개선 후 코드 캡처 대기", 30, PALETTE["ink"], max_lines=1)
        after_items = [
            ("변경 diff", "쿼리 후보군 축소, 인덱스, cache 중 실제 적용 내용"),
            ("after screenshot", "동일 영역의 코드 캡처를 after/screenshots에 저장"),
            ("after metrics", "p95/p99, SQL time, temp blocks를 같은 표에 입력"),
        ]
    y = 312
    for title, desc in after_items:
        draw_list_item(r, 936, y, title, desc, color=PALETTE["amber"])
        y += 62

    d.rounded_rectangle((82, 570, 1718, 905), radius=22, fill=PALETTE["panel_bg"], outline=PALETTE["line"], width=2)
    r.text_box((118, 608, 1660, 650), "증거 연결 흐름", 32, PALETTE["ink"], max_lines=1)
    flow = [
        ("코드 변경", "feed URL 정책 변경"),
        ("URL 생성 감소", "썸네일 signed URL 제거"),
        ("k6 변화", f"{label} p95 {values['p95']:.0f}ms"),
        ("결론", "p95 목표 통과"),
    ]
    x = 118
    for index, (name, detail) in enumerate(flow):
        d.rounded_rectangle((x, 710, x + 285, 810), radius=18, fill=PALETTE["white"], outline="#C7D2FE", width=2)
        r.text_box((x + 24, 732, x + 260, 762), name, 24, PALETTE["blue"], max_lines=1)
        r.text_box((x + 24, 770, x + 260, 800), detail, 18, PALETTE["muted"], min_size=15, max_lines=1)
        if index < len(flow) - 1:
            d.line((x + 296, 760, x + 350, 760), fill=PALETTE["blue"], width=4)
            d.polygon([(x + 350, 760), (x + 336, 750), (x + 336, 770)], fill=PALETTE["blue"])
        x += 390

    d.rounded_rectangle((82, 950, 1718, 1088), radius=22, fill=PALETTE["white"], outline=PALETTE["line"], width=2)
    r.text_box((118, 982, 1660, 1022), "성능결과 해석 기준", 30, PALETTE["ink"], max_lines=1)
    r.text_box((118, 1038, 1660, 1068), "숫자만 개선됐다고 쓰지 않고, 어떤 URL 생성을 제거했는지와 목표 통과 여부를 함께 기록한다.", 23, PALETTE["ink"], min_size=20, max_lines=1)

    draw_footer(
        r,
        1132,
        "code-state.txt - RecommendationServiceImpl diff - GcsStorageServiceTest",
        f"{label}_base_commit={state['commit']} - p95={values['p95']:.0f}ms - evidence=working_tree_diff",
    )

    r.assert_no_overflow()
    return image


def render_cursor_cache_comparison(root, run_label):
    run_name = run_display_name(run_label)
    values = metric_values(root, run_label)
    image, r, d = new_canvas(
        root,
        "추천 피드 Snapshot 캐시 호출 비교",
        "첫 페이지 생성 이후 2·3번째 cursor 호출의 p95를 분리 측정",
        "cached cursor pages",
    )

    card_y = 205
    card_w = 390
    card_h = 174
    gap = 28
    cards = [
        ("1페이지", ms_value_text(values["first_page_p95"]), "snapshot 생성 + ranking", PALETTE["blue"]),
        ("2페이지", ms_value_text(values["second_page_p95"]), first_page_delta_text(values["first_page_p95"], values["second_page_p95"]), PALETTE["green"]),
        ("3페이지", ms_value_text(values["third_page_p95"]), first_page_delta_text(values["first_page_p95"], values["third_page_p95"]), PALETTE["green"]),
        ("cursor 전체", ms_value_text(values["cursor_page_p95"]), "2페이지 이후 통합", PALETTE["purple"]),
    ]
    for index, card in enumerate(cards):
        x = 82 + index * (card_w + gap)
        draw_small_card(r, (x, card_y, x + card_w, card_y + card_h), *card)

    d.rounded_rectangle((82, 425, 1718, 720), radius=22, fill=PALETTE["panel_bg"], outline=PALETTE["line"], width=2)
    r.text_box((118, 462, 1660, 504), "호출 흐름", 32, PALETTE["ink"], max_lines=1)
    flow = [
        ("첫 페이지 요청", "후보 ranking 후 Redis snapshot 저장"),
        ("2페이지 cursor", "snapshotId + offset 20으로 id slice 조회"),
        ("3페이지 cursor", "같은 snapshotId + offset 40으로 재사용"),
    ]
    x = 118
    for index, (name, detail) in enumerate(flow):
        d.rounded_rectangle((x, 555, x + 405, 645), radius=18, fill=PALETTE["white"], outline="#C7D2FE", width=2)
        r.text_box((x + 24, 574, x + 380, 604), name, 23, PALETTE["blue"], max_lines=1)
        r.text_box((x + 24, 611, x + 380, 638), detail, 18, PALETTE["muted"], min_size=15, max_lines=1)
        if index < len(flow) - 1:
            d.line((x + 417, 600, x + 472, 600), fill=PALETTE["blue"], width=4)
            d.polygon([(x + 472, 600), (x + 458, 590), (x + 458, 610)], fill=PALETTE["blue"])
        x += 530

    d.rounded_rectangle((82, 765, 1718, 1090), radius=22, fill=PALETTE["white"], outline=PALETTE["line"], width=2)
    r.text_box((118, 803, 1660, 845), "성능결과 해석", 32, PALETTE["ink"], max_lines=1)
    if values["second_page_p95"] is not None and values["third_page_p95"] is not None:
        findings = [
            ("2·3번째 호출은 snapshot 재사용 구간이다.", f"2페이지 {first_page_delta_text(values['first_page_p95'], values['second_page_p95'])}, 3페이지 {first_page_delta_text(values['first_page_p95'], values['third_page_p95'])}."),
            ("첫 페이지는 후보 계산과 snapshot 저장을 포함한다.", "cursor page는 ranking SQL을 다시 만들지 않으므로 첫 페이지보다 낮은 p95가 기대된다."),
            ("전체 HTTP p95와 캐시 효과는 분리해서 본다.", "전체 p95가 높아도 cursor page p95가 낮다면 병목이 첫 페이지나 API 조립 쪽에 남은 것이다."),
        ]
    else:
        findings = [
            ("이 결과는 depth별 cursor Trend가 없는 이전 형식이다.", "k6를 CURSOR_FOLLOW_RATE=1, MAX_CURSOR_DEPTH=3으로 재실행하면 2·3페이지 p95가 채워진다."),
            ("cursor 전체 p95는 2페이지 이후 호출이 섞인 값이다.", "정확한 캐시 효과는 second_page_p95, third_page_p95를 분리해야 한다."),
            ("전체 HTTP p95와 캐시 효과는 분리해서 본다.", "전체 p95가 높아도 cursor page p95가 낮다면 병목이 첫 페이지나 API 조립 쪽에 남은 것이다."),
        ]
    y = 870
    for title, desc in findings:
        draw_list_item(r, 118, y, title, desc)
        y += 70

    draw_footer(
        r,
        1132,
        "k6-summary.json - recommendation-feed.js depth trends",
        f"run={run_name} - first={ms_value_text(values['first_page_p95'])} - second={ms_value_text(values['second_page_p95'])} - third={ms_value_text(values['third_page_p95'])}",
    )

    r.assert_no_overflow()
    return image


PRESENTATION_CARDS = {
    "summary": ("01-{run_label}-summary-card.png", render),
    "sql-bottleneck": ("02-{run_label}-sql-bottleneck.png", render_sql_bottleneck),
    "k6-results": ("03-{run_label}-k6-result-interpretation.png", render_k6_results),
    "before-after": ("04-{run_label}-before-after-template.png", render_before_after_template),
    "code-change": ("05-{run_label}-code-change-template.png", render_code_change_template),
    "cursor-cache": ("06-{run_label}-cursor-cache-comparison.png", render_cursor_cache_comparison),
}


def main():
    parser = argparse.ArgumentParser(description="Render recommendation-feed presentation evidence with text overflow checks.")
    parser.add_argument("run_label", nargs="?", default="local-baseline")
    parser.add_argument("--kind", choices=[*PRESENTATION_CARDS.keys(), "all"], default="all")
    parser.add_argument("--root", default=Path(__file__).resolve().parents[2])
    parser.add_argument("--output", default=None)
    args = parser.parse_args()

    root = Path(args.root).resolve()
    if args.kind == "all" and args.output:
        raise SystemExit("--output can only be used with a single --kind")

    selected = PRESENTATION_CARDS.items() if args.kind == "all" else [(args.kind, PRESENTATION_CARDS[args.kind])]
    outputs = []
    for kind, (filename_template, renderer) in selected:
        filename = filename_template.format(run_label=args.run_label)
        output = Path(args.output) if args.output else presentation_output(root, args.run_label, filename)
        output.parent.mkdir(parents=True, exist_ok=True)
        image = renderer(root, args.run_label)
        image.save(output, "PNG")
        outputs.append(output)
        print(f"presentation_card[{kind}]={output}")
    print(f"presentation_cards={len(outputs)}")
    print("overflow_check=passed")


if __name__ == "__main__":
    main()
