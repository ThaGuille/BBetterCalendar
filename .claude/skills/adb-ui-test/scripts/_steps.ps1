<#
    Shared step executor for the BBetter ADB testing system.
    Dot-sourced by bb-adb.ps1 (record, live) and replay.ps1 (replay).

    A "step" is one flat line of the recording grammar:
        START <component>                  am start -n <pkg>/<component>
        GRANT <permission>                 pm grant <pkg> <permission>
        CLEAR                              pm clear <pkg>
        TAP id=<idSuffix>                  find-node -Id ... -Tap
        TAP text=<substring>               find-node -Text ... -Tap
        TYPE <text>                        input text (spaces -> %s)
        KEY <code>                         input keyevent <code>
        SWIPE <x1> <y1> <x2> <y2> <durMs>  input swipe
        WAIT <ms>                          Start-Sleep
        ASSERT id=<idSuffix> expect~=<sub> find-node -Id ...; text must contain <sub>
                                           (<sub> empty => node must merely exist)
        SHOT <label>                       capture-screen -Label <label> (visual
                                           checkpoint PNG; use only for charts /
                                           layout / failure artifacts)

    Invoke-BBStep returns a hashtable: @{ ok = <bool>; info = <string> }.
#>

function Invoke-BBStep {
    param(
        [Parameter(Mandatory)] [string]$Step,
        [string]$Serial   = '',
        [string]$Pkg      = 'com.example.bbettercalendar',
        [string]$ScriptDir = $PSScriptRoot
    )

    if (-not $Serial) {
        . (Join-Path $ScriptDir '_device.ps1')
        $Serial = Get-BBSerial -Requested $Serial
    }

    $finder = Join-Path $ScriptDir 'find-node.ps1'
    $shooter = Join-Path $ScriptDir 'capture-screen.ps1'
    $verb   = ($Step -split '\s+', 2)[0]
    $rest   = if ($Step.Length -gt $verb.Length) { $Step.Substring($verb.Length).Trim() } else { '' }

    switch ($verb) {
        'START' {
            & adb -s $Serial shell am start -n "$Pkg/$rest" | Out-Null
            return @{ ok = $true; info = "started $rest" }
        }
        'GRANT' {
            & adb -s $Serial shell pm grant $Pkg $rest | Out-Null
            return @{ ok = $true; info = "granted $rest" }
        }
        'CLEAR' {
            & adb -s $Serial shell pm clear $Pkg | Out-Null
            return @{ ok = $true; info = "cleared $Pkg" }
        }
        'TYPE' {
            $escaped = $rest -replace ' ', '%s'
            & adb -s $Serial shell input text $escaped | Out-Null
            return @{ ok = $true; info = "typed '$rest'" }
        }
        'KEY' {
            & adb -s $Serial shell input keyevent $rest | Out-Null
            return @{ ok = $true; info = "key $rest" }
        }
        'SWIPE' {
            $p = $rest -split '\s+'
            & adb -s $Serial shell input swipe $p[0] $p[1] $p[2] $p[3] $p[4] | Out-Null
            return @{ ok = $true; info = "swipe $rest" }
        }
        'WAIT' {
            Start-Sleep -Milliseconds ([int]$rest)
            return @{ ok = $true; info = "waited $rest ms" }
        }
        'TAP' {
            if ($rest -match '^id=(.+)$') {
                $out = & $finder -Id $Matches[1] -Tap -Serial $Serial
                return @{ ok = ($LASTEXITCODE -eq 0); info = ($out -join '; ') }
            } elseif ($rest -match '^text=(.+)$') {
                $out = & $finder -Text $Matches[1] -Tap -Serial $Serial
                return @{ ok = ($LASTEXITCODE -eq 0); info = ($out -join '; ') }
            }
            return @{ ok = $false; info = "bad TAP target: $rest" }
        }
        'ASSERT' {
            if ($rest -match '^id=(\S+)(\s+expect~=(.*))?$') {
                $idSuffix = $Matches[1]
                $expect   = if ($Matches[3]) { $Matches[3].Trim() } else { '' }
                $out = & $finder -Id $idSuffix -Serial $Serial
                if ($LASTEXITCODE -ne 0) {
                    return @{ ok = $false; info = "ASSERT failed: id=$idSuffix not found" }
                }
                if ($expect) {
                    $actual = if (($out -join "`n") -match "text='(.*?)' desc=") { $Matches[1] } else { '' }
                    if ($actual.ToLower().Contains($expect.ToLower())) {
                        return @{ ok = $true; info = "ASSERT ok: id=$idSuffix text~='$expect' (actual '$actual')" }
                    }
                    return @{ ok = $false; info = "ASSERT failed: id=$idSuffix expected~='$expect' but text='$actual'" }
                }
                return @{ ok = $true; info = "ASSERT ok: id=$idSuffix exists" }
            }
            return @{ ok = $false; info = "bad ASSERT: $rest" }
        }
        'SHOT' {
            $label = if ($rest) { $rest } else { $null }
            $out = & $shooter -Label $label -Serial $Serial
            return @{ ok = ($LASTEXITCODE -eq 0); info = ($out -join '; ') }
        }
        default {
            return @{ ok = $false; info = "unknown step verb: $verb" }
        }
    }
}
