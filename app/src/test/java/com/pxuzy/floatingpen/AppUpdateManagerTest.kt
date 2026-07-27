package com.pxuzy.floatingpen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {
    @Test
    fun `parses latest release apk asset`() {
        val json = """
            {"tag_name":"v0.2.0","html_url":"https://github.com/Pxuzy/float-ink/releases/tag/v0.2.0",
             "assets":[{"name":"checksums.txt","browser_download_url":"https://example/checksums.txt",
             "uploader":{"login":"github-actions[bot]"}},
             {"name":"float-ink-0.2.0.apk","browser_download_url":"https://example/float-ink-0.2.0.apk",
             "uploader":{"login":"github-actions[bot]"}}]}
        """.trimIndent()

        val update = AppUpdateManager.parseLatestRelease(json)

        assertNotNull(update)
        assertEquals("0.2.0", update!!.version)
        assertEquals("https://example/float-ink-0.2.0.apk", update.downloadUrl)
    }

    @Test
    fun `ignores release without apk`() {
        val json = """{"tag_name":"v0.2.0","html_url":"https://example","assets":[]}"""
        assertNull(AppUpdateManager.parseLatestRelease(json))
    }

    @Test
    fun `compares semantic versions`() {
        assertTrue(AppUpdateManager.isNewer("0.2.1", "0.2.0"))
        assertFalse(AppUpdateManager.isNewer("0.2.0", "0.2.0"))
        assertFalse(AppUpdateManager.isNewer("0.1.9", "0.2.0"))
        assertTrue(AppUpdateManager.isNewer("v1.0.0", "0.9.9"))
    }
}
