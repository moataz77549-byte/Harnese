# Harnese Nova 2.0.0 RC1 — Android 9+ ARM64

- Based on the latest Nova 1.3.5 repair source merged with main.
- Resumable verified downloads and ARM64 ELF validation.
- Official CLI updates are tested in the guest before activation.
- Independent Codex installer, latest-version check, device sign-in and logout, and Linux sandbox diagnostics in Settings.
- Runtime archives enforce safe paths, size limits and regular-file writes.
- Existing application identity and owner signing certificate are used.
- Online installation, with optional Linux development toolchains. No Android SDK, NDK or emulator is bundled in the APK.

This release candidate requires first-install, in-place upgrade, Linux runtime, PTY, Google OAuth and Codex sandbox/authentication checks on actual ARM64 Android devices. The CI workflow verifies the APK, JNI names, package, architecture, tests and owner certificate. Device testing is pending.

برمجة وتطوير معتز العلقمي – تعز – اليمن – 2026م
