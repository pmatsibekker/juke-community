"""Render JUKE_MANUAL.md to JUKE_MANUAL.pdf.

Uses xhtml2pdf + Pygments for syntax-highlighted, Unicode-clean output.

Standalone, idempotent. Run from repo root:
    python build_manual_pdf.py
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

import markdown
from pygments.formatters import HtmlFormatter
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.pdfmetrics import registerFontFamily
from reportlab.pdfbase.ttfonts import TTFont
from xhtml2pdf import default as x2p_default
from xhtml2pdf import pisa

REPO = Path(__file__).resolve().parent
SRC = REPO / "JUKE_MANUAL.md"
DST = REPO / "JUKE_MANUAL.pdf"
WIN_FONTS = Path("C:/Windows/Fonts")


def _register_fonts() -> None:
    """Register Unicode TTF fonts with reportlab, then override xhtml2pdf's
    built-in CSS-name → font-name map so the CSS `font-family` declarations
    resolve to these fonts instead of the core PDF fonts."""
    families = {
        "Georgia":  ("georgia.ttf",  "georgiab.ttf", "georgiai.ttf", "georgiaz.ttf"),
        "Arial":    ("arial.ttf",    "arialbd.ttf",  "ariali.ttf",   "arialbi.ttf"),
        "Consolas": ("consola.ttf",  "consolab.ttf", "consolai.ttf", "consolaz.ttf"),
    }
    for family, (regular, bold, italic, bold_italic) in families.items():
        pdfmetrics.registerFont(TTFont(family, str(WIN_FONTS / regular)))
        b_name = f"{family}-Bold"
        i_name = f"{family}-Italic"
        bi_name = f"{family}-BoldItalic"
        pdfmetrics.registerFont(TTFont(b_name,  str(WIN_FONTS / bold)))
        pdfmetrics.registerFont(TTFont(i_name,  str(WIN_FONTS / italic)))
        pdfmetrics.registerFont(TTFont(bi_name, str(WIN_FONTS / bold_italic)))
        registerFontFamily(family, normal=family, bold=b_name, italic=i_name, boldItalic=bi_name)

    # Segoe UI Symbol — single-weight, used as the per-character fallback for
    # glyphs missing from Georgia/Arial/Consolas (arrows, box-drawing, ☑/☒/⚠).
    pdfmetrics.registerFont(TTFont("SegoeSymbol", str(WIN_FONTS / "seguisym.ttf")))
    registerFontFamily("SegoeSymbol", normal="SegoeSymbol", bold="SegoeSymbol",
                       italic="SegoeSymbol", boldItalic="SegoeSymbol")

    # xhtml2pdf maintains its own CSS-family → font-name table that hardcodes
    # "georgia" → "Times-Roman" and "arial" → "Helvetica". Point those keys at
    # the freshly registered TTF families so the styled text actually uses
    # Unicode-capable fonts (em-dashes, middle dots, smart quotes).
    x2p_default.DEFAULT_FONT.update({
        "georgia":  "Georgia",
        "arial":    "Arial",
        "consolas": "Consolas",
        "segoesymbol": "SegoeSymbol",
        "helvetica": "Arial",
        "times":    "Georgia",
        "times-roman": "Georgia",
        "monospace": "Consolas",
        "monospaced": "Consolas",
        "courier":  "Consolas",
        "courier new": "Consolas",
        "serif":    "Georgia",
        "sansserif": "Arial",
        "sans":     "Arial",
    })


# Characters that exist in Segoe UI Symbol but not in Georgia/Arial/Consolas.
# Each is wrapped in a <span class='sym'> so the renderer picks SegoeSymbol.
_FALLBACK_CHARS = "✅❌⚠️▶◀←→"


def _link_callback(uri: str, rel: str) -> str:
    """Resolve relative resource URIs (e.g. the cover image) against the repo
    root so xhtml2pdf can embed them no matter the process working directory."""
    if uri.startswith(("http://", "https://", "data:")):
        return uri
    path = Path(uri)
    if not path.is_absolute():
        path = REPO / uri
    return str(path)


def _constrain_table_columns(html: str) -> str:
    """Pin every table's columns to explicit, equal widths.

    xhtml2pdf sizes a column to the *natural* width of its widest content.
    A long, unbreakable token — a dotted config key like
    ``juke.coverage.threshold.server.instruction`` or a path default like
    ``${user.home}/juke-demo/coverage/server`` — has no break opportunity, so
    its column grows past the page frame and the text bleeds into the
    neighbouring cell (Chapter 12's Key/Default columns are the worst case).

    Giving each column a fixed width (via the header cells, the widths a
    column actually adopts) overrides that content-based sizing. But a fixed
    width is not enough on its own: ReportLab (which renders each cell) does
    **not** auto-split an unbreakable ``<code>`` token (no U+200B / soft-hyphen
    break either), so it still overruns the fixed cell. So we also insert hard
    ``<br/>`` breaks inside long ``<code>`` tokens, sized to the column: the
    token is packed greedily onto lines no wider than the cell, breaking at a
    natural separator (``. / - _ : }``) where possible. ReportLab honours
    ``<br/>``, so the token now wraps within its cell.

    Widths are merged into any existing ``style`` so column alignment from
    Markdown (``:---:`` etc.) is preserved.
    """
    def fix_table(match: re.Match) -> str:
        table = match.group(0)
        first_row = re.search(r"<tr\b[^>]*>(.*?)</tr>", table, re.S)
        if not first_row:
            return table
        row_html = first_row.group(1)
        tag = "th" if re.search(r"<th\b", row_html) else "td"
        ncols = len(re.findall(rf"<{tag}\b", row_html))
        if ncols < 2:
            return table  # nothing to balance
        width = round(100.0 / ncols, 2)

        def add_width(cell: re.Match) -> str:
            attrs = cell.group(1)  # existing attributes ("" or e.g. ' style="..."')
            if 'style="' in attrs:
                attrs = re.sub(r'style="', f'style="width: {width}%; ', attrs, count=1)
            else:
                attrs = f'{attrs} style="width: {width}%"'
            return f"<{tag}{attrs}>"

        new_row = re.sub(rf"<{tag}(\b[^>]*)?>", add_width, row_html)
        table = table.replace(row_html, new_row, 1)
        return _break_code_tokens(table, _max_code_chars(ncols))

    return re.sub(r"<table\b.*?</table>", fix_table, html, flags=re.S)


# Letter page (612pt) minus 0.85in margins each side; chrome = cell padding+border.
_CONTENT_WIDTH_PT = 612.0 - 2 * 0.85 * 72.0
_CELL_CHROME_PT = 14.0
_CODE_FONT, _CODE_SIZE = "Consolas", 9.5
# Separators inside a code token after which a line break reads naturally.
_CODE_BREAK_AFTER = frozenset("./_-:}")


def _consolas_char_width() -> float:
    """Advance width of one monospace code char, for sizing line lengths.
    Falls back to a ratio estimate if the font isn't registered yet."""
    try:
        from reportlab.pdfbase.pdfmetrics import stringWidth
        return stringWidth("0" * 20, _CODE_FONT, _CODE_SIZE) / 20.0
    except Exception:
        return 0.55 * _CODE_SIZE


