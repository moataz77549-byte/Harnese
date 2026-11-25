package com.jarves.mh.update

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class NovaUpdateManifestTest {
    @Test fun trustsOnlyNovaReleaseManifestURLs() {
        val releases = JSONArray("""
            [
              {"tag_name":"harnese-nova-v2.0.0-rc.2","assets":[{"name":"harnese-nova-update.json","browser_download_url":"https://github.com/moataz77549-byte/Harnese/releases/download/harnese-nova-v2.0.0-rc.2/harnese-nova-update.json"}]},
              {"tag_name":"harnese-nova-v2.0.0-rc.3","draft":true,"assets":[]},
              {"tag_name":"harnese-nova-v2.0.0-rc.4","assets":[{"name":"harnese-nova-update.json","browser_download_url":"https://example.invalid/manifest"}]}
            ]""".trimIndent())
        assertEquals(listOf("https://github.com/moataz77549-byte/Harnese/releases/download/harnese-nova-v2.0.0-rc.2/harnese-nova-update.json"), novaUpdateManifestUrls(releases))
    }
}
