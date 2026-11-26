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
adb shell dumpsys package "$pkg" | grep -E 'versionCode=24([[:space:]]|$)'
adb uninstall "$pkg"
if (( api < 26 )); then
  echo 'Upgrade: not applicable; RC4 minimum API is 26.'
else
  echo "API $api — RC4 upgrade"
  url=https://github.com/moataz77549-byte/Harnese/releases/download/harnese-nova-v2.0.0-rc.4/Harnese-Nova-2.0.0-rc.4-Android8Plus-ARM64.apk
  curl --fail --location --retry 3 "$url" -o "$tmp/rc4.apk"
  echo "ee6764971be45bfa6b9e0a1cdd3d005531c05cea5773d1a6571e7fd445fe5ca2  $tmp/rc4.apk" | sha256sum -c -
  # If RC4 itself cannot install, capture the actual error; do not claim upgrade success.
  install "$tmp/rc4.apk"
  install -r "$apk"
  launch
  adb shell dumpsys package "$pkg" | grep -E 'versionCode=24([[:space:]]|$)'
fi
echo "PASS API $api"