def _max_code_chars(ncols: int) -> int:
    """How many monospace chars fit on one line of an equal-width column."""
    usable = (_CONTENT_WIDTH_PT / ncols) - _CELL_CHROME_PT
    return max(8, int(usable / _consolas_char_width()))


def _break_code_tokens(html: str, max_chars: int) -> str:
    """Insert hard ``<br/>`` breaks inside long ``<code>`` tokens so they wrap
    within a fixed-width cell. Packs greedily onto lines of at most
    ``max_chars``, preferring to break just after a natural separator; HTML
    entities (``&...;``) are treated as single units so they're never split."""
    def break_token(text: str) -> str:
        if len(text) <= max_chars:
            return text
        units = re.findall(r"&[#0-9A-Za-z]+;|.", text, re.S)
        soft = max(4, int(max_chars * 0.6))
        out, line = [], 0
        for idx, unit in enumerate(units):
            out.append(unit)
            line += 1
            if idx == len(units) - 1:
                break  # never break after the final unit
            ends_sep = len(unit) == 1 and unit in _CODE_BREAK_AFTER
            if line >= max_chars or (ends_sep and line >= soft):
                out.append("<br/>")
                line = 0
        return "".join(out)

    return re.sub(r"(<code>)(.*?)(</code>)",
                  lambda m: m.group(1) + break_token(m.group(2)) + m.group(3),
                  html, flags=re.S)


def _wrap_fallback_glyphs(html: str) -> str:
    """Wrap each char in `_FALLBACK_CHARS` with a Symbol-font span so it
    renders via Segoe UI Symbol rather than as a missing-glyph box."""
    out = []
    i = 0
    while i < len(html):
        ch = html[i]
        if ch in _FALLBACK_CHARS:
            # Drop U+FE0F variation selectors that follow emoji
            j = i + 1
            while j < len(html) and html[j] == "️":
                j += 1
            out.append(f"<span class='sym'>{ch}</span>")
            i = j
        else:
            out.append(ch)
            i += 1
    return "".join(out)


