package fr.prisme.player.player

import java.net.URI

/**
 * A playlist entry that is not a media stream: a YouTube video or live, or a web page with
 * its own player. Those open in a WebView instead of ExoPlayer, which only plays direct
 * streams (HLS, DASH, TS…).
 */
sealed interface WebChannel {
    /** Played with the official YouTube IFrame player (never by extracting the stream). */
    data class YouTube(val videoId: String) : WebChannel

    /** Shown as a full-screen page; navigation with the remote's D-pad. */
    data class Page(val url: String) : WebChannel

    companion object {
        private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
        private val YOUTUBE_PATH_PREFIXES = listOf("live", "embed", "shorts", "v")
        private val PAGE_EXTENSIONS = listOf(".html", ".htm")

        /**
         * What [url] obviously is, or null when it may be a stream: those go to ExoPlayer
         * first, and a stream that turns out to be a page is caught by [pageFallback].
         */
        fun classify(url: String): WebChannel? {
            val uri = parse(url) ?: return null
            val host = uri.host.lowercase().removePrefix("www.").removePrefix("m.")
            val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }

            if (host == "youtu.be") {
                return segments.firstOrNull()?.takeIf { VIDEO_ID.matches(it) }?.let(::YouTube) ?: Page(url)
            }
            if (host == "youtube.com" || host.endsWith(".youtube.com")) {
                val id = when {
                    segments.firstOrNull() == "watch" -> queryParam(uri.rawQuery, "v")
                    segments.size >= 2 && segments[0] in YOUTUBE_PATH_PREFIXES -> segments[1]
                    else -> null
                }
                // Channel pages and handles (@name/live) have no video id: show the site.
                return id?.takeIf { VIDEO_ID.matches(it) }?.let(::YouTube) ?: Page(url)
            }
            // "site.com/#live" or "page.html" are pages; stream URLs carry neither.
            if (uri.rawFragment != null) return Page(url)
            val last = segments.lastOrNull()?.lowercase().orEmpty()
            if (PAGE_EXTENSIONS.any { last.endsWith(it) }) return Page(url)
            return null
        }

        /** A web page to show when ExoPlayer could not read [url] as media, if it is http(s). */
        fun pageFallback(url: String): Page? = parse(url)?.let { Page(url) }

        private fun parse(url: String): URI? {
            val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return null
            if (uri.host.isNullOrBlank()) return null
            return uri
        }

        private fun queryParam(rawQuery: String?, name: String): String? =
            rawQuery?.split('&')
                ?.firstOrNull { it.substringBefore('=') == name }
                ?.substringAfter('=', "")
                ?.takeIf { it.isNotEmpty() }
    }
}
