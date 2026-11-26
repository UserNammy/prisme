package fr.prisme.player.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNotNull
import fr.prisme.player.data.settings.LocalPlaylist
import fr.prisme.player.data.settings.SourceTester
import fr.prisme.player.data.settings.SourceTestResult
import fr.prisme.player.data.settings.PhoneSetupServer
import fr.prisme.player.PrismApp
import fr.prisme.player.R
import fr.prisme.player.data.settings.SourceConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class SourceType { M3u, Xtream, LocalFile }

data class SettingsUiState(
    val type: SourceType = SourceType.M3u,
    val m3uUrl: String = "",
    val host: String = "",
    val username: String = "",
    val password: String = "",
    val categoryFilter: String = "",
    val saving: Boolean = false,
    val savedOnce: Boolean = false,
    val validationError: String? = null,
    val refreshing: Boolean = false,
    val refreshError: String? = null,
    val autoRefreshEnabled: Boolean = false,
    val autoRefreshHour: Int = 3,
    val trailersAutoplay: Boolean = false,
    val resumeLastChannel: Boolean = false,
    val hardwareAvSync: Boolean = true,
    val frameRateMatching: Boolean = true,
    val parentalPinSet: Boolean = false,
    val playerAspect: String = "FIT",
    val audioLanguage: String = "",
    val subtitleLanguage: String = "",
    val testing: Boolean = false,
    val testResult: SourceTestResult? = null,
    /** Set when a phone just sent the form, so the screen can say so. */
    val filledFromPhone: Boolean = false,
    /** Name of the playlist file imported from the phone, when one is stored on the TV. */
    val localFileName: String? = null,
    /** Channel count of a file received during this visit to the settings screen. */
    val localFileJustImported: Int? = null,
)

/** Language choices offered for audio and subtitles, as ISO 639-1 codes ("" = no preference). */
val LanguageChoices: List<String> = listOf("", "fr", "en", "de", "es", "it", "nl", "ar", "tr", "pl")

/** Aspect-ratio choices, matching the player's AspectMode names. */
val AspectChoices: List<String> = listOf("FIT", "FILL", "ZOOM")

class SettingsViewModel : ViewModel() {

    private val app = PrismApp.get()
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private var initialSource: SourceConfig? = null
    private var initialCategoryFilter: String = ""

    init {
        viewModelScope.launch {
            val current = app.settings.sourceConfig.first()
            initialSource = current
            val storedFile = LocalPlaylist.exists()
            if (storedFile) {
                val name = app.settings.localPlaylistName.first() ?: "playlist.m3u"
                _state.update { it.copy(localFileName = name) }
            }
            when (current) {
                is SourceConfig.M3u -> _state.update { it.copy(type = SourceType.M3u, m3uUrl = current.url) }
                is SourceConfig.LocalM3u -> _state.update { it.copy(type = SourceType.LocalFile) }
                is SourceConfig.Xtream -> _state.update {
                    it.copy(
                        type = SourceType.Xtream,
                        host = current.host,
                        username = current.username,
                        password = current.password,
                    )
                }
                null -> Unit
            }
            initialCategoryFilter = app.settings.categoryFilter.first()
            _state.update {
                it.copy(
                    categoryFilter = initialCategoryFilter,
                    autoRefreshEnabled = app.settings.autoRefreshEnabled.first(),
                    autoRefreshHour = app.settings.autoRefreshHour.first(),
                    trailersAutoplay = app.settings.trailersAutoplay.first(),
                    resumeLastChannel = app.settings.resumeLastChannelOnStart.first(),
                    hardwareAvSync = app.settings.hardwareAvSync.first(),
                    frameRateMatching = app.settings.frameRateMatching.first(),
                    parentalPinSet = app.settings.parentalPin.first().isNotEmpty(),
                    playerAspect = app.settings.playerAspect.first(),
                    audioLanguage = app.settings.preferredAudioLanguage.first(),
                    subtitleLanguage = app.settings.preferredSubtitleLanguage.first(),
                )
            }
            // Only after the stored values are in: a phone submission must win over them.
            PhoneSetupServer.submission
                .filterNotNull()
                .onEach { applyPhoneSubmission(it) }
                .launchIn(viewModelScope)
            // The server already saved the file as the source and started the refresh.
            PhoneSetupServer.playlistImported
                .filterNotNull()
                .onEach { imported ->
                    initialSource = SourceConfig.LocalM3u(imported.fileName)
                    _state.update {
                        it.copy(
                            type = SourceType.LocalFile,
                            localFileName = imported.fileName,
                            localFileJustImported = imported.channelCount,
                            validationError = null,
                            testResult = null,
                            filledFromPhone = false,
                        )
                    }
                }
                .launchIn(viewModelScope)
        }
        app.refreshUseCase.refreshing
            .onEach { r -> _state.update { it.copy(refreshing = r) } }
            .launchIn(viewModelScope)
        app.refreshUseCase.lastError
            .onEach { e -> _state.update { it.copy(refreshError = e) } }
            .launchIn(viewModelScope)
    }

