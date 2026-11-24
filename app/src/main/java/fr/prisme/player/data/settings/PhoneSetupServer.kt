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
 * The same page also downloads a backup (`/<token>/backup`) and restores one
 * (`POST /<token>/restore`), since Android TV boxes rarely have a file picker.
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

    private class Request(val method: String, val path: String, val body: ByteArray)

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
            "GET" to null -> html("200 OK", FORM + BACKUP_BLOCK)
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
        var contentLength = 0
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
            }
        }
        val length = contentLength.coerceIn(0, MAX_BODY_BYTES)
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) break
            read += n
        }
        return Request(method, path, if (read == length) body else body.copyOf(read))
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
