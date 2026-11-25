package fr.prisme.player.data.pairing

import java.security.SecureRandom
import java.util.Base64

/**
 * In-memory authority used by the future authenticated LAN pairing transport.
 *
 * A provider still decides how many streams its subscription permits. This only
 * coordinates Prism devices that deliberately share one household playback slot.
 * The authority has no Android dependency, which keeps its security rules easy to
 * test and lets the LAN endpoint remain a thin adapter around it.
 */
class SinglePlaybackLease(
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val durationMs: Long = DEFAULT_DURATION_MS,
) {
    private var active: Lease? = null

    @Synchronized
    fun acquire(deviceId: String, deviceName: String): Decision {
        require(deviceId.isNotBlank()) { "deviceId is required" }
        val now = nowMs()
        val current = active?.takeIf { it.expiresAtMs > now }
        if (current == null || current.deviceId == deviceId) {
            val lease = Lease(
                deviceId = deviceId,
                deviceName = deviceName.ifBlank { "Prism" },
                token = newToken(),
                expiresAtMs = now + durationMs,
            )
            active = lease
            return Decision.Granted(lease)
        }
        return Decision.InUse(current)
    }

    /** Refresh can only extend a lease when both owner and opaque token match. */
    @Synchronized
    fun renew(deviceId: String, token: String): Decision {
        val now = nowMs()
        val current = active?.takeIf { it.expiresAtMs > now } ?: return Decision.Missing
        if (current.deviceId != deviceId || current.token != token) return Decision.InUse(current)
        val renewed = current.copy(expiresAtMs = now + durationMs)
        active = renewed
        return Decision.Granted(renewed)
    }

    /** Called only after a visible user confirmation such as “Lire ici à la place ?”. */
    @Synchronized
    fun takeOver(deviceId: String, deviceName: String): Lease {
        require(deviceId.isNotBlank()) { "deviceId is required" }
        val lease = Lease(
            deviceId = deviceId,
            deviceName = deviceName.ifBlank { "Prism" },
            token = newToken(),
            expiresAtMs = nowMs() + durationMs,
        )
        active = lease
        return lease
    }

    @Synchronized
    fun release(deviceId: String, token: String) {
        if (active?.deviceId == deviceId && active?.token == token) active = null
    }

    @Synchronized
    fun current(): Lease? {
        val now = nowMs()
        val current = active?.takeIf { it.expiresAtMs > now }
        if (current == null) active = null
        return current
    }

    data class Lease(
        val deviceId: String,
        val deviceName: String,
        val token: String,
        val expiresAtMs: Long,
    )

    sealed interface Decision {
        data class Granted(val lease: Lease) : Decision
        data class InUse(val lease: Lease) : Decision
        data object Missing : Decision
    }

    private fun newToken(): String = ByteArray(TOKEN_BYTES).also(random::nextBytes).let { bytes ->
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val DEFAULT_DURATION_MS = 45_000L
        const val TOKEN_BYTES = 24
        val random = SecureRandom()
    }
}
