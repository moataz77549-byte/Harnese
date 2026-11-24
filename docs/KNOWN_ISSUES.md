# Known issues and reproduction status

| Issue | Evidence or verification required |
| --- | --- |
| Setup appears to stall around 62% | Node setup runs inside this part of the legacy progress scale. The legacy downloader could accept a mismatched HTTP range and discarded the entire partial file after failing the digest. A UI stall on a physical phone still requires a trace. |
| Antigravity update can damage a working install | The old updater renamed a replacement over the working executable before invoking its version probe. |
| `agy` and `gh` missing from interactive PATH | Their guest location was `/root/.local/bin`, absent from the base environment. |
| Google OAuth exchange failure | The app hands the flow to the official CLI through a PTY. An authorized account and ARM64 Android device are required to test the complete browser code exchange. |
| Zstandard JNI abort | The stable 1.3.4 fix preserves the JNI boundary fields from R8 renaming and added a DEX release check. A new ARM64 start-up run is required to validate the reported crash against this candidate. |
| Unsupported provider models | Provider catalog retrieval, authentication state and per-account access need separate live verification. |
| Legacy target SDK | The direct APK retains target 28 because Android 10 blocks app-home executable launches for targets 29 and later. The Play configuration targets 36 and needs a validated execution design for that target. |
