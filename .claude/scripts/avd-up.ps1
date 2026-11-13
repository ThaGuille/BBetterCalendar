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

    -Count N deja N instancias del MISMO AVD en marcha (para unidades en paralelo): reutiliza las que ya corren y arranca
    las que faltan una tras otra. Con varias, el emulador exige -read-only en TODAS (con una que escribe no arranca
    ninguna más): todas arrancan así, y una que ya corría con escritura se reinicia -read-only si nadie tiene su lease
    (si lo tiene, sale con 1). Lo que pase en una -read-only se pierde al apagarla y no puede guardar el snapshot. Cada unidad toma la suya
    con .claude\scripts\with-emulator.py (o emulator-lease.ps1). -Clean solo con -Count 1.

    Uso:  powershell -NoProfile -ExecutionPolicy Bypass -File .claude\scripts\avd-up.ps1 [-Avd api36] [-Count 1] [-Clean] [-Window] [-TimeoutSec 300] [-SnapshotTimeoutSec 90]
    Sin -Window arranca sin ventana (-no-window), como los agentes.
    Imprime una línea «AVD READY <serial> (<cómo>, <s> s)» por instancia y sale con 0, o «AVD NOT READY ...» y 1.
#>
param([string]$Avd = 'api36', [string]$Snapshot = 'bt_quick', [int]$Count = 1, [switch]$Clean, [switch]$Window, [int]$TimeoutSec = 300, [int]$SnapshotTimeoutSec = 90)

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
# Instancias de -Avd en marcha CON escritura (sin -read-only). El emulador no deja mezclar: con una así no arranca
# ninguna otra, ni siquiera -read-only. Lo dice la línea de comandos del proceso; los tests (AVDUP_FAKE_PROCS) leen
# los cmd_*.txt que deja su emulador falso.
function Test-WritableRunning {
    if ($env:AVDUP_FAKE_PROCS) {
        $cmds = @(Get-ChildItem $env:AVDUP_FAKE_PROCS -Filter 'cmd_*.txt' -ErrorAction SilentlyContinue | ForEach-Object { Get-Content $_.FullName -Raw })
    } else {
        $cmds = @(Get-CimInstance Win32_Process -Filter "Name LIKE 'qemu-system%'" -ErrorAction SilentlyContinue | ForEach-Object { $_.CommandLine })
    }
    return [bool](@($cmds | Where-Object { $_ -match ('-avd\s+' + [regex]::Escape($Avd) + '(\s|$)') -and $_ -notmatch '-read-only' }).Count)
}
function Get-Lease([string]$serial) {
    $f = Join-Path $env:LOCALAPPDATA ('bbetter\emulator-leases\{0}.lock' -f $serial)
    if (Test-Path $f) { (Get-Content $f -TotalCount 1) }
}
function Say-Ready([string]$serial, [string]$how) { 'AVD READY {0} ({1}, {2:N0} s)' -f $serial, $how, ((Get-Date) - $t0).TotalSeconds }

$snapDir = Join-Path $env:USERPROFILE (".android\avd\{0}.avd\snapshots\{1}" -f $Avd, $Snapshot)
# Arranca UNA instancia más de -Avd. $ro: otra instancia ya tiene el AVD, así que esta va con -read-only (no puede guardar
# el snapshot). Devuelve @(serial, cómo) o $null si no llega a estar lista.
function Boot-One([bool]$ro) {
    $roArgs = if ($ro) { @('-read-only') } else { @() }
    $tag = if ($ro) { ', read-only' } else { '' }
    if ($Clean) {
        $s = Start-Emu (@('-no-snapshot') + $roArgs)
        if ($s -and (Wait-Ready $s $script:EmuProc)) { return @($s, 'cold boot (-Clean)') }
        return $null
    }
    if (Test-Path $snapDir) {
        $s = Start-Emu (@('-snapshot', $Snapshot, '-no-snapshot-save') + $roArgs)
        if ($s -and (Wait-Ready $s $script:EmuProc $SnapshotTimeoutSec)) { return @($s, "snapshot $Snapshot$tag") }
        # Snapshot roto o arranque colgado: se corta ese emulador y se prueba una sola vez en frío.
        Stop-Emu $s
        $s = Start-Emu (@('-no-snapshot-load') + $roArgs)
        if ($s -and (Wait-Ready $s $script:EmuProc)) { return @($s, "cold boot (snapshot $Snapshot failed)$tag") }
        return $null
    }
    $s = Start-Emu (@('-no-snapshot-load') + $roArgs)
    if ($s -and (Wait-Ready $s $script:EmuProc)) {
        if ($ro) { return @($s, "cold boot$tag") }
        Start-Sleep -Seconds 15
        & $adb -s $s emu avd snapshot save $Snapshot | Out-Null
        return @($s, "cold boot, snapshot $Snapshot saved for next time")
    }
    return $null
}

if ($Count -lt 1) { 'AVD NOT READY: -Count must be 1 or more'; exit 1 }
$running = @(Get-Emulators)
if ($Clean -and $Count -gt 1) { 'AVD NOT READY: -Clean boots one instance; use -Count 1'; exit 1 }
if ($Clean -and $running) {
    'AVD NOT READY: -Clean needs no emulator attached, but {0} is running. Stop it first (adb -s {1} emu kill) or lease another one with .claude\scripts\emulator-lease.ps1' -f ($running -join ', '), $running[0]
    exit 1
}
$have = @()
# Varias instancias: el emulador exige -read-only en TODAS. Una instancia con escritura (la de un arranque normal) se
# reinicia -read-only, salvo que una unidad la tenga tomada con su lease.
if ($Count -gt 1 -and (Test-WritableRunning)) {
    $mine = @($running | Where-Object { (Get-AvdName $_) -eq $Avd })
    $held = @($mine | Where-Object { Get-Lease $_ })
    if ($held.Count) {
        'AVD NOT READY: {0} runs {1} with write access and is leased ({2}); several instances need all of them -read-only. Release it or wait, then run again' -f $held[0], $Avd, (Get-Lease $held[0])
        exit 1
    }
    foreach ($r in $mine) { & $adb -s $r emu kill 2>$null | Out-Null }
    $w0 = Get-Date
    while ((@(Get-Emulators | Where-Object { $mine -contains $_ }).Count -or (Test-WritableRunning)) -and ((Get-Date) - $w0).TotalSeconds -lt 30) { Start-Sleep -Milliseconds 500 }
    'restarting {0} read-only: {1} ran with write access' -f $Avd, ($mine -join ', ')
    $running = @(Get-Emulators)
}
if (-not $Clean) {
    foreach ($r in $running) {
        if ($have.Count -ge $Count) { break }
        if ((Get-AvdName $r) -eq $Avd -and (Wait-Ready $r)) { $have += $r; Say-Ready $r 'reused' }
    }
}
while ($have.Count -lt $Count) {
    $b = Boot-One ($Count -gt 1)
    if (-not $b) {
        'AVD NOT READY after {0} s ({1} of {2} ready; adb devices: {3})' -f $TimeoutSec, $have.Count, $Count, (@(Get-Emulators) -join ', ')
        exit 1
    }
    $have += $b[0]
    Say-Ready $b[0] $b[1]
}
exit 0
