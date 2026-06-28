# update-status.ps1  —  regenerates the auto block of STATUS.md from repo state.
#
# Wired as a Stop + SessionStart hook (see .claude/settings.local.json) so the
# project's "where am I" page stays current without anyone editing it by hand.
#
# Deterministic + silent: it reads the source-of-truth files (the Status: lines in
# .claude/specs + .claude/plans, the tasks.md checkboxes, and `git log`) and rewrites
# ONLY the text between the <!-- AUTO:BEGIN --> / <!-- AUTO:END --> markers in STATUS.md.
# Everything outside those markers (the hand-written "Next focus" note, the footer) is
# preserved. It writes only when the content actually changed, so it adds no git churn
# on no-op runs. It emits no stdout and always exits 0 -> safe in any hook slot.

$ErrorActionPreference = 'SilentlyContinue'

try {
    $root = $env:CLAUDE_PROJECT_DIR
    if (-not $root) { $root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path }
    $statusFile = Join-Path $root 'STATUS.md'

    $beginMarker = '<!-- AUTO:BEGIN -->'
    $endMarker   = '<!-- AUTO:END -->'

    # --- helpers ---------------------------------------------------------------
    function To-RelLink($absPath) {
        $rel = $absPath.Substring($root.Length).TrimStart('\', '/')
        return ($rel -replace '\\', '/')
    }
    function Esc-Cell($s) { if ($null -eq $s) { '' } else { $s -replace '\|', '\|' } }

    # title (first H1) + status (first "Status:" line) from a markdown file
    function Get-MdMeta($path) {
        $title = $null; $status = $null
        foreach ($l in (Get-Content -LiteralPath $path -Encoding UTF8 -ErrorAction SilentlyContinue)) {
            if (-not $title  -and $l -match '^\s*#\s+(.+?)\s*$') { $title = $Matches[1] }
            if (-not $status -and $l -match '(?i)\bstatus:\s*(.+?)\s*$') {
                $status = ($Matches[1] -replace '\*+', '').Trim()
            }
            if ($title -and $status) { break }
        }
        if (-not $title)  { $title  = [IO.Path]::GetFileNameWithoutExtension($path) }
        if (-not $status) { $status = '(none)' }
        [pscustomobject]@{ Title = $title; Status = $status; Path = $path }
    }

    # bucket a free-text status into a sort rank (undone first) + a normalized tag
    function Status-Rank($s) {
        $l = "$s".ToLower()
        if ($l -match 'in progress|applied|implement') { return 0 }
        if ($l -match 'proposed')                       { return 1 }
        if ($l -match 'merged|done|archived|complete')  { return 3 }
        if ($l -match 'abandon')                        { return 4 }
        return 2
    }

    # "(done/total tasks)" from a tasks.md checkbox list
    function Task-Progress($path) {
        if (-not (Test-Path $path)) { return $null }
        $done = 0; $total = 0
        foreach ($l in (Get-Content -LiteralPath $path -Encoding UTF8)) {
            if ($l -match '^\s*-\s*\[( |x|X)\]') { $total++; if ($Matches[1] -match 'x') { $done++ } }
        }
        if ($total -eq 0) { return $null }
        return "$done/$total"
    }

    # --- gather: git ----------------------------------------------------------
    $branch = (& git -C $root rev-parse --abbrev-ref HEAD 2>$null)
    if (-not $branch) { $branch = '(unknown)' }
    $headLine = (& git -C $root log -1 --pretty=format:'%h (%ad)' --date=short 2>$null)
    $commits  = @(& git -C $root log -5 --pretty=format:'%h %ad %s' --date=short 2>$null)

    # --- gather: active /spec changes ----------------------------------------
    $specRows = @()
    $changesDir = Join-Path $root '.claude\specs\changes'
    if (Test-Path $changesDir) {
        foreach ($d in (Get-ChildItem -LiteralPath $changesDir -Directory | Sort-Object Name)) {
            $prop = Join-Path $d.FullName 'proposal.md'
            if (-not (Test-Path $prop)) { continue }
            $m = Get-MdMeta $prop
            $tasks = Task-Progress (Join-Path $d.FullName 'tasks.md')
            $specRows += [pscustomobject]@{
                Title  = $m.Title
                Status = $m.Status
                Tasks  = if ($tasks) { $tasks } else { '-' }
                Link   = To-RelLink $prop
            }
        }
    }

    # --- gather: archived /spec changes --------------------------------------
    $archived = @()
    $archiveDir = Join-Path $root '.claude\specs\archive'
    if (Test-Path $archiveDir) {
        $archived = @(Get-ChildItem -LiteralPath $archiveDir -Directory | Select-Object -ExpandProperty Name)
    }

    # --- gather: capability docs ---------------------------------------------
    $capRows = @()
    $capDir = Join-Path $root '.claude\specs\capabilities'
    if (Test-Path $capDir) {
        foreach ($f in (Get-ChildItem -LiteralPath $capDir -Filter *.md | Sort-Object Name)) {
            $m = Get-MdMeta $f.FullName
            $capRows += [pscustomobject]@{ Title = $m.Title; Link = To-RelLink $f.FullName }
        }
    }

    # --- gather: plans --------------------------------------------------------
    $planRows = @()
    $plansDir = Join-Path $root '.claude\plans'
    if (Test-Path $plansDir) {
        foreach ($f in (Get-ChildItem -LiteralPath $plansDir -Filter *.md)) {
            $m = Get-MdMeta $f.FullName
            $planRows += [pscustomobject]@{
                Title  = $m.Title
                Status = $m.Status
                Rank   = (Status-Rank $m.Status)
                Link   = To-RelLink $f.FullName
            }
        }
        $planRows = $planRows | Sort-Object Rank, Title
    }

    # --- build the auto block -------------------------------------------------
    $L = New-Object System.Collections.Generic.List[string]
    $L.Add('_Tables below are auto-generated from repo state by `.claude/hooks/update-status.ps1`. Do not edit between the AUTO markers._')
    $L.Add('')
    $L.Add("**Branch:** ``$branch``  |  **State as of commit:** $headLine")
    $L.Add('')

    $L.Add('### 1. What we touched last (recent commits)')
    if ($commits.Count) { foreach ($c in $commits) { $L.Add("- $c") } } else { $L.Add('_No commits._') }
    $L.Add('')

    $L.Add('### 2. In flight - active `/spec` changes')
    if ($specRows.Count) {
        $L.Add('| Change | Status | Tasks | Proposal |')
        $L.Add('|---|---|---|---|')
        foreach ($r in $specRows) {
            $L.Add("| $(Esc-Cell $r.Title) | $(Esc-Cell $r.Status) | $($r.Tasks) | [proposal]($($r.Link)) |")
        }
    } else { $L.Add('_None active._') }
    $L.Add('')

    $L.Add('### 3. Living capability docs (how the system behaves now)')
    if ($capRows.Count) {
        foreach ($r in $capRows) { $L.Add("- [$(Esc-Cell $r.Title)]($($r.Link))") }
    } else { $L.Add('_None yet._') }
    if ($archived.Count) { $L.Add(''); $L.Add('**Archived changes:** ' + (($archived | ForEach-Object { "``$_``" }) -join ', ')) }
    $L.Add('')

    $L.Add('### 4. Plans (`.claude/plans`) - undone first')
    if ($planRows.Count) {
        $L.Add('| Plan | Status | File |')
        $L.Add('|---|---|---|')
        foreach ($r in $planRows) {
            $name = ($r.Link -replace '^.*/', '')
            $L.Add("| $(Esc-Cell $r.Title) | $(Esc-Cell $r.Status) | [$name]($($r.Link)) |")
        }
    } else { $L.Add('_None._') }

    $auto = ($L -join "`r`n")

    # --- splice into STATUS.md, write only on change --------------------------
    if (-not (Test-Path $statusFile)) { exit 0 }   # template missing -> nothing to refresh
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    $existing = [IO.File]::ReadAllText($statusFile, $utf8)
    $bi = $existing.IndexOf($beginMarker)
    $ei = $existing.IndexOf($endMarker)
    if ($bi -lt 0 -or $ei -le $bi) { exit 0 }       # markers gone -> don't clobber a hand-edited file

    $before = $existing.Substring(0, $bi + $beginMarker.Length)
    $after  = $existing.Substring($ei)
    $new    = $before + "`r`n" + $auto + "`r`n" + $after

    if ($new -ne $existing) {
        [IO.File]::WriteAllText($statusFile, $new, (New-Object System.Text.UTF8Encoding($false)))
    }
    exit 0
}
catch { exit 0 }   # a status refresh must never break a stop / session start
