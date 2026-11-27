# Nova 2.3.1 stability increment 2
- Central existing-workspace resolver for UI, Claude, Codex, Dsh and Antigravity (via checkpoints).
- Missing directories are errors instead of automatically recreated; explicit project creation/import retains its original behavior.
- Scoped review checks project root before applying selected paths.
- Interrupted agent runs retain staged proposals for local review instead of silently discarding them; Antigravity adds output/time limits and stop checks.
- Default ZIP export excludes known credential/key paths; ZIP import rejects traversal, absolute paths and duplicate entries.
- Added JVM tests for missing workspace, nested roots, symlinks and export exclusions.
Not yet verified by CI or live devices. General ZIP import/export already existed; this is hardening, not a complete backup/restore system. Interactive headless approval, authentication and device upgrade remain open.
