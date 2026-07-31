from __future__ import annotations

import argparse
import tempfile
from pathlib import Path

from pypdf import PdfReader, PdfWriter
from reportlab.lib.colors import Color, HexColor
from reportlab.lib.pagesizes import A1
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas


PAGE_W, PAGE_H = A1
TEMPLATE_FOOTER_RATIO = 49.03238 / 780.0
FOOTER_H = PAGE_H * TEMPLATE_FOOTER_RATIO

IVORY = HexColor("#F5F2EC")
PAPER = HexColor("#FBFAF7")
NUS_BLUE = HexColor("#1C427C")
INK = HexColor("#292A2C")
WARM_GREY = HexColor("#6F6D68")
STONE = HexColor("#D9D5CD")
MIST = HexColor("#71869A")
SAGE = HexColor("#9EAE9B")
PLUM = HexColor("#A69AAA")
TERRACOTTA = HexColor("#BE927D")
GOLD = HexColor("#D1B46A")
DEEP = HexColor("#263947")
DEEP_2 = HexColor("#314957")
WHITE = HexColor("#FBFAF7")


def register_fonts() -> None:
    fonts = {
        "PosterSans": r"C:\Windows\Fonts\segoeui.ttf",
        "PosterSansBold": r"C:\Windows\Fonts\segoeuib.ttf",
        "PosterSansLight": r"C:\Windows\Fonts\segoeuil.ttf",
        "PosterSerif": r"C:\Windows\Fonts\georgia.ttf",
        "PosterSerifBold": r"C:\Windows\Fonts\georgiab.ttf",
        "PosterSerifItalic": r"C:\Windows\Fonts\georgiai.ttf",
    }
    for name, path in fonts.items():
        pdfmetrics.registerFont(TTFont(name, path))


def tracking_text(
    c: canvas.Canvas,
    text: str,
    x: float,
    y: float,
    font: str,
    size: float,
    color: Color,
    tracking: float,
) -> None:
    c.setFont(font, size)
    c.setFillColor(color)
    cursor = x
    for ch in text:
        c.drawString(cursor, y, ch)
        cursor += pdfmetrics.stringWidth(ch, font, size) + tracking


def alpha_circle(
    c: canvas.Canvas,
    x: float,
    y: float,
    radius: float,
    fill: Color,
    alpha: float,
    stroke: Color | None = None,
    stroke_width: float = 1,
) -> None:
    c.saveState()
    c.setFillAlpha(alpha)
    c.setFillColor(fill)
    if stroke is None:
        c.setStrokeColor(fill)
        c.setStrokeAlpha(0)
    else:
        c.setStrokeColor(stroke)
        c.setStrokeAlpha(min(1, alpha + 0.25))
    c.setLineWidth(stroke_width)
    c.circle(x, y, radius, fill=1, stroke=1 if stroke else 0)
    c.restoreState()


def soft_blob(
    c: canvas.Canvas,
    points: tuple[tuple[float, float], ...],
    fill: Color,
    alpha: float,
) -> None:
    c.saveState()
    c.setFillColor(fill)
    c.setFillAlpha(alpha)
    c.setStrokeAlpha(0)
    p = c.beginPath()
    p.moveTo(*points[0])
    for idx in range(1, len(points), 3):
        p.curveTo(*points[idx], *points[idx + 1], *points[idx + 2])
    p.close()
    c.drawPath(p, fill=1, stroke=0)
    c.restoreState()


def curve(
    c: canvas.Canvas,
    start: tuple[float, float],
    c1: tuple[float, float],
    c2: tuple[float, float],
    end: tuple[float, float],
    color: Color,
    width: float,
    alpha: float = 1,
    dash: tuple[float, ...] | None = None,
) -> None:
    c.saveState()
    c.setStrokeColor(color)
    c.setStrokeAlpha(alpha)
    c.setLineWidth(width)
    c.setLineCap(1)
    if dash:
        c.setDash(dash)
    p = c.beginPath()
    p.moveTo(*start)
    p.curveTo(*c1, *c2, *end)
    c.drawPath(p, fill=0, stroke=1)
    c.restoreState()


