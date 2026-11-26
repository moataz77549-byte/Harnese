# Harnese Nova 2.0.0 RC6 — Project awareness

- Recognizes imported ZIP and Git repositories as projects. Existing quick projects with recognizable local files are promoted without deleting data or changing roots.
- Detects a unique nested Gradle root for explicit terminal build commands, retains the current valid directory and respects explicit Gradle project-directory arguments. Ambiguous roots require user selection.
- Exposes filesystem metadata (framework, bounded visible names, local branch, sanitized GitHub origin) in project chat. Codex receives this metadata as context. Existing sandbox requirements, staging and local review still govern source access and edits; if sandbox verification fails, only metadata and text assistance are available.
- Explicit GitHub origin verification uses the connected official GitHub CLI account and returns only repository identity and default branch. Credentials stay in the official authentication environment.
- Avoids repeatedly invalidating saved Antigravity credentials on ambiguous authentication errors; confirmed revocation still requests reconnection. The official provider controls token renewal and expiry.
- Bot commands validate the specified workspace and actual agent state before acknowledging requests; no new remote execution commands added. Codex no longer displays a Claude model label.

Application ID: `com.moataz.harnese.nova`, version code 25, Android 8+ ARM64. Original owner signature. Real device and live provider account tests remain to be performed.

برمجة وتطوير معتز العلقمي – تعز – اليمن – 2026م
