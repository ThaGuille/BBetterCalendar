# PostToolUse guardrail — WARN ONLY (non-blocking).
# Flags legacy palette tokens added to a .java/.xml edit. CLAUDE.md rule #2:
# new UI must use the bb_* semantic palette. The edit is kept; this only warns.
# Reads the tool-call JSON from stdin (Claude Code hook contract).

$ErrorActionPreference = 'SilentlyContinue'

$raw = [Console]::In.ReadToEnd()
if ([string]::IsNullOrWhiteSpace($raw)) { exit 0 }

try { $data = $raw | ConvertFrom-Json } catch { exit 0 }

$path = $data.tool_input.file_path
if (-not $path) { exit 0 }
if ($path -notmatch '\.(java|xml)$') { exit 0 }

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

# Legacy color tokens kept only for old screens (see .claude/docs/style_guide.md).
$legacy  = 'azul|verde|purple_500|purple_700|teal_200|teal_700'
$pattern = "(@color/|R\.color\.)($legacy)\b"

$found = [regex]::Matches($text, $pattern)
if ($found.Count -eq 0) { exit 0 }

$tokens = ($found | ForEach-Object { $_.Value } | Select-Object -Unique) -join ', '
$file   = Split-Path $path -Leaf

[Console]::Error.WriteLine("[bb-palette] Legacy palette token(s) added to ${file}: $tokens")
[Console]::Error.WriteLine("CLAUDE.md rule #2: new UI must use the bb_* semantic palette + TextAppearance.BBetter.*.")
[Console]::Error.WriteLine("If you are editing a pre-existing legacy screen this is fine - leave it. If this is NEW UI, switch to the bb_* equivalent. (warning only; the edit was kept)")
# Exit 2 surfaces stderr back to Claude as feedback without undoing the edit.
exit 2
