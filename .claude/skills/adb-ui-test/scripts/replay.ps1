<#
.SYNOPSIS
    Replays a recorded scenario (recordings/<Scenario>.log) with NO model in the
    loop. Thin wrapper: the engine is `bbadb.py replay` (one implementation for both
    OSes), so this script only maps its parameters to that command's flags and returns
    its exit code (0 PASS, 1 FAIL, 2 no recording).

    By default there is no recorded pacing between steps: each TAP/ASSERT polls for its
    node, a WAIT before a TAP/ASSERT only caps that poll, START force-stops the app first.

.PARAMETER Scenario   Recording name under recordings/.
.PARAMETER Speed      Delay multiplier for recorded gaps (only matters with -MaxSettle > 0).
.PARAMETER MinSettle  Floor for inter-step delay in ms (default 0 = none).
.PARAMETER MaxSettle  Ceiling for inter-step delay in ms (default 0 = none; 4000 with -MinSettle 400 = the old pacing).

.EXAMPLE
    .\replay.ps1 progress-smoke
    .\replay.ps1 progress-smoke -MinSettle 400 -MaxSettle 4000
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$Scenario,
    [double]$Speed,
    [int]$MinSettle,
    [int]$MaxSettle,
    [string]$Serial,
    [string]$Pkg
)

$py = Get-Command python -ErrorAction SilentlyContinue
if (-not $py) { $py = Get-Command python3 -ErrorAction SilentlyContinue }
if (-not $py) { Write-Output 'python not found: replay needs it (bbadb.py replay)'; exit 2 }

# Only what the caller passed goes on: the defaults live in bbadb.py.
$flags = @()
if ($PSBoundParameters.ContainsKey('Speed'))     { $flags += @('--speed', $Speed.ToString([cultureinfo]::InvariantCulture)) }
if ($PSBoundParameters.ContainsKey('MinSettle')) { $flags += @('--min-settle', $MinSettle) }
if ($PSBoundParameters.ContainsKey('MaxSettle')) { $flags += @('--max-settle', $MaxSettle) }
if ($Serial) { $flags += @('--serial', $Serial) }
if ($Pkg)    { $flags += @('--pkg', $Pkg) }

& $py.Source (Join-Path $PSScriptRoot 'bbadb.py') replay $Scenario @flags
exit $LASTEXITCODE
