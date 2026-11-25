package nl.vanvrouwerff.iptv.data.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SinglePlaybackLeaseTest {
    @Test
    fun `second device cannot silently replace active playback`() {
        val authority = SinglePlaybackLease(nowMs = { 1_000L })
        assertTrue(authority.acquire("tv", "Salon") is SinglePlaybackLease.Decision.Granted)

        val result = authority.acquire("phone", "Téléphone")
        assertTrue(result is SinglePlaybackLease.Decision.InUse)
        assertEquals("tv", (result as SinglePlaybackLease.Decision.InUse).lease.deviceId)
    }

    @Test
    fun `expired lease frees the playback slot`() {
        var now = 1_000L
        val authority = SinglePlaybackLease(nowMs = { now }, durationMs = 100L)
        authority.acquire("tv", "Salon")
        now += 101L

        val result = authority.acquire("phone", "Téléphone")
        assertTrue(result is SinglePlaybackLease.Decision.Granted)
    }

    @Test
    fun `release requires the opaque owner token`() {
        val authority = SinglePlaybackLease(nowMs = { 1_000L })
        val lease = (authority.acquire("tv", "Salon") as SinglePlaybackLease.Decision.Granted).lease
        authority.release("tv", "not-the-token")
        assertEquals("tv", authority.current()?.deviceId)

        authority.release("tv", lease.token)
        assertNull(authority.current())
    }
}
