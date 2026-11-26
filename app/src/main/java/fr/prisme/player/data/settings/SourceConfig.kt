package fr.prisme.player.data.settings

sealed interface SourceConfig {
    data class M3u(val url: String) : SourceConfig
    data class Xtream(val host: String, val username: String, val password: String) : SourceConfig

    /**
     * A playlist file sent from the phone and kept only on this device (see [LocalPlaylist]).
     * [fileName] is the original name, for display only.
     */
    data class LocalM3u(val fileName: String) : SourceConfig
}
