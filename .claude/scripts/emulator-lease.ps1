<#
    Emulator lease for parallel units (big-task waves, parallel subagents).

    Several units may run at once in different worktrees, but each emulator can only host one
    install + UI check at a time. A unit takes a lease on a free emulator before `adb install`
    and gives it back after its last capture / logcat check. Leases live OUTSIDE the repo
    (%LOCALAPPDATA%\bbetter\emulator-leases) so every worktree sees the same ones.

    Usage (PowerShell, from any directory):
      .claude\scripts\emulator-lease.ps1 -Acquire -Unit u16      # prints: LEASED emulator-5556
      .claude\scripts\emulator-lease.ps1 -Release -Unit u16      # prints: RELEASED emulator-5556
      .claude\scripts\emulator-lease.ps1 -Status                  # lists emulators + holders

    -Acquire waits (polling every 15 s, up to -TimeoutMin, default 60) until an attached
    emulator-* in state `device` has no lease. A lease older than -StaleMin (default 45) is
    considered abandoned and is taken over. Exit code 0 = leased, 3 = timeout, 2 = usage.
    Then pass the serial explicitly everywhere: `adb -s <serial> ...`, and `-Serial <serial>` to
    the adb-ui-test scripts (find-node.ps1, capture-screen.ps1, replay.ps1).
#>
param(
    [switch]$Acquire,
    [switch]$Release,
    [switch]$Status,
    [string]$Unit = '',
    [int]$TimeoutMin = 60,
    [int]$StaleMin = 45
)

$ErrorActionPreference = 'Stop'
$leaseDir = Join-Path $env:LOCALAPPDATA 'bbetter\emulator-leases'
if (-not (Test-Path $leaseDir)) { New-Item -ItemType Directory -Path $leaseDir | Out-Null }

$adb = 'adb'
$sdkAdb = 'C:\Users\guill\AppData\Local\Android\Sdk\platform-tools\adb.exe'
if (Test-Path $sdkAdb) { $adb = $sdkAdb }

function Get-Emulators {
    $lines = & $adb devices 2>$null
    $out = @()
    foreach ($l in $lines) { if ($l -match '^(emulator-\d+)\s+device\b') { $out += $Matches[1] } }
    return $out
}

function Get-LeasePath([string]$serial) { return (Join-Path $leaseDir ($serial + '.lock')) }

function Read-Lease([string]$serial) {
    $p = Get-LeasePath $serial
    if (-not (Test-Path $p)) { return $null }
    $age = ((Get-Date) - (Get-Item $p).LastWriteTime).TotalMinutes
    $holder = (Get-Content $p -ErrorAction SilentlyContinue | Select-Object -First 1)
    return [pscustomobject]@{ Serial = $serial; Holder = $holder; AgeMin = [math]::Round($age, 1) }
}

function Try-Take([string]$serial) {
    $p = Get-LeasePath $serial
    $existing = Read-Lease $serial
    if ($existing -and $existing.AgeMin -ge $StaleMin) {
        Remove-Item -LiteralPath $p -Force -ErrorAction SilentlyContinue
    }
    try {
        # CreateNew is atomic: exactly one caller wins when two race for the same emulator.
        $fs = [IO.File]::Open($p, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
        $bytes = [Text.Encoding]::ASCII.GetBytes("$Unit $(Get-Date -Format s)`n")
        $fs.Write($bytes, 0, $bytes.Length)
        $fs.Close()
        return $true
    } catch {
        return $false
    }
}

if ($Status) {
    $emus = Get-Emulators
    if (-not $emus) { 'NO EMULATOR ATTACHED' }
    foreach ($e in $emus) {
        $l = Read-Lease $e
        if ($l) { "$e  leased by '$($l.Holder)' ($($l.AgeMin) min)" } else { "$e  free" }
    }
    Get-ChildItem $leaseDir -Filter *.lock | ForEach-Object {
        $s = $_.BaseName
        if ($emus -notcontains $s) { "$s  (lease file, emulator not attached)" }
    }
    exit 0
}

if (-not $Unit) { Write-Output 'usage: -Acquire|-Release -Unit <id>  |  -Status'; exit 2 }

if ($Release) {
    $released = $false
    Get-ChildItem $leaseDir -Filter *.lock | ForEach-Object {
        $holder = (Get-Content $_.FullName -ErrorAction SilentlyContinue | Select-Object -First 1)
        if ($holder -and $holder.Split(' ')[0] -eq $Unit) {
            Remove-Item -LiteralPath $_.FullName -Force
            Write-Output "RELEASED $($_.BaseName)"
            $released = $true
        }
    }
    if (-not $released) { Write-Output "NOTHING TO RELEASE for $Unit" }
    exit 0
}

if ($Acquire) {
    # Re-entrant: if this unit already holds a lease, return it.
    foreach ($f in (Get-ChildItem $leaseDir -Filter *.lock)) {
        $holder = (Get-Content $f.FullName -ErrorAction SilentlyContinue | Select-Object -First 1)
        if ($holder -and $holder.Split(' ')[0] -eq $Unit) {
            (Get-Item $f.FullName).LastWriteTime = Get-Date
            Write-Output "LEASED $($f.BaseName)"
            exit 0
        }
    }
    $deadline = (Get-Date).AddMinutes($TimeoutMin)
    while ((Get-Date) -lt $deadline) {
        foreach ($e in (Get-Emulators)) {
            if (Try-Take $e) { Write-Output "LEASED $e"; exit 0 }
        }
        Start-Sleep -Seconds 15
    }
    Write-Output "TIMEOUT: no free emulator after $TimeoutMin min"
    exit 3
}

Write-Output 'usage: -Acquire|-Release -Unit <id>  |  -Status'
exit 2
