#!/usr/bin/env bash
# Installs the debug APK on the running emulator, signs in a demo Pro High account,
# walks through the main screens and saves a screenshot of each in screenshots/.
set -euo pipefail

PKG=id.rahmat.marketedge
OUT=screenshots
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"

adb wait-for-device
adb install -r -g app/build/outputs/apk/debug/app-debug.apk

# Clean status bar: fixed clock, full battery and signal, no notification icons.
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter
demo clock -e hhmm 0813
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e fully true
demo network -e mobile show -e level 4 -e datatype none
demo notifications -e visible false

# Demo account with every feature unlocked, and the first-run prompts already answered.
write_prefs() {
  local name=$1 tmp=/data/local/tmp/$1.xml
  cat > "/tmp/$name.xml"
  adb push "/tmp/$name.xml" "$tmp" >/dev/null
  adb shell "run-as $PKG sh -c 'mkdir -p shared_prefs && cp $tmp shared_prefs/$name.xml'"
}
write_prefs marketedge_user <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="user_name">Rahmat Eka</string>
    <string name="user_email">rahmat@marketedge.app</string>
    <string name="subscription_tier">PRO_HIGH</string>
</map>
XML
write_prefs marketedge_accessibility <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="voice_prompt_answered" value="true" />
    <boolean name="voice_assistant_enabled" value="false" />
    <int name="voice_prompt_version" value="4" />
    <boolean name="notification_permission_requested" value="true" />
</map>
XML

dump() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1 || true
}
center() { python3 "$HERE/ui_find.py" /tmp/ui.xml "$1" "${2:-first}"; }
wait_text() {
  for _ in $(seq 1 "${2:-45}"); do
    dump
    [ -n "$(center "$1")" ] && return 0
    sleep 2
  done
  echo "::warning::timed out waiting for '$1'"
  return 0
}
tap_text() {
  dump
  local point
  point=$(center "$1" "${2:-first}")
  if [ -z "$point" ]; then
    echo "::warning::'$1' not found on screen"
    return 0
  fi
  # shellcheck disable=SC2086
  adb shell input tap $point
  sleep "${3:-2}"
}
shot() {
  sleep 2
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "saved $1"
}

adb shell am start -n "$PKG/.MainActivity"
wait_text "~aset aktif" 60
sleep 4
shot 1-pasar

tap_text "Saham"
sleep 3
shot 2-saham

tap_text "Bank Central Asia"
wait_text "Rentang harian"
tap_text "1Bln" first 8
shot 3-detail-saham

adb shell input keyevent KEYCODE_BACK
sleep 2
tap_text "Berita" last
wait_text "~headline aktif" 60
sleep 6
shot 4-berita

tap_text "~CNBC Indonesia •"
wait_text "Artikel Lengkap"
sleep 8
shot 5-artikel

tap_text "AI" last
wait_text "Aset Pilihan AI"
tap_text "Ringkas market hari ini" first 6
shot 6-warrenai

tap_text "Watchlist" last
sleep 4
shot 7-watchlist
