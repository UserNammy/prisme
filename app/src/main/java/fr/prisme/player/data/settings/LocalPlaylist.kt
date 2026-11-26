package fr.prisme.player.data.settings

import fr.prisme.player.PrismApp
import fr.prisme.player.data.Channel
import fr.prisme.player.data.m3u.M3uParser
import java.io.File
import java.net.URLDecoder

/**
 * The user's own M3U file, sent from a phone through the local setup page. It is stored in
 * the app's private storage only: never uploaded anywhere, never part of the repository.
 */
object LocalPlaylist {

    /** Largest accepted file; personal lists are far smaller, provider dumps can be big. */
    const val MAX_BYTES = 20 * 1024 * 1024

    private const val FILE_NAME = "local_playlist.m3u"

    fun file(): File = File(PrismApp.get().filesDir, FILE_NAME)

    fun exists(): Boolean = file().isFile

    /**
     * Validates [bytes] as a playlist, then replaces the stored file atomically, so a
     * refresh reading it at the same time never sees half a file.
     *
     * @return the channels found.
     * @throws IllegalArgumentException when the file holds no playable entry.
     */
    fun save(bytes: ByteArray): List<Channel> {
        val text = decode(bytes)
        val channels = parse(text)
        writeAtomically(text)
        return channels
    }

    /** Current content, or null when nothing was imported. */
    fun readText(): String? = file().takeIf { it.isFile }?.readText(Charsets.UTF_8)

    /** Restores a playlist saved in a backup; same checks as an upload. */
    fun restore(text: String) {
        parse(text)
        writeAtomically(text)
    }

    /** UTF-8 text without the byte-order mark some Windows editors add. */
    internal fun decode(bytes: ByteArray): String = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")

    /** @throws IllegalArgumentException when [text] contains no `#EXTINF` entry with a URL. */
    internal fun parse(text: String): List<Channel> {
        val channels = M3uParser.parse(text)
        require(channels.isNotEmpty()) { "Ce fichier ne contient aucune chaîne au format M3U." }
        return channels
    }

    /** A short, printable name from the browser's (URL-encoded) file name. */
    internal fun displayName(raw: String?): String {
        val decoded = raw?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }.orEmpty()
        val base = decoded.substringAfterLast('/').substringAfterLast('\\')
        return base.filterNot { it.isISOControl() }.trim().take(80).ifBlank { "playlist.m3u" }
    }

    private fun writeAtomically(text: String) {
        val target = file()
        val tmp = File(target.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "Impossible d'enregistrer le fichier sur la TV." }
        }
    }
}
