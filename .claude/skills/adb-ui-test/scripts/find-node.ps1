<#
.SYNOPSIS
    Screenshot-free UI locator for the BBetter ADB testing system.

    Dumps the live uiautomator hierarchy, finds a node by resource-id or text,
    prints a COMPACT one-line result (center coords + text), and can tap it.
    The raw XML is never returned to the caller's context -- that is the whole
    point (token efficiency). Adapted from the uiautomator-dump recipe in
    hah23255/adb-android-control and nighteblis/uiautomatorDump.

.PARAMETER Id
    resource-id suffix to match (the part after ':id/'), e.g. 'range_label'.

.PARAMETER Text
    Substring to match against a node's text OR content-desc (case-insensitive).

.PARAMETER Tap
    After locating, tap the node's center via 'adb shell input tap'.

.PARAMETER List
    Discovery mode: print every interactable node as 'id | text | desc | bounds'
    (token-bounded). Ignores -Id/-Text/-Tap.

.PARAMETER Serial
    adb device serial. Defaults to the running emulator.

.OUTPUTS
    Exit code 0 = found (and tapped if -Tap), 1 = not found, 2 = dump failed.

.EXAMPLE
    .\find-node.ps1 -Id navigation_progress -Tap
    .\find-node.ps1 -Id range_label
    .\find-node.ps1 -Text "This week"
    .\find-node.ps1 -List
#>
[CmdletBinding()]
param(
    [string]$Id,
    [string]$Text,
    [switch]$Tap,
    [switch]$List,
    [string]$Serial = 'emulator-5554',
    [string]$DumpPath = '/sdcard/bb_dump.xml'
)

$ErrorActionPreference = 'Stop'

function Invoke-UiDump {
    # uiautomator dump is flaky ("could not get idle state") -- try twice.
    for ($i = 0; $i -lt 2; $i++) {
        & adb -s $Serial shell uiautomator dump $DumpPath | Out-Null
        $raw = (& adb -s $Serial shell cat $DumpPath) -join "`n"
        $start = $raw.IndexOf('<?xml')
        if ($start -lt 0) { $start = $raw.IndexOf('<hierarchy') }
        if ($start -ge 0) { return $raw.Substring($start) }
        Start-Sleep -Milliseconds 400
    }
    return $null
}

function Get-Center([string]$bounds) {
    # bounds look like "[x1,y1][x2,y2]"
    if ($bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
        $cx = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
        $cy = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
        return @($cx, $cy)
    }
    return $null
}

$rawXml = Invoke-UiDump
if (-not $rawXml) {
    Write-Output "DUMP FAILED on $Serial (uiautomator returned no hierarchy)"
    exit 2
}

try {
    [xml]$doc = $rawXml
} catch {
    Write-Output "DUMP UNPARSEABLE: $($_.Exception.Message)"
    exit 2
}

$nodes = $doc.SelectNodes('//node')

if ($List) {
    $count = 0
    foreach ($n in $nodes) {
        $rid  = $n.GetAttribute('resource-id')
        $txt  = $n.GetAttribute('text')
        $desc = $n.GetAttribute('content-desc')
        $clk  = $n.GetAttribute('clickable')
        # Only surface nodes that are actionable or carry a label.
        if (($clk -eq 'true') -or $rid -or $txt -or $desc) {
            $short = if ($rid -match ':id/(.+)$') { $Matches[1] } else { '' }
            Write-Output ("{0,-28} | {1,-24} | {2,-20} | {3}" -f $short, $txt, $desc, $n.GetAttribute('bounds'))
            $count++
            if ($count -ge 150) { Write-Output "... (truncated at 150 nodes)"; break }
        }
    }
    exit 0
}

if (-not $Id -and -not $Text) {
    Write-Output "Provide -Id, -Text, or -List."
    exit 2
}

$match = $null
foreach ($n in $nodes) {
    if ($Id) {
        $rid = $n.GetAttribute('resource-id')
        if ($rid -and ($rid -match ":id/$([regex]::Escape($Id))$")) { $match = $n; break }
    } elseif ($Text) {
        $txt  = $n.GetAttribute('text')
        $desc = $n.GetAttribute('content-desc')
        if (($txt -and $txt.ToLower().Contains($Text.ToLower())) -or
            ($desc -and $desc.ToLower().Contains($Text.ToLower()))) { $match = $n; break }
    }
}

$selector = if ($Id) { "id=$Id" } else { "text~='$Text'" }

if (-not $match) {
    Write-Output "NOT FOUND: $selector"
    exit 1
}

$center = Get-Center $match.GetAttribute('bounds')
$rid    = $match.GetAttribute('resource-id')
$txt    = $match.GetAttribute('text')
$desc   = $match.GetAttribute('content-desc')

if (-not $center) {
    Write-Output "FOUND $selector but bounds unparseable: $($match.GetAttribute('bounds'))"
    exit 1
}

Write-Output ("FOUND $selector  center=({0},{1})  text='{2}' desc='{3}'  enabled={4}" -f `
    $center[0], $center[1], $txt, $desc, $match.GetAttribute('enabled'))

if ($Tap) {
    & adb -s $Serial shell input tap $center[0] $center[1] | Out-Null
    Write-Output ("TAPPED ({0},{1})" -f $center[0], $center[1])
}

exit 0