    private fun applyPhoneSubmission(sub: PhoneSetupServer.Submission) {
        PhoneSetupServer.consume()
        _state.update {
            if (sub.type.equals("m3u", ignoreCase = true)) {
                it.copy(type = SourceType.M3u, m3uUrl = sub.m3uUrl, testResult = null, validationError = null, filledFromPhone = true)
            } else {
                it.copy(
                    type = SourceType.Xtream,
                    host = sub.host,
                    username = sub.username,
                    password = sub.password,
                    testResult = null,
                    validationError = null,
                    filledFromPhone = true,
                )
            }
        }
    }

    fun setType(type: SourceType) { _state.update { it.copy(type = type, validationError = null, testResult = null) } }
    fun setM3uUrl(v: String) { _state.update { it.copy(m3uUrl = v, validationError = null) } }
    fun setHost(v: String) { _state.update { it.copy(host = v, validationError = null) } }
    fun setUsername(v: String) { _state.update { it.copy(username = v, validationError = null) } }
    fun setPassword(v: String) { _state.update { it.copy(password = v, validationError = null) } }
    fun setCategoryFilter(v: String) { _state.update { it.copy(categoryFilter = v) } }

    /** Validated source from the form fields, or null after setting a validation error. */
    private fun buildSource(): SourceConfig? {
        val s = _state.value
        return when (s.type) {
            SourceType.M3u -> {
                val url = normaliseUrl(s.m3uUrl) ?: run { fail(R.string.settings_invalid_m3u); return null }
                SourceConfig.M3u(url)
            }
            SourceType.Xtream -> {
                val host = normaliseUrl(s.host) ?: run { fail(R.string.settings_invalid_host); return null }
                if (s.username.isBlank()) { fail(R.string.settings_invalid_user); return null }
                SourceConfig.Xtream(host.trimEnd('/'), s.username.trim(), s.password)
            }
            SourceType.LocalFile -> {
                val name = s.localFileName
                if (name == null || !LocalPlaylist.exists()) { fail(R.string.settings_local_file_missing); return null }
                SourceConfig.LocalM3u(name)
            }
        }
    }

    fun testConnection() {
        if (_state.value.testing) return
        val source = buildSource() ?: return
        _state.update { it.copy(testing = true, testResult = null, validationError = null) }
        viewModelScope.launch {
            val result = SourceTester.test(source)
            _state.update { it.copy(testing = false, testResult = result) }
        }
    }

    fun setTrailersAutoplay(enabled: Boolean) {
        _state.update { it.copy(trailersAutoplay = enabled) }
        viewModelScope.launch { app.settings.setTrailersAutoplay(enabled) }
    }

