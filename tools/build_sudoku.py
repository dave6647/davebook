#!/usr/bin/env python3
"""Baut aus web/sudoku.html die Datei app/src/main/assets/sudoku.html für die App.

Wie bei Pfeilfrei: Das Spiel liegt komplett in einer HTML-Datei, die App zeigt es in einer WebView.
Die Schrift (Overpass, SIL Open Font License) aus web/fonts wird eingebettet, damit alles offline läuft.

    python3 tools/build_sudoku.py
"""
import base64
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
LATIN = ("U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,"
         "U+2000-206F,U+20AC,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD")
FONTS = [("Overpass", w, f"overpass-latin-{w}-normal.woff2") for w in (400, 700, 800, 900)] + [
    ("Overpass Mono", 700, "overpass-mono-latin-700-normal.woff2")]


def font_css() -> str:
    parts = []
    for family, weight, file in FONTS:
        data = base64.b64encode((ROOT / "web" / "fonts" / file).read_bytes()).decode()
        parts.append(
            f'@font-face{{font-family:"{family}";font-style:normal;font-weight:{weight};'
            f'font-display:swap;src:url(data:font/woff2;base64,{data}) format("woff2");unicode-range:{LATIN}}}'
        )
    return "\n".join(parts)


def main() -> None:
    src = (ROOT / "web" / "sudoku.html").read_text(encoding="utf-8")
    if "/*@FONTS@*/" not in src:
        raise SystemExit("Platzhalter /*@FONTS@*/ fehlt in web/sudoku.html")
    fragment = src.replace("/*@FONTS@*/", font_css())
    page = (
        "<!doctype html>\n<html lang=\"de\">\n<head>\n"
        "<meta charset=\"utf-8\">\n"
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover\">\n"
        "</head>\n<body>\n" + fragment + "\n</body>\n</html>\n"
    )
    out = ROOT / "app" / "src" / "main" / "assets" / "sudoku.html"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(page, encoding="utf-8")
    print(f"OK: {out.relative_to(ROOT)} ({len(page) // 1024} KB)")


if __name__ == "__main__":
    main()
