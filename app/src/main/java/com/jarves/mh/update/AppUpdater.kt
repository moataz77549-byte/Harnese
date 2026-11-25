package com.jarves.mh.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.jarves.mh.BuildConfig
import com.jarves.mh.R
import com.jarves.mh.ui.AppStrings
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject
import org.json.JSONArray
import com.jarves.mh.runtime.VerifiedDownload
import com.jarves.mh.runtime.atomicReplace

data class AppUpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val notes: String,
)

class AppUpdater(
    private val context: Context,
    /**
     * Optional manifest URL override. When non-empty, used in place of
     * [BuildConfig.APP_UPDATE_MANIFEST_URL]. Debug builds populate this
     * from Settings → Update channel so the update flow can be exercised
     * end-to-end against a Cloudflare Tunnel or ngrok HTTPS URL without
     * publishing a release to GitHub.
     */
    private val manifestUrlOverride: String = "",
) {
    private fun str(@androidx.annotation.StringRes id: Int, vararg args: Any): String =
        AppStrings.get(context, id, *args)

    fun check(): AppUpdateInfo? {
        if (manifestUrlOverride.isNotBlank()) return readUpdate(manifestUrlOverride)
        val available = mutableListOf<AppUpdateInfo>()
        runCatching { readUpdate(BuildConfig.APP_UPDATE_MANIFEST_URL) }.getOrNull()?.let(available::add)
        // A device already running a Nova release candidate follows that channel.
        if (context.packageName == "com.moataz.harnese.nova" && BuildConfig.VERSION_NAME.contains("-rc.")) {
            val catalog = runCatching { fetchMetadata("https://api.github.com/repos/moataz77549-byte/Harnese/releases?per_page=20") }.getOrNull()
            if (catalog != null) {
                val releases = JSONArray(catalog)
                novaUpdateManifestUrls(releases).take(5).forEach { url ->
                    runCatching { readUpdate(url) }.getOrNull()?.let(available::add)
                }
            }
        }
        return available.maxByOrNull(AppUpdateInfo::versionCode)
    }

    private fun readUpdate(manifestUrl: String): AppUpdateInfo? {
        val body = fetchMetadata(manifestUrl) ?: return null
        val root = JSONObject(body)
        val versionCode = root.optLong("versionCode")
        if (versionCode <= BuildConfig.VERSION_CODE) return null
        val artifact = root.optJSONObject("artifacts")?.optJSONObject(BuildConfig.APP_VARIANT)
            ?: root.optJSONObject(BuildConfig.APP_VARIANT) ?: root
        val url = artifact.optString("url").ifBlank { artifact.optString("apkUrl") }
        val digest = artifact.optString("sha256").lowercase()
        val size = artifact.optLong("sizeBytes", -1L)
        if (!url.startsWith("https://") || !digest.matches(Regex("[0-9a-f]{64}")) || size <= 0L) return null
        return AppUpdateInfo(versionCode, root.optString("versionName", versionCode.toString()), url, digest, size, root.optString("notes"))
    }

    private fun fetchMetadata(url: String): String? {
        if (!url.startsWith("https://")) return null
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Harnese-Nova")
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { input ->
                val data = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    check(data.size() + n <= 2 * 1024 * 1024) { "Update metadata exceeds its size limit" }
                    data.write(buffer, 0, n)
                }
                data.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }

    suspend fun download(info: AppUpdateInfo, progress: (Long, Long) -> Unit): File {
        val directory = File(context.filesDir, "updates").also { it.mkdirs() }
        val candidate = File(directory, "mobile-harness-${BuildConfig.APP_VARIANT}-${info.versionCode}.verified.apk")
        val target = File(directory, "mobile-harness-${BuildConfig.APP_VARIANT}-${info.versionCode}.apk")
        VerifiedDownload().fetch(URL(info.apkUrl), candidate, info.sha256, "SHA-256", info.sizeBytes) { bytes, total ->
            progress(bytes, total)
        }
        try {
            verifyApk(candidate, info.versionCode)
            atomicReplace(candidate, target)
            return target
        } catch (error: Throwable) {
            candidate.delete()
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyApk(apk: File, expectedVersionCode: Long) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error(str(R.string.rt_update_invalid_apk))
        check(archive.packageName == context.packageName) { str(R.string.rt_update_package_mismatch) }
        val archiveVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.longVersionCode else archive.versionCode.toLong()
        check(archiveVersion == expectedVersionCode && archiveVersion > BuildConfig.VERSION_CODE) { str(R.string.rt_update_version_mismatch) }
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val archiveSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.signingInfo?.apkContentsSigners else archive.signatures
        val installedSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) installed.signingInfo?.apkContentsSigners else installed.signatures
        check(!archiveSignatures.isNullOrEmpty() && !installedSignatures.isNullOrEmpty() &&
            archiveSignatures.map { sha256(it.toByteArray()) }.toSet() == installedSignatures.map { sha256(it.toByteArray()) }.toSet()
        ) { str(R.string.rt_update_signature_mismatch) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}

internal fun novaUpdateManifestUrls(releases: JSONArray): List<String> = buildList {
    for (i in 0 until releases.length()) {
        val release = releases.optJSONObject(i) ?: continue
        if (release.optBoolean("draft")) continue
        val tag = release.optString("tag_name")
        if (!tag.matches(Regex("harnese-nova-v[0-9]+\\.[0-9]+\\.[0-9]+(?:-rc\\.[0-9]+)?"))) continue
        val assets = release.optJSONArray("assets") ?: continue
        for (j in 0 until assets.length()) {
            val asset = assets.optJSONObject(j) ?: continue
            val expected = "https://github.com/moataz77549-byte/Harnese/releases/download/$tag/harnese-nova-update.json"
            if (asset.optString("name") == "harnese-nova-update.json" && asset.optString("browser_download_url") == expected) add(expected)
        }
    }
}