    fun setResumeLastChannel(enabled: Boolean) {
        _state.update { it.copy(resumeLastChannel = enabled) }
        viewModelScope.launch { app.settings.setResumeLastChannelOnStart(enabled) }
    }

    fun setParentalPin(pin: String) {
        _state.update { it.copy(parentalPinSet = pin.isNotEmpty()) }
        viewModelScope.launch { app.settings.setParentalPin(pin) }
    }

    fun setFrameRateMatching(enabled: Boolean) {
        _state.update { it.copy(frameRateMatching = enabled) }
        viewModelScope.launch { app.settings.setFrameRateMatching(enabled) }
    }

    fun setHardwareAvSync(enabled: Boolean) {
        _state.update { it.copy(hardwareAvSync = enabled) }
        viewModelScope.launch { app.settings.setHardwareAvSync(enabled) }
    }

    fun cycleAspect() {
        val next = AspectChoices[(AspectChoices.indexOf(_state.value.playerAspect) + 1).mod(AspectChoices.size)]
        _state.update { it.copy(playerAspect = next) }
        viewModelScope.launch { app.settings.setPlayerAspect(next) }
    }

    fun cycleAudioLanguage() {
        val next = LanguageChoices[(LanguageChoices.indexOf(_state.value.audioLanguage) + 1).mod(LanguageChoices.size)]
        _state.update { it.copy(audioLanguage = next) }
        viewModelScope.launch { app.settings.setPreferredAudioLanguage(next) }
    }

    /** Cycles no preference → off → each language. */
    fun cycleSubtitleLanguage() {
        val options = listOf("", "off") + LanguageChoices.drop(1)
        val next = options[(options.indexOf(_state.value.subtitleLanguage) + 1).mod(options.size)]
        _state.update { it.copy(subtitleLanguage = next) }
        viewModelScope.launch { app.settings.setPreferredSubtitleLanguage(next) }
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        val newSource: SourceConfig = buildSource() ?: return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, validationError = null) }
            when (newSource) {
                is SourceConfig.M3u -> app.settings.saveM3u(newSource.url)
                is SourceConfig.LocalM3u -> app.settings.saveLocalM3u(newSource.fileName)
                is SourceConfig.Xtream ->
                    app.settings.saveXtream(newSource.host, newSource.username, newSource.password)
            }
            val filterChanged = s.categoryFilter.trim() != initialCategoryFilter.trim()
            if (filterChanged) app.settings.setCategoryFilter(s.categoryFilter)
            // A new source (or filter) makes the cached catalogue wrong; refresh right away
            // instead of waiting for the 6h staleness check on the home screen.
            if (newSource != initialSource || filterChanged) {
                initialSource = newSource
                initialCategoryFilter = s.categoryFilter.trim()
                app.appScope.launch { app.refreshUseCase() }
            }
            _state.update { it.copy(saving = false, savedOnce = true) }
            onDone()
        }
    }

    private fun fail(messageRes: Int) {
        _state.update { it.copy(validationError = app.getString(messageRes)) }
    }

    fun setAutoRefreshEnabled(enabled: Boolean) {
        _state.update { it.copy(autoRefreshEnabled = enabled) }
        viewModelScope.launch { app.settings.setAutoRefreshEnabled(enabled) }
    }

    /** Cycles 0..23 for both buttons so remote-only users can reach any hour in ≤12 presses. */
    fun bumpAutoRefreshHour(delta: Int) {
        val next = ((_state.value.autoRefreshHour + delta) % 24 + 24) % 24
        _state.update { it.copy(autoRefreshHour = next) }
        viewModelScope.launch { app.settings.setAutoRefreshHour(next) }
    }

    fun refreshNow() {
        if (_state.value.refreshing) return
        app.appScope.launch { app.refreshUseCase() }
    }

    private companion object {
        /** Adds `http://` when the scheme is missing; null when the result isn't a valid URL. */
        fun normaliseUrl(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
            return withScheme.takeIf { it.toHttpUrlOrNull() != null }
        }
    }
}
