package com.jarves.mh.runtime

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class ReleaseArtifactsAndSearchTest {
    private fun archive(path: File, vararg entries: String): File {
        path.parentFile?.mkdirs()
        ZipOutputStream(path.outputStream()).use { out ->
            entries.forEach { entry ->
                out.putNextEntry(ZipEntry(entry))
                out.write(byteArrayOf(1, 2))
                out.closeEntry()
            }
        }
        return path
    }

    @Test fun releaseArtifactsAreSeparateFromDebugAndRejectMalformedBundles() {
        val root = Files.createTempDirectory("nova-release-test").toFile()
        try {
            val release = archive(File(root, "app/build/outputs/apk/release/app-release-unsigned.apk"), "AndroidManifest.xml")
            val debug = archive(File(root, "app/build/outputs/apk/debug/app-debug.apk"), "AndroidManifest.xml")
            archive(File(root, "app/build/outputs/bundle/release/invalid.aab"), "fake")
            assertEquals(release, findReleaseApk(root))
            assertEquals(debug, findDebugApk(root))
            assertNull(findReleaseBundle(root))
            val valid = archive(File(root, "app/build/outputs/bundle/release/app-release.aab"),
                "BundleConfig.pb", "base/manifest/AndroidManifest.xml")
            assertEquals(valid, findReleaseBundle(root))
            assertTrue(isAndroidArtifact(release, false))
            assertFalse(isAndroidArtifact(release, true))
        } finally { root.deleteRecursively() }
    }

    @Test fun projectSearchFindsSourceLocationsButSkipsBuildOutputAndPrivatePaths() {
        val root = Files.createTempDirectory("nova-search-test").toFile()
        try {
            File(root, "Main.kt").writeText("hello\nval target = 5")
            File(root, "build").mkdirs()
            File(root, "build/Generated.kt").writeText("target")
            File(root, "credentials.json").writeText("target")
            val results = searchProjectContent(root, "TARGET")
            assertEquals(listOf(ProjectSearchHit("Main.kt", 2, "val target = 5")), results.hits)
        } finally { root.deleteRecursively() }
    }
}
