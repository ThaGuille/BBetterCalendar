<#
    Regresiones de .claude\scripts\avd-up.ps1 con un adb.exe y un emulator.exe falsos (se compilan con csc.exe de .NET
    Framework, que viene con Windows) y un LOCALAPPDATA / USERPROFILE temporales: no toca ningún emulador real.
      powershell -NoProfile -ExecutionPolicy Bypass -File .claude\scripts\test\test-avd-up.ps1
    Sale con 1 si alguna comprobación falla.
#>
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$script = Join-Path $repo '.claude\scripts\avd-up.ps1'
$csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
$tmp = Join-Path ([IO.Path]::GetTempPath()) ('avdup-test-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
$sdk = Join-Path $tmp 'Android\Sdk'
$state = Join-Path $tmp 'state'
New-Item -ItemType Directory -Force (Join-Path $sdk 'platform-tools'), (Join-Path $sdk 'emulator'), $state | Out-Null

$adbSrc = @'
using System; using System.IO; using System.Linq;
class P {
  static string S = Environment.GetEnvironmentVariable("FAKE_STATE");
  static string Dev() { return Path.Combine(S, "devices.txt"); }
  static string[] Devs() { return File.Exists(Dev()) ? File.ReadAllLines(Dev()).Where(l => l.Length > 0).ToArray() : new string[0]; }
  static int Main(string[] a) {
    File.AppendAllText(Path.Combine(S, "adb.log"), string.Join(" ", a) + "\n");
    string serial = null; int i = 0;
    if (a.Length > 1 && a[0] == "-s") { serial = a[1]; i = 2; }
    string[] c = a.Skip(i).ToArray();
    if (c.Length == 0) return 0;
    if (c[0] == "devices") { Console.WriteLine("List of devices attached"); foreach (var d in Devs()) Console.WriteLine(d + "\tdevice"); return 0; }
    if (c[0] == "shell") { if (c.Contains("getprop")) Console.WriteLine(File.Exists(Path.Combine(S, "noboot_" + serial)) ? "0" : "1"); return 0; }
    if (c[0] == "emu" && c.Length > 2 && c[1] == "avd" && c[2] == "name") {
      string f = Path.Combine(S, "avd_" + serial + ".txt");
      Console.WriteLine(File.Exists(f) ? File.ReadAllText(f).Trim() : "unknown"); Console.WriteLine("OK"); return 0;
    }
    if (c[0] == "emu" && c.Length > 1 && c[1] == "kill") {
      File.WriteAllLines(Dev(), Devs().Where(d => d != serial).ToArray()); File.Delete(Path.Combine(S, "cmd_" + serial + ".txt")); Console.WriteLine("OK: killing emulator, bye bye"); return 0;
    }
    return 0;
  }
}
'@
$emuSrc = @'
using System; using System.IO; using System.Linq; using System.Threading;
class P {
  static int Main(string[] a) {
    string S = Environment.GetEnvironmentVariable("FAKE_STATE");
    File.AppendAllText(Path.Combine(S, "emu.log"), string.Join(" ", a) + "\n");
    string mode = File.Exists(Path.Combine(S, "emu_mode")) ? File.ReadAllText(Path.Combine(S, "emu_mode")).Trim() : "ok";
    bool snap = a.Contains("-snapshot");
    if (mode == "exitearly" && snap) return 1;
    string dev = Path.Combine(S, "devices.txt");
    string[] now = File.Exists(dev) ? File.ReadAllLines(dev).Where(l => l.Length > 0).ToArray() : new string[0];
    string serial = null;
    for (int n = 5554; serial == null; n += 2) if (!now.Contains("emulator-" + n)) serial = "emulator-" + n;
    string avd = a[Array.IndexOf(a, "-avd") + 1];
    File.WriteAllText(Path.Combine(S, "avd_" + serial + ".txt"), avd);
    File.WriteAllText(Path.Combine(S, "cmd_" + serial + ".txt"), string.Join(" ", a));
    string nb = Path.Combine(S, "noboot_" + serial);
    if (mode == "snaphang" && snap) File.WriteAllText(nb, "x"); else if (File.Exists(nb)) File.Delete(nb);
    File.AppendAllText(dev, serial + "\n");
    for (int t = 0; t < 150; t++) {
      Thread.Sleep(200);
      string[] cur = File.ReadAllLines(dev);
      if (!cur.Contains(serial)) return 0;
    }
    return 0;
  }
}
'@
$null = Set-Content (Join-Path $tmp 'adb.cs') $adbSrc
$null = Set-Content (Join-Path $tmp 'emu.cs') $emuSrc
foreach ($pair in @(@('platform-tools\adb.exe', 'adb.cs'), @('emulator\emulator.exe', 'emu.cs'))) {
    $exe = Join-Path $sdk $pair[0]
    $o = & $csc /nologo "/out:$exe" (Join-Path $tmp $pair[1]) | Out-String
    if (-not (Test-Path (Join-Path $sdk $pair[0]))) { $o; throw "csc no pudo compilar $($pair[1])" }
}

$env:LOCALAPPDATA = $tmp
$env:USERPROFILE = Join-Path $tmp 'home'
$env:FAKE_STATE = $state
$env:AVDUP_FAKE_PROCS = $state
New-Item -ItemType Directory -Force (Join-Path $env:USERPROFILE '.android\avd\api36.avd\snapshots\bt_quick') | Out-Null

$fails = 0
# $rw: emuladores ya en marcha CON escritura (sin -read-only), como los deja un arranque normal.
function Reset-State([string[]]$devices = @(), [hashtable]$avds = @{}, [string]$mode = 'ok', [string[]]$rw = @()) {
    # Los emuladores falsos de un caso anterior siguen vivos unos segundos leyendo devices.txt: se cierran antes.
    Get-Process emulator -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "$sdk*" } | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Milliseconds 300
    Get-ChildItem $state | Remove-Item -Force
    Remove-Item -Recurse -Force (Join-Path $tmp 'bbetter') -ErrorAction SilentlyContinue
    foreach ($k in $rw) { Set-Content (Join-Path $state "cmd_$k.txt") '-avd api36 -no-window -snapshot bt_quick' }
    if ($devices.Count) { Set-Content (Join-Path $state 'devices.txt') $devices } else { Set-Content (Join-Path $state 'devices.txt') @() }
    foreach ($k in $avds.Keys) { Set-Content (Join-Path $state "avd_$k.txt") $avds[$k] }
    Set-Content (Join-Path $state 'emu_mode') $mode
}
function Run-AvdUp([string[]]$extra = @()) {
    $out = & powershell -NoProfile -ExecutionPolicy Bypass -File $script @extra 2>&1 | Out-String
    return @{ Out = $out; Code = $LASTEXITCODE }
}
function Check([string]$what, [bool]$ok, $r) {
    if ($ok) { "  ok   $what" } else { "  FAIL $what"; $script:fails++; ($r.Out -split "`n" | Select-Object -Last 5 | ForEach-Object { "       | $_" }) }
}
function Emu-Launches { $f = Join-Path $state 'emu.log'; if (Test-Path $f) { @(Get-Content $f) } else { @() } }

