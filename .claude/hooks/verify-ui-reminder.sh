#!/usr/bin/env bash
# verify-ui-reminder.sh — Stop hook (CLAUDE.md rule #7). Linux/macOS twin of verify-ui-reminder.ps1:
# same thresholds, same contract, same message. Keep the two in sync.
#
# Blocks the stop ONCE when the uncommitted runtime diff (git diff HEAD over app/src/main java + layout +
# nav) is big and an emulator is attached, telling Claude to verify through the ui-tester subagent.
#   - stdin JSON `stop_hook_active: true` -> we already continued once -> never re-block (loop guard).
#   - stdout {"decision":"block","reason":"..."} (exit 0) blocks the stop.
#   - any failure -> exit 0 silently: a verify hook must NEVER trap the user's ability to stop.
# Tunables: BB_UI_VERIFY_LINES (default 50), BB_UI_VERIFY_FILES (default 4).
set -u
trap 'exit 0' ERR

input="$(cat 2>/dev/null || true)"
if printf '%s' "$input" | grep -Eq '"stop_hook_active"[[:space:]]*:[[:space:]]*true'; then exit 0; fi

lines_threshold="${BB_UI_VERIFY_LINES:-50}"
files_threshold="${BB_UI_VERIFY_FILES:-4}"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)" || exit 0
marker="${TMPDIR:-/tmp}/bb-ui-verify.flag"

numstat="$(git -C "$repo_root" diff --numstat HEAD -- app/src/main/java app/src/main/res/layout app/src/main/res/navigation 2>/dev/null)" || exit 0
read -r total files < <(printf '%s\n' "$numstat" | awk -F'\t' 'NF>=3 { f++; t += ($1=="-"?0:$1) + ($2=="-"?0:$2) } END { print t+0, f+0 }')

if [ "$total" -lt "$lines_threshold" ] && [ "$files" -lt "$files_threshold" ]; then
  rm -f "$marker"   # re-arm once changes are committed/reverted
  exit 0
fi
[ -e "$marker" ] && exit 0   # already reminded for this uncommitted batch

# Only worth it if there's a device to actually test on. adb is often not on PATH: same lookup as bbadb.py.
adb="${ADB:-$(command -v adb || true)}"
if [ -z "$adb" ]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  [ -z "$sdk" ] && sdk="$(sed -n 's/^sdk\.dir=//p' "$repo_root/local.properties" 2>/dev/null | head -1)"
  for d in "$sdk" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
    [ -n "$d" ] && [ -x "$d/platform-tools/adb" ] && { adb="$d/platform-tools/adb"; break; }
  done
fi
[ -n "$adb" ] || exit 0
"$adb" devices 2>/dev/null | grep -Eq '^[^[:space:]]+[[:space:]]+device[[:space:]]*$' || exit 0

echo 1 > "$marker" 2>/dev/null || true
printf '{"decision":"block","reason":"Big uncommitted runtime change detected (%s changed lines across %s files under app/src/main java+layout+navigation) and an emulator is attached. Per CLAUDE.md rule #7, verify the app actually RUNS before finishing -- don'"'"'t trust the compiler alone. Delegate to the ui-tester subagent: it builds+installs the debug APK, drives the emulator by resource-id via the adb-ui-test flow, and scans '"'"'logcat -b crash'"'"' for FATAL EXCEPTION, in an isolated context. If this change does NOT affect UI/runtime behaviour (pure refactor, docs, gradle/config), say so in one line and stop -- this won'"'"'t repeat for the current diff."}\n' "$total" "$files"
exit 0
