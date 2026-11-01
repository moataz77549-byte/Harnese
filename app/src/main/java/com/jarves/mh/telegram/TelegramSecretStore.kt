package com.jarves.mh.telegram

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Telegram credentials never enter project files, logs, or cloud storage.
 * AndroidKeyStore AES-GCM is used instead of deprecated EncryptedSharedPreferences.
 */
class TelegramSecretStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("harnese_telegram_private", Context.MODE_PRIVATE)
    private val rng = SecureRandom()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey("harnese.telegram.bot.v1", null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            "harnese.telegram.bot.v1",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }

    @Synchronized fun saveToken(raw: String) {
        val token = raw.trim()
        require(TOKEN_PATTERN.matches(token)) { "صيغة توكن BotFather غير صحيحة." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("token_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("token_blob", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .remove("linked_user").remove("linked_chat").remove("last_project").remove("last_chat")
            .remove("pair_hash").remove("pair_expiry").remove("pair_attempts").remove("update_offset")
            .commit()
    }

    fun token(): String? = runCatching {
        val iv = prefs.getString("token_iv", null) ?: return null
        val body = prefs.getString("token_blob", null) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(body, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()

    fun hasToken(): Boolean = prefs.contains("token_blob")
    fun remoteTasksEnabled(): Boolean = prefs.getBoolean("remote_tasks_enabled", false)
    fun setRemoteTasksEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("remote_tasks_enabled", enabled).commit()
    }
    fun linkedUserId(): Long = prefs.getLong("linked_user", 0L)
    fun linkedChatId(): Long = prefs.getLong("linked_chat", 0L)
    fun lastProjectId(): String? = prefs.getString("last_project", null)
    fun lastChatId(): String? = prefs.getString("last_chat", null)
    fun selection(projectId: String, chatId: String?) {
        prefs.edit().putString("last_project", projectId).putString("last_chat", chatId).apply()
    }
    fun selectChat(chatId: String) { prefs.edit().putString("last_chat", chatId).apply() }
    fun updateOffset(): Long = prefs.getLong("update_offset", 0L)

    /** At-most-once dispatch: acknowledge locally BEFORE executing remote work. */
    fun reserveUpdate(updateId: Long): Boolean = synchronized(this) {
        if (updateId < updateOffset()) false
        else prefs.edit().putLong("update_offset", updateId + 1L).commit()
    }

    @Synchronized fun generatePairCode(): String {
        require(hasToken()) { "أدخل توكن البوت أولًا." }
        val code = (100_000_000L + randomCodePart()).toString()
        prefs.edit().putString("pair_hash", hash(code))
            .putLong("pair_expiry", System.currentTimeMillis() + 5L * 60_000L)
            .putInt("pair_attempts", 0).commit()
        return code
    }
    private fun randomCodePart(): Long = (rng.nextInt(900_000_000)).toLong()
    private fun hash(code: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(code.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** Pair only via a private chat, one linked user, and a five-minute one-time code. */
    @Synchronized fun tryPair(code: String, userId: Long, chatId: Long, privateChat: Boolean): Boolean {
        if (!privateChat || userId <= 0 || chatId != userId || linkedUserId() != 0L) return false
        val expected = prefs.getString("pair_hash", null) ?: return false
        val attempts = prefs.getInt("pair_attempts", 0)
        val expiresAt = prefs.getLong("pair_expiry", 0L)
        if (attempts >= 5 || System.currentTimeMillis() >= expiresAt) return false
        prefs.edit().putInt("pair_attempts", attempts + 1).commit()
        val provided = hash(code)
        if (!MessageDigest.isEqual(expected.toByteArray(), provided.toByteArray())) return false
        return prefs.edit()
            .putLong("linked_user", userId).putLong("linked_chat", chatId)
            .remove("pair_hash").remove("pair_expiry").remove("pair_attempts")
            .commit()
    }

    fun authorized(userId: Long, chatId: Long, privateChat: Boolean): Boolean =
        privateChat && userId > 0L && linkedUserId() == userId && linkedChatId() == chatId

    @Synchronized fun unlink() {
        prefs.edit().remove("linked_user").remove("linked_chat").remove("last_project")
            .remove("last_chat").remove("pair_hash").remove("pair_expiry")
            .remove("pair_attempts").commit()
    }
    @Synchronized fun clear() {
        prefs.edit().clear().commit()
    }
    companion object {
        private val TOKEN_PATTERN = Regex("^[0-9]{5,12}:[A-Za-z0-9_-]{30,}$")
    }
}
