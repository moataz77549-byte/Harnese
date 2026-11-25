# Implementation and release sequence

1. Integrate the newest repair source with main and establish the package and signing baseline.
2. Fix resumable downloads, verify native CLI archive contents, and validate replacements before activation.
3. Expose independent Codex installation and compatibility checks while preserving the provider's execution sandbox.
4. Validate both JVM and Android packaging with the Android 9 minimum and ARM64 only.
5. Gate a production release on the existing signing identity and device results for install, upgrade, runtime, authentication and interactive sessions.
