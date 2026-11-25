# Engine compatibility

Verified against the official stable release metadata on 25 November 2026.

| Engine | Official Linux ARM64 version | Archive SHA-256 | Executable test |
| --- | --- | --- | --- |
| Antigravity | 1.5.17 | d6d2b88493e6e02854da478b215cd1dd98fd8cf985b6be37829dacadea4ffc07 | `--version` and `--help` passed under AArch64 QEMU using the app's Ubuntu 20.04 core glibc 2.31 loader. |
| Codex | 0.172.0 | 0b99526c185a4425fe96a2214fb7b6a6a7f7c1471883b7afbebe97e15e83e79e | `--version` and `--help` passed under AArch64 QEMU. The executable is statically linked musl. |

Origins:

- https://github.com/google-antigravity/antigravity-cli/releases/latest
- https://github.com/openai/codex/releases/latest

The app discovers these versions dynamically and rejects missing or mismatched official SHA-256 digests. It verifies the extracted ELF architecture and version in the guest before installation.

Physical ARM64 Android launch, Google OAuth, ChatGPT device authorization, actual model responses and Codex's Linux sandbox remain to be checked with authorized accounts and a physical device. The Codex review action checks the official sandbox before opening a project; the application does not disable provider sandboxing or automatically call external tools.

Direct APK target SDK stays at 28 to retain compatibility with the existing Linux runtime. The release's minimum Android API is 28 (Android 9). Play target 36 needs a different validated execution architecture.
