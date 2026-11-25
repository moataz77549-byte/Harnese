# Architecture

The Compose UI talks to a retained runtime owner through the ViewModel facade. The built-in agent registry routes Claude, DeepSeek and Antigravity calls to independent adapters. Android's foreground services own user-started work that must continue while the UI is backgrounded. PRoot and the JNI process bridge provide a Linux guest and PTY.

The app owns distinct directories for workspaces, histories, the Linux guest and cached artifacts. Runtime detection validates the guest layout and its markers before launching processes. Downloads are staged, bounded and checked against trusted asset digests before publication. The official CLI installer probes the staged ARM64 executable inside the guest before replacing the active executable.

PRoot provides Linux compatibility. It does not enforce an independent security boundary. An agent requiring a stricter execution sandbox needs a successful provider sandbox probe or a separately isolated authorized host.
