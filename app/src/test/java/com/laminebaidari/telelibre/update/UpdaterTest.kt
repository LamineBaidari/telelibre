package com.laminebaidari.telelibre.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test
    fun comparesVersionsNumerically() {
        assertTrue(Updater.isNewer("1.2", "1.1"))
        assertTrue(Updater.isNewer("1.10", "1.9"))
        assertTrue(Updater.isNewer("1.1.1", "1.1"))
        assertFalse(Updater.isNewer("1.1", "1.1"))
        assertFalse(Updater.isNewer("1.1", "1.1.0"))
        assertFalse(Updater.isNewer("1.0.9", "1.1"))
    }

    @Test
    fun findsTheApkAsset() {
        val release = JSONObject(
            """{"tag_name":"v1.2","assets":[
                {"name":"notes.txt","browser_download_url":"https://g/notes.txt"},
                {"name":"telelibre-1.2.apk","browser_download_url":"https://g/telelibre-1.2.apk"}]}"""
        )
        assertEquals("https://g/telelibre-1.2.apk", Updater.assetUrl(release))
        assertNull(Updater.assetUrl(JSONObject("""{"tag_name":"v1.2"}""")))
    }
}
