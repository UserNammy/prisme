package fr.prisme.player.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import android.util.Log
import fr.prisme.player.BuildConfig
import fr.prisme.player.data.remote.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.CacheControl
import okhttp3.Request

/** A newer build published on GitHub Releases. */
data class AvailableUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    data class Downloading(val update: AvailableUpdate, val progress: Float) : UpdateState

    /** Handed to Android's installer; the system dialog takes over from here. */
    data object Installing : UpdateState

    /** Android needs the user to allow Prism to install apps; settings were opened. */
    data object NeedsInstallPermission : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Self-update from the project's GitHub Releases. CI publishes `prism-<versionCode>.apk`
 * for every `v*` tag; we compare that number with our own BuildConfig.VERSION_CODE and,
 * when newer, stream the APK straight into a PackageInstaller session (no temp file, no
 * FileProvider). Android shows its own confirmation, then replaces the app in place.
 *
 * Updates only install when the APK is signed with the same key as the installed app —
 * see docs/RELEASE.md.
 */
class AppUpdater(private val context: Context) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val prefs = context.getSharedPreferences("app_updater", Context.MODE_PRIVATE)

    /** Version the user answered "later" to; the startup prompt stays quiet for it. */
    val dismissedVersion: Int get() = prefs.getInt(KEY_DISMISSED, 0)

    fun dismiss(update: AvailableUpdate) {
        prefs.edit().putInt(KEY_DISMISSED, update.versionCode).apply()
        if (_state.value is UpdateState.Available) _state.value = UpdateState.Idle
    }

    fun clearMessage() {
        val s = _state.value
        if (s is UpdateState.Failed || s is UpdateState.UpToDate || s is UpdateState.NeedsInstallPermission) {
            _state.value = UpdateState.Idle
        }
    }

    /** Startup check, at most every [CHECK_INTERVAL_MS]; skipped on debug builds. */
    suspend fun checkIfDue() {
        if (BuildConfig.DEBUG || BuildConfig.UPDATE_REPO.isBlank()) return
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return
        check(userInitiated = false)
    }

    suspend fun check(userInitiated: Boolean = true) {
        if (BuildConfig.UPDATE_REPO.isBlank()) return
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Installing) return
        _state.value = UpdateState.Checking
        val result = runCatching { fetchLatest() }
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        _state.value = result.fold(
            onSuccess = { latest ->
                when {
                    latest == null || latest.versionCode <= BuildConfig.VERSION_CODE ->
                        if (userInitiated) UpdateState.UpToDate else UpdateState.Idle
                    else -> UpdateState.Available(latest)
                }
            },
            onFailure = {
                Log.w(TAG, "update check failed", it)
                if (userInitiated) UpdateState.Failed("Impossible de vérifier les mises à jour.") else UpdateState.Idle
            },
        )
    }

    suspend fun install(update: AvailableUpdate) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val opened = runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.isSuccess
            // Some TV boxes have no such settings screen: try anyway, the installer prompts.
            if (opened) {
                _state.value = UpdateState.NeedsInstallPermission
                return
            }
        }
        _state.value = UpdateState.Downloading(update, 0f)
        val result = runCatching { downloadIntoSession(update) }
        result.onFailure {
            Log.w(TAG, "update download failed", it)
            _state.value = UpdateState.Failed("Le téléchargement de la mise à jour a échoué.")
        }
    }

    /** Called by [UpdateInstallReceiver] when Android reports the outcome. */
    internal fun onInstallResult(status: Int, message: String?) {
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                UpdateState.Failed(
                    "Cette mise à jour n'est pas signée avec la même clé que l'appli installée. " +
                        "Désinstallez Prism puis installez la nouvelle version.",
                )
            PackageInstaller.STATUS_FAILURE_STORAGE -> UpdateState.Failed("Espace de stockage insuffisant.")
            else -> UpdateState.Failed("Installation impossible${message?.let { " : $it" }.orEmpty()}.")
        }
    }

    private suspend fun fetchLatest(): AvailableUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        HttpClient.okHttp.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null // no release published yet
            check(response.isSuccessful) { "HTTP ${response.code}" }
            parseRelease(response.body?.string().orEmpty())
        }
    }

    private suspend fun downloadIntoSession(update: AvailableUpdate) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (update.sizeBytes > 0) setSize(update.sizeBytes)
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            val request = Request.Builder().url(update.apkUrl).build()
            HttpClient.streaming.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                val body = checkNotNull(response.body)
                val total = body.contentLength().takeIf { it > 0 } ?: update.sizeBytes
                session.openWrite("prism.apk", 0, if (total > 0) total else -1L).use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        var lastReported = -1
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            written += n
                            if (total > 0) {
                                val percent = (written * 100 / total).toInt()
                                if (percent != lastReported) {
                                    lastReported = percent
                                    _state.value = UpdateState.Downloading(update, percent / 100f)
                                }
                            }
                        }
                    }
                    session.fsync(out)
                }
            }
            val intent = Intent(context, UpdateInstallReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            _state.value = UpdateState.Installing
            session.commit(pending.intentSender)
        } catch (t: Throwable) {
            runCatching { session.abandon() }
            throw t
        } finally {
            session.close()
        }
    }

    companion object {
        private const val TAG = "AppUpdater"
        private const val KEY_LAST_CHECK = "last_check_at"
        private const val KEY_DISMISSED = "dismissed_version"
        private const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000

        private val ApkName = Regex("""^prism-(\d+)\.apk$""")
        private val json = Json { ignoreUnknownKeys = true }

        /** Extracts the `prism-<versionCode>.apk` asset from a GitHub release JSON. */
        internal fun parseRelease(body: String): AvailableUpdate? {
            val release = json.parseToJsonElement(body).jsonObject
            val tag = release["tag_name"]?.jsonPrimitive?.content.orEmpty()
            val assets = release["assets"] as? JsonArray ?: return null
            return assets.asSequence()
                .map { it.jsonObject }
                .mapNotNull { asset -> asset.toUpdate(tag) }
                .maxByOrNull { it.versionCode }
        }

        private fun JsonObject.toUpdate(tag: String): AvailableUpdate? {
            val name = this["name"]?.jsonPrimitive?.content ?: return null
            val code = ApkName.find(name)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            val url = this["browser_download_url"]?.jsonPrimitive?.content ?: return null
            val size = this["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            return AvailableUpdate(code, tag.removePrefix("v").ifBlank { code.toString() }, url, size)
        }
    }
}
