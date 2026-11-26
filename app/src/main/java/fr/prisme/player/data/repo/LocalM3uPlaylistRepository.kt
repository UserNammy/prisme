package fr.prisme.player.data.repo

import fr.prisme.player.data.m3u.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Reads the playlist file imported from the phone (see LocalPlaylist). */
class LocalM3uPlaylistRepository(private val file: File) : PlaylistRepository {

    override suspend fun fetch(
        etag: String?,
        lastModified: String?,
        onProgress: (ImportProgress) -> Unit,
    ): PlaylistSnapshot = withContext(Dispatchers.IO) {
        if (!file.isFile) error("Fichier de chaînes introuvable : renvoyez-le depuis le téléphone.")
        // Size + date identify a version of the file, like an ETag for a remote playlist:
        // an unchanged file skips the rewrite of the whole catalogue.
        val version = "${file.length()}-${file.lastModified()}"
        if (etag == version) {
            return@withContext PlaylistSnapshot(channels = emptyList(), notModified = true)
        }
        onProgress(ImportProgress(ImportProgress.Stage.Downloading, 0))
        val channels = file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            M3uParser.parse(lines.mapIndexed { i, l -> if (i == 0) l.removePrefix("\uFEFF") else l })
        }
        onProgress(ImportProgress(ImportProgress.Stage.Live, channels.size))
        PlaylistSnapshot(channels = channels, etag = version)
    }
}
