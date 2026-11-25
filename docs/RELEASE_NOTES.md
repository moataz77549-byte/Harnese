# Harnese Nova 2.0.0 RC2 — Android 9+ ARM64

- Official unmodified OpenAI Codex CLI 0.172.0 Linux ARM64 musl archive is now embedded in the APK, with verified SHA-256 and accompanying Apache 2.0 license.
- Codex is a fourth selectable coding engine in the main agent screen and project chat. Its account flow, cancelable tasks, bundled restoration, CLI model catalog and model selection, update checking and project review are integrated.
- Project edits run in a secret-filtered staging copy only after the official Linux sandbox probe succeeds; local approval in Agent Hub is required to apply them. If the sandbox cannot start, the chat attempts text-only assistance with no project mounted, keeping the provider's read-only sandbox enabled.
- Antigravity continues to discover verified official stable releases. The online core remains downloadable, and the base APK contains no Android SDK/NDK.
- Versioned app-update manifest for the candidate channel and checksum/signature-validated resumable downloads.
- Existing Nova package and owner signing certificate retained.

Physical Android installation, account authentication and sandbox availability still require device verification. The ARM64 Codex binary passed the version and help probes under QEMU. The official CLI archive is bundled; cloud model responses still require a valid provider account and network.

برمجة وتطوير معتز العلقمي – تعز – اليمن – 2026م
