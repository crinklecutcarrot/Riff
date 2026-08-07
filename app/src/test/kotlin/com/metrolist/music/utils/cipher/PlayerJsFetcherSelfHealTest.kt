package com.metrolist.music.utils.cipher

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The player.js self-heal re-fetch (CipherDeobfuscator.getOrCreateWebView, on hashNowKnown=false)
 * is gated by [PlayerJsFetcher.shouldAttemptSelfHeal] so a config-less player hash can't re-download
 * the ~2.8 MB player.js on every track. It must: allow one attempt per distinct hash, cool down a
 * repeat of the SAME hash, always let a newly rotated hash through, and never wedge on a backward
 * clock step. These exercise that gate directly, no network.
 */
class PlayerJsFetcherSelfHealTest {

    @After
    fun tearDown() {
        // Don't leak guard state into other tests sharing this JVM.
        PlayerJsFetcher.resetSelfHealForTest()
    }

    @Test
    fun `first attempt for a hash is allowed, an immediate repeat is on cooldown`() {
        val t0 = 100_000_000L
        assertTrue("first attempt for a hash must be allowed", PlayerJsFetcher.shouldAttemptSelfHeal("hashA", t0))
        assertFalse(
            "re-fetching the same failing hash again immediately must be gated",
            PlayerJsFetcher.shouldAttemptSelfHeal("hashA", t0 + 1_000L),
        )
    }

    @Test
    fun `a newly rotated hash always gets a fresh attempt`() {
        val t0 = 100_000_000L
        assertTrue(PlayerJsFetcher.shouldAttemptSelfHeal("hashA", t0))
        assertTrue(
            "a different (rotated) hash must not inherit hashA's cooldown",
            PlayerJsFetcher.shouldAttemptSelfHeal("hashB", t0 + 1_000L),
        )
    }

    @Test
    fun `the same hash retries only after the cooldown window`() {
        val t0 = 100_000_000L
        assertTrue(PlayerJsFetcher.shouldAttemptSelfHeal("hashA", t0))
        assertFalse(
            "still within the 30-minute window",
            PlayerJsFetcher.shouldAttemptSelfHeal("hashA", t0 + 29 * 60_000L),
        )
        assertTrue(
            "past the window, the same failing hash may retry once more",
            PlayerJsFetcher.shouldAttemptSelfHeal("hashA", t0 + 31 * 60_000L),
        )
    }

    @Test
    fun `a backward clock step does not wedge the cooldown`() {
        val t0 = 100_000_000L
        assertTrue(PlayerJsFetcher.shouldAttemptSelfHeal("hashA", t0))
        assertTrue(
            "a negative delta (clock stepped back) must not hold the cooldown for the skew",
            PlayerJsFetcher.shouldAttemptSelfHeal("hashA", t0 - 5_000L),
        )
    }
}
