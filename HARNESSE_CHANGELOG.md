# Harnese — Antigravity modernization / تحديث أنتيجرافيتي

Based on MIT-licensed [Mobile-Harness](https://github.com/techjarves/Mobile-Harness), retaining its copyright and LICENSE.

## Changes

- Official Google Antigravity CLI update discovery from the official GitHub Releases API.
- Integrity verification using each release asset's published SHA-256 digest before installation.
- ARM64 official release archive extraction with support for nested paths.
- Antigravity model picker reads the actual `agy models` list from the signed-in account, avoiding false promises about paid models.
- Gemini 4 Argon (High/Medium/Low), Claude Opus 5.5 and Sonnet 5.5 human-friendly labels.
- Refresh available model catalog after updating the CLI, and when opening the model picker.
- Separate Android application ID `com.jarves.harnese` so the original app is not overwritten.

## العربية

في التطبيق افتح Agent، واختر Antigravity، ثم Runtime Updates > Check Updates > Update Antigravity. بعد ذلك افتح قائمة النماذج واضغط مزامنة. ستظهر نماذج Gemini 4 Argon فقط إذا أتاحتها Google لحسابك ومنطقتك واشتراكك. لا يمكن للتطبيق منح صلاحيات مدفوعة أو تجاوز قيود Google.

تحديث التطبيق لا يحدث تلقائياً على جهاز المستخدم بمجرد تحديث المستودع. ثبّت APK ناتجاً عن بناء ناجح. أندرويد 9 وما فوق وARM64 فقط وفق متطلبات المصدر الأصلي.

## Validation

CI runs Android online-debug unit tests and APK assembly. Signed release distribution requires a separate release key and a verified end-to-end test with an entitled Google account.
