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
curl --fail --location --retry 3 https://github.com/moataz77549-byte/Harnese/releases/download/harnese-nova-v4.0.0/Harnese-Nova-4.0.0-Android8Plus-ARM64.apk -o "$previous"
echo "aa5ec305cb516935dfd124e1694adbfebecb97ce8e2cea71a17095408ce0a20a  $previous" | sha256sum -c -
adb install "$previous"
launch() {
  adb shell am start -W -n "$pkg/com.jarves.mh.MainActivity" | grep -F 'Status: ok'
  sleep 4
  adb shell pidof "$pkg"
}
launch
adb shell am force-stop "$pkg"
dir="/data/user/0/$pkg"
uid_before=$(adb shell stat -c %u "$dir" | tr -d '\r')
[[ "$uid_before" =~ ^[0-9]+$ && "$uid_before" != 0 ]]
paths=(files/workspaces/upgrade-fixture/src/Main.kt files/chats/upgrade-fixture/main.json files/runtime/ubuntu/root/upgrade-fixture/env.txt files/terminal-history/upgrade-fixture-slot-2.json shared_prefs/nova_chat_drafts.xml)
values=('fun main() = Unit' '[{"id":"one","fromUser":true,"text":"اختبار بقاء المحادثة"}]' 'synthetic environment marker' '{"cwd":"/workspace/example","lines":[]}' '<map><string name="example:main">مسودة تجريبية</string></map>')
seed=$(mktemp)
for i in "${!paths[@]}"; do
  path="$dir/${paths[i]}"
  printf '%s' "${values[i]}" > "$seed"
  adb shell mkdir -p "${path%/*}"
  adb push "$seed" "$path"
  adb shell chown "$uid_before:$uid_before" "$path"
  adb shell restorecon "$path"
done
adb install -r delivery/Harnes-Plus-5.0.0-Android8Plus-ARM64.apk
launch
adb shell dumpsys package "$pkg" > /tmp/nova-package.txt
grep -E 'versionCode=32([[:space:]]|$)' /tmp/nova-package.txt
test "$(adb shell stat -c %u "$dir" | tr -d '\r')" = "$uid_before"
for i in "${!paths[@]}"; do
  printf '%s' "${values[i]}" > "$seed"
  adb exec-out cat "$dir/${paths[i]}" | cmp "$seed" -
done
echo '4.0.0 upgraded in place to 5.0.0; package UID and all five private synthetic fixtures verified.'
