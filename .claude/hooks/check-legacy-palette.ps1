# PostToolUse guardrail - WARN ONLY (non-blocking).
# Flags design-token violations ADDED by a .java/.kt/.xml edit. CLAUDE.md rule #2:
# new UI must use the bb_* semantic palette + TextAppearance.BBetter.*. The edit is kept;
# this only warns. Reads the tool-call JSON from stdin (Claude Code hook contract).
#
# Same categories as .claude/scripts/token_audit.py (visual-migration ratchet, plan u1):
# keep the legacy colour list and patterns in sync with that script.

$ErrorActionPreference = 'SilentlyContinue'

$raw = [Console]::In.ReadToEnd()
if ([string]::IsNullOrWhiteSpace($raw)) { exit 0 }

try { $data = $raw | ConvertFrom-Json } catch { exit 0 }

$path = $data.tool_input.file_path
if (-not $path) { exit 0 }
if ($path -notmatch '\.(java|kt|xml)$') { exit 0 }

# Collect only the text this edit ADDED (so we warn on introductions, not pre-existing refs).
$added = @()
if ($data.tool_input.new_string) { $added += $data.tool_input.new_string }
if ($data.tool_input.content)    { $added += $data.tool_input.content }
if ($data.tool_input.edits) {
    foreach ($e in $data.tool_input.edits) {
        if ($e.new_string) { $added += $e.new_string }
    }
}
$text = ($added -join "`n")
if ([string]::IsNullOrWhiteSpace($text)) { exit 0 }

$norm   = $path -replace '\\', '/'
$file   = Split-Path $path -Leaf
$isCode = $path -match '\.(java|kt)$'
$isXml  = -not $isCode

# Legacy colour names: DELETED from values/colors.xml in u24, kept here as a blocklist so a new
# reference is flagged (final patterns; keep in sync with FALLBACK_LEGACY in token_audit.py).
# Longest first so e.g. teal_200 wins over teal.
$legacyNames = @(
    'eventalkAzulSecundario', 'eventalkNaranja', 'eventalkAzul',
    'c_azul_claro', 'c_verde_claro', 'c_azul_oscuro', 'c_verde_oscuro',
    'marron_claro', 'azul_marino', 'gris_oscuro', 'gris_claro',
    'purple_200', 'purple_500', 'purple_700', 'teal_200', 'teal_700',
    'transparent', 'amarillo', 'naranja', 'magenta', 'purpura',
    'blanco', 'black', 'white', 'beige', 'plata', 'verde', 'azul', 'gris', 'rojo', 'teal'
)
$legacy = $legacyNames -join '|'

$checks = [ordered]@{}
$checks['legacy colour (@color/ or R.color.)'] = "(@color/|R\.color\.)($legacy)(?!\w)"
$checks['@android:color/white|black'] = '@android:color/(white|black)\b|\bandroid\.R\.color\.(white|black)\b'
$checks['fraunces (font is being removed)'] = '(?i)fraunces'
# Palette primitives (values/palette.xml, u4) are only allowed in palette.xml and colors.xml.
# Families kept in sync with palette.xml; token_audit.py reads the names from the file itself.
if ($file -ne 'palette.xml' -and $file -ne 'colors.xml') {
    $checks['palette primitive (use a bb_* token)'] = '(@color/|R\.color\.)(pine|forest|sage|seafoam|honey|mist|lichen|dusk|ember)_\d+(_a\d+)?(?!\w)'
}

if ($isCode) {
    $checks['Color.WHITE/BLACK/LTGRAY/parseColor'] = '\bColor\s*\.\s*(WHITE|BLACK|LTGRAY|parseColor)\b'
    $checks['setTextSize(<literal>)'] = '\bsetTextSize\s*\(\s*(TypedValue\s*\.\s*COMPLEX_UNIT_SP\s*,\s*)?-?\d'
    $checks['R.font. (use TextAppearance)'] = '\bR\.font\.'
    $checks['setTheme( (theme is static)'] = '\bsetTheme\s*\('
}

if ($isXml) {
    $isLayoutOrDrawable = $norm -match '/res/(layout|drawable)[^/]*/' -and $file -notlike 'ic_launcher*'
    if ($isLayoutOrDrawable) {
        $checks['hex literal in layout/drawable'] = '(?<![\w&])#([0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{4}|[0-9a-fA-F]{3})(?![0-9a-zA-Z_])'
    }
    $checks['textSize as literal sp (use @dimen/text_* or a TextAppearance)'] = 'textSize\s*=\s*"\s*-?\d+(\.\d+)?\s*sp\s*"|<item\s+name\s*=\s*"(android:)?textSize"\s*>\s*-?\d+(\.\d+)?\s*sp\s*<'
    $fontAllowed = ($norm -match '/res/font[^/]*/') -or ($file -eq 'styles_typography.xml') -or ($file -eq 'themes.xml')
    if (-not $fontAllowed) {
        $checks['@font/ outside styles_typography/themes'] = '@font/'
    }
}

$lines = @()
foreach ($label in $checks.Keys) {
    $found = [regex]::Matches($text, $checks[$label])
    if ($found.Count -gt 0) {
        $tokens = ($found | ForEach-Object { $_.Value.Trim() } | Select-Object -Unique | Select-Object -First 6) -join ', '
        $lines += "  - ${label}: $tokens"
    }
}
if ($lines.Count -eq 0) { exit 0 }

[Console]::Error.WriteLine("[bb-tokens] Design-token violation(s) added to ${file}:")
foreach ($l in $lines) { [Console]::Error.WriteLine($l) }
[Console]::Error.WriteLine("CLAUDE.md rule #2: new UI must use bb_* colours + TextAppearance.BBetter.* (no hex, primitives, white/black, @font/, setTheme).")
[Console]::Error.WriteLine("If you are editing a pre-existing legacy screen this is fine - leave it. If this is NEW UI, switch to the token. Ratchet: python .claude/scripts/token_audit.py --baseline .claude/scripts/token_audit_baseline.json (warning only; the edit was kept)")
# Exit 2 surfaces stderr back to Claude as feedback without undoing the edit.
exit 2
