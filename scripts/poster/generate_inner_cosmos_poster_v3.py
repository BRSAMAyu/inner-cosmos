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

IVORY = HexColor("#F6F1EA")
PAPER = HexColor("#FCFAF6")
INK = HexColor("#292523")
WARM_GREY = HexColor("#706B66")
STONE = HexColor("#D9D1C6")
NUS_BLUE = HexColor("#1C427C")
GOLD = HexColor("#C7A94F")
MIST = HexColor("#7F96A6")
SAGE = HexColor("#9EAE9B")
PLUM = HexColor("#A79AAA")
CLAY = HexColor("#BE927D")
SOFT_BLUE = HexColor("#E4EAED")
SOFT_SAGE = HexColor("#E7ECE5")
SOFT_PLUM = HexColor("#ECE7EC")
SOFT_CLAY = HexColor("#F0E4DE")


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


def label(
    c: canvas.Canvas,
    text: str,
    x: float,
    y: float,
    size: float,
    color: Color = INK,
    font: str = "PosterSans",
) -> None:
    c.setFillColor(color)
    c.setFont(font, size)
    c.drawString(x, y, text)


def right_label(
    c: canvas.Canvas,
    text: str,
    x: float,
    y: float,
    size: float,
    color: Color = INK,
    font: str = "PosterSans",
) -> None:
    c.setFillColor(color)
    c.setFont(font, size)
    c.drawRightString(x, y, text)


def centered_label(
    c: canvas.Canvas,
    text: str,
    x: float,
    y: float,
    size: float,
    color: Color = INK,
    font: str = "PosterSans",
) -> None:
    c.setFillColor(color)
    c.setFont(font, size)
    c.drawCentredString(x, y, text)


def tracking_text(
    c: canvas.Canvas,
    text: str,
    x: float,
    y: float,
    size: float,
    color: Color,
    tracking: float,
    font: str = "PosterSansBold",
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
    stroke_width: float = 1.0,
) -> None:
    c.saveState()
    c.setFillColor(fill)
    c.setFillAlpha(alpha)
    if stroke:
        c.setStrokeColor(stroke)
        c.setStrokeAlpha(min(1, alpha + 0.28))
        c.setLineWidth(stroke_width)
        do_stroke = 1
    else:
        do_stroke = 0
    c.circle(x, y, radius, fill=1, stroke=do_stroke)
    c.restoreState()


