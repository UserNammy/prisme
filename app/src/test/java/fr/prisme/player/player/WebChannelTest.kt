package fr.prisme.player.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebChannelTest {

    @Test
    fun youtubeVideoLinksUseTheEmbeddedPlayer() {
        val id = "dQw4w9WgXcQ"
        listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://youtube.com/watch?feature=share&v=$id",
            "https://m.youtube.com/watch?v=$id",
            "https://youtu.be/$id",
            "https://www.youtube.com/live/$id?si=abc",
            "https://www.youtube.com/embed/$id",
            "https://www.youtube.com/shorts/$id",
        ).forEach { assertEquals(it, WebChannel.YouTube(id), WebChannel.classify(it)) }
    }

    @Test
    fun youtubeLinksWithoutVideoOpenTheSite() {
        val url = "https://www.youtube.com/@FreeDocumentary/live"
        assertEquals(WebChannel.Page(url), WebChannel.classify(url))
    }

    @Test
    fun obviousPagesAreDetected() {
        listOf("https://iqraa.com/#live", "https://example.test/live/index.html")
            .forEach { assertEquals(it, WebChannel.Page(it), WebChannel.classify(it)) }
    }

    @Test
    fun possibleStreamsGoToTheVideoPlayer() {
        listOf(
            "http://provider.test:8080/live/user/pass/123.ts",
            "https://cdn.test/hls/master.m3u8",
            "http://provider.test/play/123",
            "https://watch.example.test/live/1025",
            "rtmp://cdn.test/live",
        ).forEach { assertNull(it, WebChannel.classify(it)) }
    }

    @Test
    fun onlyWebLinksCanFallBackToAPage() {
        assertEquals(WebChannel.Page("https://a.test/x"), WebChannel.pageFallback("https://a.test/x"))
        assertNull(WebChannel.pageFallback("rtmp://cdn.test/live"))
    }
}
