<#
    Shared device-selection helper for the BBetter ADB testing system.

    Dot-sourced by every script that talks to adb. Resolves which serial to
    drive: if the caller passed -Serial explicitly, that wins outright (so an
    operator can still deliberately target a phone). Then $env:ANDROID_SERIAL
    (with-emulator.py sets it to the emulator it leased, so parallel units
    each drive their own). Otherwise this skill
    must default to an attached emulator-* device and never silently fall
    back to a physical phone, even if the phone is connected and is the
    device preselected/active in Android Studio.
#>

# The unit holding a LIVE lease on $serial, else $null. A lease that names processes (pid=, child=) is live while any
# of them runs, however old; one taken by hand (no pids) is live until it is 45 min old (emulator-lease's default).
function Get-BBLeaseHolder([string]$serial) {
    $f = Join-Path $env:LOCALAPPDATA ('bbetter\emulator-leases\{0}.lock' -f $serial)
    if (-not (Test-Path $f)) { return $null }
    $first = [string](Get-Content $f -TotalCount 1)
    $pids = @([regex]::Matches($first, '\b(?:pid|child)=(\d+)\b') | ForEach-Object { [int]$_.Groups[1].Value })
    if ($pids.Count) { $live = [bool]($pids | Where-Object { Get-Process -Id $_ -ErrorAction SilentlyContinue }) }
    else { $live = ((Get-Date) - (Get-Item $f).LastWriteTime).TotalMinutes -lt 45 }
    if (-not $live) { return $null }
    $h = ($first -split ' ')[0]
    if ($h) { $h } else { '?' }
}

function Get-BBSerial {
    param([string]$Requested)

    if ($Requested) { return $Requested }
    if ($env:ANDROID_SERIAL) { return $env:ANDROID_SERIAL }

    $lines = & adb devices 2>$null
    $emulators = @($lines | ForEach-Object { if ($_ -match '^(emulator-\S+)\s+device\b') { $Matches[1] } })
    # Never an emulator a unit has leased (with-emulator.py / emulator-lease): the caller's own unit ($env:BT_UNIT) is fine.
    $leased = @()
    foreach ($e in $emulators) {
        $holder = Get-BBLeaseHolder $e
        if (-not $holder -or ($env:BT_UNIT -and $holder -eq $env:BT_UNIT)) { return $e }
        $leased += "$e by $holder"
    }
    if ($emulators.Count) {
        [Console]::Error.WriteLine('every attached emulator is leased by a unit (' + ($leased -join ', ') + '): run through with-emulator.py or set ANDROID_SERIAL')
        exit 2
    }

    # No attached emulator-* device matched. Fall back to the conventional
    # default rather than guessing a physical device's serial -- if no
    # emulator is actually running this fails loudly on the adb call instead
    # of silently driving a connected phone.
    return 'emulator-5554'
}