def arrowhead(c: canvas.Canvas, x: float, y: float, color: Color, scale: float = 1) -> None:
    c.saveState()
    c.setFillColor(color)
    c.setFillAlpha(0.95)
    p = c.beginPath()
    p.moveTo(x + 10 * scale, y)
    p.lineTo(x - 8 * scale, y + 7 * scale)
    p.lineTo(x - 8 * scale, y - 7 * scale)
    p.close()
    c.drawPath(p, fill=1, stroke=0)
    c.restoreState()


def label(
    c: canvas.Canvas,
    text: str,
    x: float,
    y: float,
    size: float = 18,
    color: Color = INK,
    font: str = "PosterSansBold",
) -> None:
    c.setFillColor(color)
    c.setFont(font, size)
    c.drawString(x, y, text)


def centered_label(
    c: canvas.Canvas,
    text: str,
    x: float,
    y: float,
    size: float = 18,
    color: Color = INK,
    font: str = "PosterSansBold",
) -> None:
    c.setFillColor(color)
    c.setFont(font, size)
    c.drawCentredString(x, y, text)


def draw_aurora(c: canvas.Canvas, x: float, y: float) -> None:
    alpha_circle(c, x, y, 116, MIST, 0.16)
    alpha_circle(c, x, y, 83, PAPER, 0.94, MIST, 2)
    alpha_circle(c, x - 22, y + 19, 38, SAGE, 0.35)
    alpha_circle(c, x + 21, y - 13, 29, PLUM, 0.3)
    curve(c, (x - 49, y - 7), (x - 20, y + 39), (x + 20, y - 43), (x + 49, y + 7), NUS_BLUE, 4)
    for dx, dy, r in ((-62, 46, 6), (66, 53, 4), (48, -63, 5), (-68, -48, 4)):
        alpha_circle(c, x + dx, y + dy, r, GOLD, 0.85)


def draw_memory_cosmos(c: canvas.Canvas, x: float, y: float) -> None:
    alpha_circle(c, x, y, 112, SAGE, 0.14)
    nodes = (
        (-66, 26, 13, TERRACOTTA),
        (-21, 66, 9, GOLD),
        (33, 49, 17, MIST),
        (69, 6, 8, PLUM),
        (39, -52, 12, SAGE),
        (-23, -66, 7, GOLD),
        (-71, -26, 10, MIST),
        (0, 4, 24, NUS_BLUE),
    )
    links = ((0, 1), (1, 2), (2, 3), (0, 7), (3, 7), (4, 7), (5, 7), (6, 7), (4, 5), (5, 6))
    c.saveState()
    c.setLineWidth(1.6)
    c.setStrokeColor(MIST)
    c.setStrokeAlpha(0.55)
    for a, b in links:
        c.line(x + nodes[a][0], y + nodes[a][1], x + nodes[b][0], y + nodes[b][1])
    c.restoreState()
    for dx, dy, r, color in nodes:
        alpha_circle(c, x + dx, y + dy, r, color, 0.88)


def draw_resonance(c: canvas.Canvas, x: float, y: float) -> None:
    alpha_circle(c, x - 35, y + 8, 88, MIST, 0.23, MIST, 2)
    alpha_circle(c, x + 38, y - 9, 88, PLUM, 0.23, PLUM, 2)
    alpha_circle(c, x + 2, y, 38, GOLD, 0.28)
    curve(c, (x - 82, y + 2), (x - 32, y + 70), (x + 33, y - 70), (x + 84, y - 3), NUS_BLUE, 3, 0.8)
    alpha_circle(c, x - 75, y + 1, 8, NUS_BLUE, 0.9)
    alpha_circle(c, x + 77, y - 2, 8, NUS_BLUE, 0.9)


