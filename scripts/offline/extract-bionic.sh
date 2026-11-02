#!/usr/bin/env bash
set -euo pipefail
# CI-only Android runtime for executing the actual Android ARM64 aapt2 on Linux ARM64.
# No Bionic libraries are added to the APK; phones supply their own Android runtime.
sdkmanager 'system-images;android-30;google_apis;arm64-v8a'
sudo apt-get update
sudo apt-get install -y android-sdk-libsparse-utils unzip
IMAGE="$ANDROID_HOME/system-images/android-30/google_apis/arm64-v8a/system.img"
mkdir -p android-image android-runtime/system/bin android-runtime/system/lib64 apex-image
if file "$IMAGE" | grep -qi 'sparse'; then simg2img "$IMAGE" system.raw.img; else cp "$IMAGE" system.raw.img; fi
sudo mount -o loop,ro system.raw.img android-image
SYSTEM=android-image/system
if [ ! -d "$SYSTEM/lib64" ]; then SYSTEM=android-image; fi
APEX=$(find "$SYSTEM/apex" -maxdepth 1 -name 'com.android.runtime*.apex' -print -quit)
test -n "$APEX"
unzip -p "$APEX" apex_payload.img > runtime-apex.img
sudo mount -o loop,ro runtime-apex.img apex-image
cp apex-image/bin/linker64 android-runtime/system/bin/linker64
for name in libc.so libm.so libdl.so; do cp "apex-image/lib64/bionic/$name" "android-runtime/system/lib64/$name"; done
cp "$SYSTEM/lib64/libz.so" android-runtime/system/lib64/libz.so
chmod 755 android-runtime/system/bin/linker64
sha256sum android-runtime/system/bin/linker64 android-runtime/system/lib64/* > android-runtime/SHA256.txt
sudo umount apex-image android-image
