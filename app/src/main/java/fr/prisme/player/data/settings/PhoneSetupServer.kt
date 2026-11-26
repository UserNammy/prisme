package fr.prisme.player.data.settings

import android.util.Log
import fr.prisme.player.PrismApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tiny LAN web server so the source can be typed on a phone instead of with the remote.
 * The TV shows a QR code to `http://<tv-ip>:<port>/<token>`; the phone gets a form, and
 * a submit lands in [submission] for the settings screen to pick up. The random token
 * keeps other devices on the network from stumbling onto the form. Runs only while a
 * screen that shows the QR is open (reference counted via [start]/[stop]).
 *
 * The same page also downloads a backup (`/<token>/backup`), restores one
 * (`POST /<token>/restore`) and receives an M3U file (`POST /<token>/playlist`) that
 * becomes the source, since Android TV boxes rarely have a file picker.
 */
object PhoneSetupServer {

    data class Submission(
        val type: String,
        val m3uUrl: String,
        val host: String,
        val username: String,
        val password: String,
    )

    private val _submission = MutableStateFlow<Submission?>(null)
    /** Latest form sent from a phone; consume with [consume] once applied. */
    val submission: StateFlow<Submission?> = _submission.asStateFlow()

    /** An M3U file received from the phone and already saved as the active source. */
    data class ImportedPlaylist(val fileName: String, val channelCount: Int)

    private val _playlistImported = MutableStateFlow<ImportedPlaylist?>(null)
    /** Last playlist received while the server ran, so the settings screen can say so. */
    val playlistImported: StateFlow<ImportedPlaylist?> = _playlistImported.asStateFlow()

    private val _restored = MutableStateFlow(0)
    /** Incremented after each successful restore, so the settings screen can reload. */
    val restored: StateFlow<Int> = _restored.asStateFlow()

    private var socket: ServerSocket? = null
    private var thread: Thread? = null
    private var users = 0
    private var token: String = ""

    /** Starts (or joins) the server; returns the URL to encode in the QR, or null offline. */
    @Synchronized
    fun start(): String? {
        val address = lanAddress() ?: return null
        if (socket == null) {
            val server = runCatching { ServerSocket(0) }.getOrElse {
                Log.w(TAG, "could not open server socket", it)
                return null
            }
            token = newToken()
            _submission.value = null
            _playlistImported.value = null
            socket = server
            thread = Thread({ acceptLoop(server) }, "phone-setup").apply {
                isDaemon = true
                start()
            }
        }
        users++
        return "http://$address:${socket!!.localPort}/$token"
    }

    @Synchronized
    fun stop() {
        users = (users - 1).coerceAtLeast(0)
        if (users > 0) return
        runCatching { socket?.close() }
        socket = null
        thread = null
    }

    fun consume() {
        _submission.value = null
    }

    val isRunning: Boolean get() = socket != null

    private fun acceptLoop(server: ServerSocket) {
        while (!server.isClosed) {
            val client = runCatching { server.accept() }.getOrNull() ?: break
            runCatching { client.use { handle(it) } }
                .onFailure { Log.w(TAG, "request failed", it) }
        }
    }

    private class Request(
        val method: String,
        val path: String,
        val body: ByteArray,
        /** Lower-cased header names. */
        val headers: Map<String, String>,
        /** Content-Length as sent, which may exceed what was read into [body]. */
        val declaredLength: Int,
    )

