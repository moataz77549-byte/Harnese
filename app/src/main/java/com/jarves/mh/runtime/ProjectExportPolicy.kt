package com.jarves.mh.runtime

/** Default project ZIP exports must not carry credentials or signing keys. */
internal fun isPrivateProjectExportPath(relative: String): Boolean =
    relative.replace('\\', '/').split('/').any { part ->
        val name = part.lowercase()
        name in setOf(".git", ".ssh", ".gnupg", ".codex", ".gemini", ".claude", ".claude.json",
            ".pocketdev", ".npmrc", "credentials.json", "secrets.json", "key.properties", "local.properties",
            "google-services.json", ".env", "keystore.properties", "gradle.properties") ||
            name.startsWith(".env.") || listOf(".jks", ".keystore", ".p12", ".pfx", ".pk8", ".key", ".pem").any { name.endsWith(it) }
    }
