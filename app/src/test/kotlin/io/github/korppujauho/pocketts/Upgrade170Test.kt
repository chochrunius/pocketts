package io.github.korppujauho.pocketts

import io.github.korppujauho.pocketts.proto.JoinLink
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Upgrade170Test {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun prefs(name: String) = app.getSharedPreferences(name, 0)

    @Test fun upgradeReads161WithoutRewritingAndMovesBothPasswordsWithTheFavourite() {
        val original = Secrets.box
        Secrets.box = SecretBox(SoftwareKey())
        try {
            val legacy = """[{"address":"a.example","name":"A","nickname":"","auto":true,"rememberChannel":true,"defaultChannel":"Lobby"},{"address":"b.example","name":"B","nickname":"Bee","auto":false,"rememberChannel":false,"defaultChannel":"Music"}]"""
            prefs("settings").edit().putBoolean("defaults161", true).putString("nickname", "My nick").commit()
            prefs("favorites").edit().putString("list", legacy).commit()
            val encrypted = Secrets.box.seal("""["server-a","server-b"]""", "favorites/passwords")
            prefs("secrets").edit().putString("favorites", encrypted).commit()
            val channels = ChannelPreferences(app)
            channels.save("a.example", ChannelPreference(true, "Lobby", "/42"))
            repeat(2) { Settings(app).initializeDefaults() }
            val existing = Favorites.load(app)
            assertEquals(legacy, prefs("favorites").getString("list", null))
            assertEquals(encrypted, prefs("secrets").getString("favorites", null))
            assertEquals(listOf("A", "B"), existing.map { it.name })
            assertTrue(existing.first().auto)
            assertEquals("My nick", existing.first().nickname.ifBlank { Settings(app).nickname })
            assertTrue(existing.all { it.channel.isEmpty() })
            assertEquals("/42", channels.load("a.example").channel())

            val a = existing.first().copy(folder = "Game", channel = "Games/Lobby", channelId = 43, channelPassword = "channel-a")
            assertTrue(Favorites.save(app, listOf(a, existing.last())))
            val saved = Favorites.load(app)
            assertTrue(Favorites.save(app, Favorites.moved(saved, saved.first(), "", 1)))
            val reordered = Favorites.load(app)
            assertEquals(listOf("B", "A"), reordered.map { it.name })
            assertEquals(listOf("server-b", "server-a"), reordered.map { it.password })
            assertEquals(listOf("", "channel-a"), reordered.map { it.channelPassword })
            assertEquals(43, reordered.last().channelId)
            assertTrue(reordered.last().auto)
            assertEquals("Lobby", reordered.last().defaultChannel)
            assertEquals(ChannelPreference(true, "Lobby", "/42"), channels.load("a.example"))
        } finally {
            Secrets.box = original
        }
    }

    @Test fun corruptMetadataOrEncryptedPasswordDoesNotGetOverwritten() {
        prefs("favorites").edit().putString("list", "[{}]").commit()
        assertFalse(Favorites.save(app, listOf(Favorite("new.example"))))
        assertEquals("[{}]", prefs("favorites").getString("list", null))
        val original = Secrets.box
        try {
            Secrets.box = SecretBox(SoftwareKey())
            val cipher = Secrets.box.seal("""["password"]""", "favorites/passwords")
            prefs("favorites").edit().putString("list", """[{"address":"a.example"}]""").commit()
            prefs("secrets").edit().putString("favorites", cipher).commit()
            Secrets.box = SecretBox(SoftwareKey())
            assertFalse(Favorites.save(app, Favorites.load(app)))
            assertEquals(cipher, prefs("secrets").getString("favorites", null))
        } finally {
            Secrets.box = original
        }
    }

    @Test fun actualConnectionPolicyDistinguishesLinkFavouriteAndReconnectPasswords() {
        val legacy = ChannelPreference(true, "Default", "/42")
        val saved: (Int) -> String = { if (it == 42) "remembered" else "" }
        val link = JoinLink.parse("ts3server://example.com?channel=Links&channelpassword=linkpass")!!
        assertEquals(ConnectionChannel("Links", "linkpass", explicit = true),
            connectionChannel(legacy, link.channel, link.channelPassword, 0, false, saved))
        assertEquals(ConnectionChannel("Games/Lobby", "favourite", 43, explicit = true),
            connectionChannel(legacy, "Games/Lobby", "favourite", 43, false, saved))
        assertEquals(ConnectionChannel("/42", "remembered", 42),
            connectionChannel(legacy, "Games/Lobby", "favourite", 43, true, saved))
        assertEquals(ConnectionChannel("/42", "remembered", 42),
            connectionChannel(legacy, "", "", 0, false, saved))
        assertEquals(ConnectionChannel("/42", "favourite", 42),
            connectionChannel(legacy, "Games/Lobby", "favourite", 42, true) { "" })
        assertEquals(ConnectionChannel("Default", ""),
            connectionChannel(legacy.copy(remember = false), "", "", 0, false, saved))
    }
}
