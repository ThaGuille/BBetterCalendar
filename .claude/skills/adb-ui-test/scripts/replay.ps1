<#
.SYNOPSIS
    Replays a recorded scenario (recordings/<Scenario>.log) with NO model in the
    loop -- re-resolving every TAP by resource-id live, honoring recorded inter-step
    delays (scaled by -Speed), checking each ASSERT, and failing fast on any missing
    node or FATAL EXCEPTION in the crash log. Screenshot-free.

.PARAMETER Scenario   Recording name under recordings/.
.PARAMETER Speed      Delay multiplier for recorded gaps (1.0 = real time, 0.5 = 2x faster).
.PARAMETER MinSettle  Floor for inter-step delay in ms (taps must not race the UI).
.PARAMETER MaxSettle  Ceiling for inter-step delay in ms.

.EXAMPLE
    .\replay.ps1 progress-smoke
    .\replay.ps1 progress-smoke -Speed 0.5
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$Scenario,
    [double]$Speed   = 1.0,
    [int]$MinSettle  = 400,
    [int]$MaxSettle  = 4000,
    [string]$Serial  = 'emulator-5554',
    [string]$Pkg     = 'com.example.bbettercalendar'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_steps.ps1')

$log = Join-Path (Split-Path $PSScriptRoot -Parent) "recordings\$Scenario.log"
if (-not (Test-Path $log)) { Write-Output "No recording: $log"; exit 2 }

function Test-Crash {
    $crash = (& adb -s $Serial logcat -d -b crash) -join "`n"
    return ($crash -match 'FATAL EXCEPTION')
}

# Parse: skip comments/blanks; each line is "<epochMs>\t<STEP>".
$steps = @()
foreach ($line in (Get-Content $log)) {
    if ($line -match '^\s*#' -or $line -match '^\s*$') { continue }
    $parts = $line -split "`t", 2
    if ($parts.Count -eq 2) { $steps += [pscustomobject]@{ T = [long]$parts[0]; Step = $parts[1].Trim() } }
}
if ($steps.Count -eq 0) { Write-Output "Recording has no steps: $log"; exit 2 }

Write-Output "Replaying '$Scenario' ($($steps.Count) steps, speed x$Speed) on $Serial"
& adb -s $Serial logcat -c -b crash 2>$null    # start from a clean crash buffer

$failures = 0
$prevT = $steps[0].T
for ($i = 0; $i -lt $steps.Count; $i++) {
    $s = $steps[$i]
    if ($i -gt 0) {
        $delay = [int](($s.T - $prevT) * $Speed)
        if ($delay -lt $MinSettle) { $delay = $MinSettle }
        if ($delay -gt $MaxSettle) { $delay = $MaxSettle }
        Start-Sleep -Milliseconds $delay
    }
    $prevT = $s.T

    $r = Invoke-BBStep -Step $s.Step -Serial $Serial -Pkg $Pkg -ScriptDir $PSScriptRoot
    $tag = if ($r.ok) { 'OK  ' } else { 'FAIL' }
    Write-Output ("  [{0}] {1,-40} {2}" -f $tag, $s.Step, $r.info)
    if (-not $r.ok) { $failures++ }

    if (Test-Crash) {
        Write-Output "  [FAIL] FATAL EXCEPTION detected in crash log after: $($s.Step)"
        $failures++
        break
    }
}

Write-Output ""
if ($failures -eq 0) {
    Write-Output "PASS: '$Scenario' ($($steps.Count) steps, no crash)"
    exit 0
} else {
    Write-Output "FAIL: '$Scenario' had $failures failing step(s). See above."
    exit 1
}
