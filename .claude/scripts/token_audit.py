#!/usr/bin/env python3
"""Token audit with a ratchet for the visual migration (plan u1).

Counts design-token violations per category in app/src/main/{res,java} and,
with --baseline, fails (exit 1) if any category grew above its baseline.
Counters may only go DOWN: when they do, re-run with --update and commit the
new baseline.

Usage (from the repo root; relative paths resolve against the repo root):
    python .claude/scripts/token_audit.py
    python .claude/scripts/token_audit.py --baseline .claude/scripts/token_audit_baseline.json
    python .claude/scripts/token_audit.py --baseline .claude/scripts/token_audit_baseline.json --update
    python .claude/scripts/token_audit.py --list hex_layout_drawable   # file:line of each hit
    python .claude/scripts/token_audit.py --json

Exit codes: 0 OK, 1 a category exceeds its baseline, 2 usage/IO error.
Python 3 stdlib only. Comments (XML <!-- -->, Java // and /* */) are ignored.
"""

import argparse
import datetime
import json
import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MAIN = os.path.join(REPO_ROOT, "app", "src", "main")
RES = os.path.join(MAIN, "res")
JAVA = os.path.join(MAIN, "java")
COLORS_XML = os.path.join(RES, "values", "colors.xml")
PALETTE_XML = os.path.join(RES, "values", "palette.xml")

# Blocklist of the legacy palette names. u24 deleted them from values/colors.xml, so this list is
# now the source of truth: any new reference to one of them is a legacy_color hit. Keep it in sync
# with .claude/hooks/check-legacy-palette.ps1.
FALLBACK_LEGACY = [
    "purple_200", "purple_500", "purple_700", "teal_200", "teal_700",
    "black", "white", "transparent",
    "azul", "naranja", "blanco", "gris", "gris_claro", "rojo",
    "verde", "amarillo", "beige", "marron_claro",
    "azul_marino", "teal", "plata", "purpura", "magenta", "gris_oscuro",
    "eventalkNaranja", "eventalkAzul", "eventalkAzulSecundario",
    "c_azul_claro", "c_verde_claro", "c_azul_oscuro", "c_verde_oscuro",
]

# Semantic (non-legacy) colour names: bb_* and calendar_item_*.
SEMANTIC_PREFIXES = ("bb_", "calendar_item_")

CATEGORIES = [
    ("hex_layout_drawable", "hex literal in layout/drawable (no ic_launcher*)"),
    ("android_white_black", "@android:color/(white|black), android.R.color.(white|black)"),
    ("legacy_color", "@color/ or R.color. legacy palette name"),
    ("java_color_const", "Color.(WHITE|BLACK|LTGRAY|parseColor) in Java"),
    ("textsize_literal_sp", "textSize as a literal sp (XML) / setTextSize(<number>) (Java)"),
    ("font_ref_outside_typography", "@font/ or R.font. outside styles_typography/themes/res/font"),
    ("fraunces", "'fraunces' mentions (text + file names)"),
    ("set_theme_java", "setTheme( in Java"),
    # Added in u4: primitives (names defined in values/palette.xml) may only be referenced from
    # palette.xml and values*/colors.xml; everything else goes through the bb_* tokens.
    ("palette_primitive_ref", "palette.xml primitive referenced outside palette.xml/colors.xml"),
]

HEX_RE = re.compile(r"(?<![\w&])#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{4}|[0-9a-fA-F]{3})(?![0-9a-zA-Z_])")
ANDROID_WB_XML_RE = re.compile(r"@android:color/(?:white|black)\b")
ANDROID_WB_JAVA_RE = re.compile(r"\bandroid\.R\.color\.(?:white|black)\b")
JAVA_COLOR_RE = re.compile(r"\bColor\s*\.\s*(?:WHITE|BLACK|LTGRAY|parseColor)\b")
TEXTSIZE_XML_ATTR_RE = re.compile(r"textSize\s*=\s*\"\s*-?\d+(?:\.\d+)?\s*sp\s*\"")
TEXTSIZE_XML_ITEM_RE = re.compile(r"<item\s+name\s*=\s*\"(?:android:)?textSize\"\s*>\s*-?\d+(?:\.\d+)?\s*sp\s*<")
TEXTSIZE_JAVA_RE = re.compile(r"\bsetTextSize\s*\(\s*(?:TypedValue\s*\.\s*COMPLEX_UNIT_SP\s*,\s*)?-?\d")
FONT_XML_RE = re.compile(r"@font/")
FONT_JAVA_RE = re.compile(r"\bR\.font\.")
FRAUNCES_RE = re.compile(r"fraunces", re.IGNORECASE)
SET_THEME_RE = re.compile(r"\bsetTheme\s*\(")
XML_COMMENT_RE = re.compile(r"<!--.*?-->", re.DOTALL)


def read_text(path):
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        return fh.read()


def blank_keep_newlines(match):
    return re.sub(r"[^\n]", " ", match.group(0))


def strip_xml_comments(text):
    return XML_COMMENT_RE.sub(blank_keep_newlines, text)


