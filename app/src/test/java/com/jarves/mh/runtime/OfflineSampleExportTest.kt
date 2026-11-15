package com.jarves.mh.runtime

import com.jarves.mh.model.AndroidTemplate
import java.io.File
import org.junit.Test

class OfflineSampleExportTest {
    @Test fun exportRealTemplateWhenRequestedByOfflineCI() {
        val destination = System.getenv("NOVA_OFFLINE_SAMPLE_DIR") ?: return
        AndroidProjectTemplateGenerator.generate(File(destination), "NovaOfflineSmoke", AndroidTemplate.XML)
        AndroidProjectTemplateGenerator.generate(File(destination + "-compose"), "NovaOfflineCompose", AndroidTemplate.COMPOSE)
    }
}
