# verify-ui-reminder.ps1  —  Stop hook (CLAUDE.md rule #7)
#
# Differentiates a *small bug fix* from a *big implementation* by the size of the
# uncommitted runtime diff (git diff HEAD over app/src/main java + layout + nav).
# When the diff is BIG and an emulator is attached, it blocks the stop ONCE and
# hands Claude an instruction to verify the app actually runs via the `ui-tester`
# subagent. Small fixes, clean trees, and "no device" stay silent.
#
# Contract used (Stop hook):
#   - stdin JSON has `stop_hook_active`; when true we already continued once -> never re-block (loop guard).
#   - stdout `{"decision":"block","reason":"..."}` (exit 0) blocks the stop and feeds `reason` to Claude.
#   - any failure -> exit 0 silently: a verify hook must NEVER trap the user's ability to stop.
#
# Tunables (env overrides; defaults chosen so a typical bug fix stays under them):
#   BB_UI_VERIFY_LINES  total changed lines that count as "big"  (default 50)
#   BB_UI_VERIFY_FILES  changed file count that counts as "big"  (default 4)

try {
    # --- loop guard: read hook input, bail if this stop was already continued by us ---
    $raw = [Console]::In.ReadToEnd()
    if ($raw) {
        try { if ([bool]($raw | ConvertFrom-Json).stop_hook_active) { exit 0 } } catch {}
    }

    $linesThreshold = if ($env:BB_UI_VERIFY_LINES) { [int]$env:BB_UI_VERIFY_LINES } else { 50 }
    $filesThreshold = if ($env:BB_UI_VERIFY_FILES) { [int]$env:BB_UI_VERIFY_FILES } else { 4 }

    $repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
    $marker   = Join-Path $env:TEMP 'bb-ui-verify.flag'

    # --- measure the uncommitted runtime diff vs HEAD ---
    $paths   = @('app/src/main/java', 'app/src/main/res/layout', 'app/src/main/res/navigation')
    $numstat = & git -C $repoRoot diff --numstat HEAD -- $paths 2>$null
    if ($LASTEXITCODE -ne 0) { exit 0 }   # not a repo / no HEAD / git unavailable

    $totalLines = 0; $filesChanged = 0
    foreach ($line in $numstat) {
        if (-not $line) { continue }
        $cols = $line -split "`t"
        if ($cols.Count -lt 3) { continue }
        $filesChanged++
        $add = if ($cols[0] -eq '-') { 0 } else { [int]$cols[0] }   # '-' == binary
        $del = if ($cols[1] -eq '-') { 0 } else { [int]$cols[1] }
        $totalLines += $add + $del
    }

    $isBig = ($totalLines -ge $linesThreshold) -or ($filesChanged -ge $filesThreshold)
    if (-not $isBig) {
        Remove-Item $marker -ErrorAction SilentlyContinue   # re-arm once changes are committed/reverted
        exit 0
    }
    if (Test-Path $marker) { exit 0 }   # already reminded for this uncommitted batch

    # --- only worth it if there's a device to actually test on ---
    $devices = & adb devices 2>$null
    $online  = $false
    if ($LASTEXITCODE -eq 0 -and $devices) {
        foreach ($d in $devices) { if ($d -match '^\S+\s+device\s*$') { $online = $true; break } }
    }
    if (-not $online) { exit 0 }   # operator keeps one up; nothing to drive -> stay silent

    # --- big + device + first time: block the stop, hand Claude the instruction ---
    Set-Content -Path $marker -Value '1' -ErrorAction SilentlyContinue

    $reason = "Big uncommitted runtime change detected ($totalLines changed lines across $filesChanged files under app/src/main java+layout+navigation) and an emulator is attached. Per CLAUDE.md rule #7, verify the app actually RUNS before finishing -- don't trust the compiler alone. Delegate to the ui-tester subagent: it builds+installs the debug APK, drives the emulator by resource-id via the adb-ui-test flow, and scans 'logcat -b crash' for FATAL EXCEPTION, in an isolated context. If this change does NOT affect UI/runtime behaviour (pure refactor, docs, gradle/config), say so in one line and stop -- this won't repeat for the current diff."

    @{ decision = 'block'; reason = $reason } | ConvertTo-Json -Compress
    exit 0
}
catch {
    exit 0   # never trap the user
}
