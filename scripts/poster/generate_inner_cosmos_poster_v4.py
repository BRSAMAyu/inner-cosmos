from __future__ import annotations

import argparse
import tempfile
from pathlib import Path

from pypdf import PdfReader, PdfWriter
from reportlab.lib.colors import Color, HexColor
from reportlab.lib.pagesizes import A1
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas


PAGE_W, PAGE_H = A1
TEMPLATE_FOOTER_RATIO = 49.03238 / 780.0
FOOTER_H = PAGE_H * TEMPLATE_FOOTER_RATIO
REPO_ROOT = Path(__file__).resolve().parents[2]
SCREENSHOT_ROOT = REPO_ROOT / "evidence" / "g9" / "FINAL-E2E-001" / "screenshots"

IVORY = HexColor("#F6F1EA")
PAPER = HexColor("#FCFAF6")
INK = HexColor("#292523")
WARM_GREY = HexColor("#6F6A65")
STONE = HexColor("#D7CFC4")
NUS_BLUE = HexColor("#1C427C")
GOLD = HexColor("#C7A94F")
MIST = HexColor("#7F96A6")
SAGE = HexColor("#91A58E")
PLUM = HexColor("#9B8DA1")
CLAY = HexColor("#B9846E")
SOFT_SAGE = HexColor("#E8ECE5")
SOFT_PLUM = HexColor("#ECE7EC")
SOFT_CLAY = HexColor("#F0E3DD")


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


def wrap_lines(text: str, width: float, font: str, size: float) -> list[str]:
    words = text.split()
    lines: list[str] = []
    current = ""
    for word in words:
        candidate = word if not current else f"{current} {word}"
        if pdfmetrics.stringWidth(candidate, font, size) <= width:
            current = candidate
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def paragraph(
    c: canvas.Canvas,
    text: str,
    x: float,
    y: float,
    width: float,
    size: float,
    leading: float,
    color: Color = INK,
    font: str = "PosterSans",
    max_lines: int | None = None,
) -> float:
    lines = wrap_lines(text, width, font, size)
    if max_lines is not None:
        lines = lines[:max_lines]
    c.setFillColor(color)
    c.setFont(font, size)
    cursor = y
    for line in lines:
        c.drawString(x, cursor, line)
        cursor -= leading
    return cursor


def section_heading(c: canvas.Canvas, number: str, title: str, x: float, y: float, end_x: float) -> None:
    label(c, number, x, y - 4, 43, GOLD, "PosterSerif")
    label(c, title, x + 45, y, 30, INK, "PosterSerifBold")
    c.saveState()
    c.setStrokeColor(STONE)
    c.setLineWidth(1.2)
    c.line(x + 45, y - 14, end_x, y - 14)
    c.restoreState()


def draw_background(c: canvas.Canvas) -> None:
    c.setFillColor(IVORY)
    c.rect(0, FOOTER_H, PAGE_W, PAGE_H - FOOTER_H, fill=1, stroke=0)
    # Only a few peripheral stars; the product screenshots carry the visual identity.
    for x, y, radius, color in (
        (70, 2242, 4, GOLD),
        (1595, 2256, 5, SAGE),
        (1620, 1965, 3, PLUM),
        (58, 858, 3, MIST),
        (1615, 780, 4, GOLD),
        (75, 230, 3, PLUM),
    ):
        c.setFillColor(color)
        c.circle(x, y, radius, fill=1, stroke=0)


def draw_header(c: canvas.Canvas) -> None:
    tracking_text(c, "RESONANCE BEFORE CONNECTION", 86, 2260, 14, WARM_GREY, 4.0)
    label(c, "Inner Cosmos", 84, 2142, 76, INK, "PosterSerif")
    label(
        c,
        "A living AI companion for self-understanding and slow, consent-based social connection.",
        88,
        2075,
        22,
        PLUM,
        "PosterSerifItalic",
    )
    right_label(c, "SWS3004  /  CLOUD COMPUTING  /  2026", PAGE_W - 84, 2225, 22, NUS_BLUE, "PosterSansBold")
    right_label(c, "Deng Boren  /  Sang Chenyi  /  Peng Cheng  /  Huo Mingxian", PAGE_W - 84, 2178, 16, WARM_GREY)
    c.saveState()
    c.setStrokeColor(GOLD)
    c.setLineWidth(2)
    c.line(84, 2027, PAGE_W - 84, 2027)
    c.restoreState()


