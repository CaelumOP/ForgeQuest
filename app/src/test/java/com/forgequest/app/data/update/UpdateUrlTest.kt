package com.forgequest.app.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateUrlTest {
    @Test fun acceptsThisRepoReleaseFiles() {
        assertTrue(UpdateChecker.isTrustedApkUrl("https://github.com/CaelumOP/ForgeQuest/releases/download/v2.17.0/ForgeQuest-2.17.0.apk"))
    }

    @Test fun rejectsEverythingElse() {
        listOf(
            "",
            "http://github.com/CaelumOP/ForgeQuest/releases/download/v2.17.0/ForgeQuest-2.17.0.apk",
            "https://github.com/someone/ForgeQuest/releases/download/v2.17.0/ForgeQuest-2.17.0.apk",
            "https://github.com/CaelumOP/ForgeQuest/releases/download/",
            "https://github.com/CaelumOP/ForgeQuest/releases/download/../../../evil/app.apk",
            "https://github.com.evil.example/CaelumOP/ForgeQuest/releases/download/v1/a.apk",
            "https://raw.githubusercontent.com/CaelumOP/ForgeQuest/main/apk/ForgeQuest.apk",
            "https://example.com/?u=https://github.com/CaelumOP/ForgeQuest/releases/download/v1/a.apk",
        ).forEach { assertFalse(it, UpdateChecker.isTrustedApkUrl(it)) }
    }
}
