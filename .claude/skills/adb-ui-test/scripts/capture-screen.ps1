<#
.SYNOPSIS
    Visual checkpoint for the BBetter ADB testing system: grab a real PNG of the
    current screen and save it host-side so the agent can SEE it (via the Read
    tool) -- the ONE escape hatch from the otherwise screenshot-free doctrine.

    Use this ONLY when a visual judgement is actually required: a chart/graph
    rendered (MPAndroidChart is canvas-drawn and INVISIBLE to the uiautomator
    dump), a layout/palette spot-check, or attaching a failure artifact. For
    structure/text/crash checks keep using find-node.ps1 -- it is far cheaper
    and replay-friendly. A PNG only becomes useful once the agent reads it,
    which costs image tokens, so capture at checkpoints, not every step.

.WHY device-side screencap + adb pull (NOT `exec-out ... > out.png`)
    The upstream skydoves recipe `adb exec-out screencap -p > out.png` is a BASH
    recipe. In PowerShell 5.1 the `>` operator re-encodes the byte stream as
    UTF-16 text and corrupts the PNG -- the same class of bug as the CRLF trap
    it was meant to dodge. `adb pull` is binary-clean regardless of host shell,
    so we screencap to the device then pull. Robust on Windows.

.PARAMETER Label
    Filename stem for the saved PNG. Defaults to a UTC timestamp. The file lands
    at <OutDir>\<Label>.png. Spaces/odd chars are sanitised.

.PARAMETER OutDir
    Host directory for PNGs. Defaults to the skill's captures\ folder.

.PARAMETER DevicePath
    Scratch path on the device. Removed after pull unless -Keep.

.PARAMETER Keep
    Leave the screenshot on the device (default: delete it after pull).

.PARAMETER Serial
    adb device serial. Defaults to the first attached emulator-* device --
    even if a phone is also connected and preselected in Android Studio.
    Pass explicitly to target something else (e.g. a physical device).

.OUTPUTS
    Prints 'SAVED <abs-path>' on success (Read that path to view the image).
    Exit 0 = saved, 2 = capture/pull failed.

.EXAMPLE
    .\capture-screen.ps1 -Label progress-month-chart
    # -> SAVED C:\...\.claude\skills\adb-ui-test\captures\progress-month-chart.png
    # then the agent uses the Read tool on that path to inspect the chart.
#>
[CmdletBinding()]
param(
    [string]$Label,
    [string]$OutDir,
    [string]$DevicePath = '/sdcard/bb_shot.png',
    [switch]$Keep,
    [string]$Serial = ''
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_device.ps1')
$Serial = Get-BBSerial -Requested $Serial

if (-not $Label)  { $Label = 'shot-' + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() }
# Sanitise: only keep filename-safe chars so -Label can come straight from a step line.
$Label = ($Label -replace '[^A-Za-z0-9._-]', '_')

if (-not $OutDir) { $OutDir = Join-Path (Split-Path $PSScriptRoot -Parent) 'captures' }
if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir | Out-Null }

$hostPath = Join-Path $OutDir "$Label.png"

# 1) Encode a PNG on the device (-p is mandatory; without it the file is raw RGBA).
& adb -s $Serial shell screencap -p $DevicePath | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Output "CAPTURE FAILED on ${Serial}: screencap exit $LASTEXITCODE"
    exit 2
}

# 2) Pull binary-clean to the host (works on Windows where `>` would corrupt bytes).
& adb -s $Serial pull $DevicePath $hostPath | Out-Null
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $hostPath)) {
    Write-Output "PULL FAILED on ${Serial}: could not retrieve $DevicePath"
    exit 2
}

if (-not $Keep) { & adb -s $Serial shell rm -f $DevicePath 2>$null | Out-Null }

# Sanity: a real PNG starts with the 8-byte signature 89 50 4E 47 0D 0A 1A 0A.
$sig = [System.IO.File]::ReadAllBytes($hostPath) | Select-Object -First 8
$isPng = ($sig.Count -ge 4 -and $sig[0] -eq 0x89 -and $sig[1] -eq 0x50 -and $sig[2] -eq 0x4E -and $sig[3] -eq 0x47)
if (-not $isPng) {
    Write-Output "WARNING: $hostPath does not start with the PNG signature (possible corruption)."
    exit 2
}

$size = (Get-Item $hostPath).Length
Write-Output "SAVED $hostPath ($size bytes)  -- Read this path to view the screen."
exit 0
