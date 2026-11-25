#!/usr/bin/env bash
# Use ONLY on a dedicated, disposable ARM64 test device. Never a user's phone.
set -euo pipefail
api="${1:?Expected Android API}"
apk="${2:?Signed candidate APK}"
pkg=com.moataz.harnese.nova
test -s "$apk"
test "$(adb get-state)" = device
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = "$api"
adb shell getprop ro.product.cpu.abilist | grep -F arm64-v8a
# A clean runner must not contain user data or an existing Nova installation.
if adb shell pm path "$pkg" | grep -q '^package:'; then
  echo 'Refusing to remove preexisting Nova data from this device'; exit 1
fi
tmp=$(mktemp -d)
cleanup() { adb uninstall "$pkg" >/dev/null 2>&1 || true; rm -rf "$tmp"; }
trap cleanup EXIT
install() {
  local output
  output=$(adb install "$@" 2>&1) || { printf '%s\n' "$output"; return 1; }
  printf '%s\n' "$output"
  grep -qx 'Success' <<< "$output"
}
launch() {
  adb logcat -c
  adb shell am start -W -n "$pkg/com.jarves.mh.MainActivity"
  sleep 8
  adb shell pidof "$pkg"
  adb logcat -d -b crash > "$tmp/crash.txt"
  cat "$tmp/crash.txt"
  if grep -F "$pkg" "$tmp/crash.txt"; then return 1; fi
}
echo "API $api — fresh install"
install "$apk"
launch
adb shell dumpsys package "$pkg" | grep -E 'versionCode=23([[:space:]]|$)'
adb uninstall "$pkg"
if (( api < 28 )); then
  echo 'Upgrade: not applicable; RC3 minimum API is 28.'
else
  echo "API $api — RC3 upgrade"
  url=https://github.com/moataz77549-byte/Harnese/releases/download/harnese-nova-v2.0.0-rc.3/Harnese-Nova-2.0.0-rc.3-Android9Plus-ARM64.apk
  curl --fail --location --retry 3 "$url" -o "$tmp/rc3.apk"
  echo "5726fc6bdbe0e77a73ad83c86503da4e1fb345a49521e2308ca538e9f6bf005c  $tmp/rc3.apk" | sha256sum -c -
  # If RC3 itself cannot install, capture the actual error; do not claim upgrade success.
  install "$tmp/rc3.apk"
  install -r "$apk"
  launch
  adb shell dumpsys package "$pkg" | grep -E 'versionCode=23([[:space:]]|$)'
fi
echo "PASS API $api"
