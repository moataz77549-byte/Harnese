package com.jarves.mh.connect

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Agents edit copies. Only a locally authenticated caller may commit a pending review. */
class SecureReviewApprovalService(private val directory: File) {
    companion object {
        private val instances = mutableMapOf<String, SecureReviewApprovalService>()
        @Synchronized fun get(directory: File): SecureReviewApprovalService = instances.getOrPut(directory.canonicalPath) { SecureReviewApprovalService(directory) }
    }
    @Synchronized fun recoverInterruptedStaging() {
        records().filter { it.state == "running" }.forEach { runCatching { finish(it.id) } }
    }
    data class StagedRun(val id: String, val workspace: File)
    data class Entry(val path: String, val before: String?, val after: String?)
    data class Review(val id: String, val project: String, val state: String, val expires: Long, val entries: List<Entry>)
    private fun folder(id: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        return File(directory, id)
    }
    private fun metadata(id: String) = File(folder(id), "review.json")
    private fun hash(file: File): String? = if (!file.isFile) null else {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream -> val b = ByteArray(65536); while (true) { val n = stream.read(b); if (n < 0) break; digest.update(b, 0, n) } }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    private val excluded = setOf("build", ".gradle", ".idea")
    private fun sensitive(file: File): Boolean {
        val name = file.name.lowercase()
        return name == ".git" || name == ".env" || name.startsWith(".env.") ||
            name in setOf(".npmrc", ".ssh", ".gnupg", ".codex", ".gemini", "credentials.json", "secrets.json",
                "local.properties", "key.properties", "google-services.json") ||
            name.endsWith(".jks") || name.endsWith(".keystore") || name.endsWith(".p12") ||
            name.endsWith(".pem") || name.endsWith(".key")
    }
    private fun files(root: File, protectSensitive: Boolean = false): List<File> = root.walkTopDown().onEnter {
        it == root || (!Files.isSymbolicLink(it.toPath()) && it.name !in excluded && !(protectSensitive && sensitive(it)))
    }.filter { it.isFile && !Files.isSymbolicLink(it.toPath()) && !(protectSensitive && sensitive(it)) }.toList()
    private fun safe(root: File, path: String): File {
        require(path.isNotBlank() && !File(path).isAbsolute && path.split('/').none { it == ".." })
        var file = root.canonicalFile
        path.split('/').forEach { file = File(file, it); require(!Files.isSymbolicLink(file.toPath())) }
        require(file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()))
        return file
    }
    private fun save(id: String, json: JSONObject) {
        val temp = File(folder(id), "review.json.tmp")
        java.io.FileOutputStream(temp).use { output -> output.write(json.toString().toByteArray(Charsets.UTF_8)); output.fd.sync() }
        Files.move(temp.toPath(), metadata(id).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
    @Synchronized fun stage(project: String, original: File, protectSensitive: Boolean = false): StagedRun {
        val id = UUID.randomUUID().toString()
        val root = original.canonicalFile
        val input = files(root, protectSensitive)
        require(input.size <= 10000 && input.sumOf { it.length() } <= 200L * 1024 * 1024) { "Project is too large for safe staging" }
        require(root.usableSpace > input.sumOf { it.length() } * 3 + 64L * 1024 * 1024) { "Insufficient storage for staging and rollback" }
        val workspace = File(folder(id), "workspace").apply { mkdirs() }
        val baseline = JSONObject()
        input.forEach { file ->
            val path = file.relativeTo(root).invariantSeparatorsPath
            baseline.put(path, hash(file))
            val copy = safe(workspace, path); copy.parentFile.mkdirs(); file.copyTo(copy)
            copy.setExecutable(file.canExecute(), true)
        }
        root.walkTopDown().onEnter { it == root || (!Files.isSymbolicLink(it.toPath()) && it.name !in excluded && !(protectSensitive && sensitive(it))) }
            .filter { Files.isSymbolicLink(it.toPath()) && !protectSensitive }.forEach { link ->
                val destination = link.canonicalFile
                if (destination.toPath().startsWith(root.toPath())) {
                    val relative = link.relativeTo(root).invariantSeparatorsPath
                    val copy = File(workspace, relative)
                    copy.parentFile.mkdirs()
                    val resolved = workspace.toPath().resolve(destination.relativeTo(root).invariantSeparatorsPath)
                    Files.createSymbolicLink(copy.toPath(), copy.parentFile.toPath().relativize(resolved))
                }
            }
        save(id, JSONObject().put("id", id).put("project", project).put("original", root.path)
            .put("protectSensitive", protectSensitive).put("state", "running").put("expires", System.currentTimeMillis() + 24L * 60 * 60 * 1000).put("baseline", baseline))
        return StagedRun(id, workspace)
    }
    @Synchronized fun finish(id: String): Review {
        val json = JSONObject(metadata(id).readText())
        require(json.getString("state") == "running")
        val workspace = File(folder(id), "workspace")
        val baseline = json.getJSONObject("baseline")
        val paths = baseline.keys().asSequence().toSet() + files(workspace, json.optBoolean("protectSensitive")).map { it.relativeTo(workspace).invariantSeparatorsPath }
        val entries = JSONArray()
        paths.sorted().forEach { path ->
            val before = if (baseline.has(path)) baseline.getString(path) else null
            val after = hash(safe(workspace, path))
            if (before != after) entries.put(JSONObject().put("path", path).put("before", before ?: JSONObject.NULL).put("after", after ?: JSONObject.NULL))
        }
        json.put("entries", entries).put("state", if (entries.length() == 0) "unchanged" else "pending")
        save(id, json)
        return read(id)
    }
    @Synchronized fun abandon(id: String) {
        val json = JSONObject(metadata(id).readText())
        if (json.getString("state") == "running") {
            save(id, json.put("state", "rejected"))
            File(folder(id), "workspace").deleteRecursively()
        }
    }
    fun read(id: String): Review {
        val json = JSONObject(metadata(id).readText())
        val entries = json.optJSONArray("entries") ?: JSONArray()
        return Review(id, json.getString("project"), json.getString("state"), json.getLong("expires"), (0 until entries.length()).map {
            val e = entries.getJSONObject(it)
            Entry(e.getString("path"), if(e.isNull("before")) null else e.getString("before"), if(e.isNull("after")) null else e.getString("after"))
        })
    }
    fun records(): List<Review> = directory.listFiles().orEmpty().mapNotNull { runCatching { read(it.name) }.getOrNull() }
    fun pending(): List<Review> = records().filter { it.state == "pending" }
    fun matchesWorkspace(id: String, project: String, original: File): Boolean = runCatching {
        val json = JSONObject(metadata(id).readText())
        json.getString("project") == project && File(json.getString("original")).canonicalFile == original.canonicalFile && original.isDirectory
    }.getOrDefault(false)
    fun preview(id: String): String {
        val json = JSONObject(metadata(id).readText())
        val original = File(json.getString("original"))
        val staged = File(folder(id), "workspace")
        return read(id).entries.asSequence().take(100).joinToString("\n\n") { entry ->
            fun text(file: File) = if (!file.isFile) "[absent]" else if (file.length() > 32000 || file.readBytes().any { it == 0.toByte() }) "[binary or large file]" else file.readText()
            "--- ${entry.path}\n${text(safe(original, entry.path))}\n+++ ${entry.path}\n${text(safe(staged, entry.path))}"
        }.take(60000)
    }
    @Synchronized fun reject(id: String) {
        val json = JSONObject(metadata(id).readText()); require(json.getString("state") == "pending")
        save(id, json.put("state", "rejected"))
        File(folder(id), "workspace").deleteRecursively()
    }
    /** Only the phone's credential-confirmation result calls this method. Never expose remotely. */
    @Synchronized fun applyLocally(id: String, selectedPaths: Set<String>? = null) {
        val json = JSONObject(metadata(id).readText()); val review = read(id)
        require(review.state == "pending" && review.expires > System.currentTimeMillis()) { "Review expired or already consumed" }
        val approved = selectedPaths ?: review.entries.map { it.path }.toSet()
        require(approved.isNotEmpty() && approved.all { path -> review.entries.any { it.path == path } }) { "Invalid review selection" }
        val entries = review.entries.filter { it.path in approved }
        val original = File(json.getString("original")); val staged = File(folder(id), "workspace"); val backup = File(folder(id), "backup")
        require(original.isDirectory) { "Original workspace unavailable" }
        entries.forEach { e ->
            val target = safe(original, e.path)
            require(!target.exists() || target.isFile) { "Destination is not a regular file: ${e.path}" }
            require(hash(safe(original, e.path)) == e.before) { "Original changed: ${e.path}" }
            require(hash(safe(staged, e.path)) == e.after) { "Proposal changed: ${e.path}" }
            val source = safe(original, e.path)
            if (source.isFile) {
                val dest = safe(backup, e.path)
                dest.parentFile.mkdirs()
                java.io.FileOutputStream(dest).use { output ->
                    source.inputStream().use { it.copyTo(output) }
                    output.fd.sync()
                }
                dest.setExecutable(source.canExecute(), true)
                require(hash(dest) == e.before) { "Backup changed: ${e.path}" }
            }
        }
        // Persist before mutation. A crash leaves an explicit recovery record, never an auto approval.
        save(id, json.put("selectedPaths", JSONArray(approved.sorted())).put("state", "applying"))
        val mutated = mutableListOf<Entry>()
        try {
            entries.forEach { e ->
                val dest = safe(original, e.path)
                require(hash(dest) == e.before && (!dest.exists() || dest.isFile)) { "Original changed during approval: ${e.path}" }
                if (e.after == null) {
                    check(!dest.exists() || dest.delete())
                    mutated += e
                }
                else {
                    dest.parentFile.mkdirs()
                    val temp = File(dest.parentFile, ".nova-${UUID.randomUUID()}.tmp")
                    val source = safe(staged, e.path)
                    try {
                        java.io.FileOutputStream(temp).use { output ->
                            source.inputStream().use { it.copyTo(output) }
                            output.fd.sync()
                        }
                        temp.setExecutable(source.canExecute(), true)
                        require(hash(temp) == e.after) { "Staged copy verification failed: ${e.path}" }
                        require(hash(dest) == e.before && (!dest.exists() || dest.isFile)) { "Original changed before replacement: ${e.path}" }
                        Files.move(temp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                        mutated += e
                    } finally { temp.delete() }
                }
            }
            entries.forEach { e ->
                require(hash(safe(original, e.path)) == e.after) { "Applied file verification failed: ${e.path}" }
            }
            save(id, json.put("state", "applied").put("appliedAt", System.currentTimeMillis()))
        } catch (error: Exception) {
            // Never overwrite a concurrent edit to a file we did not mutate.
            // A conflicting rollback remains an explicit recovery record.
            try {
                mutated.forEach { e -> require(hash(safe(original, e.path)) == e.after) { "Concurrent edit prevents rollback: ${e.path}" } }
                restore(original, backup, mutated.asReversed())
                save(id, json.put("state", "rolled_back"))
            } catch (recovery: Exception) {
                error.addSuppressed(recovery)
                save(id, json.put("state", "applying").put("recoveryError", recovery.message.orEmpty()))
            }
            throw error
        }
    }
    private fun restore(original: File, backup: File, entries: List<Entry>) {
        entries.forEach { e -> val dest = safe(original, e.path)
            if (e.before == null) { check(!dest.exists() || dest.delete()) }
            else {
                val source = safe(backup, e.path)
                require(hash(source) == e.before) { "Backup verification failed: ${e.path}" }
                dest.parentFile.mkdirs()
                val temp = File(dest.parentFile, ".nova-restore-${UUID.randomUUID()}.tmp")
                try {
                    java.io.FileOutputStream(temp).use { output -> source.inputStream().use { it.copyTo(output) }; output.fd.sync() }
                    temp.setExecutable(source.canExecute(), true)
                    require(hash(temp) == e.before)
                    Files.move(temp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } finally { temp.delete() }
            }
        }
    }
    @Synchronized fun rollbackLocally(id: String) {
        val json = JSONObject(metadata(id).readText()); val review = read(id)
        require(review.state == "applied" || review.state == "applying")
        val original = File(json.getString("original"))
        val selection = json.optJSONArray("selectedPaths")
        val approved = selection?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        val entries = review.entries.filter { approved == null || it.path in approved }
        require(original.isDirectory) { "Original workspace unavailable" }
        entries.forEach { e ->
            val target = safe(original, e.path)
            require(!target.exists() || target.isFile)
            require(hash(target) in listOf(e.before, e.after)) { "File changed after approval" }
        }
        restore(original, File(folder(id), "backup"), entries)
        entries.forEach { require(hash(safe(original, it.path)) == it.before) { "Rollback verification failed: ${it.path}" } }
        save(id,json.put("state", "rolled_back"))
    }
}