try {
    '== F10: -Clean con un emulador ya conectado'
    Reset-State @('emulator-5554') @{ 'emulator-5554' = 'api36' }
    $r = Run-AvdUp @('-Clean', '-TimeoutSec', '30')
    Check 'se niega con exit 1 y dice cómo pararlo o usar otro' (($r.Code -eq 1) -and ($r.Out -match 'emu kill') -and ($r.Out -match 'emulator-lease\.ps1')) $r
    Check 'no arranca ningún emulador ni dice «cold boot»' (((Emu-Launches).Count -eq 0) -and ($r.Out -notmatch 'cold boot')) $r
    Reset-State @('emulator-5554') @{ 'emulator-5554' = 'Pixel_other' }
    $r = Run-AvdUp @('-Clean', '-TimeoutSec', '30')
    Check 'hermana: también si el conectado es de otro AVD' (($r.Code -eq 1) -and ($r.Out -match 'emu kill') -and ((Emu-Launches).Count -eq 0)) $r

    '== F2 / F11 / F14: solo se reutiliza un emulador del AVD pedido'
    Reset-State @('emulator-5554') @{ 'emulator-5554' = 'Pixel_other' }
    $r = Run-AvdUp @('-TimeoutSec', '60')
    Check 'otro AVD conectado: no se reutiliza, arranca api36 desde el snapshot (en emulator-5556)' (($r.Code -eq 0) -and ($r.Out -match 'AVD READY emulator-5556 \(snapshot bt_quick') -and ($r.Out -notmatch 'reused') -and ((Emu-Launches) -match '-avd api36')) $r
    Reset-State @('emulator-5554', 'emulator-5556') @{ 'emulator-5554' = 'Pixel_other'; 'emulator-5556' = 'api36' }
    $r = Run-AvdUp @('-TimeoutSec', '60')
    Check 'hermana: entre varios se reutiliza el que es api36' (($r.Code -eq 0) -and ($r.Out -match 'AVD READY emulator-5556 \(reused') -and ((Emu-Launches).Count -eq 0)) $r

    '== F12: el snapshot falla o se cuelga: un solo arranque en frío'
    Reset-State @() @{} 'exitearly'
    $r = Run-AvdUp @('-TimeoutSec', '90', '-SnapshotTimeoutSec', '20')
    $l = @(Emu-Launches)
    Check 'el emulador del snapshot sale al instante: cae a «-no-snapshot-load» sin esperar los 300 s' (($r.Code -eq 0) -and ($r.Out -match 'cold boot') -and ($l.Count -eq 2) -and ($l[1] -match '-no-snapshot-load')) $r
    Reset-State @() @{} 'snaphang'
    $r = Run-AvdUp @('-TimeoutSec', '90', '-SnapshotTimeoutSec', '3')
    $l = @(Emu-Launches)
    Check 'hermana: el snapshot arranca pero no llega a listo: se corta y cae a frío' (($r.Code -eq 0) -and ($r.Out -match 'cold boot') -and ($l.Count -eq 2) -and ($l[1] -match '-no-snapshot-load')) $r
    Reset-State @() @{} 'ok'
    $r = Run-AvdUp @('-TimeoutSec', '60')
    Check 'hermana: el camino normal sigue siendo un solo arranque desde el snapshot' (($r.Code -eq 0) -and ($r.Out -match 'snapshot bt_quick') -and ((Emu-Launches).Count -eq 1)) $r

    '== -Count: varias instancias del mismo AVD, las de más con -read-only'
    Reset-State @() @{} 'ok'
    $r = Run-AvdUp @('-Count', '2', '-TimeoutSec', '60')
    $l = @(Emu-Launches)
    Check 'ninguna en marcha: arranca dos, las dos -read-only (el emulador no deja mezclar)' (($r.Code -eq 0) -and ($l.Count -eq 2) -and ($l[0] -match '-read-only') -and ($l[1] -match '-read-only') -and ($r.Out -match 'AVD READY emulator-5554 \(snapshot bt_quick, read-only') -and ($r.Out -match 'AVD READY emulator-5556 \(snapshot bt_quick, read-only')) $r
    Reset-State @('emulator-5554') @{ 'emulator-5554' = 'api36' }
    $r = Run-AvdUp @('-Count', '2', '-TimeoutSec', '60')
    $l = @(Emu-Launches)
    Check 'hermana: una ya en marcha: la reutiliza y arranca solo la segunda, -read-only' (($r.Code -eq 0) -and ($r.Out -match 'AVD READY emulator-5554 \(reused') -and ($l.Count -eq 1) -and ($l[0] -match '-read-only') -and ($r.Out -match 'AVD READY emulator-5556')) $r
    Reset-State @('emulator-5554', 'emulator-5556') @{ 'emulator-5554' = 'api36'; 'emulator-5556' = 'api36' }
    $r = Run-AvdUp @('-Count', '2', '-TimeoutSec', '60')
    Check 'hermana: las dos en marcha: no arranca nada' (($r.Code -eq 0) -and ((Emu-Launches).Count -eq 0) -and (([regex]::Matches($r.Out, 'reused')).Count -eq 2)) $r
    '== -Count 2 con una instancia que escribe (la de un arranque normal)'
    Reset-State @('emulator-5554') @{ 'emulator-5554' = 'api36' } 'ok' @('emulator-5554')
    $r = Run-AvdUp @('-Count', '2', '-TimeoutSec', '60')
    $l = @(Emu-Launches)
    Check 'sin lease: la para y arranca dos -read-only' (($r.Code -eq 0) -and ($r.Out -match 'restarting api36 read-only') -and ($r.Out -notmatch 'reused') -and ($l.Count -eq 2) -and ($l[0] -match '-read-only') -and ($l[1] -match '-read-only') -and ((Get-Content (Join-Path $state 'adb.log')) -match 'emulator-5554 emu kill')) $r
    Reset-State @('emulator-5554') @{ 'emulator-5554' = 'api36' } 'ok' @('emulator-5554')
    New-Item -ItemType Directory -Force (Join-Path $tmp 'bbetter\emulator-leases') | Out-Null
    Set-Content (Join-Path $tmp 'bbetter\emulator-leases\emulator-5554.lock') "u7 2026-11-13T10:00:00 pid=$PID"
    $r = Run-AvdUp @('-Count', '2', '-TimeoutSec', '60')
    Check 'hermana: con lease: no la toca, sale con 1 y no arranca nada' (($r.Code -eq 1) -and ($r.Out -match 'leased \(u7') -and ((Emu-Launches).Count -eq 0) -and -not ((Get-Content (Join-Path $state 'adb.log')) -match 'emu kill')) $r
    # F3 / F10: un lease cuyo pid murió (una llamada de with-emulator que cayó) no cuenta como tomado.
    $dead = Start-Process cmd -ArgumentList '/c exit' -PassThru -WindowStyle Hidden; $dead.WaitForExit()
    $live = Start-Process cmd -ArgumentList '/c ping -n 40 127.0.0.1 >nul' -PassThru -WindowStyle Hidden
    Reset-State @('emulator-5554') @{ 'emulator-5554' = 'api36' } 'ok' @('emulator-5554')
    New-Item -ItemType Directory -Force (Join-Path $tmp 'bbetter\emulator-leases') | Out-Null
    Set-Content (Join-Path $tmp 'bbetter\emulator-leases\emulator-5554.lock') "u7 2026-11-13T10:00:00 pid=$($dead.Id)"
    $r = Run-AvdUp @('-Count', '2', '-TimeoutSec', '60')
    Check 'F3/F10: con el lease de un pid muerto: no bloquea, la reinicia -read-only y deja dos' (($r.Code -eq 0) -and ($r.Out -match 'restarting api36 read-only') -and ((Emu-Launches).Count -eq 2)) $r
    Reset-State @('emulator-5554') @{ 'emulator-5554' = 'api36' } 'ok' @('emulator-5554')
    New-Item -ItemType Directory -Force (Join-Path $tmp 'bbetter\emulator-leases') | Out-Null
    Set-Content (Join-Path $tmp 'bbetter\emulator-leases\emulator-5554.lock') "u7 2026-11-13T10:00:00 pid=$($dead.Id) child=$($live.Id)"
    $r = Run-AvdUp @('-Count', '2', '-TimeoutSec', '60')
    Check 'F3/F10 hermana: pid muerto pero child= vivo: sigue tomado (exit 1, no arranca nada)' (($r.Code -eq 1) -and ($r.Out -match 'leased') -and ((Emu-Launches).Count -eq 0)) $r
    Stop-Process -Id $live.Id -Force -ErrorAction SilentlyContinue
    Reset-State @('emulator-5554') @{ 'emulator-5554' = 'api36' } 'ok' @('emulator-5554')
    New-Item -ItemType Directory -Force (Join-Path $tmp 'bbetter\emulator-leases') | Out-Null
    $f = Join-Path $tmp 'bbetter\emulator-leases\emulator-5554.lock'
    Set-Content $f 'u8 2026-11-13T07:00:00'
    (Get-Item $f).LastWriteTime = (Get-Date).AddHours(-3)
    $r = Run-AvdUp @('-Count', '2', '-TimeoutSec', '60')
    Check 'F10 hermana: un lease manual (sin pid) de 3 h está abandonado y no bloquea' (($r.Code -eq 0) -and ($r.Out -match 'restarting api36 read-only')) $r

    # F2: dos avd-up -Count 2 a la vez no arrancan cada uno los suyos: el segundo espera y reutiliza lo del primero.
    '== F2: dos avd-up -Count 2 concurrentes'
    Reset-State @() @{} 'ok'
    $o1 = Join-Path $tmp 'p1.txt'; $o2 = Join-Path $tmp 'p2.txt'
    $a = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $script, '-Count', '2', '-TimeoutSec', '90')
    $p1 = Start-Process powershell -ArgumentList $a -PassThru -WindowStyle Hidden -RedirectStandardOutput $o1; $null = $p1.Handle
    $p2 = Start-Process powershell -ArgumentList $a -PassThru -WindowStyle Hidden -RedirectStandardOutput $o2; $null = $p2.Handle
    $p1.WaitForExit(); $p2.WaitForExit()
    $r = @{ Out = ((Get-Content $o1 -Raw) + (Get-Content $o2 -Raw)); Code = 0 }
    Check 'ambos salen con 0 y entre los dos solo arrancan 2 emuladores (el segundo los reutiliza)' (($p1.ExitCode -eq 0) -and ($p2.ExitCode -eq 0) -and ((Emu-Launches).Count -eq 2) -and (([regex]::Matches($r.Out, 'reused')).Count -eq 2)) $r
    Reset-State @() @{} 'ok'
    $p1 = Start-Process powershell -ArgumentList (@('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $script, '-Count', '1', '-TimeoutSec', '90')) -PassThru -WindowStyle Hidden -RedirectStandardOutput $o1; $null = $p1.Handle
    $p2 = Start-Process powershell -ArgumentList (@('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $script, '-Count', '1', '-TimeoutSec', '90')) -PassThru -WindowStyle Hidden -RedirectStandardOutput $o2; $null = $p2.Handle
    $p1.WaitForExit(); $p2.WaitForExit()
    $r = @{ Out = ((Get-Content $o1 -Raw) + (Get-Content $o2 -Raw)); Code = 0 }
    Check 'hermana: dos con -Count 1: un solo arranque' (($p1.ExitCode -eq 0) -and ($p2.ExitCode -eq 0) -and ((Emu-Launches).Count -eq 1)) $r

    Reset-State @() @{} 'ok'
    $r = Run-AvdUp @('-TimeoutSec', '60')
    $l = @(Emu-Launches)
    Check 'hermana: -Count 1 sigue arrancando con escritura' (($r.Code -eq 0) -and ($l.Count -eq 1) -and ($l[0] -notmatch '-read-only')) $r
    Reset-State @('emulator-5554') @{ 'emulator-5554' = 'Pixel_other' }
    $r = Run-AvdUp @('-Count', '2', '-TimeoutSec', '60')
    $l = @(Emu-Launches)
    Check 'hermana: otro AVD conectado no cuenta: arranca dos api36' (($r.Code -eq 0) -and ($l.Count -eq 2) -and ($l[1] -match '-read-only') -and ($r.Out -notmatch 'reused')) $r
    Reset-State @() @{} 'ok'
    $r = Run-AvdUp @('-Count', '2', '-Clean', '-TimeoutSec', '30')
    Check '-Clean con -Count 2: se niega sin arrancar nada' (($r.Code -eq 1) -and ((Emu-Launches).Count -eq 0)) $r
}
finally {
    Get-Process emulator -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "$sdk*" } | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Milliseconds 300
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
}
if ($fails) { "test-avd-up: $fails fallos"; exit 1 }
'test-avd-up: todo bien'