def strip_java_comments(text):
    """Blank // and /* */ comments, respecting string/char literals. Keeps newlines."""
    out = []
    i, n = 0, len(text)
    state = None  # None | 'line' | 'block' | '"' | "'"
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if state is None:
            if c == "/" and nxt == "/":
                state = "line"; out.append("  "); i += 2; continue
            if c == "/" and nxt == "*":
                state = "block"; out.append("  "); i += 2; continue
            if c in ('"', "'"):
                state = c
            out.append(c); i += 1; continue
        if state == "line":
            if c == "\n":
                state = None; out.append(c)
            else:
                out.append(" ")
            i += 1; continue
        if state == "block":
            if c == "*" and nxt == "/":
                state = None; out.append("  "); i += 2; continue
            out.append("\n" if c == "\n" else " "); i += 1; continue
        # inside a string or char literal
        if c == "\\" and i + 1 < n:
            out.append(c); out.append(nxt); i += 2; continue
        if c == state or c == "\n":
            state = None
        out.append(c); i += 1
    return "".join(out)


def legacy_color_names():
    names = []
    try:
        text = strip_xml_comments(read_text(COLORS_XML))
        for name in re.findall(r"<color\s+name\s*=\s*\"([^\"]+)\"", text):
            if not name.startswith(SEMANTIC_PREFIXES):
                names.append(name)
    except OSError:
        pass
    if not names:
        names = list(FALLBACK_LEGACY)
    # Always include the historic set: u24 deleted the legacy colours from colors.xml, and the
    # blocklist keeps flagging a reference to one (it would not link anyway).
    for name in FALLBACK_LEGACY:
        if name not in names:
            names.append(name)
    return names


def palette_primitive_names():
    """Colour names defined in values/palette.xml (u4). Empty if the file is missing."""
    try:
        text = strip_xml_comments(read_text(PALETTE_XML))
    except OSError:
        return []
    return re.findall(r"<color\s+name\s*=\s*\"([^\"]+)\"", text)


def iter_files(root, exts):
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames.sort()
        for fn in sorted(filenames):
            if fn.lower().endswith(exts):
                yield os.path.join(dirpath, fn)


def rel(path):
    return os.path.relpath(path, REPO_ROOT).replace("\\", "/")


def line_of(text, pos):
    return text.count("\n", 0, pos) + 1


def audit():
    hits = {key: [] for key, _ in CATEGORIES}
    legacy = legacy_color_names()
    alt = "|".join(re.escape(n) for n in sorted(legacy, key=len, reverse=True))
    legacy_xml_re = re.compile(r"@color/(?:%s)(?![\w])" % alt)
    legacy_java_re = re.compile(r"\bR\.color\.(?:%s)(?![\w])" % alt)
    primitives = palette_primitive_names()
    prim_xml_re = prim_java_re = None
    if primitives:
        palt = "|".join(re.escape(n) for n in sorted(primitives, key=len, reverse=True))
        prim_xml_re = re.compile(r"@color/(?:%s)(?![\w])" % palt)
        prim_java_re = re.compile(r"\bR\.color\.(?:%s)(?![\w])" % palt)

    def add(key, path, text, regex):
        for m in regex.finditer(text):
            hits[key].append((rel(path), line_of(text, m.start()), m.group(0).strip()))

    # --- res ---
    for path in iter_files(RES, (".xml",)):
        parts = os.path.relpath(path, RES).replace("\\", "/").split("/")
        res_dir = parts[0]
        fname = parts[-1]
        res_type = res_dir.split("-")[0]
        text = strip_xml_comments(read_text(path))

        if res_type in ("layout", "drawable") and not fname.startswith("ic_launcher"):
            add("hex_layout_drawable", path, text, HEX_RE)
        add("android_white_black", path, text, ANDROID_WB_XML_RE)
        add("legacy_color", path, text, legacy_xml_re)
        add("textsize_literal_sp", path, text, TEXTSIZE_XML_ATTR_RE)
        add("textsize_literal_sp", path, text, TEXTSIZE_XML_ITEM_RE)
        font_allowed = (
            res_type == "font"
            or (res_type == "values" and fname in ("styles_typography.xml", "themes.xml"))
        )
        if not font_allowed:
            add("font_ref_outside_typography", path, text, FONT_XML_RE)
        add("fraunces", path, text, FRAUNCES_RE)
        prim_allowed = res_type == "values" and fname in ("palette.xml", "colors.xml")
        if prim_xml_re is not None and not prim_allowed:
            add("palette_primitive_ref", path, text, prim_xml_re)

    # file names mentioning fraunces (fonts, ttf, xml)
    for root in (RES, JAVA):
        for dirpath, dirnames, filenames in os.walk(root):
            dirnames.sort()
            for fn in sorted(filenames):
                if FRAUNCES_RE.search(fn):
                    hits["fraunces"].append((rel(os.path.join(dirpath, fn)), 0, "<file name>"))

    # --- java ---
    for path in iter_files(JAVA, (".java", ".kt")):
        text = strip_java_comments(read_text(path))
        add("android_white_black", path, text, ANDROID_WB_JAVA_RE)
        add("legacy_color", path, text, legacy_java_re)
        add("java_color_const", path, text, JAVA_COLOR_RE)
        add("textsize_literal_sp", path, text, TEXTSIZE_JAVA_RE)
        add("font_ref_outside_typography", path, text, FONT_JAVA_RE)
        add("fraunces", path, text, FRAUNCES_RE)
        add("set_theme_java", path, text, SET_THEME_RE)
        if prim_java_re is not None:
            add("palette_primitive_ref", path, text, prim_java_re)

    return hits


