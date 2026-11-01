# Harnese Nova — independent Android application

The Connect edition is installed **alongside** earlier Harnese applications.

| Variant | Application ID | Display name |
|---|---|---|
| Normal legacy app | `com.jarves.harnese` | Harnese |
| Connect edition (`-PmhParallelInstall=true`) | `com.moataz.harnese.nova` | Harnese Nova |

Both reuse the same functional code and Android 9+/ARM64 support. Their private storage (projects, chats, agent logins, BotFather token, and Linux runtime) is isolated and **not migrated automatically**.

## Critical rule: stable signing
Android upgrades require the **same package ID and same signing certificate** across releases. CI debug APKs are for test only and may change signing certificates on every GitHub Actions build.

A private, owner-held keystore is required for a stable signed APK. Never commit a JKS/P12 file or its password to a public repository. For future CI release builds, use `MH_UPLOAD_STORE_FILE`, `MH_UPLOAD_STORE_PASSWORD`, `MH_UPLOAD_KEY_ALIAS`, `MH_UPLOAD_KEY_PASSWORD` and build:
```bash
./gradlew :app:assembleOnlineRelease -PmhParallelInstall=true
```
The keystore and password must be retained outside the source tree and GitHub public artifacts. The same key must sign every Connect update.

## New features included in the build
- Antigravity CLI official install/update path, dynamic model discovery, code/project chat workflows.
- New typed `HarneseCommandBus` for project and chat access.
- Telegram personal bot with encrypted token, 5-minute one-time pairing, commands to create/list/open projects, list chats, inspect directory names and recent messages, send prompts and stop tasks.
- Remote `/ask` is off by default; phone-side tool approval is still required.
- The remote bot **does not** support accepting code patches, MCP database integrations, or headless agent execution when the UI is closed. Those are planned, not implemented.

## Try it
Open Settings > Telegram Connect, create a bot with @BotFather, enter token, save, generate code, start service, send `/pair CODE` privately, test `/projects`. Your original Harnese data remains untouched.
