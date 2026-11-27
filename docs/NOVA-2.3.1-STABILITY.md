# Nova 2.3.1 first stability increment

Baseline: 2bd747ddea6eef3a9266162d1ccfe87ef6322d9c. This branch deliberately leaves the signed 2.3.0 release source intact. Version 28 candidate. Selected-path approval, transaction metadata sync, atomic rollback and selection regression coverage are added. No claims of live CLI or Android upgrade tests. Remaining work: authoritative workspace mapping, permission/session continuation, safe backup/import, authentication persistence, process lifecycle, device integration and original-key signing/publishing.

## Further stability increments
- Existing workspace roots are resolved without silently recreating missing directories.
- Last opened workspace restored at startup; explicit close clears navigation preference.
- Interrupted staged proposals are retained for local review; terminal command result can be reviewed/edited before explicit continuation.
- Multiline shell text is not rewritten as a package installation command.
- ZIP import rejects unsafe and duplicate names; default export excludes known credential paths.
- CI has an owner-signing gate, certificate comparison with 2.1, source freshness verification, and an Android 11 synthetic upgrade gate. Missing secrets stop publication, and public signature transport remains supported.
- Live provider, physical device, authentication continuity, automated backup retention and full recovery matrix still require verification.
