<#
.SYNOPSIS
    Recorder for the BBetter ADB testing system. Performs ONE UI step live AND
    appends it (with a wall-clock timestamp) to recordings/<scenario>.log, so the
    flow can later be replayed cheaply by replay.ps1 with no model in the loop.

    Screenshot-free: checkpoints are XML-dump assertions, not image diffs.
    Mirrors the record/replay idea from tobrun/android-qa-agent.

.PARAMETER Scenario
    Recording name -> recordings/<Scenario>.log  (required).

.PARAMETER Init
    Start a fresh recording: truncate the log, write a header, clear logcat buffers.

    One action per call (pick exactly one):
.PARAMETER Start    Activity component to launch, e.g. '.configuration.SplashActivity'
.PARAMETER Grant    Runtime permission to grant
.PARAMETER Clear    pm clear the package (hermetic reset)
.PARAMETER Tap      resource-id suffix to tap
.PARAMETER TapText  visible text/desc substring to tap
.PARAMETER Type     text to input
.PARAMETER Key      keyevent code (e.g. 4 = BACK)
.PARAMETER Swipe    x1 y1 x2 y2 durMs
.PARAMETER Wait     milliseconds to sleep
.PARAMETER AssertId resource-id suffix to assert exists (+ optional -Expect substring)
.PARAMETER Expect   substring the asserted node's text must contain

.EXAMPLE
    .\bb-adb.ps1 -Scenario progress-smoke -Init
    .\bb-adb.ps1 -Scenario progress-smoke -Start .configuration.SplashActivity
    .\bb-adb.ps1 -Scenario progress-smoke -Tap navigation_progress
    .\bb-adb.ps1 -Scenario progress-smoke -Tap granularity_month
    .\bb-adb.ps1 -Scenario progress-smoke -AssertId range_label
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$Scenario,
    [switch]$Init,
    [string]$Start,
    [string]$Grant,
    [switch]$Clear,
    [string]$Tap,
    [string]$TapText,
    [string]$Type,
    [string]$Key,
    [int[]]$Swipe,
    [int]$Wait,
    [string]$AssertId,
    [string]$Expect,
    [string]$Serial = 'emulator-5554',
    [string]$Pkg    = 'com.example.bbettercalendar'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_steps.ps1')

$recDir = Join-Path (Split-Path $PSScriptRoot -Parent) 'recordings'
if (-not (Test-Path $recDir)) { New-Item -ItemType Directory -Path $recDir | Out-Null }
$log = Join-Path $recDir "$Scenario.log"

if ($Init) {
    "# scenario: $Scenario"                                   | Out-File $log -Encoding utf8
    "# recorded: $(Get-Date -Format o)  serial=$Serial pkg=$Pkg" | Out-File $log -Encoding utf8 -Append
    & adb -s $Serial logcat -c -b crash 2>$null
    & adb -s $Serial logcat -c          2>$null
    Write-Output "Initialized recording: $log"
    if (-not ($Start -or $Grant -or $Clear -or $Tap -or $TapText -or $Type -or $Key -or $Swipe -or $Wait -or $AssertId)) {
        return
    }
}

# Build the single step string from whichever action param was supplied.
$step = $null
if     ($Start)             { $step = "START $Start" }
elseif ($Grant)            { $step = "GRANT $Grant" }
elseif ($Clear)            { $step = "CLEAR" }
elseif ($Tap)              { $step = "TAP id=$Tap" }
elseif ($TapText)          { $step = "TAP text=$TapText" }
elseif ($PSBoundParameters.ContainsKey('Type')) { $step = "TYPE $Type" }
elseif ($PSBoundParameters.ContainsKey('Key'))  { $step = "KEY $Key" }
elseif ($Swipe)            { $step = "SWIPE $($Swipe -join ' ')" }
elseif ($PSBoundParameters.ContainsKey('Wait')) { $step = "WAIT $Wait" }
elseif ($AssertId)         {
    $step = if ($Expect) { "ASSERT id=$AssertId expect~=$Expect" } else { "ASSERT id=$AssertId" }
}

if (-not $step) {
    Write-Output "No action supplied. Pass one of -Start/-Grant/-Clear/-Tap/-TapText/-Type/-Key/-Swipe/-Wait/-AssertId (or just -Init)."
    exit 2
}

$result = Invoke-BBStep -Step $step -Serial $Serial -Pkg $Pkg -ScriptDir $PSScriptRoot
$epoch  = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

# Record even a failed step so the log mirrors what was attempted; flag it for the human.
"$epoch`t$step" | Out-File $log -Encoding utf8 -Append

$tag = if ($result.ok) { 'OK ' } else { 'FAIL' }
Write-Output "[$tag] $step  ->  $($result.info)"
if (-not $result.ok) { exit 1 }
exit 0
