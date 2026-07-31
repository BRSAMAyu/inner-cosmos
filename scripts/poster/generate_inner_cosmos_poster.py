from __future__ import annotations

import argparse
import math
import tempfile
from pathlib import Path

from pypdf import PdfReader, PdfWriter
from reportlab.graphics.barcode.qr import QrCodeWidget
from reportlab.graphics.shapes import Drawing
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
MIST_BLUE = HexColor("#71869A")
SAGE = HexColor("#9EAE9B")
PLUM = HexColor("#A69AAA")
TERRACOTTA = HexColor("#BE927D")
GOLD = HexColor("#C6A85E")
SOFT_BLUE = HexColor("#E5EAED")
SOFT_SAGE = HexColor("#E8ECE6")
SOFT_PLUM = HexColor("#ECE8ED")
SOFT_TERRACOTTA = HexColor("#F0E5DF")


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


def text_width(text: str, font: str, size: float) -> float:
    return pdfmetrics.stringWidth(text, font, size)


def draw_tracking_text(
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
        cursor += text_width(ch, font, size) + tracking


def wrap_lines(text: str, font: str, size: float, width: float) -> list[str]:
    words = text.split()
    if not words:
        return []
    lines: list[str] = []
    current = words[0]
    for word in words[1:]:
        candidate = f"{current} {word}"
        if text_width(candidate, font, size) <= width:
            current = candidate
        else:
            lines.append(current)
            current = word
    lines.append(current)
    return lines


def draw_wrapped(
    c: canvas.Canvas,
    text: str,
    x: float,
    y: float,
    width: float,
    font: str,
    size: float,
    color: Color,
    leading: float | None = None,
    max_lines: int | None = None,
) -> float:
    lines = wrap_lines(text, font, size, width)
    if max_lines is not None:
        lines = lines[:max_lines]
    line_height = leading or size * 1.28
    c.setFont(font, size)
    c.setFillColor(color)
    for line in lines:
        c.drawString(x, y, line)
        y -= line_height
    return y


def rounded_card(
    c: canvas.Canvas,
    x: float,
    y: float,
    w: float,
    h: float,
    fill: Color,
    stroke: Color = STONE,
    radius: float = 24,
    line_width: float = 1.5,
) -> None:
    c.setFillColor(fill)
    c.setStrokeColor(stroke)
    c.setLineWidth(line_width)
    c.roundRect(x, y, w, h, radius, fill=1, stroke=1)


def draw_arrow(
    c: canvas.Canvas,
    x1: float,
    y1: float,
    x2: float,
    y2: float,
    color: Color = NUS_BLUE,
    width: float = 4,
) -> None:
    c.setStrokeColor(color)
    c.setFillColor(color)
    c.setLineWidth(width)
    c.setLineCap(1)
    c.line(x1, y1, x2, y2)
    angle = math.atan2(y2 - y1, x2 - x1)
    head = 13
    wing = 7
    points = [
        (x2, y2),
        (
            x2 - head * math.cos(angle) + wing * math.sin(angle),
            y2 - head * math.sin(angle) - wing * math.cos(angle),
        ),
        (
            x2 - head * math.cos(angle) - wing * math.sin(angle),
            y2 - head * math.sin(angle) + wing * math.cos(angle),
        ),
    ]
    path = c.beginPath()
    path.moveTo(*points[0])
    path.lineTo(*points[1])
    path.lineTo(*points[2])
    path.close()
    c.drawPath(path, fill=1, stroke=0)


def draw_speech_icon(c: canvas.Canvas, cx: float, cy: float, color: Color) -> None:
    c.setFillColor(PAPER)
    c.setStrokeColor(color)
    c.setLineWidth(4)
    c.roundRect(cx - 80, cy - 43, 150, 90, 26, fill=1, stroke=1)
    tail = c.beginPath()
    tail.moveTo(cx - 28, cy - 43)
    tail.lineTo(cx - 50, cy - 67)
    tail.lineTo(cx - 5, cy - 45)
    tail.close()
    c.drawPath(tail, fill=1, stroke=1)
    c.setFillColor(color)
    for dx in (-38, 0, 38):
        c.circle(cx + dx, cy + 2, 6, fill=1, stroke=0)
    c.setFillColor(GOLD)
    c.circle(cx + 61, cy + 59, 22, fill=1, stroke=0)
    c.setStrokeColor(HexColor("#E8D9AE"))
    c.setLineWidth(3)
    c.circle(cx + 61, cy + 59, 33, fill=0, stroke=1)


def draw_starfield_icon(c: canvas.Canvas, cx: float, cy: float, color: Color) -> None:
    c.setStrokeColor(color)
    c.setLineWidth(2.5)
    c.circle(cx, cy, 86, fill=0, stroke=1)
    c.setStrokeColor(STONE)
    c.circle(cx, cy, 55, fill=0, stroke=1)
    stars = [
        (-45, 22, 13, GOLD),
        (28, 50, 9, color),
        (46, -17, 15, SAGE),
        (-17, -46, 8, PLUM),
        (0, 3, 20, TERRACOTTA),
    ]
    for dx, dy, radius, fill in stars:
        c.setFillColor(fill)
        c.circle(cx + dx, cy + dy, radius, fill=1, stroke=0)
    c.setStrokeColor(color)
    c.setLineWidth(3)
    c.arc(cx - 106, cy - 63, cx + 106, cy + 63, 200, 145)
    c.setFillColor(color)
    c.circle(cx + 80, cy - 31, 5, fill=1, stroke=0)


def draw_capsule_icon(c: canvas.Canvas, cx: float, cy: float, color: Color) -> None:
    c.setStrokeColor(color)
    c.setLineWidth(4)
    c.setFillColor(PAPER)
    c.roundRect(cx - 93, cy - 58, 120, 132, 60, fill=1, stroke=1)
    c.setFillColor(SOFT_PLUM)
    c.roundRect(cx - 8, cy - 70, 112, 132, 56, fill=1, stroke=1)
    c.setFillColor(color)
    c.circle(cx - 33, cy + 26, 14, fill=1, stroke=0)
    c.setStrokeColor(color)
    c.setLineWidth(3)
    c.arc(cx - 62, cy - 31, cx - 4, cy + 14, 8, 164)
    c.setFillColor(TERRACOTTA)
    c.circle(cx + 48, cy + 18, 13, fill=1, stroke=0)
    c.setStrokeColor(TERRACOTTA)
    c.arc(cx + 20, cy - 39, cx + 78, cy + 6, 8, 164)
    c.setStrokeColor(GOLD)
    c.setLineWidth(2.5)
    c.line(cx - 5, cy + 65, cx + 20, cy + 45)
    c.line(cx + 17, cy + 67, cx + 36, cy + 48)


def draw_letter_icon(c: canvas.Canvas, cx: float, cy: float, color: Color) -> None:
    c.setFillColor(PAPER)
    c.setStrokeColor(color)
    c.setLineWidth(4)
    c.roundRect(cx - 88, cy - 56, 176, 112, 18, fill=1, stroke=1)
    path = c.beginPath()
    path.moveTo(cx - 83, cy + 46)
    path.lineTo(cx, cy - 8)
    path.lineTo(cx + 83, cy + 46)
    c.drawPath(path, fill=0, stroke=1)
    c.setStrokeColor(TERRACOTTA)
    c.setLineWidth(3)
    c.setDash(6, 8)
    c.bezier(cx - 118, cy + 82, cx - 30, cy + 145, cx + 76, cy + 127, cx + 126, cy + 84)
    c.setDash()
    c.setFillColor(TERRACOTTA)
    c.circle(cx + 126, cy + 84, 7, fill=1, stroke=0)


def draw_product_stage(
    c: canvas.Canvas,
    x: float,
    y: float,
    w: float,
    label: str,
    title: str,
    description: str,
    tint: Color,
    accent: Color,
    icon: str,
) -> None:
    rounded_card(c, x, y, w, 400, tint, stroke=Color(accent.red, accent.green, accent.blue, alpha=0.35), radius=28)
    cx = x + w / 2
    cy = y + 254
    if icon == "speech":
        draw_speech_icon(c, cx, cy, accent)
    elif icon == "stars":
        draw_starfield_icon(c, cx, cy, accent)
    elif icon == "capsule":
        draw_capsule_icon(c, cx, cy, accent)
    elif icon == "letter":
        draw_letter_icon(c, cx, cy, accent)
    draw_tracking_text(c, label, x + 28, y + 125, "PosterSansBold", 18, accent, 2.5)
    c.setFillColor(INK)
    c.setFont("PosterSerifBold", 32)
    c.drawString(x + 28, y + 78, title)
    draw_wrapped(c, description, x + 28, y + 40, w - 56, "PosterSans", 20, WARM_GREY, 26, 2)


def draw_arch_node(
    c: canvas.Canvas,
    x: float,
    y: float,
    w: float,
    h: float,
    title: str,
    subtitle: str,
    fill: Color,
    accent: Color,
) -> None:
    rounded_card(c, x, y, w, h, fill, stroke=Color(accent.red, accent.green, accent.blue, alpha=0.45), radius=18, line_width=1.7)
    c.setFillColor(accent)
    c.setFont("PosterSansBold", 21)
    c.drawCentredString(x + w / 2, y + h / 2 + 10, title)
    c.setFillColor(WARM_GREY)
    c.setFont("PosterSans", 15)
    c.drawCentredString(x + w / 2, y + h / 2 - 18, subtitle)


def draw_continuity_card(c: canvas.Canvas, x: float, y: float, w: float, h: float) -> None:
    rounded_card(c, x, y, w, h, SOFT_BLUE, stroke=MIST_BLUE, radius=26)
    draw_tracking_text(c, "CONTINUITY", x + 28, y + h - 46, "PosterSansBold", 17, MIST_BLUE, 2.2)
    c.setFont("PosterSerifBold", 29)
    c.setFillColor(NUS_BLUE)
    c.drawCentredString(x + w * 0.25, y + h - 111, "POD LOST")
    c.drawCentredString(x + w * 0.75, y + h - 111, "TURN SAVED")
    c.setStrokeColor(Color(MIST_BLUE.red, MIST_BLUE.green, MIST_BLUE.blue, alpha=0.35))
    c.setLineWidth(2)
    c.line(x + w / 2, y + h - 161, x + w / 2, y + h - 86)
    cross_x = x + w * 0.25
    mark_y = y + h - 153
    c.setFillColor(TERRACOTTA)
    c.setStrokeColor(TERRACOTTA)
    c.setLineWidth(7)
    c.line(cross_x - 10, mark_y - 10, cross_x + 10, mark_y + 10)
    c.line(cross_x - 10, mark_y + 10, cross_x + 10, mark_y - 10)
    check_x = x + w * 0.75
    c.setStrokeColor(SAGE)
    c.setLineWidth(7)
    c.line(check_x - 14, mark_y, check_x - 2, mark_y - 12)
    c.line(check_x - 2, mark_y - 12, check_x + 20, mark_y + 14)
    c.setStrokeColor(MIST_BLUE)
    c.setLineWidth(4)
    c.line(x + 36, y + 103, x + w - 36, y + 103)
    for px, r, fill in (
        (x + 63, 9, MIST_BLUE),
        (x + w * 0.45, 9, TERRACOTTA),
        (x + w - 63, 9, SAGE),
    ):
        c.setFillColor(fill)
        c.circle(px, y + 103, r, fill=1, stroke=0)
    c.setFont("PosterSansBold", 22)
    c.setFillColor(INK)
    c.drawString(x + 28, y + 57, "1.233 s graceful")
    c.drawRightString(x + w - 28, y + 57, "16.677 s hard-kill")
    c.setFont("PosterSans", 18)
    c.setFillColor(WARM_GREY)
    c.drawString(x + 28, y + 26, "Resume from a durable timeline")


def draw_elasticity_card(c: canvas.Canvas, x: float, y: float, w: float, h: float) -> None:
    rounded_card(c, x, y, w, h, SOFT_SAGE, stroke=SAGE, radius=26)
    draw_tracking_text(c, "ELASTICITY", x + 28, y + h - 46, "PosterSansBold", 17, HexColor("#71846E"), 2.2)
    number_y = y + h - 122
    c.setFont("PosterSerifBold", 55)
    c.setFillColor(NUS_BLUE)
    number_xs = (x + 68, x + w / 2, x + w - 68)
    for value, number_x in zip(("1", "3", "6"), number_xs):
        c.drawCentredString(number_x, number_y, value)
    draw_arrow(c, number_xs[0] + 28, number_y + 23, number_xs[1] - 32, number_y + 23, NUS_BLUE, 3)
    draw_arrow(c, number_xs[1] + 28, number_y + 23, number_xs[2] - 32, number_y + 23, NUS_BLUE, 3)
    chart_x = x + 31
    chart_y = y + 82
    chart_w = w - 62
    chart_h = 88
    c.setStrokeColor(STONE)
    c.setLineWidth(2)
    c.line(chart_x, chart_y, chart_x + chart_w, chart_y)
    c.setFillColor(SAGE)
    bar_w = 26
    for i, height in enumerate((22, 45, 80, 55, 28)):
        c.roundRect(chart_x + i * 43, chart_y, bar_w, height, 6, fill=1, stroke=0)
    c.setStrokeColor(TERRACOTTA)
    c.setLineWidth(4)
    points = [
        (chart_x, chart_y + chart_h),
        (chart_x + chart_w * 0.23, chart_y + chart_h * 0.8),
        (chart_x + chart_w * 0.55, chart_y + chart_h * 0.42),
        (chart_x + chart_w, chart_y + 4),
    ]
    path = c.beginPath()
    path.moveTo(*points[0])
    for p in points[1:]:
        path.lineTo(*p)
    c.drawPath(path, fill=0, stroke=1)
    c.setFont("PosterSansBold", 22)
    c.setFillColor(INK)
    c.drawString(x + 28, y + 48, "3,000 events to 0")
    c.drawRightString(x + w - 28, y + 48, "duplicates 0")
    c.setFont("PosterSans", 18)
    c.setFillColor(WARM_GREY)
    c.drawString(x + 28, y + 20, "Scale on backlog, not just CPU")


def draw_trace_card(c: canvas.Canvas, x: float, y: float, w: float, h: float) -> None:
    rounded_card(c, x, y, w, h, SOFT_PLUM, stroke=PLUM, radius=26)
    draw_tracking_text(c, "OBSERVABILITY", x + 28, y + h - 46, "PosterSansBold", 17, HexColor("#7F7283"), 2.2)
    c.setFont("PosterSerifBold", 43)
    c.setFillColor(NUS_BLUE)
    c.drawString(x + 28, y + h - 110, "8 spans · 2 services")
    nodes = [
        (x + 65, y + 129, MIST_BLUE),
        (x + 157, y + 129, PLUM),
        (x + 249, y + 158, TERRACOTTA),
        (x + 249, y + 101, SAGE),
        (x + 340, y + 129, GOLD),
    ]
    c.setStrokeColor(PLUM)
    c.setLineWidth(4)
    for a, b in zip(nodes, nodes[1:2] + nodes[2:3]):
        c.line(a[0], a[1], b[0], b[1])
    c.line(nodes[1][0], nodes[1][1], nodes[2][0], nodes[2][1])
    c.line(nodes[1][0], nodes[1][1], nodes[3][0], nodes[3][1])
    c.line(nodes[2][0], nodes[2][1], nodes[4][0], nodes[4][1])
    c.line(nodes[3][0], nodes[3][1], nodes[4][0], nodes[4][1])
    for nx, ny, fill in nodes:
        c.setFillColor(fill)
        c.circle(nx, ny, 12, fill=1, stroke=0)
    c.setFont("PosterSansBold", 22)
    c.setFillColor(INK)
    c.drawString(x + 28, y + 49, "0 forbidden privacy tags")
    c.setFont("PosterSans", 18)
    c.setFillColor(WARM_GREY)
    c.drawString(x + 28, y + 20, "Trace one turn across async work")


def draw_qr(c: canvas.Canvas, url: str, x: float, y: float, size: float) -> None:
    widget = QrCodeWidget(url)
    bounds = widget.getBounds()
    bw = bounds[2] - bounds[0]
    bh = bounds[3] - bounds[1]
    drawing = Drawing(size, size, transform=[size / bw, 0, 0, size / bh, 0, 0])
    drawing.add(widget)
    drawing.drawOn(c, x, y)


def draw_overlay(path: Path, project_url: str) -> None:
    c = canvas.Canvas(str(path), pagesize=A1)
    c.setTitle("Inner Cosmos - Cloud-Native Architecture for AI That Persists Through Time")
    c.setAuthor("Deng Boren, Sang Chenyi, Peng Cheng, Huo Mingxian")

    c.setFillColor(IVORY)
    c.rect(0, FOOTER_H + 0.5, PAGE_W, PAGE_H - FOOTER_H, fill=1, stroke=0)

    margin = 96
    content_w = PAGE_W - margin * 2

    # Quiet ambient geometry, deliberately lighter than body text.
    c.setStrokeColor(HexColor("#E4DED4"))
    c.setLineWidth(2)
    c.arc(PAGE_W - 410, PAGE_H - 260, PAGE_W + 180, PAGE_H + 300, 174, 112)
    c.arc(-160, 1450, 360, 1970, 282, 117)
    c.setFillColor(GOLD)
    for px, py, r in (
        (86, 2260, 4),
        (1510, 2250, 5),
        (1574, 1745, 3),
        (92, 1510, 3),
        (1540, 690, 4),
    ):
        c.circle(px, py, r, fill=1, stroke=0)

    # Header.
    draw_tracking_text(
        c,
        "RESONANCE BEFORE CONNECTION",
        margin,
        PAGE_H - 92,
        "PosterSansBold",
        19,
        HexColor("#8D7652"),
        4.1,
    )
    c.setFont("PosterSerif", 101)
    c.setFillColor(INK)
    c.drawString(margin, PAGE_H - 205, "Inner Cosmos")
    c.setFont("PosterSerifItalic", 35)
    c.setFillColor(NUS_BLUE)
    c.drawString(margin + 6, PAGE_H - 260, "AI that remembers. Infrastructure that keeps the promise.")

    right_x = PAGE_W - margin
    c.setFillColor(NUS_BLUE)
    c.setFont("PosterSansBold", 23)
    c.drawRightString(right_x, PAGE_H - 120, "SWS3004 · 2026")
    draw_tracking_text(
        c,
        "NUS SOC SUMMER WORKSHOP",
        PAGE_W - 545,
        PAGE_H - 158,
        "PosterSansBold",
        15,
        HexColor("#8D7652"),
        3.0,
    )
    c.setFont("PosterSans", 19)
    c.setFillColor(WARM_GREY)
    c.drawRightString(right_x, PAGE_H - 197, "Deng Boren · Sang Chenyi")
    c.drawRightString(right_x, PAGE_H - 225, "Peng Cheng · Huo Mingxian")

    # Product thesis.
    section_top = PAGE_H - 350
    draw_tracking_text(c, "THE APPLICATION", margin, section_top, "PosterSansBold", 17, TERRACOTTA, 3.4)
    c.setFillColor(INK)
    c.setFont("PosterSerifBold", 46)
    c.drawString(margin, section_top - 66, "One conversation becomes a path to understanding.")
    c.setFont("PosterSerifItalic", 36)
    c.setFillColor(NUS_BLUE)
    c.drawString(margin, section_top - 116, "Only then, outward to other people.")
    draw_wrapped(
        c,
        "Aurora turns everyday expression into a user-owned, correctable inner model; only explicit consent carries it outward.",
        margin,
        section_top - 162,
        content_w * 0.75,
        "PosterSans",
        24,
        WARM_GREY,
        32,
        2,
    )

    stage_y = 1392
    gap = 24
    stage_w = (content_w - gap * 3) / 4
    stages = [
        ("01 · TALK", "Aurora", "Listens, pauses and can be interrupted.", SOFT_TERRACOTTA, TERRACOTTA, "speech"),
        ("02 · UNDERSTAND", "Inner Cosmos", "Memories stay traceable and correctable.", SOFT_SAGE, HexColor("#71846E"), "stars"),
        ("03 · RESONATE", "Echo Capsules", "Authorized data becomes a bounded identity.", SOFT_PLUM, HexColor("#7F7283"), "capsule"),
        ("04 · CONNECT", "Slow Social", "A letter opens contact at a human pace.", SOFT_BLUE, MIST_BLUE, "letter"),
    ]
    for i, stage in enumerate(stages):
        x = margin + i * (stage_w + gap)
        draw_product_stage(c, x, stage_y, stage_w, *stage)
        if i < 3:
            draw_arrow(c, x + stage_w + 5, stage_y + 204, x + stage_w + gap - 5, stage_y + 204, GOLD, 3)

    # Transition line.
    transition_y = 1334
    c.setStrokeColor(STONE)
    c.setLineWidth(2)
    c.line(margin, transition_y, PAGE_W - margin, transition_y)
    c.setFillColor(IVORY)
    c.rect(PAGE_W / 2 - 218, transition_y - 18, 436, 36, fill=1, stroke=0)
    c.setFont("PosterSansBold", 18)
    c.setFillColor(NUS_BLUE)
    c.drawCentredString(PAGE_W / 2, transition_y - 6, "LONG-LIVED AI WORK NEEDS LONG-LIVED SYSTEM SEMANTICS")

    # Cloud-native section.
    cloud_top = 1260
    draw_tracking_text(c, "THE CLOUD-NATIVE LAYER", margin, cloud_top, "PosterSansBold", 17, NUS_BLUE, 3.4)
    c.setFillColor(INK)
    c.setFont("PosterSerifBold", 46)
    c.drawString(margin, cloud_top - 66, "Kubernetes restores the service.")
    c.setFillColor(NUS_BLUE)
    c.drawString(margin, cloud_top - 116, "The application restores the relationship.")
    c.setFont("PosterSans", 24)
    c.setFillColor(WARM_GREY)
    c.drawString(margin, cloud_top - 161, "One signed application artifact runs as independently scalable roles around durable state.")

    arch_y = 970
    arch_h = 112
    nodes = [
        (margin, 195, "PWA / MOBILE", "React 19", PAPER, MIST_BLUE),
        (margin + 240, 190, "API x2", "Spring + SSE", SOFT_BLUE, MIST_BLUE),
        (margin + 475, 250, "POSTGRES + REDIS", "truth + coordination", SOFT_SAGE, HexColor("#71846E")),
        (margin + 770, 160, "OUTBOX", "durable work", SOFT_TERRACOTTA, TERRACOTTA),
        (margin + 975, 175, "WORKERS", "1 to N", SOFT_PLUM, HexColor("#7F7283")),
        (margin + 1195, 292, "MEMORY · PROFILE", "CAPSULE · WAKE", PAPER, NUS_BLUE),
    ]
    for i, (nx, nw, title, subtitle, fill, accent) in enumerate(nodes):
        draw_arch_node(c, nx, arch_y, nw, arch_h, title, subtitle, fill, accent)
        if i < len(nodes) - 1:
            next_x = nodes[i + 1][0]
            draw_arrow(c, nx + nw + 8, arch_y + arch_h / 2, next_x - 8, arch_y + arch_h / 2, NUS_BLUE, 3)

    # Evidence cards.
    cards_y = 617
    cards_h = 330
    cards_gap = 25
    card_w = (content_w - cards_gap * 2) / 3
    draw_continuity_card(c, margin, cards_y, card_w, cards_h)
    draw_elasticity_card(c, margin + card_w + cards_gap, cards_y, card_w, cards_h)
    draw_trace_card(c, margin + (card_w + cards_gap) * 2, cards_y, card_w, cards_h)

    # Controlled-change strip and compact evidence boundary.
    strip_y = 490
    rounded_card(c, margin, strip_y, content_w - 220, 84, PAPER, stroke=STONE, radius=20)
    c.setFillColor(TERRACOTTA)
    c.circle(margin + 35, strip_y + 42, 10, fill=1, stroke=0)
    c.setFillColor(INK)
    c.setFont("PosterSansBold", 21)
    c.drawString(margin + 58, strip_y + 35, "KYVERNO")
    c.setFont("PosterSans", 19)
    c.setFillColor(WARM_GREY)
    c.drawString(margin + 170, strip_y + 35, "unsafe workloads rejected")
    c.setStrokeColor(STONE)
    c.line(margin + 485, strip_y + 18, margin + 485, strip_y + 66)
    c.setFillColor(NUS_BLUE)
    c.circle(margin + 525, strip_y + 42, 10, fill=1, stroke=0)
    c.setFillColor(INK)
    c.setFont("PosterSansBold", 21)
    c.drawString(margin + 548, strip_y + 35, "ARGO ROLLOUTS")
    c.setFont("PosterSans", 19)
    c.setFillColor(WARM_GREY)
    c.drawString(margin + 735, strip_y + 35, "degrading releases rolled back")

    qr_x = PAGE_W - margin - 174
    qr_y = 386
    rounded_card(c, qr_x - 18, qr_y - 18, 210, 188, PAPER, stroke=STONE, radius=18)
    draw_qr(c, project_url, qr_x, qr_y + 18, 150)
    c.setFont("PosterSansBold", 14)
    c.setFillColor(NUS_BLUE)
    c.drawCentredString(qr_x + 75, qr_y - 2, "EXPLORE THE PROJECT")

    draw_tracking_text(c, "VERIFIED EVIDENCE", margin, 420, "PosterSansBold", 15, HexColor("#8D7652"), 2.5)
    c.setFont("PosterSansBold", 20)
    c.setFillColor(INK)
    c.drawString(margin, 382, "Local kind · PostgreSQL 16 · Redis · KEDA · Prometheus · OpenTelemetry · Jaeger")
    draw_wrapped(
        c,
        "The three hero experiments prove application and runtime semantics on local kind. "
        "The Academy EKS profile uses the same application contract; multi-AZ production and commercial Singapore remain explicit targets.",
        margin,
        344,
        content_w - 280,
        "PosterSans",
        18,
        WARM_GREY,
        25,
        3,
    )
    c.setFont("PosterSans", 14)
    c.setFillColor(HexColor("#8B8882"))
    c.drawString(margin, 245, "Evidence: CN-THREE-HERO-SHOWCASE-001 · 27 Jul 2026 · Values include all recorded failures.")

    c.showPage()
    c.save()


def merge_with_template(template_path: Path, overlay_path: Path, output_path: Path) -> None:
    template_reader = PdfReader(str(template_path))
    if len(template_reader.pages) != 1:
        raise ValueError("Expected a one-page NUS poster template.")
    overlay_reader = PdfReader(str(overlay_path))
    base = template_reader.pages[0]
    base.scale_to(PAGE_W, PAGE_H)
    base.merge_page(overlay_reader.pages[0])
    writer = PdfWriter()
    writer.add_page(base)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("wb") as stream:
        writer.write(stream)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--template", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument(
        "--project-url",
        default="https://github.com/BRSAMAyu/inner-cosmos",
    )
    args = parser.parse_args()

    register_fonts()
    with tempfile.TemporaryDirectory(prefix="inner-cosmos-poster-") as tmp:
        overlay = Path(tmp) / "overlay.pdf"
        draw_overlay(overlay, args.project_url)
        merge_with_template(args.template, overlay, args.output)


if __name__ == "__main__":
    main()
