# Harnese Connect 1.2 — بوت تيليجرام المحلي

**المنفذ:** Command Bus + encrypted BotFather token + one-time pairing + Telegram long polling + UI for owner.

1. Settings > Telegram Connect.
2. Enter new BotFather token, save; set temporary pairing code and start bot.
3. Send /pair CODE in private chat, then /projects, /open PROJECT_ID, /chats, /chat CHAT_ID, /newchat, /status, /pending.
4. Enable remote agent prompts on phone (OFF by default) to send /ask YOUR_PROMPT. /stop stops active task. /reject APPROVAL_UUID rejects a pending tool.
5. All commands are handled by the same app MainViewModel via HarneseCommandBus; no direct unrestricted shell/file API.

## Important security limits
- Telegram-only *approval* is not implemented. Local phone tool approval remains required; Telegram may reject.
- Remote /ask can modify files according to existing agent policies; there is no staged patch approval yet. Keep this disabled for sensitive projects.
- Bot token AES-GCM via Android Keystore, paired by Telegram numerical user+chat IDs in private chat, 9-digit one-time code good 5 minutes and max five guesses. Revoke in settings.
- Persist update offset *before* executing to prevent duplicate remote prompts after process restart: at-most-once, not exactly-once delivery. A command may need to be resent after crash.
- Only a service started by the user runs while phone is powered and online. Android background restrictions can interrupt polling.
- An active MainViewModel (app process/UI alive) is required for project operations; gateway reports unavailable when it is absent.
- getUpdates conflicts with existing webhook or a second poller. Disable them yourself; the app does not delete a webhook.
- Secrets and code should not be sent via Telegram; bot conversations transit Telegram infrastructure.
- Original signed app data is unchanged: same applicationId as previous Harnese builds.
- Before Play Store distribution, review the specialUse foreground-service declaration against Google Play policies.

## Automated checks
- Kotlin unit tests for command parser and command results.
- CI Android debug build/tests.
- Real device login/pairing/background tests still required.

## أوامر إضافية
- `/newproject NAME` لإنشاء مشروع فارغ جديد.
- `/files` لعرض أسماء ملفات جذر المشروع دون إرسال محتوياتها.
- `/history` لعرض آخر ثماني رسائل في الدردشة المختارة (لا ترسل المحادثة إن كانت تحتوي على أسرار).
