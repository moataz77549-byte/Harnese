#!/usr/bin/env bash
set -euo pipefail
# Invoked inside the extracted ARM64 guest with networking disabled by the CI host.
mkdir -p /tmp/nova-smoke
cd /tmp/nova-smoke
python3 -m venv venv
venv/bin/python -m pip --version
cat > test_offline.py <<'PY'
import unittest
class OfflineTest(unittest.TestCase):
    def test_runtime(self): self.assertEqual(sum([1,2,3]),6)
if __name__ == '__main__': unittest.main()
PY
venv/bin/python test_offline.py
node --version
npm --version
tsc --version
printf 'const result: number = 6 * 7; console.log(result);\n' > smoke.ts
tsc --target ES2020 --module commonjs smoke.ts
[ "$(node smoke.js)" = '42' ]
java -version
gradle --version
# The sample is generated from the app's real Kotlin template by the workflow.
cd /workspace/offline-kotlin
gradle --offline --stacktrace --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m --init-script /root/.gradle/init.d/pocketdev-android.gradle -Pandroid.aapt2FromMavenOverride=/root/android-sdk/build-tools/35.0.0/aapt2 assembleDebug
test -s app/build/outputs/apk/debug/app-debug.apk
/root/android-sdk/build-tools/35.0.0/aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk
