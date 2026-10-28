#!/usr/bin/env python3
# PostToolUse guardrail - WARN ONLY (non-blocking). Linux/macOS twin of check-legacy-palette.ps1:
# keep the patterns of both files (and of .claude/scripts/token_audit.py) in sync.
# Flags design-token violations ADDED by a .java/.kt/.xml edit (CLAUDE.md rule #2). The edit is kept;
# exit 2 only surfaces the warning to Claude. Reads the tool-call JSON from stdin.
import json
import re
import sys

try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(0)

ti = data.get('tool_input') or {}
path = ti.get('file_path') or ''
if not re.search(r'\.(java|kt|xml)$', path):
    sys.exit(0)

# Only the text this edit ADDED (warn on introductions, not pre-existing refs).
added = [ti.get('new_string') or '', ti.get('content') or '']
added += [(e or {}).get('new_string') or '' for e in (ti.get('edits') or [])]
text = '\n'.join(a for a in added if a)
if not text.strip():
    sys.exit(0)

norm = path.replace('\\', '/')
file = norm.rsplit('/', 1)[-1]
is_code = re.search(r'\.(java|kt)$', path) is not None
is_xml = not is_code

# Legacy colour names deleted in u24 (keep in sync with FALLBACK_LEGACY in token_audit.py). Longest first.
legacy = '|'.join([
    'eventalkAzulSecundario', 'eventalkNaranja', 'eventalkAzul',
    'c_azul_claro', 'c_verde_claro', 'c_azul_oscuro', 'c_verde_oscuro',
    'marron_claro', 'azul_marino', 'gris_oscuro', 'gris_claro',
    'purple_200', 'purple_500', 'purple_700', 'teal_200', 'teal_700',
    'transparent', 'amarillo', 'naranja', 'magenta', 'purpura',
    'blanco', 'black', 'white', 'beige', 'plata', 'verde', 'azul', 'gris', 'rojo', 'teal',
])

checks = {
    'legacy colour (@color/ or R.color.)': rf'(@color/|R\.color\.)({legacy})(?!\w)',
    '@android:color/white|black': r'@android:color/(white|black)\b|\bandroid\.R\.color\.(white|black)\b',
    'fraunces (font is being removed)': r'(?i)fraunces',
}
if file not in ('palette.xml', 'colors.xml'):
    checks['palette primitive (use a bb_* token)'] = r'(@color/|R\.color\.)(pine|forest|sage|seafoam|honey|mist|lichen|dusk|ember)_\d+(_a\d+)?(?!\w)'
if is_code:
    checks['Color.WHITE/BLACK/LTGRAY/parseColor'] = r'\bColor\s*\.\s*(WHITE|BLACK|LTGRAY|parseColor)\b'
    checks['setTextSize(<literal>)'] = r'\bsetTextSize\s*\(\s*(TypedValue\s*\.\s*COMPLEX_UNIT_SP\s*,\s*)?-?\d'
    checks['R.font. (use TextAppearance)'] = r'\bR\.font\.'
    checks['setTheme( (theme is static)'] = r'\bsetTheme\s*\('
if is_xml:
    if re.search(r'/res/(layout|drawable)[^/]*/', norm) and not file.startswith('ic_launcher'):
        checks['hex literal in layout/drawable'] = r'(?<![\w&])#([0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{4}|[0-9a-fA-F]{3})(?![0-9a-zA-Z_])'
    checks['textSize as literal sp (use @dimen/text_* or a TextAppearance)'] = r'textSize\s*=\s*"\s*-?\d+(\.\d+)?\s*sp\s*"|<item\s+name\s*=\s*"(android:)?textSize"\s*>\s*-?\d+(\.\d+)?\s*sp\s*<'
    if not (re.search(r'/res/font[^/]*/', norm) or file in ('styles_typography.xml', 'themes.xml')):
        checks['@font/ outside styles_typography/themes'] = r'@font/'

lines = []
for label, pattern in checks.items():
    found = []
    for m in re.finditer(pattern, text):
        v = m.group(0).strip()
        if v not in found:
            found.append(v)
    if found:
        lines.append(f'  - {label}: {", ".join(found[:6])}')
if not lines:
    sys.exit(0)

err = sys.stderr
print(f'[bb-tokens] Design-token violation(s) added to {file}:', file=err)
for l in lines:
    print(l, file=err)
print('CLAUDE.md rule #2: new UI must use bb_* colours + TextAppearance.BBetter.* (no hex, primitives, white/black, @font/, setTheme).', file=err)
print('If you are editing a pre-existing legacy screen this is fine - leave it. If this is NEW UI, switch to the token. '
      'Ratchet: python3 .claude/scripts/token_audit.py --baseline .claude/scripts/token_audit_baseline.json (warning only; the edit was kept)', file=err)
sys.exit(2)
