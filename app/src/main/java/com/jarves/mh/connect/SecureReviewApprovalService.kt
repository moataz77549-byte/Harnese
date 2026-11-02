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
    private val excluded = setOf(".git", "node_modules", "build", ".gradle", ".idea")
    private fun files(root: File): List<File> = root.walkTopDown().onEnter {
        it == root || (!Files.isSymbolicLink(it.toPath()) && it.name !in excluded)
    }.filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }.toList()
    private fun safe(root: File, path: String): File {
        require(path.isNotBlank() && !File(path).isAbsolute && path.split('/').none { it == ".." })
        var file = root.canonicalFile
        path.split('/').forEach { file = File(file, it); require(!Files.isSymbolicLink(file.toPath())) }
        require(file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()))
        return file
    }
    private fun save(id: String, json: JSONObject) {
        val temp = File(folder(id), "review.json.tmp")
        temp.writeText(json.toString())
        Files.move(temp.toPath(), metadata(id).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
    @Synchronized fun stage(project: String, original: File): StagedRun {
        val id = UUID.randomUUID().toString()
        val root = original.canonicalFile
        val input = files(root)
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
        save(id, JSONObject().put("id", id).put("project", project).put("original", root.path)
            .put("state", "running").put("expires", System.currentTimeMillis() + 24L * 60 * 60 * 1000).put("baseline", baseline))
        return StagedRun(id, workspace)
    }
    @Synchronized fun finish(id: String): Review {
        val json = JSONObject(metadata(id).readText())
        require(json.getString("state") == "running")
        val workspace = File(folder(id), "workspace")
        val baseline = json.getJSONObject("baseline")
        val paths = baseline.keys().asSequence().toSet() + files(workspace).map { it.relativeTo(workspace).invariantSeparatorsPath }
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
    fun preview(id: String): String {
        val json = JSONObject(metadata(id).readText())
        val original = File(json.getString("original"))
        val staged = File(folder(id), "workspace")
        return read(id).entries.joinToString("\n\n") { entry ->
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
    @Synchronized fun applyLocally(id: String) {
        val json = JSONObject(metadata(id).readText()); val review = read(id)
        require(review.state == "pending" && review.expires > System.currentTimeMillis()) { "Review expired or already consumed" }
        val original = File(json.getString("original")); val staged = File(folder(id), "workspace"); val backup = File(folder(id), "backup")
        review.entries.forEach { e ->
            require(hash(safe(original, e.path)) == e.before) { "Original changed: ${e.path}" }
            require(hash(safe(staged, e.path)) == e.after) { "Proposal changed: ${e.path}" }
            val source = safe(original, e.path)
            if (source.isFile) { val dest = safe(backup, e.path); dest.parentFile.mkdirs(); source.copyTo(dest, overwrite = true); dest.setExecutable(source.canExecute(), true) }
        }
        // Persist before mutation. A crash leaves an explicit recovery record, never an auto approval.
        save(id, json.put("state", "applying"))
        try {
            review.entries.forEach { e ->
                val dest = safe(original, e.path)
                if (e.after == null) { check(!dest.exists() || dest.delete()) }
                else {
                    dest.parentFile.mkdirs()
                    val temp = File(dest.parentFile, ".nova-${UUID.randomUUID()}.tmp")
                    val source = safe(staged, e.path); source.copyTo(temp)
                    temp.setExecutable(source.canExecute(), true)
                    Files.move(temp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                }
            }
            save(id, json.put("state", "applied").put("appliedAt", System.currentTimeMillis()))
        } catch (error: Exception) {
            restore(original, backup, review.entries)
            save(id, json.put("state", "rolled_back"))
            throw error
        }
    }
    private fun restore(original: File, backup: File, entries: List<Entry>) {
        entries.forEach { e -> val dest = safe(original, e.path)
            if (e.before == null) { check(!dest.exists() || dest.delete()) }
            else { dest.parentFile.mkdirs(); safe(backup, e.path).copyTo(dest, overwrite = true); dest.setExecutable(safe(backup,e.path).canExecute(),true) }
        }
    }
    @Synchronized fun rollbackLocally(id: String) {
        val json = JSONObject(metadata(id).readText()); val review = read(id)
        require(review.state == "applied" || review.state == "applying")
        val original = File(json.getString("original"))
        review.entries.forEach { require(hash(safe(original,it.path)) in listOf(it.before, it.after)) { "File changed after approval" } }
        restore(original, File(folder(id), "backup"), review.entries)
        save(id,json.put("state", "rolled_back"))
    }
}
