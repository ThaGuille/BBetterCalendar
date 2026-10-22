<#
.SYNOPSIS
  adb reverse de los puertos del Emulator Suite (F2-u2, D-026).

.DESCRIPTION
  Lee los puertos de "emulators" en firebase/firebase.json (auth, functions, firestore, database y hosting
  cuando exista; la UI no hace falta) y ejecuta "adb -s <serial> reverse tcp:P tcp:P" por cada uno, para que la
  app (compilada con -PfirebaseEmulatorHost=127.0.0.1) llegue al PC por 127.0.0.1. Vale para un movil fisico
  por USB y tambien para el AVD. Solo 127.0.0.1: no se usa ninguna IP de la red local.

.PARAMETER Serial
  Serie del dispositivo (adb devices). Obligatorio si hay varios conectados.

.PARAMETER Remove
  Quita los reverse en lugar de crearlos.

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File firebase\scripts\adb-reverse.ps1
  powershell -NoProfile -ExecutionPolicy Bypass -File firebase\scripts\adb-reverse.ps1 -Serial emulator-5554 -Remove
#>
param(
    [string]$Serial = "",
    [switch]$Remove
)

$ErrorActionPreference = "Stop"

function Fail([string]$Message) {
    [Console]::Error.WriteLine("adb-reverse: " + $Message)
    exit 1
}

# adb: PATH o el SDK por defecto de Android Studio.
$adb = $null
$cmd = Get-Command adb -ErrorAction SilentlyContinue
if ($cmd) {
    $adb = $cmd.Source
} else {
    $candidates = @()
    if ($env:ANDROID_HOME) { $candidates += (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe") }
    if ($env:ANDROID_SDK_ROOT) { $candidates += (Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe") }
    if ($env:LOCALAPPDATA) { $candidates += (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe") }
    foreach ($c in $candidates) {
        if (Test-Path $c) { $adb = $c; break }
    }
}
if (-not $adb) { Fail "no encuentro adb (ni en PATH ni en ANDROID_HOME / el SDK por defecto)." }

# Puertos desde firebase.json (la carpeta firebase/ es la madre de scripts/).
$configPath = Join-Path (Split-Path -Parent $PSScriptRoot) "firebase.json"
if (-not (Test-Path $configPath)) { Fail ("no encuentro " + $configPath) }
$config = Get-Content -Raw -Path $configPath | ConvertFrom-Json
if (-not $config.emulators) { Fail "firebase.json no tiene la seccion 'emulators'." }

$ports = @()
foreach ($name in @("auth", "functions", "firestore", "database", "hosting")) {
    $entry = $config.emulators.$name
    if ($entry -and $entry.port) { $ports += [int]$entry.port }
}
if ($ports.Count -eq 0) { Fail "firebase.json no declara puertos de emuladores." }

# Dispositivos conectados (estado "device"; unauthorized/offline no sirven).
$devices = @()
foreach ($line in (& $adb devices)) {
    if ($line -match "^(\S+)\s+device\s*$") { $devices += $Matches[1] }
}

if ($Serial -ne "") {
    if ($devices -notcontains $Serial) {
        $found = "ninguno"
        if ($devices.Count -gt 0) { $found = ($devices -join ", ") }
        Fail ("el dispositivo '" + $Serial + "' no esta conectado (adb devices: " + $found + ").")
    }
} else {
    if ($devices.Count -eq 0) { Fail "no hay ningun dispositivo conectado (adb devices esta vacio)." }
    if ($devices.Count -gt 1) {
        Fail ("hay varios dispositivos (" + ($devices -join ", ") + "); indica cual con -Serial.")
    }
    $Serial = $devices[0]
}

foreach ($port in $ports) {
    $spec = "tcp:" + $port
    if ($Remove) {
        & $adb -s $Serial reverse --remove $spec | Out-Null
    } else {
        & $adb -s $Serial reverse $spec $spec | Out-Null
    }
    if ($LASTEXITCODE -ne 0) {
        Fail ("adb reverse " + $spec + " fallo en '" + $Serial + "' (codigo " + $LASTEXITCODE + ").")
    }
}

if ($Remove) {
    Write-Output ("adb-reverse: quitados " + ($ports -join ", ") + " en " + $Serial)
} else {
    Write-Output ("adb-reverse: " + ($ports -join ", ") + " -> 127.0.0.1 del PC, en " + $Serial)
}
exit 0
