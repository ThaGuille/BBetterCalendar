#!/usr/bin/env python3
"""Checks the four project accent colours (rediseno-proyectos u3, D-P13).

Resolves bb_project_1..4, bb_surface_card, bb_sanct_primary and bb_danger through
values/colors.xml and values/palette.xml (following @color aliases), prints one line per
colour (hex, relative luminance, hue, contrast on the card) and then the verdict:

  - every project colour has at least 3:1 contrast against bb_surface_card (WCAG 1.4.11);
  - bb_project_2 is at least 30 degrees of hue away from bb_sanct_primary (amber) and
    bb_danger (red), so it can never be mistaken for Focus or for an error.

Last line "project colours ok" and exit 0 when everything holds, exit 1 otherwise.
Run from the repo root (or anywhere: paths are resolved from this file).
"""
import colorsys
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, "app", "src", "main", "res", "values")
MIN_CONTRAST = 3.0
MIN_HUE_DISTANCE = 30.0
PROJECTS = ["bb_project_1", "bb_project_2", "bb_project_3", "bb_project_4"]

COLOR_RE = re.compile(r'<color\s+name="([^"]+)"\s*>\s*([^<\s]+)\s*</color>')


def load():
    table = {}
    for name in ("colors.xml", "palette.xml"):
        with open(os.path.join(RES, name), encoding="utf-8") as f:
            text = re.sub(r"<!--.*?-->", "", f.read(), flags=re.S)
        for key, value in COLOR_RE.findall(text):
            table[key] = value
    return table


def resolve(table, name):
    seen = set()
    value = "@color/" + name
    while value.startswith("@color/"):
        key = value[len("@color/"):]
        if key in seen or key not in table:
            raise KeyError("cannot resolve colour " + name)
        seen.add(key)
        value = table[key]
    m = re.fullmatch(r"#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})", value)
    if not m:
        raise ValueError("unsupported colour value " + value + " for " + name)
    digits = m.group(1)[-6:]
    return tuple(int(digits[i:i + 2], 16) for i in (0, 2, 4))


def luminance(rgb):
    def lin(c):
        c = c / 255.0
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (lin(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a, b):
    la, lb = luminance(a), luminance(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def hue(rgb):
    h, _, _ = colorsys.rgb_to_hls(*(c / 255.0 for c in rgb))
    return h * 360.0


def hue_distance(a, b):
    d = abs(hue(a) - hue(b)) % 360.0
    return min(d, 360.0 - d)


def main():
    table = load()
    names = PROJECTS + ["bb_surface_card", "bb_sanct_primary", "bb_danger"]
    colours = {n: resolve(table, n) for n in names}
    card = colours["bb_surface_card"]
    ok = True
    for n in names:
        rgb = colours[n]
        print("%-17s #%02X%02X%02X  lum %.3f  hue %5.1f  contrast on card %.2f"
              % (n, rgb[0], rgb[1], rgb[2], luminance(rgb), hue(rgb), contrast(rgb, card)))
    for n in PROJECTS:
        c = contrast(colours[n], card)
        if c < MIN_CONTRAST:
            print("FAIL %s contrast %.2f < %.1f" % (n, c, MIN_CONTRAST))
            ok = False
    for other in ("bb_sanct_primary", "bb_danger"):
        d = hue_distance(colours["bb_project_2"], colours[other])
        print("bb_project_2 hue distance to %s: %.1f" % (other, d))
        if d < MIN_HUE_DISTANCE:
            print("FAIL bb_project_2 is only %.1f degrees from %s (need %d)"
                  % (d, other, MIN_HUE_DISTANCE))
            ok = False
    if not ok:
        return 1
    print("project colours ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
