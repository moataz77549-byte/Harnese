# Harnese Nova 2.0.0 RC3 — Android 9+ ARM64

- The online APK no longer embeds the ~127 MB Codex archive. First-time setup defaults to downloading the official stable Linux ARM64 Codex CLI, even when another primary agent is chosen. The checkbox may be disabled unless Codex is the primary agent. The installer validates release metadata, checks SHA-256 and architecture, probes the executable version before activation, and resumes interrupted downloads of the same version. Existing installations are reused.
- Codex is a fourth selectable coding engine in the main agent screen and project chat. Its account flow, cancelable tasks, on-demand installation and updates, CLI model catalog and model selection, update checking and project review are integrated.
- Project edits run in a secret-filtered staging copy only after the official Linux sandbox probe succeeds; local approval in Agent Hub is required to apply them. If the sandbox cannot start, the chat attempts text-only assistance with no project mounted, keeping the provider's read-only sandbox enabled.
- Antigravity continues to discover verified official stable releases. The online core remains downloadable, and the base APK contains no Android SDK/NDK.
- Versioned app-update manifest for the candidate channel and checksum/signature-validated resumable downloads.
- Existing Nova package and owner signing certificate retained.

Physical Android installation, account authentication and sandbox availability still require device verification. The previous RC2 Codex binary passed version and help probes under QEMU; the setup-installed current release is verified locally before activation. Cloud model responses require a valid provider account and network.

برمجة وتطوير معتز العلقمي – تعز – اليمن – 2026م