def draw_motivation(c: canvas.Canvas) -> None:
    section_heading(c, "1", "Motivation", 84, 1960, 1080)
    text_1 = (
        "People talk more than ever, yet most digital systems still understand them poorly. "
        "Social platforms match declared profiles and engagement signals; AI chat usually responds "
        "in the moment, then forgets how a person changes."
    )
    text_2 = (
        "Inner Cosmos reverses the order: understand before connecting. Aurora turns conversation "
        "into a long-term, user-correctable model of memory, emotion, beliefs, goals and boundaries. "
        "Only the parts a user authorizes may leave that private space."
    )
    y = paragraph(c, text_1, 84, 1894, 960, 17.5, 25.5, INK)
    paragraph(c, text_2, 84, y - 11, 960, 17.5, 25.5, INK)

    c.saveState()
    c.setFillColor(SOFT_SAGE)
    c.setStrokeColor(SAGE)
    c.setLineWidth(1.2)
    c.roundRect(1120, 1726, 480, 183, 12, fill=1, stroke=1)
    c.restoreState()
    tracking_text(c, "OUR POSITION", 1147, 1869, 13, SAGE, 3)
    paragraph(
        c,
        "Understanding should be built slowly, owned by the user, and correctable at any time.",
        1147,
        1828,
        420,
        21,
        29,
        INK,
        "PosterSerifBold",
    )
    tracking_text(c, "PRIVATE BY DEFAULT  /  CONSENT BEFORE CONTACT", 1147, 1750, 11.5, WARM_GREY, 1.1)


def screenshot_frame(
    c: canvas.Canvas,
    path: Path,
    x: float,
    y: float,
    width: float,
    height: float,
) -> None:
    if not path.exists():
        raise FileNotFoundError(path)
    image = ImageReader(str(path))
    src_w, src_h = image.getSize()
    scale = width / src_w
    draw_h = src_h * scale
    # Preserve the screenshot exactly; clip only the lower continuation of the mobile page.
    c.saveState()
    clip = c.beginPath()
    clip.roundRect(x, y, width, height, 15)
    c.clipPath(clip, stroke=0, fill=0)
    c.drawImage(image, x, y + height - draw_h, width=width, height=draw_h, mask="auto")
    c.restoreState()
    c.saveState()
    c.setFillAlpha(0)
    c.setStrokeColor(INK)
    c.setStrokeAlpha(0.38)
    c.setLineWidth(1.8)
    c.roundRect(x, y, width, height, 15, fill=0, stroke=1)
    c.restoreState()


def draw_product_design(c: canvas.Canvas) -> None:
    section_heading(c, "2", "Design - How Inner Cosmos Works", 84, 1652, PAGE_W - 84)

    panels = (
        (
            "01  AURORA",
            MIST,
            SCREENSHOT_ROOT / "aurora-en-SG-mobile390.png",
            "An interruptible, time-aware companion. New messages become fresh input; conversation can create candidate memories, tasks and future follow-ups.",
        ),
        (
            "02  INNER COSMOS",
            SAGE,
            SCREENSHOT_ROOT / "cosmos-en-SG-mobile390.png",
            "A user-owned self-model. Facts, emotions, beliefs, goals and relationships stay linked to their source and can be corrected, merged or faded.",
        ),
        (
            "03  ECHO CAPSULE",
            PLUM,
            SCREENSHOT_ROOT / "resonance-en-SG-mobile390.png",
            "A versioned, authorized projection. Only selected memory facets compile into a persona; private dialogue never becomes public by default.",
        ),
        (
            "04  CONNECTION",
            CLAY,
            SCREENSHOT_ROOT / "letters-en-SG-mobile390.png",
            "Resonance becomes contact only through consent. Slow Letters let both people explore a connection without forcing instant access or intimacy.",
        ),
    )
    xs = (84, 484, 884, 1284)
    image_y = 1070
    image_w = 315
    image_h = 500
    for idx, (title, color, image_path, body) in enumerate(panels):
        x = xs[idx]
        screenshot_frame(c, image_path, x, image_y, image_w, image_h)
        label(c, title, x, 1024, 20, color, "PosterSansBold")
        paragraph(c, body, x, 989, image_w, 15.5, 21.5, INK, "PosterSans", max_lines=5)

    for x in (435, 835, 1235):
        label(c, ">", x, 1315, 28, GOLD, "PosterSansBold")


def differentiator(
    c: canvas.Canvas,
    number: str,
    title: str,
    body: str,
    x: float,
    y: float,
    width: float,
    color: Color,
) -> None:
    c.saveState()
    c.setStrokeColor(color)
    c.setLineWidth(4)
    c.line(x, y - 4, x, y - 93)
    c.restoreState()
    label(c, number, x + 17, y, 13, color, "PosterSansBold")
    label(c, title, x + 55, y - 1, 19, INK, "PosterSerifBold")
    paragraph(c, body, x + 17, y - 33, width - 17, 15.2, 20.5, WARM_GREY, "PosterSans", max_lines=3)


