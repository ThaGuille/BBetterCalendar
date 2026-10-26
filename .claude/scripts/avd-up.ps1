<#
    Pone en marcha el AVD de pruebas por la vía más rápida:
      1. si ya hay un emulator-* en estado `device` que es el AVD -Avd (se mira con `adb -s <serial> emu avd name`),
         lo reutiliza (~1,5 s); uno de otro AVD no se toca ni se reutiliza;
      2. si no, arranca -Avd (api36) desde el snapshot -Snapshot (bt_quick, ~16-30 s) sin guardar al salir; si ese arranque
         termina enseguida o no llega a estar listo en -SnapshotTimeoutSec (90), se corta y se arranca UNA vez en frío
         (-no-snapshot-load);
      3. si el snapshot no existe, arranca en frío, lo guarda una vez y sigue.
    -Clean fuerza un arranque en frío (-no-snapshot, ~50-60 s): solo para un check que necesita un dispositivo limpio.
    Con -Clean y cualquier emulador ya conectado se niega (exit 1): hay que pararlo antes (`adb -s <serial> emu kill`) o
    tomar otro con .claude\scripts\emulator-lease.ps1; un emulador en marcha bloquea el AVD y no sería un arranque en frío.
    avd-up NO toma el lease (emulator-lease.ps1): si varias unidades comparten emuladores, toma el lease con
    emulator-lease.ps1 -Acquire después de este script.
    El snapshot por defecto (default_boot) del api36 estaba roto («Device 'cache' does not have the requested snapshot»)
    y cada «arranque rápido» acababa en uno en frío; por eso se usa uno con nombre propio.

    Uso:  powershell -NoProfile -ExecutionPolicy Bypass -File .claude\scriptsvd-up.ps1 [-Avd api36] [-Clean] [-Window] [-TimeoutSec 300] [-SnapshotTimeoutSec 90]
    Sin -Window arranca sin ventana (-no-window), como los agentes.
    Imprime «AVD READY <serial> (<cómo>, <s> s)» y sale con 0, o «AVD NOT READY ...» y 1.
#>
param([string]$Avd = 'api36', [string]$Snapshot = 'bt_quick', [switch]$Clean, [switch]$Window, [int]$TimeoutSec = 300, [int]$SnapshotTimeoutSec = 90)

$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$emu = Join-Path $sdk 'emulator\emulator.exe'
$t0 = Get-Date

function Get-Emulators { @(& $adb devices 2>$null | ForEach-Object { if ($_ -match '^(emulator-\d+)\s+device\b') { $Matches[1] } }) }
function Get-AvdName([string]$serial) {
    $n = @(& $adb -s $serial emu avd name 2>$null) | Select-Object -First 1
    if ($n) { "$n".Trim() }
}
# $proc: el proceso emulator.exe que arrancamos (si sale, el arranque ha fallado). $limitSec: tope propio de esta espera.
function Wait-Ready([string]$serial, $proc = $null, [int]$limitSec = 0) {
    $w0 = Get-Date
    while (((Get-Date) - $t0).TotalSeconds -lt $TimeoutSec) {
        if ($limitSec -and (((Get-Date) - $w0).TotalSeconds -ge $limitSec)) { return $false }
        if ($proc -and $proc.HasExited) { return $false }
        $boot = (& $adb -s $serial shell getprop sys.boot_completed 2>$null | Out-String).Trim()
        if ($boot -eq '1') { & $adb -s $serial shell pm path android 2>$null | Out-Null; if ($LASTEXITCODE -eq 0) { return $true } }
        Start-Sleep -Seconds 1
    }
    return $false
}
# Devuelve el serial del emulador NUEVO (uno que no estaba antes) o $null si el proceso sale antes de aparecer en adb.
function Start-Emu([string[]]$emuArgs) {
    $before = @(Get-Emulators)
    $headless = if ($Window) { @() } else { @('-no-window') }
    $script:EmuProc = Start-Process -FilePath $emu -ArgumentList (@('-avd', $Avd, '-no-audio', '-no-boot-anim') + $headless + $emuArgs) -WindowStyle Hidden -PassThru
    while (((Get-Date) - $t0).TotalSeconds -lt $TimeoutSec) {
        $new = @(Get-Emulators | Where-Object { $before -notcontains $_ })
        if ($new.Count) { return $new[0] }
        if ($script:EmuProc.HasExited) { return $null }
        Start-Sleep -Seconds 1
    }
    return $null
}
function Stop-Emu([string]$serial) {
    if ($serial) { & $adb -s $serial emu kill 2>$null | Out-Null }
    if ($script:EmuProc -and -not $script:EmuProc.WaitForExit(20000)) { Stop-Process -Id $script:EmuProc.Id -Force -ErrorAction SilentlyContinue }
}
function Done([string]$serial, [string]$how) { 'AVD READY {0} ({1}, {2:N0} s)' -f $serial, $how, ((Get-Date) - $t0).TotalSeconds; exit 0 }

$running = @(Get-Emulators)
if ($Clean -and $running) {
    'AVD NOT READY: -Clean needs no emulator attached, but {0} is running. Stop it first (adb -s {1} emu kill) or lease another one with .claude\scripts\emulator-lease.ps1' -f ($running -join ', '), $running[0]
    exit 1
}
if (-not $Clean) {
    foreach ($r in $running) {
        if ((Get-AvdName $r) -eq $Avd -and (Wait-Ready $r)) { Done $r 'reused' }
    }
}

$snapDir = Join-Path $env:USERPROFILE (".android\avd\{0}.avd\snapshots\{1}" -f $Avd, $Snapshot)
if ($Clean) {
    $s = Start-Emu @('-no-snapshot')
    if ($s -and (Wait-Ready $s $script:EmuProc)) { Done $s 'cold boot (-Clean)' }
} elseif (Test-Path $snapDir) {
    $s = Start-Emu @('-snapshot', $Snapshot, '-no-snapshot-save')
    if ($s -and (Wait-Ready $s $script:EmuProc $SnapshotTimeoutSec)) { Done $s "snapshot $Snapshot" }
    # Snapshot roto o arranque colgado: se corta ese emulador y se prueba una sola vez en frío.
    Stop-Emu $s
    $s = Start-Emu @('-no-snapshot-load')
    if ($s -and (Wait-Ready $s $script:EmuProc)) { Done $s "cold boot (snapshot $Snapshot failed)" }
} else {
    $s = Start-Emu @('-no-snapshot-load')
    if ($s -and (Wait-Ready $s $script:EmuProc)) {
        Start-Sleep -Seconds 15
        & $adb -s $s emu avd snapshot save $Snapshot | Out-Null
        Done $s "cold boot, snapshot $Snapshot saved for next time"
    }
}
'AVD NOT READY after {0} s (adb devices: {1})' -f $TimeoutSec, (@(Get-Emulators) -join ', ')
exit 1
