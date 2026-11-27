# Harnes Plus 5.0.0 — Android 8+ ARM64

- New H+ vector/adaptive icon, navy/teal light and dark colors, and Harnes Plus application label.
- Conversation history search by message text, work activity and attachment name; navigate to original message; explicit copy action.
- Project dashboard shows the selected agent/provider connection status.
- Workspace screens separated into project, Android build, file/editor, and conversation source modules. UI state models extracted from MainViewModel without changing stored values.
- Coalesces terminal display updates when output is backlogged, preserving final output, input and stop behavior.
- Retains existing editor, Linux environments, agent setup, workflows, projects, and backup formats. No destructive data migrations, cleanup or environment reinstall added.
- Package `com.moataz.harnese.nova`, versionCode 32, minSdk 26, ARM64. Existing owner certificate required. Previously published APK from 4.0.0 is the in-place upgrade baseline.

## Verification policy

The release gate builds and runs unit tests, builds a sample Android project, checks APK/native structure, label and lint, and requires original signing identity and an Android 11 upgrade with private synthetic fixtures before publishing. Real provider sessions, PRoot workloads, project credentials and the user's physical device need separate interactive testing. This is a targeted modularization, not a full rewrite of all application controllers or a language server implementation.

برمجة وتطوير معتز العلقمي – تعز – اليمن – 2026م
