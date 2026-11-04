package io.github.korppujauho.pocketts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    @Test
    fun alfredReleases() {
        assertEquals("https://api.github.com/repos/chochrunius/pocketts/releases/latest", UpdateCheck.LATEST)
        val releases = "https://github.com/chochrunius/pocketts/releases"
        assertEquals(releases, UpdateCheck.RELEASES)
        assertEquals("$releases/tag/v1.6.0", UpdateCheck.releaseUrl("$releases/tag/v1.6.0"))
        for (url in listOf(null, "http://github.com/chochrunius/pocketts/releases/tag/v1.6.0",
            "https://github.com/KorppuJauho/pocketts/releases/tag/v1.6.0",
            "$releases.evil.example/tag/v9.0.0")) {
            assertEquals(releases, UpdateCheck.releaseUrl(url))
        }
        assertTrue(UpdateCheck.newer("1.6.0", "1.5.0"))
        assertFalse(UpdateCheck.newer("1.6.0", "1.6.0"))
    }

    @Test
    fun versions() {
        assertTrue(UpdateCheck.newer("0.2.0", "0.1.0"))
        assertTrue(UpdateCheck.newer("0.10.0", "0.9.2"))
        assertTrue(UpdateCheck.newer("1.0", "0.9.9"))
        assertTrue(UpdateCheck.newer("0.1.1", "0.1.0-debug"))
        assertFalse(UpdateCheck.newer("0.1.0", "0.1.0"))
        assertFalse(UpdateCheck.newer("0.1.0", "0.1.0-debug"))
        assertFalse(UpdateCheck.newer("0.1.0", "0.2.0"))
        assertFalse(UpdateCheck.newer("nonsense", "0.1.0"))
    }
}
