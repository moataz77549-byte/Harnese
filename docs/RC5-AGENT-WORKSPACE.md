# Harnese Nova 2.0.0 RC5 — Agent & Workspace

- Switch agents directly from the workspace. During a running task, the requested switch waits for that task to finish; existing provider settings and chat history stay intact.
- Messages are routed only from the runtime and session that started the task. Assistant replies record the originating agent. Failed launches and missing terminal results are reported without presenting a successful task.
- Composer drafts survive rejected submissions; retry failed requests explicitly from chat. Queued requests retain their original project, chat and agent. Existing user projects are not deleted by closing a screen.
- Antigravity reuses the official saved credential when present, without automatically opening interactive OAuth at startup. A task failure does not clear account or conversation metadata. Explicit logout still clears the official login.
- Project chat offers Git command shortcuts that populate the terminal for user-controlled execution. Existing local review and approval gates remain in force.

Application ID: `com.moataz.harnese.nova`; version code 24; Android 8+ ARM64. Signed using the original owner certificate.

Device installation and live account behavior require a device test. The CI lint report includes pre-existing translation and indentation findings; new errors block the release.

برمجة وتطوير معتز العلقمي – تعز – اليمن – 2026م
