package fr.prisme.player.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    val sourceConfig: Flow<SourceConfig?> = context.dataStore.data.map { prefs ->
        when (prefs[TYPE]) {
            TYPE_M3U -> prefs[M3U_URL]?.takeIf { it.isNotBlank() }?.let(SourceConfig::M3u)
            TYPE_M3U_FILE -> SourceConfig.LocalM3u(prefs[M3U_FILE_NAME]?.takeIf { it.isNotBlank() } ?: "playlist.m3u")
            TYPE_XTREAM -> {
                val host = prefs[XT_HOST]?.trim().orEmpty()
                val user = prefs[XT_USER].orEmpty()
                val pass = prefs[XT_PASS].orEmpty()
                if (host.isNotBlank() && user.isNotBlank()) {
                    SourceConfig.Xtream(host, user, pass)
                } else null
            }
            else -> null
        }
    }

    /**
     * Name of the last playlist file imported from the phone. Kept when another source is
     * chosen, since the file itself stays on the TV and can be selected again.
     */
    val localPlaylistName: Flow<String?> = context.dataStore.data.map { it[M3U_FILE_NAME] }

    val playlistEtag: Flow<String?> = context.dataStore.data.map { it[PLAYLIST_ETAG] }
    val playlistLastModified: Flow<String?> = context.dataStore.data.map { it[PLAYLIST_LAST_MODIFIED] }

    /**
     * Currently-active profile. Defaults to the built-in "default" profile seeded by
     * the database; anything the app reads before the user has picked a different profile
     * points at that one, so behaviour is identical to the pre-profile build.
     */
    val activeProfileId: Flow<String> =
        context.dataStore.data.map { it[ACTIVE_PROFILE_ID] ?: DEFAULT_PROFILE_ID }

    suspend fun setActiveProfile(id: String) {
        context.dataStore.edit { prefs -> prefs[ACTIVE_PROFILE_ID] = id }
    }

    /**
     * Per-profile last-watched channel id. Each profile gets its own key so switching
     * profiles instantly swaps the hero banner without a single read of the others'
     * history. Returns null for profiles that have never played anything.
     */
    fun lastWatchedChannelId(profileId: String): Flow<String?> =
        context.dataStore.data.map { it[lastWatchedKey(profileId)] }

    suspend fun setLastWatched(profileId: String, channelId: String) {
        context.dataStore.edit { prefs -> prefs[lastWatchedKey(profileId)] = channelId }
    }

    /**
     * Per-profile recent search queries, newest first, capped to RECENT_SEARCHES_MAX.
     * Stored as a single \n-separated string — DataStore Preferences has no list
     * primitive and Proto DataStore would be overkill for five strings per profile.
     */
    fun recentSearches(profileId: String): Flow<List<String>> =
        context.dataStore.data.map { prefs ->
            prefs[recentSearchesKey(profileId)]
                ?.split('\n')
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        }

    suspend fun pushRecentSearch(profileId: String, query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        val key = recentSearchesKey(profileId)
        context.dataStore.edit { prefs ->
            val existing = prefs[key]
                ?.split('\n')
                ?.filter { it.isNotBlank() && !it.equals(q, ignoreCase = true) }
                ?: emptyList()
            prefs[key] = (listOf(q) + existing).take(RECENT_SEARCHES_MAX).joinToString("\n")
        }
    }

    suspend fun clearRecentSearches(profileId: String) {
        context.dataStore.edit { prefs -> prefs.remove(recentSearchesKey(profileId)) }
    }

    /** Drop every per-profile key when a profile is deleted — no orphan prefs. */
    suspend fun wipeProfilePrefs(profileId: String) {
        context.dataStore.edit { prefs ->
            prefs.remove(lastWatchedKey(profileId))
            prefs.remove(recentSearchesKey(profileId))
        }
    }

    private fun lastWatchedKey(profileId: String) =
        stringPreferencesKey("last_watched_id::$profileId")

    private fun recentSearchesKey(profileId: String) =
        stringPreferencesKey("recent_searches::$profileId")

    /** Epoch millis of the last successful catalogue refresh. 0 = never. */
    val lastRefreshSuccessAt: Flow<Long> = context.dataStore.data.map { it[LAST_REFRESH_AT] ?: 0L }

    suspend fun markRefreshSuccess(nowMs: Long = System.currentTimeMillis()) {
        context.dataStore.edit { prefs -> prefs[LAST_REFRESH_AT] = nowMs }
    }

    val lastEpgRefreshAt: Flow<Long> = context.dataStore.data.map { it[LAST_EPG_REFRESH_AT] ?: 0L }

    suspend fun markEpgRefresh(nowMs: Long = System.currentTimeMillis()) {
        context.dataStore.edit { prefs -> prefs[LAST_EPG_REFRESH_AT] = nowMs }
    }

    /** Version of the catalogue format stored in Room; below the app's version forces a full reload. */
    val catalogueVersion: Flow<Int> = context.dataStore.data.map { it[CATALOGUE_VERSION] ?: 0 }

    suspend fun setCatalogueVersion(version: Int) {
        context.dataStore.edit { prefs -> prefs[CATALOGUE_VERSION] = version }
    }

    /**
     * Epoch millis of the last time the user picked a profile (cold start picker or the
     * "Wisselen van profiel" shortcut). Used to gate the cold-start profile picker —
     * within [PROFILE_SESSION_WINDOW_MS] of the last pick the picker is skipped and the
     * app boots straight into Channels.
     */
    val lastProfileSessionAt: Flow<Long> =
        context.dataStore.data.map { it[LAST_PROFILE_SESSION_AT] ?: 0L }

    suspend fun setLastProfileSessionAt(nowMs: Long = System.currentTimeMillis()) {
        context.dataStore.edit { prefs -> prefs[LAST_PROFILE_SESSION_AT] = nowMs }
    }

    /**
     * Automatic nightly refresh. Disabled by default so we don't silently burn the user's
     * bandwidth if they never open Instellingen. Hour is 0..23 in local time; minutes are
     * always :00 — a single knob is enough and avoids a fiddly minute-picker on the remote.
     */
    val autoRefreshEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[AUTO_REFRESH_ENABLED] ?: false }

    val autoRefreshHour: Flow<Int> =
        context.dataStore.data.map { (it[AUTO_REFRESH_HOUR] ?: DEFAULT_AUTO_REFRESH_HOUR).coerceIn(0, 23) }

    /** Hero trailers (YouTube WebView) are heavy on TV boxes: opt-in only. */
    val trailersAutoplay: Flow<Boolean> =
        context.dataStore.data.map { it[TRAILERS_AUTOPLAY] ?: false }

    suspend fun setTrailersAutoplay(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[TRAILERS_AUTOPLAY] = enabled }
    }

    /** Open the last watched live channel straight away when the app starts. */
    val resumeLastChannelOnStart: Flow<Boolean> =
        context.dataStore.data.map { it[RESUME_LAST_CHANNEL] ?: false }

    suspend fun setResumeLastChannelOnStart(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[RESUME_LAST_CHANNEL] = enabled }
    }

    /** Four-digit parental PIN; empty when none is set. */
    val parentalPin: Flow<String> = context.dataStore.data.map { it[PARENTAL_PIN].orEmpty() }

    suspend fun setParentalPin(pin: String) {
        context.dataStore.edit { prefs -> if (pin.isBlank()) prefs.remove(PARENTAL_PIN) else prefs[PARENTAL_PIN] = pin }
    }

    /** Tunneled playback: the video hardware keeps audio and video in sync. */
    val hardwareAvSync: Flow<Boolean> =
        context.dataStore.data.map { it[HARDWARE_AV_SYNC] ?: true }

    suspend fun setHardwareAvSync(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[HARDWARE_AV_SYNC] = enabled }
    }

    /** Switch the display refresh rate to the stream's frame rate while playing. */
    val frameRateMatching: Flow<Boolean> =
        context.dataStore.data.map { it[FRAME_RATE_MATCHING] ?: true }

    suspend fun setFrameRateMatching(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[FRAME_RATE_MATCHING] = enabled }
    }

    /** Player defaults. Aspect: "FIT" | "FILL" | "ZOOM". Languages: ISO 639-1, "" = no preference. */
    val playerAspect: Flow<String> = context.dataStore.data.map { it[PLAYER_ASPECT] ?: "FIT" }
    val preferredAudioLanguage: Flow<String> = context.dataStore.data.map { it[AUDIO_LANGUAGE] ?: DEFAULT_AUDIO_LANGUAGE }
    /** "" = no preference, "off" = subtitles off by default, else an ISO 639-1 code. */
    val preferredSubtitleLanguage: Flow<String> = context.dataStore.data.map { it[SUBTITLE_LANGUAGE] ?: "" }

    suspend fun setPlayerAspect(value: String) {
        context.dataStore.edit { prefs -> prefs[PLAYER_ASPECT] = value }
    }

    suspend fun setPreferredAudioLanguage(value: String) {
        context.dataStore.edit { prefs -> prefs[AUDIO_LANGUAGE] = value }
    }

    suspend fun setPreferredSubtitleLanguage(value: String) {
        context.dataStore.edit { prefs -> prefs[SUBTITLE_LANGUAGE] = value }
    }

    suspend fun setAutoRefreshEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[AUTO_REFRESH_ENABLED] = enabled }
    }

    suspend fun setAutoRefreshHour(hour: Int) {
        context.dataStore.edit { prefs -> prefs[AUTO_REFRESH_HOUR] = hour.coerceIn(0, 23) }
    }

    /**
     * Comma-separated country/language codes matched against Xtream category prefixes
     * ("┃NL┃ …", "|UK| …"). Blank = keep the whole catalogue.
     */
    val categoryFilter: Flow<String> =
        context.dataStore.data.map { it[CATEGORY_FILTER] ?: DEFAULT_CATEGORY_FILTER }

    suspend fun setCategoryFilter(raw: String) {
        context.dataStore.edit { prefs ->
            prefs[CATEGORY_FILTER] = raw.trim()
            prefs.remove(PLAYLIST_ETAG); prefs.remove(PLAYLIST_LAST_MODIFIED)
        }
    }

    suspend fun saveM3u(url: String) {
        context.dataStore.edit { prefs ->
            prefs[TYPE] = TYPE_M3U
            prefs[M3U_URL] = url.trim()
            prefs.remove(XT_HOST); prefs.remove(XT_USER); prefs.remove(XT_PASS)
            prefs.remove(PLAYLIST_ETAG); prefs.remove(PLAYLIST_LAST_MODIFIED)
        }
    }

    /** Makes the playlist file imported from the phone the active source. */
    suspend fun saveLocalM3u(fileName: String) {
        context.dataStore.edit { prefs ->
            prefs[TYPE] = TYPE_M3U_FILE
            prefs[M3U_FILE_NAME] = fileName
            prefs.remove(M3U_URL)
            prefs.remove(XT_HOST); prefs.remove(XT_USER); prefs.remove(XT_PASS)
            prefs.remove(PLAYLIST_ETAG); prefs.remove(PLAYLIST_LAST_MODIFIED)
        }
    }

    suspend fun saveXtream(host: String, username: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[TYPE] = TYPE_XTREAM
            prefs[XT_HOST] = host.trim().trimEnd('/')
            prefs[XT_USER] = username.trim()
            prefs[XT_PASS] = password
            prefs.remove(M3U_URL)
            prefs.remove(PLAYLIST_ETAG); prefs.remove(PLAYLIST_LAST_MODIFIED)
        }
    }

    /**
     * Every user setting as typed JSON (`{"key": {"t": "s", "v": "..."}}`), for the backup
     * file. Sync bookkeeping (ETags, last refresh times, catalogue version) is left out so
     * a restore triggers a fresh download of the catalogue.
     */
    suspend fun exportForBackup(): JsonObject {
        val prefs = context.dataStore.data.first().asMap()
        return buildJsonObject {
            for ((key, value) in prefs) {
                if (key.name in BACKUP_EXCLUDED) continue
                val typed: Pair<String, JsonElement> = when (value) {
                    is Boolean -> "b" to JsonPrimitive(value)
                    is Int -> "i" to JsonPrimitive(value)
                    is Long -> "l" to JsonPrimitive(value)
                    is Float -> "f" to JsonPrimitive(value)
                    is Double -> "d" to JsonPrimitive(value)
                    is String -> "s" to JsonPrimitive(value)
                    is Set<*> -> "ss" to JsonArray(value.map { JsonPrimitive(it.toString()) })
                    else -> continue
                }
                put(key.name, buildJsonObject {
                    put("t", JsonPrimitive(typed.first))
                    put("v", typed.second)
                })
            }
        }
    }

    suspend fun restoreFromBackup(saved: JsonObject) {
        context.dataStore.edit { prefs ->
            for ((name, entry) in saved) {
                if (name in BACKUP_EXCLUDED) continue
                val obj = entry as? JsonObject ?: continue
                val v = obj["v"] ?: continue
                when (obj["t"]?.jsonPrimitive?.content) {
                    "b" -> v.jsonPrimitive.booleanOrNull?.let { prefs[booleanPreferencesKey(name)] = it }
                    "i" -> v.jsonPrimitive.intOrNull?.let { prefs[intPreferencesKey(name)] = it }
                    "l" -> v.jsonPrimitive.longOrNull?.let { prefs[longPreferencesKey(name)] = it }
                    "f" -> v.jsonPrimitive.floatOrNull?.let { prefs[floatPreferencesKey(name)] = it }
                    "d" -> v.jsonPrimitive.doubleOrNull?.let { prefs[doublePreferencesKey(name)] = it }
                    "s" -> prefs[stringPreferencesKey(name)] = v.jsonPrimitive.content
                    "ss" -> (v as? JsonArray)?.let { arr ->
                        prefs[stringSetPreferencesKey(name)] = arr.map { it.jsonPrimitive.content }.toSet()
                    }
                }
            }
            for (key in listOf(PLAYLIST_ETAG, PLAYLIST_LAST_MODIFIED)) prefs.remove(key)
            prefs.remove(LAST_REFRESH_AT)
            prefs.remove(LAST_EPG_REFRESH_AT)
        }
    }

    suspend fun savePlaylistValidators(etag: String?, lastModified: String?) {
        context.dataStore.edit { prefs ->
            if (etag != null) prefs[PLAYLIST_ETAG] = etag else prefs.remove(PLAYLIST_ETAG)
            if (lastModified != null) prefs[PLAYLIST_LAST_MODIFIED] = lastModified
            else prefs.remove(PLAYLIST_LAST_MODIFIED)
        }
    }

    private companion object {
        val TYPE = stringPreferencesKey("source_type")
        val M3U_URL = stringPreferencesKey("m3u_url")
        val M3U_FILE_NAME = stringPreferencesKey("m3u_file_name")
        val XT_HOST = stringPreferencesKey("xt_host")
        val XT_USER = stringPreferencesKey("xt_user")
        val XT_PASS = stringPreferencesKey("xt_pass")
        val PLAYLIST_ETAG = stringPreferencesKey("playlist_etag")
        val PLAYLIST_LAST_MODIFIED = stringPreferencesKey("playlist_last_modified")
        val LAST_REFRESH_AT = longPreferencesKey("last_refresh_at")
        val LAST_EPG_REFRESH_AT = longPreferencesKey("last_epg_refresh_at")
        val CATALOGUE_VERSION = androidx.datastore.preferences.core.intPreferencesKey("catalogue_version")
        val LAST_PROFILE_SESSION_AT = longPreferencesKey("last_profile_session_at")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val AUTO_REFRESH_ENABLED = booleanPreferencesKey("auto_refresh_enabled")
        val TRAILERS_AUTOPLAY = booleanPreferencesKey("trailers_autoplay")
        val RESUME_LAST_CHANNEL = booleanPreferencesKey("resume_last_channel")
        val HARDWARE_AV_SYNC = booleanPreferencesKey("hardware_av_sync")
        val PARENTAL_PIN = stringPreferencesKey("parental_pin")
        val FRAME_RATE_MATCHING = booleanPreferencesKey("frame_rate_matching")
        val PLAYER_ASPECT = stringPreferencesKey("player_aspect")
        val AUDIO_LANGUAGE = stringPreferencesKey("audio_language")
        val SUBTITLE_LANGUAGE = stringPreferencesKey("subtitle_language")
        val AUTO_REFRESH_HOUR = intPreferencesKey("auto_refresh_hour")
        val CATEGORY_FILTER = stringPreferencesKey("category_filter")
        const val TYPE_M3U = "m3u"
        const val TYPE_M3U_FILE = "m3u_file"
        const val TYPE_XTREAM = "xtream"
        const val RECENT_SEARCHES_MAX = 6
        const val DEFAULT_PROFILE_ID = "default"
        const val DEFAULT_AUTO_REFRESH_HOUR = 3
        const val DEFAULT_CATEGORY_FILTER = "FR, FRA"
        const val DEFAULT_AUDIO_LANGUAGE = "fr"
        val BACKUP_EXCLUDED = setOf(
            "playlist_etag", "playlist_last_modified", "last_refresh_at",
            "last_epg_refresh_at", "catalogue_version", "last_profile_session_at",
        )
    }
}
