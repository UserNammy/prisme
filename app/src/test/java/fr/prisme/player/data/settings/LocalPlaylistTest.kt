package fr.prisme.player.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalPlaylistTest {

    @Test
    fun acceptsPlaylistWithByteOrderMark() {
        val bytes = "\uFEFF#EXTM3U\n#EXTINF:-1 group-title=\"Test\",Chaîne 1\nhttp://example.test/1.m3u8\n"
            .toByteArray(Charsets.UTF_8)

        val channels = LocalPlaylist.parse(LocalPlaylist.decode(bytes))

        assertEquals(1, channels.size)
        assertEquals("Chaîne 1", channels.single().name)
    }

    @Test
    fun rejectsFilesWithoutChannels() {
        assertThrows(IllegalArgumentException::class.java) { LocalPlaylist.parse("bonjour\nceci n'est pas une liste") }
        assertThrows(IllegalArgumentException::class.java) { LocalPlaylist.parse("#EXTM3U\n") }
    }

    @Test
    fun displayNameIsDecodedShortAndPrintable() {
        assertEquals("Mes chaînes.m3u", LocalPlaylist.displayName("Mes%20cha%C3%AEnes.m3u"))
        assertEquals("liste.m3u", LocalPlaylist.displayName("..%2F..%2Fdossier%2Fliste.m3u"))
        assertEquals("playlist.m3u", LocalPlaylist.displayName(null))
        assertEquals("playlist.m3u", LocalPlaylist.displayName("%0A%0D"))
        assertEquals(80, LocalPlaylist.displayName("a".repeat(200)).length)
    }
}