def draw_differentiators(c: canvas.Canvas) -> None:
    section_heading(c, "3", "What Makes It Different", 84, 825, 1005)
    items = (
        (
            "01",
            "Living companionship",
            "Aurora can be interrupted, replan its response and return later instead of behaving like a one-shot chatbot.",
            MIST,
        ),
        (
            "02",
            "Correctable AI understanding",
            "Inferences expose source and version. Users can edit, mute, merge or delete what the system thinks it knows.",
            SAGE,
        ),
        (
            "03",
            "Memory with structure",
            "Time, theme, people and emotional gravity form a navigable inner cosmos rather than another chronological feed.",
            GOLD,
        ),
        (
            "04",
            "Persona with boundaries",
            "Echo Capsules compile chosen memory facets with topic, privacy and interaction limits instead of copying a profile.",
            PLUM,
        ),
        (
            "05",
            "Slow social states",
            "Resonance, invitations, Slow Letters and mutual consent make relationship progress explicit and reversible.",
            CLAY,
        ),
        (
            "06",
            "More than conversation",
            "Daily records, weekly reviews, beliefs, tasks and reflection tools return to the same memory lineage.",
            NUS_BLUE,
        ),
    )
    positions = (
        (84, 758),
        (525, 758),
        (84, 636),
        (525, 636),
        (84, 514),
        (525, 514),
    )
    for item, (x, y) in zip(items, positions):
        number, title, body, color = item
        differentiator(c, number, title, body, x, y, 395, color)


def flow_box(
    c: canvas.Canvas,
    text: str,
    x: float,
    y: float,
    width: float,
    height: float,
    fill: Color,
    stroke: Color,
    text_color: Color = INK,
    size: float = 12.5,
) -> None:
    c.saveState()
    c.setFillColor(fill)
    c.setStrokeColor(stroke)
    c.setLineWidth(1.2)
    c.roundRect(x, y, width, height, 7, fill=1, stroke=1)
    c.restoreState()
    centered_label(c, text, x + width / 2, y + height / 2 - size * 0.35, size, text_color, "PosterSansBold")


def arrow(c: canvas.Canvas, x1: float, y: float, x2: float, color: Color) -> None:
    c.saveState()
    c.setStrokeColor(color)
    c.setFillColor(color)
    c.setLineWidth(1.8)
    c.line(x1, y, x2 - 8, y)
    p = c.beginPath()
    p.moveTo(x2, y)
    p.lineTo(x2 - 9, y + 5)
    p.lineTo(x2 - 9, y - 5)
    p.close()
    c.drawPath(p, fill=1, stroke=0)
    c.restoreState()


def cloud_row(
    c: canvas.Canvas,
    title: str,
    body: str,
    y: float,
    color: Color,
    boxes: tuple[tuple[str, float], ...],
) -> None:
    x = 1050
    tracking_text(c, title, x, y, 13, color, 2.1)
    cursor = x
    box_y = y - 63
    for idx, (text, width) in enumerate(boxes):
        flow_box(c, text, cursor, box_y, width, 42, PAPER, color, INK, 12.2)
        cursor += width
        if idx < len(boxes) - 1:
            arrow(c, cursor + 6, box_y + 21, cursor + 35, color)
            cursor += 42
    paragraph(c, body, x, y - 91, 545, 15.2, 20.5, WARM_GREY, "PosterSans", max_lines=3)


def draw_cloud_native(c: canvas.Canvas) -> None:
    section_heading(c, "4", "Cloud-Native Engineering", 1018, 825, PAGE_W - 84)
    cloud_row(
        c,
        "CONTINUITY DURING CHANGE",
        "A Pod replacement does not have to become a lost or duplicated Aurora turn: the durable timeline and Last-Event-ID support replay.",
        758,
        MIST,
        (("POD A", 80), ("DURABLE TURN", 125), ("POD B REPLAY", 130)),
    )
    cloud_row(
        c,
        "SCALE THE WORK USERS ARE WAITING FOR",
        "Post-conversation memory and profile work scales from outbox pressure through KEDA and HPA, rather than relying on CPU alone.",
        607,
        SAGE,
        (("OUTBOX LAG", 105), ("KEDA / HPA", 115), ("WORKERS xN", 110)),
    )
    cloud_row(
        c,
        "TRACE ONE TURN ACROSS ASYNC WORK",
        "OpenTelemetry carries W3C context from SSE and LLM calls into memory, profile and WakeIntent work without recording private text.",
        456,
        PLUM,
        (("SSE", 62), ("LLM", 62), ("OUTBOX", 84), ("PROJECTION", 106)),
    )


def build_overlay(path: Path) -> None:
    c = canvas.Canvas(str(path), pagesize=A1)
    draw_background(c)
    draw_header(c)
    draw_motivation(c)
    draw_product_design(c)
    draw_differentiators(c)
    draw_cloud_native(c)
    c.showPage()
    c.save()


def merge_with_template(template: Path, overlay: Path, output: Path) -> None:
    base_reader = PdfReader(str(template))
    overlay_reader = PdfReader(str(overlay))
    page = base_reader.pages[0]
    page.scale_to(PAGE_W, PAGE_H)
    page.merge_page(overlay_reader.pages[0])
    writer = PdfWriter()
    writer.add_page(page)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as stream:
        writer.write(stream)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--template", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    register_fonts()
    with tempfile.TemporaryDirectory(prefix="inner-cosmos-poster-v4-") as temp_dir:
        overlay = Path(temp_dir) / "overlay.pdf"
        build_overlay(overlay)
        merge_with_template(args.template, overlay, args.output)
    print(args.output)


if __name__ == "__main__":
    main()