def draw_letter(c: canvas.Canvas, x: float, y: float) -> None:
    alpha_circle(c, x, y, 112, TERRACOTTA, 0.12)
    c.saveState()
    c.translate(x, y)
    c.rotate(-8)
    c.setFillColor(PAPER)
    c.setStrokeColor(TERRACOTTA)
    c.setLineWidth(2.5)
    c.roundRect(-74, -48, 148, 96, 9, fill=1, stroke=1)
    c.line(-72, 42, 0, -10)
    c.line(72, 42, 0, -10)
    c.restoreState()
    curve(c, (x - 124, y - 54), (x - 72, y - 82), (x + 84, y - 75), (x + 125, y - 29), GOLD, 3, 0.75, (4, 8))
    alpha_circle(c, x - 126, y - 54, 10, MIST, 0.9)
    alpha_circle(c, x + 126, y - 28, 10, SAGE, 0.9)


def draw_product_story(c: canvas.Canvas) -> None:
    tracking_text(c, "THE PRODUCT", 100, 1909, "PosterSansBold", 18, NUS_BLUE, 3.4)
    label(c, "FROM A PRIVATE CONVERSATION", 100, 1805, 51, INK, "PosterSerif")
    label(c, "TO A LIVING MAP OF YOU", 100, 1737, 51, INK, "PosterSerif")
    label(c, "TO RESONANCE WITH SOMEONE ELSE.", 100, 1669, 51, NUS_BLUE, "PosterSerifBold")

    y = 1435
    xs = (250, 650, 1040, 1430)
    curve(c, (xs[0] + 115, y + 7), (470, y + 72), (515, y - 72), (xs[1] - 112, y), MIST, 4, 0.65)
    curve(c, (xs[1] + 112, y), (830, y - 73), (890, y + 76), (xs[2] - 115, y), SAGE, 4, 0.7)
    curve(c, (xs[2] + 115, y), (1210, y + 80), (1265, y - 70), (xs[3] - 115, y), PLUM, 4, 0.7)
    draw_aurora(c, xs[0], y)
    draw_memory_cosmos(c, xs[1], y)
    draw_resonance(c, xs[2], y)
    draw_letter(c, xs[3], y)

    stages = (
        ("AURORA", "LISTENS", xs[0], MIST),
        ("MEMORY COSMOS", "REMEMBERS", xs[1], SAGE),
        ("ECHO CAPSULE", "RESONATES", xs[2], PLUM),
        ("SLOW LETTER", "CONNECTS", xs[3], TERRACOTTA),
    )
    for title, verb, x, color in stages:
        centered_label(c, title, x, 1272, 18, color)
        centered_label(c, verb, x, 1238, 26, INK, "PosterSerifBold")


def draw_dark_field(c: canvas.Canvas) -> None:
    # The curved edge turns the technical half into a field, not another card.
    p = c.beginPath()
    p.moveTo(0, FOOTER_H)
    p.lineTo(0, 1115)
    p.curveTo(300, 1205, 610, 1165, 875, 1215)
    p.curveTo(1130, 1263, 1390, 1180, PAGE_W, 1255)
    p.lineTo(PAGE_W, FOOTER_H)
    p.close()
    c.setFillColor(DEEP)
    c.drawPath(p, fill=1, stroke=0)

    # Asymmetric ambient pools create hierarchy without boxes.
    soft_blob(
        c,
        (
            (40, 790),
            (190, 1080), (560, 1160), (760, 990),
            (880, 810), (610, 640), (310, 675),
            (130, 690), (70, 735), (40, 790),
        ),
        MIST,
        0.12,
    )
    soft_blob(
        c,
        (
            (690, 490),
            (780, 800), (1110, 1020), (1370, 880),
            (1550, 780), (1600, 480), (1360, 360),
            (1100, 245), (805, 300), (690, 490),
        ),
        SAGE,
        0.10,
    )
    soft_blob(
        c,
        (
            (100, 210),
            (320, 430), (700, 450), (930, 310),
            (1090, 210), (630, 180), (100, 210),
        ),
        PLUM,
        0.08,
    )


def tiny_dot_train(c: canvas.Canvas, x: float, y: float, count: int, color: Color) -> None:
    for idx in range(count):
        radius = 5 if idx % 3 else 7
        alpha_circle(c, x + idx * 27, y + (idx % 2) * 5, radius, color, 0.85)


