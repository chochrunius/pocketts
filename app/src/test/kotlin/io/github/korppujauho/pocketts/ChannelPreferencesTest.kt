package io.github.korppujauho.pocketts

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChannelPreferencesTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Test fun priority() {
        val p = ChannelPreference(true, "Výchozí 🎵", "Poslední čaj 🫖")
        assertEquals("Odkaz", p.channel("Odkaz"))
        assertEquals(p.lastChannel, p.channel())
        assertEquals(p.defaultChannel, p.copy(remember = false).channel())
        assertEquals(p.defaultChannel, p.copy(lastChannel = "").channel())
        assertEquals("", ChannelPreference().channel())
    }
    @Test fun reconnectUsesActualChannelInsteadOfOriginalLinkWhenRemembering() {
        val p = ChannelPreference(true, "Výchozí", "/42")
        assertEquals("Odkaz 🎮", p.channel("Odkaz 🎮"))
        assertEquals("/42", p.channel("Odkaz 🎮", reconnecting = true))
        assertEquals("Odkaz 🎮", p.copy(remember = false).channel("Odkaz 🎮", reconnecting = true))
    }
    @Test fun successfulMovesSurviveReconnectAndFutureConnections() {
        val store = ChannelPreferences(context)
        store.save("Example.org", ChannelPreference(true, "Výchozí"))
        store.joined("example.org:9987", "Rodič/Český kanál 🎮")
        val reopened = ChannelPreferences(context)
        assertEquals("Rodič/Český kanál 🎮", reopened.load("example.org").channel())
        reopened.joined("example.org", "Další 🎵")
        assertEquals("Další 🎵", store.load("example.org").channel())
        assertEquals("", store.load("other.org").channel())
        store.save("example.org", store.load("example.org").copy(remember = false))
        store.joined("example.org", "Ignorovat")
        assertEquals("Výchozí", store.load("example.org").channel())
        assertEquals("Další 🎵", store.load("example.org").lastChannel)
    }
    @Test fun oldFavoritesKeepSettingsAndNewPreferencesRoundTrip() {
        val prefs = context.getSharedPreferences("favorites", 0)
        prefs.edit().putString("list", """[{"address":"legacy.org","nickname":"Nick","auto":true,"filePort":30034}]""").commit()
        val old = Favorites.load(context).single()
        assertFalse(old.rememberChannel)
        assertEquals("", old.defaultChannel)
        val updated = old.copy(rememberChannel = true, defaultChannel = "Čaj 🎮")
        Favorites.save(context, listOf(updated))
        assertEquals(updated, Favorites.load(context).single())
    }
}