def resolve(path):
    return path if os.path.isabs(path) else os.path.join(REPO_ROOT, path)


def load_baseline(path):
    with open(path, "r", encoding="utf-8") as fh:
        data = json.load(fh)
    return data.get("counts", data)


def write_baseline(path, counts):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    payload = {
        "description": "token_audit.py ratchet baseline. Counters may only go down; "
                       "re-run with --update when they do and commit this file.",
        "updated": datetime.date.today().isoformat(),
        "counts": counts,
    }
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(payload, fh, indent=2, sort_keys=False)
        fh.write("\n")


def main(argv=None):
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

    ap = argparse.ArgumentParser(description="Design-token audit with a ratchet baseline.")
    ap.add_argument("--baseline", help="baseline JSON path (relative paths resolve against the repo root)")
    ap.add_argument("--update", action="store_true", help="rewrite the baseline with the current counts")
    ap.add_argument("--json", action="store_true", help="print machine-readable JSON instead of a table")
    ap.add_argument("--list", metavar="CATEGORY", nargs="?", const="all",
                    help="also list file:line of each hit (one category or 'all')")
    args = ap.parse_args(argv)

    if not os.path.isdir(MAIN):
        print("token_audit: app/src/main not found under %s" % REPO_ROOT, file=sys.stderr)
        return 2
    if args.update and not args.baseline:
        print("token_audit: --update needs --baseline", file=sys.stderr)
        return 2
    valid = [k for k, _ in CATEGORIES]
    if args.list and args.list != "all" and args.list not in valid:
        print("token_audit: unknown category %r (one of: %s)" % (args.list, ", ".join(valid)), file=sys.stderr)
        return 2

    hits = audit()
    counts = {k: len(hits[k]) for k in valid}

    baseline = None
    baseline_path = resolve(args.baseline) if args.baseline else None
    if baseline_path and os.path.exists(baseline_path):
        try:
            baseline = load_baseline(baseline_path)
        except (OSError, ValueError) as exc:
            print("token_audit: cannot read baseline %s: %s" % (baseline_path, exc), file=sys.stderr)
            return 2
    elif baseline_path and not args.update:
        print("token_audit: baseline %s not found (create it with --update)" % baseline_path, file=sys.stderr)
        return 2

    regressions = []
    rows = []
    for key, desc in CATEGORIES:
        cur = counts[key]
        base = baseline.get(key) if baseline is not None else None
        if base is None:
            status, delta = ("new" if baseline is not None else "-"), None
        else:
            delta = cur - base
            if delta > 0:
                status = "UP"
                regressions.append(key)
            elif delta < 0:
                status = "down"
            else:
                status = "ok"
        rows.append((key, desc, cur, base, delta, status))

    if args.json:
        out = {
            "counts": counts,
            "baseline": baseline,
            "regressions": regressions,
        }
        if args.list:
            keys = valid if args.list == "all" else [args.list]
            out["hits"] = {k: [{"file": f, "line": ln, "match": m} for f, ln, m in hits[k]] for k in keys}
        print(json.dumps(out, indent=2))
    else:
        w = max(len(k) for k in valid)
        print("%-*s  %7s  %8s  %6s  %s" % (w, "category", "current", "baseline", "delta", "status"))
        print("%s  %s  %s  %s  %s" % ("-" * w, "-" * 7, "-" * 8, "-" * 6, "-" * 6))
        for key, desc, cur, base, delta, status in rows:
            print("%-*s  %7d  %8s  %6s  %s" % (
                w, key, cur,
                "-" if base is None else str(base),
                "-" if delta is None else ("%+d" % delta),
                status))
        print("%s  %7d" % ("TOTAL".ljust(w), sum(counts.values())))
        if args.list:
            keys = valid if args.list == "all" else [args.list]
            for k in keys:
                print("\n[%s] %d hit(s)" % (k, len(hits[k])))
                for f, ln, m in hits[k]:
                    print("  %s:%d  %s" % (f, ln, m))

    if args.update:
        if regressions:
            print("token_audit: WARNING baseline raised for: %s (explain it in the impl notes)"
                  % ", ".join(regressions), file=sys.stderr)
        write_baseline(baseline_path, counts)
        print("token_audit: baseline written to %s" % rel(baseline_path), file=sys.stderr)
        return 0

    if regressions:
        print("token_audit: FAIL - above baseline: %s" % ", ".join(regressions), file=sys.stderr)
        return 1
    if any(r[5] == "down" for r in rows):
        print("token_audit: counters went down - re-run with --update and commit the baseline",
              file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
