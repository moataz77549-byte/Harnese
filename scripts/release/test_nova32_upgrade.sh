#!/usr/bin/env bash
# Only the temporary Actions emulator is used for this synthetic upgrade check.
set -euo pipefail
pkg=com.moataz.harnese.nova
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
if adb shell pm path "$pkg" | grep -q '^package:'; then exit 1; fi
adb shell getprop ro.product.cpu.abilist | grep -F arm64-v8a
adb root
adb wait-for-device
previous="$(mktemp --suffix=.apk)"
curl --fail --location --retry 3 https://github.com/moataz77549-byte/Harnese/releases/download/harnese-nova-v3.0.0/Harnese-Nova-3.0.0-Android8Plus-ARM64.apk -o "$previous"
echo "b0ab61a5a74f03e011f77a10d6fe0e66c1f390d48e60816cb06f967622fa8003  $previous" | sha256sum -c -
adb install "$previous"
launch() {
  adb shell am start -W -n "$pkg/com.jarves.mh.MainActivity" | grep -F 'Status: ok'
  sleep 4
  adb shell pidof "$pkg"
}
launch
adb shell am force-stop "$pkg"
dir="/data/user/0/$pkg/files"
uid_before=$(adb shell stat -c %u "$dir" | tr -d '\r')
[[ "$uid_before" =~ ^[0-9]+$ && "$uid_before" != 0 ]]
paths=(workspaces/upgrade-fixture/src/Main.kt chats/upgrade-fixture/main.json runtime/ubuntu/root/upgrade-fixture/env.txt)
values=('fun main() = Unit' '[{"id":"one","fromUser":true,"text":"اختبار بقاء المحادثة"}]' 'synthetic environment marker')
seed=$(mktemp)
for i in "${!paths[@]}"; do
  path="$dir/${paths[i]}"
  printf '%s' "${values[i]}" > "$seed"
  adb shell mkdir -p "${path%/*}"
  adb push "$seed" "$path"
  adb shell chown "$uid_before:$uid_before" "$path"
  adb shell restorecon "$path"
done
adb install -r delivery/Harnese-Nova-3.2.0-Android8Plus-ARM64.apk
launch
adb shell dumpsys package "$pkg" > /tmp/nova-package.txt
grep -E 'versionCode=30([[:space:]]|$)' /tmp/nova-package.txt
test "$(adb shell stat -c %u "$dir" | tr -d '\r')" = "$uid_before"
for i in "${!paths[@]}"; do
  printf '%s' "${values[i]}" > "$seed"
  adb exec-out cat "$dir/${paths[i]}" | cmp "$seed" -
done
echo '3.0.0 upgraded in place to 3.2.0; package UID and all three private synthetic fixtures verified.'
