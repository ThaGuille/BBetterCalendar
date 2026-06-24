<#
    Shared device-selection helper for the BBetter ADB testing system.

    Dot-sourced by every script that talks to adb. Resolves which serial to
    drive: if the caller passed -Serial explicitly, that wins outright (so an
    operator can still deliberately target a phone). Otherwise this skill
    must default to an attached emulator-* device and never silently fall
    back to a physical phone, even if the phone is connected and is the
    device preselected/active in Android Studio.
#>

function Get-BBSerial {
    param([string]$Requested)

    if ($Requested) { return $Requested }

    $lines = & adb devices 2>$null
    $emulators = $lines | Where-Object { $_ -match '^(emulator-\S+)\s+device\b' }
    if ($emulators) {
        $first = $emulators | Select-Object -First 1
        if ($first -match '^(emulator-\S+)\s') { return $Matches[1] }
    }

    # No attached emulator-* device matched. Fall back to the conventional
    # default rather than guessing a physical device's serial -- if no
    # emulator is actually running this fails loudly on the adb call instead
    # of silently driving a connected phone.
    return 'emulator-5554'
}