    private fun handle(client: Socket) {
        client.soTimeout = 15_000
        val input = client.getInputStream()
        val request = readRequest(input) ?: return
        val out = client.getOutputStream()
        fun respond(status: String, contentType: String, bytes: ByteArray, extraHeaders: String = "") {
            out.write(
                ("HTTP/1.1 $status\r\nContent-Type: $contentType\r\n$extraHeaders" +
                    "Cache-Control: no-store, no-cache, must-revalidate\r\n" +
                    "Pragma: no-cache\r\nX-Content-Type-Options: nosniff\r\n" +
                    "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray(),
            )
            out.write(bytes)
            out.flush()
        }
        fun html(status: String, content: String) =
            respond(status, "text/html; charset=utf-8", page(content).toByteArray(Charsets.UTF_8))
        fun text(status: String, message: String) =
            respond(status, "text/plain; charset=utf-8", message.toByteArray(Charsets.UTF_8))

        val segments = request.path.split('/').filter { it.isNotEmpty() }
        if (segments.firstOrNull() != token) {
            html("404 Not Found", "<p>Page introuvable.</p>")
            return
        }
        when (request.method to segments.getOrNull(1)) {
            "GET" to null -> html("200 OK", FORM + PLAYLIST_BLOCK + BACKUP_BLOCK)
            "POST" to null -> {
                val fields = parseForm(String(request.body, Charsets.UTF_8))
                _submission.value = Submission(
                    type = fields["type"].orEmpty().ifBlank { "xtream" },
                    m3uUrl = fields["m3u"].orEmpty().trim(),
                    host = fields["host"].orEmpty().trim(),
                    username = fields["username"].orEmpty().trim(),
                    password = fields["password"].orEmpty(),
                )
                html("200 OK", "<h2>Reçu</h2><p>Revenez sur votre TV : les données sont prêtes dans Réglages. Testez la connexion puis enregistrez.</p>")
            }
            "POST" to "playlist" -> {
                if (request.declaredLength > LocalPlaylist.MAX_BYTES) {
                    text("413 Payload Too Large", "Fichier trop volumineux (20 Mo maximum).")
                    return
                }
                val app = PrismApp.get()
                val name = LocalPlaylist.displayName(request.headers["x-file-name"])
                runCatching { LocalPlaylist.save(request.body) }.fold(
                    onSuccess = { channels ->
                        runBlocking { app.settings.saveLocalM3u(name) }
                        _playlistImported.value = ImportedPlaylist(name, channels.size)
                        app.appScope.launch { app.refreshUseCase(force = true) }
                        text("200 OK", "Liste reçue : ${channels.size} chaînes. Le catalogue se recharge sur la TV.")
                    },
                    onFailure = {
                        Log.w(TAG, "playlist import failed", it)
                        text("400 Bad Request", it.message ?: "Fichier refusé.")
                    },
                )
            }
            "GET" to "backup" -> {
                val app = PrismApp.get()
                val backup = runBlocking { app.backup.export() }
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                respond(
                    "200 OK",
                    "application/json; charset=utf-8",
                    backup.toByteArray(Charsets.UTF_8),
                    "Content-Disposition: attachment; filename=\"prism-sauvegarde-$date.json\"\r\n",
                )
            }
            "POST" to "restore" -> {
                val app = PrismApp.get()
                val result = runCatching { runBlocking { app.backup.restore(String(request.body, Charsets.UTF_8)) } }
                result.fold(
                    onSuccess = {
                        _restored.value += 1
                        // The catalogue is not in the backup: fetch it again from the restored source.
                        app.appScope.launch { app.refreshUseCase(force = true) }
                        text("200 OK", "Sauvegarde restaurée. Le catalogue se recharge sur la TV.")
                    },
                    onFailure = {
                        Log.w(TAG, "restore failed", it)
                        text("400 Bad Request", it.message ?: "Restauration impossible.")
                    },
                )
            }
            else -> html("405 Method Not Allowed", "<p>Méthode non prise en charge.</p>")
        }
    }

    /** Reads request line + headers byte-wise, then exactly Content-Length body bytes. */
    private fun readRequest(input: InputStream): Request? {
        val requestLine = readLine(input) ?: return null
        val parts = requestLine.split(" ")
        val method = parts.getOrNull(0).orEmpty()
        val path = parts.getOrNull(1).orEmpty().substringBefore('?')
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val name = line.substringBefore(':', "").trim().lowercase(Locale.ROOT)
            if (name.isNotEmpty()) headers[name] = line.substringAfter(':').trim()
        }
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        // Playlists and backups that carry one can be large; everything else stays small.
        val limit = when (path.trimEnd('/').substringAfterLast('/')) {
            "playlist" -> LocalPlaylist.MAX_BYTES
            "restore" -> LocalPlaylist.MAX_BYTES + MAX_BODY_BYTES
            else -> MAX_BODY_BYTES
        }
        // An oversized upload is refused without reading it, rather than silently cut.
        val length = if (contentLength > limit) 0 else contentLength.coerceAtLeast(0)
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) break
            read += n
        }
        return Request(method, path, if (read == length) body else body.copyOf(read), headers, contentLength)
    }

    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buffer.size() == 0) null else buffer.toString(Charsets.UTF_8.name())
            if (b == '\n'.code) break
            if (b != '\r'.code) buffer.write(b)
            if (buffer.size() > 8_192) break
        }
        return buffer.toString(Charsets.UTF_8.name())
    }

    internal fun parseForm(body: String): Map<String, String> = body.split('&')
        .mapNotNull { pair ->
            val key = pair.substringBefore('=', "")
            if (key.isEmpty()) null
            else URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
        }
        .toMap()

    private fun lanAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    /** A short-lived, unguessable capability URL for the local QR setup page. */
    internal fun newToken(): String {
        val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
        val random = SecureRandom()
        return (1..TOKEN_LENGTH).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    private fun page(content: String) = """
        <!doctype html><html lang="fr"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Configurer Prism</title>
        <style>
          body{font-family:system-ui,sans-serif;background:#000;color:#F4F6F7;margin:0;padding:24px;max-width:480px}
          h1,h2{font-size:1.3rem} label{display:block;margin:14px 0 4px;color:#B4BBD0}
          input[type=text],input[type=password],input[type=url]{width:100%;box-sizing:border-box;padding:12px;border-radius:10px;border:1px solid rgba(255,255,255,.16);background:rgba(255,255,255,.08);color:#F4F6F7;font-size:1rem}
          .row{display:flex;gap:16px;margin-top:8px} button,.btn{display:block;text-align:center;text-decoration:none;box-sizing:border-box;margin-top:20px;width:100%;padding:14px;border:0;border-radius:10px;background:#4D86FF;color:#fff;font-size:1rem;font-weight:600}
          .btn.secondary{background:rgba(255,255,255,.16)} hr{border:0;border-top:1px solid rgba(255,255,255,.16);margin:32px 0}
          p.hint{color:#B4BBD0;font-size:.9rem} #restore-status{margin-top:12px}
        </style></head><body>$content</body></html>
    """.trimIndent()

    private val FORM = """
        <h1>Configurer ma source</h1>
        <form method="post" autocomplete="off">
          <div class="row">
            <label><input type="radio" name="type" value="xtream" checked> Xtream Codes</label>
            <label><input type="radio" name="type" value="m3u"> M3U-URL</label>
          </div>
          <label for="host">Host (Xtream)</label><input type="text" id="host" name="host" placeholder="http://provider.example:8080" autocapitalize="off">
          <label for="username">Identifiant</label><input type="text" id="username" name="username" autocapitalize="off" autocomplete="off">
          <label for="password">Mot de passe</label><input type="password" id="password" name="password" autocomplete="off">
          <label for="m3u">M3U-URL</label><input type="url" id="m3u" name="m3u" placeholder="http://…/playlist.m3u">
          <button type="submit">Envoyer à la TV</button>
        </form>
    """.trimIndent()

    private val PLAYLIST_BLOCK = """
        <hr>
        <h2>Envoyer un fichier de chaînes</h2>
        <p class="hint">Un fichier .m3u ou .m3u8 de votre téléphone. Il est enregistré uniquement
        sur la TV et devient la source de Prism ; il n'est envoyé nulle part ailleurs.</p>
        <input type="file" id="playlist-file">
        <button type="button" id="playlist-btn">Envoyer le fichier à la TV</button>
        <p id="playlist-status" class="hint"></p>
        <script>
          (function () {
            var base = location.pathname.replace(/\/+$/, '');
            var max = 20 * 1024 * 1024;
            document.getElementById('playlist-btn').onclick = function () {
              var f = document.getElementById('playlist-file').files[0];
              var status = document.getElementById('playlist-status');
              if (!f) { status.textContent = 'Choisissez d’abord un fichier.'; return; }
              if (f.size > max) { status.textContent = 'Fichier trop volumineux (20 Mo maximum).'; return; }
              status.textContent = 'Envoi…';
              fetch(base + '/playlist', {
                method: 'POST',
                headers: { 'X-File-Name': encodeURIComponent(f.name) },
                body: f
              }).then(function (r) {
                return r.text().then(function (t) { status.textContent = t; });
              }).catch(function () { status.textContent = 'Échec de l’envoi.'; });
            };
          })();
        </script>
    """.trimIndent()

    private val BACKUP_BLOCK = """
        <hr>
        <h2>Sauvegarde</h2>
        <p class="hint">Un fichier contenant la source (identifiants compris), les profils, favoris,
        listes et reprises de lecture. Gardez-le en lieu sûr.</p>
        <a class="btn" href="backup" id="backup-link">Télécharger la sauvegarde</a>
        <label for="restore-file">Restaurer une sauvegarde</label>
        <input type="file" id="restore-file" accept="application/json,.json">
        <button type="button" class="btn secondary" id="restore-btn">Restaurer sur la TV</button>
        <p id="restore-status" class="hint"></p>
        <script>
          var base = location.pathname.replace(/\/+$/, '');
          document.getElementById('backup-link').href = base + '/backup';
          document.getElementById('restore-btn').onclick = function () {
            var f = document.getElementById('restore-file').files[0];
            var status = document.getElementById('restore-status');
            if (!f) { status.textContent = 'Choisissez d\'abord un fichier.'; return; }
            status.textContent = 'Envoi…';
            f.text().then(function (body) {
              return fetch(base + '/restore', { method: 'POST', body: body });
            }).then(function (r) {
              return r.text().then(function (t) { status.textContent = t; });
            }).catch(function () { status.textContent = 'Échec de l\'envoi.'; });
          };
        </script>
    """.trimIndent()

    private const val MAX_BODY_BYTES = 5 * 1024 * 1024
    private const val TAG = "PhoneSetupServer"
    private const val TOKEN_LENGTH = 16
}
