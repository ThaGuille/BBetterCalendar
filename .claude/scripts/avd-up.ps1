<#
    Pone en marcha el AVD de pruebas por la vía más rápida:
      1. si ya hay un emulator-* en estado `device`, lo reutiliza (~1,5 s);
      2. si no, arranca -Avd (api36) desde el snapshot -Snapshot (bt_quick, ~16-30 s) sin guardar al salir;
      3. si el snapshot no existe, arranca en frío, lo guarda una vez y sigue.
    -Clean fuerza un arranque en frío (-no-snapshot, ~50-60 s): solo para un check que necesita un dispositivo limpio.
    El snapshot por defecto (default_boot) del api36 estaba roto («Device 'cache' does not have the requested snapshot»)
    y cada «arranque rápido» acababa en uno en frío; por eso se usa uno con nombre propio.

    Uso:  powershell -NoProfile -ExecutionPolicy Bypass -File .claude\scripts\avd-up.ps1 [-Avd api36] [-Clean] [-Window] [-TimeoutSec 300]
    Sin -Window arranca sin ventana (-no-window), como los agentes.
    Imprime «AVD READY <serial> (<cómo>, <s> s)» y sale con 0, o «AVD NOT READY ...» y 1.
#>
param([string]$Avd = 'api36', [string]$Snapshot = 'bt_quick', [switch]$Clean, [switch]$Window, [int]$TimeoutSec = 300)

$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$emu = Join-Path $sdk 'emulator\emulator.exe'
$t0 = Get-Date

function Get-Emulators { @(& $adb devices 2>$null | ForEach-Object { if ($_ -match '^(emulator-\d+)\s+device\b') { $Matches[1] } }) }
function Wait-Ready([string]$serial) {
    while (((Get-Date) - $t0).TotalSeconds -lt $TimeoutSec) {
        $boot = (& $adb -s $serial shell getprop sys.boot_completed 2>$null | Out-String).Trim()
        if ($boot -eq '1') { & $adb -s $serial shell pm path android 2>$null | Out-Null; if ($LASTEXITCODE -eq 0) { return $true } }
        Start-Sleep -Seconds 1
    }
    return $false
}
function Start-Emu([string[]]$emuArgs) {
    $headless = if ($Window) { @() } else { @('-no-window') }
    Start-Process -FilePath $emu -ArgumentList (@('-avd', $Avd, '-no-audio', '-no-boot-anim') + $headless + $emuArgs) -WindowStyle Hidden | Out-Null
    while (((Get-Date) - $t0).TotalSeconds -lt $TimeoutSec) { $e = @(Get-Emulators); if ($e.Count) { return $e[0] }; Start-Sleep -Seconds 1 }
    return $null
}
function Done([string]$serial, [string]$how) { 'AVD READY {0} ({1}, {2:N0} s)' -f $serial, $how, ((Get-Date) - $t0).TotalSeconds; exit 0 }

$running = @(Get-Emulators)
if ($running -and -not $Clean) { if (Wait-Ready $running[0]) { Done $running[0] 'reused' } }

$snapDir = Join-Path $env:USERPROFILE (".android\avd\{0}.avd\snapshots\{1}" -f $Avd, $Snapshot)
if ($Clean) {
    $s = Start-Emu @('-no-snapshot')
    if ($s -and (Wait-Ready $s)) { Done $s 'cold boot (-Clean)' }
} elseif (Test-Path $snapDir) {
    $s = Start-Emu @('-snapshot', $Snapshot, '-no-snapshot-save')
    if ($s -and (Wait-Ready $s)) { Done $s "snapshot $Snapshot" }
} else {
    $s = Start-Emu @('-no-snapshot-load')
    if ($s -and (Wait-Ready $s)) {
        Start-Sleep -Seconds 15
        & $adb -s $s emu avd snapshot save $Snapshot | Out-Null
        Done $s "cold boot, snapshot $Snapshot saved for next time"
    }
}
'AVD NOT READY after {0} s (adb devices: {1})' -f $TimeoutSec, (@(Get-Emulators) -join ', ')
exit 1
