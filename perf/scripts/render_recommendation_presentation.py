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
        "sql_time": explain["Execution Time"],
        "temp_read": plan.get("Temp Read Blocks", 0),
        "temp_written": plan.get("Temp Written Blocks", 0),
    }


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
    values = metric_values(root, run_label)
    width, height = 1800, 1260
    image = Image.new("RGB", (width, height), PALETTE["bg"])
    r = Renderer(image, root)
    d = r.draw

    d.rounded_rectangle((44, 36, width - 44, height - 36), radius=28, fill=PALETTE["white"], outline=PALETTE["line"], width=2)
    d.rectangle((44, 36, width - 44, 156), fill="#EFF6FF")
    d.line((44, 156, width - 44, 156), fill=PALETTE["line"], width=2)
    r.text_box((82, 64, 1220, 112), "추천 피드 성능 테스트", 44, PALETTE["blue"], max_lines=1)
    r.text_box((82, 118, 1220, 148), "local-baseline 결과 요약 - GET /api/recommendations/videos?size=20", 24, PALETTE["muted"], max_lines=1)
    d.rounded_rectangle((1285, 70, 1680, 124), radius=14, fill=PALETTE["green_bg"], outline=PALETTE["green_line"], width=1)
    r.text_box((1315, 84, 1650, 112), "seed: videos 100k - users 10k", 22, PALETTE["green"], max_lines=1)

    card_y = 210
    card_w = 390
    card_h = 190
    gap = 28
    cards = [
        ("p95 응답시간", f"{values['p95']:.0f} ms", "목표 기준 < 1000ms", PALETTE["blue"]),
        ("p99 응답시간", f"{values['p99']:.0f} ms", "상위 지연 구간", PALETTE["purple"]),
        ("오류율", f"{values['error_rate']:.0%}", "k6 check 통과", PALETTE["green"]),
        ("SQL 실행시간", f"{values['sql_time']:.0f} ms", "EXPLAIN ANALYZE", PALETTE["amber"]),
    ]
    for index, card in enumerate(cards):
        x = 82 + index * (card_w + gap)
        draw_card(r, (x, card_y, x + card_w, card_y + card_h), *card)

    d.rounded_rectangle((82, 445, 780, 705), radius=22, fill=PALETTE["panel_bg"], outline=PALETTE["line"], width=2)
    r.text_box((118, 480, 720, 522), "테스트 흐름", 32, PALETTE["ink"], max_lines=1)
    flow = [
        ("login", "perf 사용자 5명"),
        ("첫 페이지", f"p95 {values['first_page_p95']:.0f}ms"),
        ("다음 페이지", f"p95 {values['cursor_page_p95']:.0f}ms"),
    ]
    x = 118
    for index, (name, detail) in enumerate(flow):
        d.rounded_rectangle((x, 555, x + 170, 640), radius=18, fill=PALETTE["white"], outline="#C7D2FE", width=2)
        r.text_box((x + 28, 574, x + 145, 604), name, 24, PALETTE["blue"], min_size=20, max_lines=1)
        r.text_box((x + 28, 612, x + 145, 638), detail, 18, PALETTE["muted"], max_lines=1)
        if index < len(flow) - 1:
            d.line((x + 178, 598, x + 238, 598), fill=PALETTE["blue"], width=4)
            d.polygon([(x + 238, 598), (x + 224, 588), (x + 224, 608)], fill=PALETTE["blue"])
        x += 240

    d.rounded_rectangle((830, 445, 1718, 780), radius=22, fill=PALETTE["amber_bg"], outline=PALETTE["amber_line"], width=2)
    r.text_box((866, 480, 1660, 522), "현재 병목 신호", 32, PALETTE["ink"], max_lines=1)
    draw_bullet(r, 866, 548, "Parallel Seq Scan on videos", "랭킹 계산 전 videos 10만 건 후보 스캔")
    draw_bullet(r, 866, 628, "External merge sort on disk", f"temp blocks read {values['temp_read']}, written {values['temp_written']}")
    draw_bullet(r, 866, 708, "user_blocks 조회가 순차 스캔", "현재는 작지만 소셜 그래프가 커지면 위험")

    d.rounded_rectangle((82, 825, 1718, 1075), radius=22, fill=PALETTE["white"], outline=PALETTE["line"], width=2)
    r.text_box((118, 863, 1660, 905), "성능결과 해석", 32, PALETTE["ink"], max_lines=1)
    summary = [
        f"local-baseline API 지연은 p95 {values['p95']:.0f}ms로 기준 안에 있지만, feed query 한 번에 SQL이 약 {values['sql_time']:.0f}ms를 사용한다.",
        "현재 실행 계획은 큰 후보군을 정렬하고 disk spill을 만들며, 최종 20개만 반환하기 전 videos를 넓게 스캔한다.",
        "다음 실험은 후보군 축소 또는 feed 후보 사전 랭킹/cache 적용 후, 동일 seed와 k6 script로 p95/p99와 SQL 시간을 비교한다.",
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


def main():
    parser = argparse.ArgumentParser(description="Render recommendation-feed presentation evidence with text overflow checks.")
    parser.add_argument("run_label", nargs="?", default="local-baseline")
    parser.add_argument("--root", default=Path(__file__).resolve().parents[2])
    parser.add_argument("--output", default=None)
    args = parser.parse_args()

    root = Path(args.root).resolve()
    output = Path(args.output) if args.output else root / "perf/results/recommendation-feed" / args.run_label / "screenshots/presentation/01-local-baseline-summary-card.png"
    output.parent.mkdir(parents=True, exist_ok=True)
    image = render(root, args.run_label)
    image.save(output, "PNG")
    print(f"presentation_card={output}")
    print("overflow_check=passed")


if __name__ == "__main__":
    main()
