# Harnese Nova 2.0.0 RC4 — GitHub Hotfix

- Fix GitHub repository JSON loading for accounts with hundreds of repositories: page-level field projection, complete stdout, separate stderr, retries, timeouts and Arabic messages.
- Preserve GitHub credentials, existing projects, downloaded Linux/PRoot environments, Codex and Antigravity deployments, preferences, conversations and on-device files. No database or filesystem migration is included in this hotfix.
- Application ID: `com.moataz.harnese.nova`. Version code 23; min SDK 26; ARM64 standalone APK.
- Original owner certificate: `d7f31fde154fc9b46fe8ef333fd12ecdfbd9b331d71a5284c24a6ad8ad45de51`.
- Built and verified by CI: unit tests, package identity, native libraries, ZIP integrity, Zstd JNI, Android API compatibility lint regression check, and APK signature/alignment verification. The complete lint report retains pre-existing translation/indentation findings, and the gate rejects new errors.
- Physical-device installation and live GitHub authentication have not been verified on a device. Install over the existing app without uninstalling or clearing data. Do not install if Android reports a signature mismatch.

برمجة وتطوير معتز العلقمي – تعز – اليمن – 2026م