def draw_architecture(c: canvas.Canvas) -> None:
    tracking_text(c, "THE COMPUTATION BEHIND ONE LIVING CONVERSATION", 100, 1092, "PosterSansBold", 18, WHITE, 3.0)

    # Three purpose-led system moments.
    tracking_text(c, "01  CONTINUITY", 100, 1019, "PosterSansBold", 17, MIST, 2.2)
    label(c, "A LIVE TURN OUTLIVES ITS POD.", 100, 965, 34, WHITE, "PosterSerifBold")
    label(c, "KUBERNETES  /  SSE REPLAY  /  DURABLE TIMELINE", 100, 923, 15, STONE, "PosterSansBold")

    tracking_text(c, "02  ELASTICITY", 840, 718, "PosterSansBold", 17, SAGE, 2.2)
    label(c, "COGNITIVE WORK FANS OUT.", 840, 664, 34, WHITE, "PosterSerifBold")
    label(c, "OUTBOX PRESSURE  /  KEDA  /  HPA", 840, 622, 15, STONE, "PosterSansBold")

    tracking_text(c, "03  CAUSALITY", 100, 402, "PosterSansBold", 17, GOLD, 2.2)
    label(c, "ONE TURN. ONE EXPLAINABLE PATH.", 100, 348, 34, WHITE, "PosterSerifBold")
    label(c, "OPENTELEMETRY  /  W3C CONTEXT  /  PRIVACY-SAFE SPANS", 100, 306, 15, STONE, "PosterSansBold")

    # The gold trace is the single causal thread through synchronous and async work.
    curve(c, (116, 814), (285, 856), (407, 825), (546, 826), GOLD, 4.5, 0.92)
    curve(c, (546, 826), (670, 826), (766, 818), (869, 810), GOLD, 4.5, 0.92)
    curve(c, (869, 810), (1015, 808), (1195, 859), (1329, 815), GOLD, 4.5, 0.92)
    curve(c, (1329, 815), (1454, 770), (1540, 698), (1546, 550), GOLD, 4.5, 0.92)
    curve(c, (1546, 550), (1538, 468), (1455, 433), (1365, 452), GOLD, 4.5, 0.92)
    arrowhead(c, 525, 826, GOLD, 0.9)
    arrowhead(c, 854, 811, GOLD, 0.9)
    arrowhead(c, 1315, 818, GOLD, 0.9)

    # CLIENT and live API pods
    alpha_circle(c, 125, 814, 34, TERRACOTTA, 0.95)
    centered_label(c, "TURN", 125, 805, 15, WHITE)
    label(c, "CLIENT", 91, 754, 15, STONE)

    for x, y, active in ((330, 837, True), (405, 805, False), (465, 857, False)):
        alpha_circle(c, x, y, 32, MIST if active else DEEP_2, 0.98, MIST, 2)
    label(c, "API / SSE", 325, 754, 17, WHITE)
    label(c, "REDIS COORDINATION", 307, 719, 13, STONE, "PosterSansBold")

    # Replay arc and the durable timeline rails.
    curve(c, (456, 884), (535, 1006), (676, 997), (731, 894), MIST, 5, 0.9)
    curve(c, (730, 894), (683, 850), (630, 845), (556, 861), MIST, 2.5, 0.65, (6, 7))
    c.saveState()
    c.setStrokeColor(MIST)
    c.setStrokeAlpha(0.8)
    c.setLineWidth(3)
    for dy in (-18, 0, 18):
        c.line(548, 808 + dy, 737, 808 + dy)
    for x in (580, 632, 684):
        c.line(x, 783, x, 833)
    c.restoreState()
    label(c, "POSTGRES", 590, 746, 16, WHITE)
    label(c, "DURABLE TIMELINE", 548, 714, 15, STONE)

    # Transactional outbox is a queue of work, not a box.
    tiny_dot_train(c, 780, 806, 5, TERRACOTTA)
    label(c, "OUTBOX", 790, 754, 16, WHITE)

    # Worker fan-out cluster.
    origins = ((920, 816), (1020, 870), (1050, 796), (1132, 842), (1184, 780))
    for idx, (x, y) in enumerate(origins):
        curve(c, (886, 810), (918, 818), (x - 36, y), (x, y), SAGE, 2.5, 0.66)
        alpha_circle(c, x, y, 25 + (idx % 2) * 4, SAGE, 0.86)
        centered_label(c, "W", x, y - 7, 15, DEEP)
    label(c, "WORKER PODS", 1017, 742, 16, WHITE)

    # Projections form a constellation, then WakeIntent closes the loop.
    projections = (
        (1328, 908, "MEMORY", MIST),
        (1430, 835, "PROFILE", PLUM),
        (1320, 750, "CAPSULE", TERRACOTTA),
    )
    for x, y, text, color in projections:
        alpha_circle(c, x, y, 44, color, 0.9)
        centered_label(c, text, x, y - 7, 13, DEEP)
    curve(c, (1198, 830), (1245, 852), (1264, 884), (1284, 900), SAGE, 2.5, 0.65)
    curve(c, (1198, 830), (1270, 832), (1333, 833), (1385, 835), SAGE, 2.5, 0.65)
    curve(c, (1198, 830), (1252, 805), (1264, 774), (1278, 757), SAGE, 2.5, 0.65)
    alpha_circle(c, 1517, 708, 48, GOLD, 0.9)
    centered_label(c, "WAKE", 1517, 710, 14, DEEP)
    centered_label(c, "INTENT", 1517, 690, 12, DEEP)
    curve(c, (1363, 750), (1423, 750), (1451, 726), (1473, 714), GOLD, 3, 0.7)

    # A subtle return loop indicates product continuity instead of a terminal pipeline.
    curve(c, (1515, 659), (1490, 513), (1215, 474), (1092, 536), PLUM, 3, 0.52, (5, 8))
    curve(c, (1092, 536), (809, 676), (504, 644), (332, 733), PLUM, 3, 0.42, (5, 8))
    label(c, "WAKEINTENT  ->  AURORA", 1190, 481, 14, PLUM, "PosterSansBold")