CSS = """
@page {
  size: letter;
  margin: 0.85in 0.85in 1.0in 0.85in;
  @frame footer {
    -pdf-frame-content: footerContent;
    bottom: 0.4in; left: 0.85in; right: 0.85in; height: 0.4in;
  }
}
body {
  font-family: "Georgia";
  font-size: 10.5pt;
  line-height: 1.45;
  color: #1a1a1a;
}
h1 {
  font-family: "Arial";
  font-size: 22pt;
  color: #1d3557;
  margin-top: 18pt;
  margin-bottom: 10pt;
  border-bottom: 2pt solid #1d3557;
  padding-bottom: 4pt;
}
h2 {
  font-family: "Arial";
  font-size: 15pt;
  color: #2a4a6b;
  margin-top: 16pt;
  margin-bottom: 6pt;
}
h3 {
  font-family: "Arial";
  font-size: 12pt;
  color: #3a6090;
  margin-top: 12pt;
  margin-bottom: 4pt;
}
h4 { font-family: "Arial"; font-size: 11pt; color: #3a6090; }
p { margin: 6pt 0; text-align: justify; }
code {
  font-family: "Consolas";
  font-size: 9.5pt;
  background-color: #f3f3f3;
  color: #a02050;
  padding: 0 2pt;
}
pre {
  font-family: "Consolas";
  font-size: 9pt;
  background-color: #fbfbf8;
  border: 0.5pt solid #c8c8c0;
  padding: 6pt 8pt;
  margin: 6pt 0;
  line-height: 1.25;
  white-space: pre-wrap;
  color: #222;
}
pre code { background-color: transparent; color: inherit; padding: 0; font-size: 9pt; }
.codehilite { background-color: #fbfbf8; border: 0.5pt solid #c8c8c0; padding: 6pt 8pt; margin: 6pt 0; }
.codehilite pre { background-color: transparent; border: 0; padding: 0; margin: 0; }
table {
  border-collapse: collapse;
  margin: 8pt 0;
  width: 100%;
}
th, td {
  border: 0.5pt solid #999;
  padding: 4pt 6pt;
  vertical-align: top;
  font-size: 9.5pt;
}
th {
  background-color: #e3eaf3;
  font-family: "Arial";
  font-weight: bold;
  text-align: left;
  color: #1d3557;
}
blockquote {
  border-left: 3pt solid #c89b3c;
  padding: 4pt 10pt;
  margin: 6pt 0;
  color: #555;
  background-color: #fdf8ec;
}
hr {
  border: 0;
  page-break-after: always;
}
ul, ol { margin: 4pt 0 4pt 16pt; padding: 0; }
li { margin: 2pt 0; }
a { color: #1a4a7a; text-decoration: none; }
strong { color: #111; }
em { color: inherit; }
.footer {
  font-family: "Arial";
  font-size: 8pt;
  color: #666;
  text-align: center;
}
.sym { font-family: "SegoeSymbol"; }
"""

FOOTER = (
    '<div id="footerContent" class="footer">'
    'Juke Manual &middot; Covers Juke 1.0.0 &middot; Page '
    '<pdf:pagenumber /> of <pdf:pagecount />'
    '</div>'
)


def main() -> int:
    _register_fonts()

    md_text = SRC.read_text(encoding="utf-8")
    html_body = markdown.markdown(
        md_text,
        extensions=[
            "tables",
            "fenced_code",
            "sane_lists",
            "attr_list",
            "def_list",
            "codehilite",
        ],
        extension_configs={
            "codehilite": {
                "css_class": "codehilite",
                "guess_lang": True,
                "noclasses": False,
                "pygments_style": "friendly",
            },
        },
    )

    pygments_css = HtmlFormatter(style="friendly").get_style_defs(".codehilite")

    css = CSS + "\n" + pygments_css
    html_body = _constrain_table_columns(html_body)
    html_body = _wrap_fallback_glyphs(html_body)

    html = (
        "<!DOCTYPE html><html><head><meta charset='utf-8'>"
        f"<style>{css}</style></head><body>"
        f"{FOOTER}{html_body}"
        "</body></html>"
    )
    with DST.open("wb") as fh:
        result = pisa.CreatePDF(src=html, dest=fh, encoding="utf-8",
                                link_callback=_link_callback)
    if result.err:
        print(f"xhtml2pdf reported {result.err} error(s)", file=sys.stderr)
        return 1
    print(f"Wrote {DST} ({DST.stat().st_size:,} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
