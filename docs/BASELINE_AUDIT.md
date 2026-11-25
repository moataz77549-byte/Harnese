# Harnese Nova baseline audit

The development branch started from main at `a04e4b2e1e1cf54dc1e19cdd4b18506975179874`. The newest GitHub release at the start of the audit was the 1.3.5 repair prerelease, built from `98c02c425170f8faa1b3ac6112244896276a36f4`. Its separate identifier is `com.moataz.harnese.nova.repair`. The newest stable release was 1.3.4, built from `2b1448d71eb604d5259a5a0dafe8948e3b3bc415`. The feature branch merges the 1.3.5 source to retain its changes and its inherited 1.3.4 changes.

| Area | Repository evidence |
| --- | --- |
| App | Native Kotlin and Jetpack Compose, Android Gradle Plugin 8.13.2, Kotlin 2.2.21, Gradle 8.14 |
| Android | ARM64 only, compile SDK 36. The 1.3.5 direct build configured min SDK 29 and target SDK 28. The requested direct candidate uses min SDK 28. |
| Native | NDK 28.2.13676358 and CMake 3.22.1. PRoot and libandroid-shmem are pinned Git submodules, with vendored talloc. NativeSpawn handles process creation and the PTY. |
| Runtime | Ubuntu 20.04.5 ARM64 rootfs, Git, Node and a glibc loader. The embedded offline edition adds verified Python and TypeScript archives. Android tools are optional. |
| Installation | Runtime overlays are checksum pinned. The core uses a staging directory and repair backup. Provider binaries and some overlays had separate activation paths. |
| Providers | Claude Code, DeepSeek Harness and Antigravity have independent bridges. No Codex driver in the baseline. |
| Authentication | Antigravity uses the CLI's interactive browser-code flow through a PTY. GitHub has device authorization. App-managed credentials use Android Keystore. |
| Integrations | Telegram has owner pairing, a typed command gateway and persisted task events. The repair source also carries work on reviewed file changes and MCP over HTTP. |
| Persistence | Workspaces, preferences, terminal histories and runtime live in private app storage. An in-place update requires the same package and signing identity. |
| Signing | The stable build expects the owner's SHA-256 certificate fingerprint `d7f31fde154fc9b46fe8ef333fd12ecdfbd9b331d71a5284c24a6ad8ad45de51`. Its private key is external to this checkout. |
| Verification | JVM tests, Gradle CI, native APK inspection, a Zstandard JNI DEX check, and an offline runtime smoke script are present. |

The official GitHub release endpoints listed Antigravity 1.5.16 and Codex CLI 0.172.0 as stable at the time of the audit. Both ARM64 archives were retrieved and their locally calculated SHA-256 digests matched the published asset digests.