def draw_header(c: canvas.Canvas) -> None:
    label(c, "INNER COSMOS", 100, 2211, 26, NUS_BLUE, "PosterSansBold")
    label(c, "AURORA  /  MEMORY  /  RESONANCE", 100, 2169, 15, WARM_GREY, "PosterSansBold")
    c.saveState()
    c.setStrokeColor(STONE)
    c.setLineWidth(1.5)
    c.line(100, 2136, PAGE_W - 100, 2136)
    c.restoreState()

    right = PAGE_W - 100
    c.setFillColor(NUS_BLUE)
    c.setFont("PosterSansBold", 16)
    c.drawRightString(right, 2214, "SWS3004  /  CLOUD COMPUTING  /  2026")
    c.setFillColor(WARM_GREY)
    c.setFont("PosterSans", 14)
    c.drawRightString(right, 2176, "Deng Boren  ·  Sang Chenyi  ·  Peng Cheng  ·  Huo Mingxian")


def build_overlay(path: Path) -> None:
    c = canvas.Canvas(str(path), pagesize=A1)
    c.setFillColor(IVORY)
    c.rect(0, FOOTER_H, PAGE_W, PAGE_H - FOOTER_H, fill=1, stroke=0)
    draw_header(c)
    draw_product_story(c)
    draw_dark_field(c)
    draw_architecture(c)
    c.showPage()
    c.save()


def merge_with_template(template: Path, overlay: Path, output: Path) -> None:
    template_reader = PdfReader(str(template))
    overlay_reader = PdfReader(str(overlay))
    base_page = template_reader.pages[0]
    base_page.scale_to(PAGE_W, PAGE_H)
    base_page.merge_page(overlay_reader.pages[0])
    writer = PdfWriter()
    writer.add_page(base_page)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as fh:
        writer.write(fh)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--template", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    register_fonts()
    with tempfile.TemporaryDirectory(prefix="inner-cosmos-poster-v2-") as tmp:
        overlay = Path(tmp) / "overlay.pdf"
        build_overlay(overlay)
        merge_with_template(args.template, overlay, args.output)
    print(args.output)


if __name__ == "__main__":
    main()
