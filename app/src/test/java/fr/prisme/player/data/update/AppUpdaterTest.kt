package fr.prisme.player.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdaterTest {

    @Test
    fun `picks the prism apk asset and its version code`() {
        val json = """
            {
              "tag_name": "v0.2.0",
              "assets": [
                { "name": "notes.txt", "browser_download_url": "https://x/notes.txt", "size": 10 },
                { "name": "prism-42.apk", "browser_download_url": "https://x/prism-42.apk", "size": 12345 }
              ]
            }
        """.trimIndent()
        val update = AppUpdater.parseRelease(json)!!
        assertEquals(42, update.versionCode)
        assertEquals("0.2.0", update.versionName)
        assertEquals("https://x/prism-42.apk", update.apkUrl)
        assertEquals(12345L, update.sizeBytes)
    }

    @Test
    fun `release without apk yields no update`() {
        assertNull(AppUpdater.parseRelease("""{"tag_name":"v1","assets":[]}"""))
    }
}