def curve(
    c: canvas.Canvas,
    start: tuple[float, float],
    cp1: tuple[float, float],
    cp2: tuple[float, float],
    end: tuple[float, float],
    color: Color,
    width: float,
    alpha: float = 1.0,
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
    p.curveTo(*cp1, *cp2, *end)
    c.drawPath(p, fill=0, stroke=1)
    c.restoreState()


def arrowhead(c: canvas.Canvas, x: float, y: float, color: Color, scale: float = 1.0) -> None:
    c.saveState()
    c.setFillColor(color)
    p = c.beginPath()
    p.moveTo(x + 10 * scale, y)
    p.lineTo(x - 8 * scale, y + 7 * scale)
    p.lineTo(x - 8 * scale, y - 7 * scale)
    p.close()
    c.drawPath(p, fill=1, stroke=0)
    c.restoreState()


def rounded_panel(
    c: canvas.Canvas,
    x: float,
    y: float,
    width: float,
    height: float,
    fill: Color,
    stroke: Color = STONE,
    radius: float = 12,
) -> None:
    c.saveState()
    c.setFillColor(fill)
    c.setStrokeColor(stroke)
    c.setLineWidth(1.2)
    c.roundRect(x, y, width, height, radius, fill=1, stroke=1)
    c.restoreState()


def section_heading(c: canvas.Canvas, number: str, title: str, x: float, y: float, line_to: float) -> None:
    label(c, number, x, y - 4, 48, GOLD, "PosterSerif")
    label(c, title, x + 46, y, 31, INK, "PosterSerifBold")
    c.saveState()
    c.setStrokeColor(STONE)
    c.setLineWidth(1.2)
    c.line(x + 46, y - 15, line_to, y - 15)
    c.restoreState()


def draw_background(c: canvas.Canvas) -> None:
    c.setFillColor(IVORY)
    c.rect(0, FOOTER_H, PAGE_W, PAGE_H - FOOTER_H, fill=1, stroke=0)

    # Sparse orbital geometry: enough to feel like Inner Cosmos, never enough to compete with content.
    c.saveState()
    c.setFillAlpha(0)
    c.setStrokeColor(GOLD)
    c.setStrokeAlpha(0.14)
    c.setLineWidth(1.5)
    c.circle(1500, 2210, 280, fill=0, stroke=1)
    c.circle(1500, 2210, 420, fill=0, stroke=1)
    c.arc(1110, 1790, 1870, 2510, 204, 105)
    c.setStrokeColor(MIST)
    c.setStrokeAlpha(0.10)
    c.circle(90, 570, 430, fill=0, stroke=1)
    c.circle(90, 570, 560, fill=0, stroke=1)
    c.restoreState()

    for x, y, r, color in (
        (1452, 2250, 6, GOLD),
        (1550, 2150, 4, PLUM),
        (1605, 2300, 5, SAGE),
        (80, 585, 5, MIST),
        (118, 480, 4, GOLD),
        (156, 665, 4, PLUM),
    ):
        alpha_circle(c, x, y, r, color, 0.75)


def draw_header(c: canvas.Canvas) -> None:
    alpha_circle(c, 105, 2264, 10, PAPER, 1, GOLD, 2)
    alpha_circle(c, 105, 2264, 3.5, GOLD, 1)
    tracking_text(c, "RESONANCE BEFORE CONNECTION", 135, 2255, 14, WARM_GREY, 4.1)
    label(c, "Inner Cosmos", 85, 2130, 78, INK, "PosterSerif")
    label(c, "An AI companion that turns everyday conversation into a user-owned map of self -", 88, 2068, 21, INK, "PosterSans")
    label(c, "then helps compatible people understand one another.", 88, 2034, 21, PLUM, "PosterSerifItalic")

    right_label(c, "SWS3004  /  2026", PAGE_W - 88, 2198, 28, PLUM, "PosterSerifBold")
    right_label(c, "NUS SOC SUMMER WORKSHOP", PAGE_W - 88, 2154, 14, WARM_GREY, "PosterSansBold")
    right_label(c, "Deng Boren  /  Sang Chenyi", PAGE_W - 88, 2107, 16, INK, "PosterSerif")
    right_label(c, "Peng Cheng  /  Huo Mingxian", PAGE_W - 88, 2075, 16, INK, "PosterSerif")

    c.saveState()
    c.setStrokeColor(GOLD)
    c.setLineWidth(2)
    c.line(85, 1995, PAGE_W - 85, 1995)
    c.restoreState()


def draw_motivation(c: canvas.Canvas) -> None:
    x = 85
    section_heading(c, "1", "Motivation", x, 1925, 603)

    label(c, "WE TALK MORE.", x, 1847, 24, WARM_GREY, "PosterSansBold")
    label(c, "WE ARE UNDERSTOOD LESS.", x, 1807, 31, INK, "PosterSerifBold")
    label(c, "PROFILES CAPTURE CLAIMS.", x, 1733, 24, WARM_GREY, "PosterSansBold")
    label(c, "CONVERSATIONS REVEAL CHANGE.", x, 1693, 29, INK, "PosterSerifBold")

    rounded_panel(c, x, 1565, 530, 88, SOFT_SAGE, SAGE, 10)
    tracking_text(c, "OUR POSITION", x + 22, 1622, 13, SAGE, 3)
    label(c, "UNDERSTANDING FIRST. CONNECTION SECOND.", x + 22, 1589, 18, INK, "PosterSerifBold")


def trust_node(
    c: canvas.Canvas,
    x: float,
    y: float,
    radius: float,
    color: Color,
    title: str,
    boundary: str,
    index: str,
    icon: str,
) -> None:
    alpha_circle(c, x, y, radius + 12, color, 0.10)
    alpha_circle(c, x, y, radius, PAPER, 1, color, 2)
    alpha_circle(c, x - radius + 8, y + radius - 8, 14, color, 0.95)
    centered_label(c, index, x - radius + 8, y + radius - 14, 11, PAPER, "PosterSansBold")
    if icon == "aurora":
        curve(c, (x - 31, y - 5), (x - 16, y + 23), (x + 14, y - 23), (x + 31, y + 4), color, 3)
        for dx in (-25, 0, 25):
            alpha_circle(c, x + dx, y + 23, 3.5, color, 0.9)
    elif icon == "cosmos":
        draw_star_icon(c, x, y, color)
    elif icon == "capsule":
        draw_capsule_icon(c, x, y, color)
    elif icon == "connection":
        draw_letter_icon(c, x, y, color)
    centered_label(c, title, x, y - radius - 38, 20, INK, "PosterSerifBold")
    centered_label(c, boundary, x, y - radius - 67, 13, color, "PosterSansBold")


def draw_design(c: canvas.Canvas) -> None:
    x = 645
    section_heading(c, "2", "Design", x, 1925, PAGE_W - 85)
    tracking_text(c, "FOUR TRUST ZONES", x, 1852, 13, WARM_GREY, 3)
    label(c, "PRIVATE", x, 1814, 19, MIST, "PosterSansBold")
    right_label(c, "OUTWARD", PAGE_W - 85, 1814, 19, CLAY, "PosterSansBold")
    curve(c, (x + 82, 1774), (910, 1810), (1290, 1748), (PAGE_W - 135, 1776), STONE, 2.5, 0.9)

    nodes = (
        (740, 1732, 54, MIST, "Aurora", "PRIVATE DIALOGUE", "1", "aurora"),
        (1000, 1732, 60, SAGE, "Inner Cosmos", "USER-OWNED", "2", "cosmos"),
        (1270, 1732, 54, PLUM, "Echo Capsule", "AUTHORIZED", "3", "capsule"),
        (1510, 1732, 58, CLAY, "Connection", "CONSENTED", "4", "connection"),
    )
    for node in nodes:
        trust_node(c, *node)
    for x_arrow in (870, 1135, 1398):
        arrowhead(c, x_arrow, 1777, GOLD, 0.7)


def draw_pause_icon(c: canvas.Canvas, x: float, y: float, color: Color) -> None:
    alpha_circle(c, x, y, 34, PAPER, 1, color, 2)
    c.setFillColor(color)
    c.rect(x - 11, y - 13, 7, 26, fill=1, stroke=0)
    c.rect(x + 4, y - 13, 7, 26, fill=1, stroke=0)


def draw_edit_icon(c: canvas.Canvas, x: float, y: float, color: Color) -> None:
    alpha_circle(c, x, y, 34, PAPER, 1, color, 2)
    c.saveState()
    c.setStrokeColor(color)
    c.setLineWidth(4)
    c.line(x - 13, y - 12, x + 13, y + 14)
    c.line(x - 15, y - 17, x - 7, y - 15)
    c.line(x + 10, y + 16, x + 15, y + 11)
    c.restoreState()


def draw_star_icon(c: canvas.Canvas, x: float, y: float, color: Color) -> None:
    nodes = ((-25, 8), (0, 28), (28, 12), (18, -22), (-15, -27), (0, 0))
    c.saveState()
    c.setStrokeColor(color)
    c.setStrokeAlpha(0.75)
    c.setLineWidth(2)
    for a, b in ((0, 1), (1, 2), (2, 5), (5, 3), (3, 4), (4, 0)):
        c.line(x + nodes[a][0], y + nodes[a][1], x + nodes[b][0], y + nodes[b][1])
    c.restoreState()
    for dx, dy in nodes:
        alpha_circle(c, x + dx, y + dy, 5 if (dx, dy) != (0, 0) else 9, color, 0.9)


def draw_capsule_icon(c: canvas.Canvas, x: float, y: float, color: Color) -> None:
    alpha_circle(c, x - 18, y, 30, MIST, 0.30, MIST, 1.5)
    alpha_circle(c, x + 18, y, 30, color, 0.30, color, 1.5)
    alpha_circle(c, x, y, 12, GOLD, 0.75)


def draw_letter_icon(c: canvas.Canvas, x: float, y: float, color: Color) -> None:
    c.saveState()
    c.setFillColor(PAPER)
    c.setStrokeColor(color)
    c.setLineWidth(2)
    c.roundRect(x - 39, y - 25, 78, 50, 5, fill=1, stroke=1)
    c.line(x - 37, y + 21, x, y - 5)
    c.line(x + 37, y + 21, x, y - 5)
    c.restoreState()
    alpha_circle(c, x + 39, y - 23, 11, GOLD, 0.85)


def feature_node(
    c: canvas.Canvas,
    x: float,
    y: float,
    radius: float,
    color: Color,
    title: str,
    icon: str,
) -> None:
    alpha_circle(c, x, y, radius + 14, color, 0.09)
    alpha_circle(c, x, y, radius, PAPER, 1, color, 1.7)
    if icon == "pause":
        draw_pause_icon(c, x, y, color)
    elif icon == "edit":
        draw_edit_icon(c, x, y, color)
    elif icon == "star":
        draw_star_icon(c, x, y, color)
    elif icon == "capsule":
        draw_capsule_icon(c, x, y, color)
    elif icon == "letter":
        draw_letter_icon(c, x, y, color)
    centered_label(c, title, x, y - radius - 42, 18, INK, "PosterSerifBold")


def draw_features(c: canvas.Canvas) -> None:
    section_heading(c, "3", "Features", 85, 1490, PAGE_W - 85)
    tracking_text(c, "EXPERIENCE LAYER", 85, 1424, 13, WARM_GREY, 3)

    path_y = 1303
    curve(c, (150, path_y), (430, 1365), (720, 1240), (915, path_y), GOLD, 2, 0.55)
    curve(c, (915, path_y), (1120, 1375), (1360, 1240), (1530, path_y), GOLD, 2, 0.55)
    feature_node(c, 195, 1312, 62, MIST, "Interruptible Aurora", "pause")
    feature_node(c, 500, 1288, 73, SAGE, "Correctable Self", "edit")
    feature_node(c, 820, 1323, 66, GOLD, "Living Starfield", "star")
    feature_node(c, 1145, 1288, 78, PLUM, "Authorized Capsule", "capsule")
    feature_node(c, 1480, 1320, 66, CLAY, "Slow Connection", "letter")

    tracking_text(c, "CLOUD-NATIVE FOUNDATION", 85, 1131, 13, WARM_GREY, 3)
    c.saveState()
    c.setStrokeColor(STONE)
    c.setLineWidth(2)
    c.line(85, 1074, PAGE_W - 85, 1074)
    c.restoreState()
    foundations = (
        (330, "TURN CONTINUITY", "KUBERNETES / SSE"),
        (840, "WORKLOAD-AWARE SCALE", "OUTBOX / KEDA / HPA"),
        (1350, "CAUSAL OBSERVABILITY", "OPENTELEMETRY / W3C"),
    )
    for idx, (x, title, tech) in enumerate(foundations):
        color = (MIST, SAGE, PLUM)[idx]
        alpha_circle(c, x, 1074, 11, color, 0.95)
        centered_label(c, title, x, 1027, 18, INK, "PosterSansBold")
        centered_label(c, tech, x, 997, 13, color, "PosterSansBold")


def draw_chat_scene(c: canvas.Canvas, x: float, y: float) -> None:
    alpha_circle(c, x, y, 102, MIST, 0.12)
    c.saveState()
    c.setFillColor(PAPER)
    c.setStrokeColor(MIST)
    c.setLineWidth(2)
    c.roundRect(x - 70, y + 12, 92, 49, 15, fill=1, stroke=1)
    c.roundRect(x - 15, y - 57, 90, 49, 15, fill=1, stroke=1)
    c.restoreState()
    for dx in (-46, -24, -2):
        alpha_circle(c, x + dx, y + 36, 4, MIST, 0.9)
    curve(c, (x - 54, y - 17), (x - 25, y + 10), (x + 26, y - 12), (x + 57, y + 8), NUS_BLUE, 3)


def draw_cosmos_scene(c: canvas.Canvas, x: float, y: float) -> None:
    alpha_circle(c, x, y, 105, SAGE, 0.12)
    center = (x, y)
    satellites = (
        (-65, 34, "FACT", MIST),
        (-18, 75, "EMOTION", CLAY),
        (67, 35, "BELIEF", PLUM),
        (57, -52, "GOAL", GOLD),
        (-53, -60, "NEED", SAGE),
    )
    c.saveState()
    c.setStrokeColor(STONE)
    c.setLineWidth(1.7)
    for dx, dy, _, _ in satellites:
        c.line(center[0], center[1], x + dx, y + dy)
    c.restoreState()
    alpha_circle(c, x, y, 29, NUS_BLUE, 0.95)
    for dx, dy, _, color in satellites:
        alpha_circle(c, x + dx, y + dy, 12, color, 0.95)
    for dx, dy, text, color in satellites:
        centered_label(c, text, x + dx, y + dy - 29, 9, color, "PosterSansBold")
    draw_edit_icon(c, x + 82, y - 82, SAGE)


def draw_capsule_scene(c: canvas.Canvas, x: float, y: float) -> None:
    alpha_circle(c, x, y, 105, PLUM, 0.11)
    alpha_circle(c, x - 25, y + 2, 60, MIST, 0.28, MIST, 1.5)
    alpha_circle(c, x + 27, y - 3, 60, PLUM, 0.28, PLUM, 1.5)
    alpha_circle(c, x + 1, y, 24, GOLD, 0.75)
    c.saveState()
    c.setStrokeColor(SAGE)
    c.setLineWidth(3)
    c.arc(x - 88, y - 88, x + 88, y + 88, 225, 95)
    c.restoreState()
    label(c, "CONSENT", x - 44, y - 91, 11, SAGE, "PosterSansBold")


def draw_connection_scene(c: canvas.Canvas, x: float, y: float) -> None:
    alpha_circle(c, x, y, 105, CLAY, 0.11)
    alpha_circle(c, x - 60, y - 12, 27, MIST, 0.9)
    alpha_circle(c, x + 60, y - 12, 27, SAGE, 0.9)
    c.saveState()
    c.setFillColor(PAPER)
    c.setStrokeColor(CLAY)
    c.setLineWidth(2)
    c.roundRect(x - 42, y - 17, 84, 58, 6, fill=1, stroke=1)
    c.line(x - 40, y + 36, x, y + 7)
    c.line(x + 40, y + 36, x, y + 7)
    c.restoreState()
    curve(c, (x - 92, y - 48), (x - 52, y - 85), (x + 55, y - 85), (x + 92, y - 48), GOLD, 2.5, 0.75, (5, 7))


def draw_how_it_works(c: canvas.Canvas) -> None:
    section_heading(c, "4", "How Inner Cosmos Works", 85, 910, PAGE_W - 85)

    y = 575
    xs = (235, 645, 1055, 1460)
    curve(c, (xs[0] + 106, y), (435, 632), (480, 517), (xs[1] - 108, y), MIST, 3, 0.65)
    curve(c, (xs[1] + 108, y), (835, 517), (875, 632), (xs[2] - 108, y), SAGE, 3, 0.65)
    curve(c, (xs[2] + 108, y), (1245, 637), (1280, 517), (xs[3] - 108, y), PLUM, 3, 0.65)
    for x_arrow in (440, 850, 1265):
        arrowhead(c, x_arrow, y, GOLD, 0.75)

    draw_chat_scene(c, xs[0], y)
    draw_cosmos_scene(c, xs[1], y)
    draw_capsule_scene(c, xs[2], y)
    draw_connection_scene(c, xs[3], y)

    stages = (
        ("01", "AURORA", "DIALOGUE", xs[0], MIST),
        ("02", "INNER COSMOS", "MEMORY / FRAGMENTS / GRAVITY", xs[1], SAGE),
        ("03", "ECHO CAPSULE", "AUTHORIZED SELF-PROJECTION", xs[2], PLUM),
        ("04", "HUMAN CONNECTION", "RESONANCE / SLOW LETTER / CONSENT", xs[3], CLAY),
    )
    for index, title, vocabulary, x, color in stages:
        centered_label(c, index, x, 407, 13, GOLD, "PosterSansBold")
        centered_label(c, title, x, 368, 22, INK, "PosterSerifBold")
        centered_label(c, vocabulary, x, 335, 12, color, "PosterSansBold")

    # Privacy is visible as a boundary, not explained in a paragraph.
    c.saveState()
    c.setStrokeColor(MIST)
    c.setStrokeAlpha(0.55)
    c.setLineWidth(1.8)
    c.setDash(7, 7)
    c.roundRect(90, 285, 775, 440, 70, fill=0, stroke=1)
    c.restoreState()
    label(c, "PRIVATE + USER-OWNED", 112, 298, 12, MIST, "PosterSansBold")
    arrowhead(c, 906, 575, GOLD, 0.75)
    label(c, "AUTHORIZE", 876, 604, 12, GOLD, "PosterSansBold")


def build_overlay(path: Path) -> None:
    c = canvas.Canvas(str(path), pagesize=A1)
    draw_background(c)
    draw_header(c)
    draw_motivation(c)
    draw_design(c)
    draw_features(c)
    draw_how_it_works(c)
    c.showPage()
    c.save()


def merge_with_template(template: Path, overlay: Path, output: Path) -> None:
    template_reader = PdfReader(str(template))
    overlay_reader = PdfReader(str(overlay))
    base = template_reader.pages[0]
    base.scale_to(PAGE_W, PAGE_H)
    base.merge_page(overlay_reader.pages[0])
    writer = PdfWriter()
    writer.add_page(base)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as stream:
        writer.write(stream)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--template", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    register_fonts()
    with tempfile.TemporaryDirectory(prefix="inner-cosmos-poster-v3-") as temp_dir:
        overlay = Path(temp_dir) / "overlay.pdf"
        build_overlay(overlay)
        merge_with_template(args.template, overlay, args.output)
    print(args.output)


if __name__ == "__main__":
    main()
