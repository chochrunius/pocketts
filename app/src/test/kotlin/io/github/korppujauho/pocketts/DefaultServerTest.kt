package io.github.korppujauho.pocketts

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DefaultServerTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun prefs(name: String) = context.getSharedPreferences(name, 0)
    private fun initialize() = Settings(context).initializeDefaults()

    @Test fun freshInstallAndRepeatedLaunches() {
        repeat(3) { initialize() }
        assertEquals("AlfredTS_VaseJmeno", Settings(context).nickname)
        assertEquals("chochrun.eu", Settings(context).address)
        assertEquals(listOf(Favorite("chochrun.eu", "Ludmila Chochrun.eu", rememberChannel = true)), Favorites.load(context))
        assertEquals(ChannelPreference(remember = true), ChannelPreferences(context).load("chochrun.eu"))
        assertEquals("", Settings(context).serverPassword)
        assertTrue(prefs("settings").getBoolean("defaults161", false))
        assertTrue(prefs("secrets").all.isEmpty()) // no identity or credentials generated
    }

    @Test fun upgradePreservesAllExistingStorageAndAutoConnect() {
        // Even unreadable encrypted data must survive byte-for-byte; migration does not need its key.
        prefs("secrets").edit().putString("identity", "unreadable encrypted identity")
            .putString("serverPassword", "unreadable server password")
            .putString("favorites", "unreadable favourite passwords").commit()
        prefs("settings").edit().putString("nickname", "My nickname").putString("address", "other.example")
            .putString("language", "cs").commit()
        val existing = """[{"address":"other.example","name":"Mine","auto":true,"rememberChannel":false,"extra":"keep"}]"""
        prefs("favorites").edit().putString("list", existing).commit()
        val channels = ChannelPreferences(context)
        channels.save("chochrun.eu:9987", ChannelPreference(false, "Private", "History"))
        channels.save("other.example", ChannelPreference(true, "Other", "Last"))
        val beforeSecrets = prefs("secrets").all.toMap()
        val beforeChannels = prefs("channelPreferences").all.toMap()
        initialize()
        assertEquals(beforeSecrets, prefs("secrets").all)
        assertEquals(beforeChannels, prefs("channelPreferences").all)
        assertEquals("My nickname", Settings(context).nickname)
        assertEquals("other.example", Settings(context).address)
        assertEquals("cs", prefs("settings").getString("language", ""))
        val list = org.json.JSONArray(prefs("favorites").getString("list", ""))
        assertEquals(org.json.JSONArray(existing).getJSONObject(0).toString(), list.getJSONObject(0).toString())
        assertEquals(2, list.length())
        assertTrue(list.getJSONObject(0).getBoolean("auto"))
        assertFalse(list.getJSONObject(1).getBoolean("auto"))
    }

    @Test fun equivalentFavoriteIsNotRewritten() {
        val original = """[ {"address":" TS3SERVER://CHOCHRUN.EU:9987/ ","name":"Custom","nickname":"Own","auto":true,"rememberChannel":false,"defaultChannel":"Lobby","password":"legacy password"} ]"""
        prefs("favorites").edit().putString("list", original).commit()
        initialize()
        assertEquals(original, prefs("favorites").getString("list", null))
    }

    @Test fun deletionAndClearedFieldsAreNotRestored() {
        initialize()
        Favorites.save(context, emptyList())
        Settings(context).nickname = ""
        Settings(context).address = ""
        ChannelPreferences(context).save("chochrun.eu", ChannelPreference(false))
        repeat(2) { initialize() }
        assertTrue(Favorites.load(context).isEmpty())
        assertEquals("", Settings(context).nickname)
        assertEquals("", Settings(context).address)
        assertFalse(ChannelPreferences(context).load("chochrun.eu").remember)
    }

    @Test fun initializesOnlyMissingConnectionField() {
        Settings(context).nickname = "Keep me"
        initialize()
        assertEquals("Keep me", Settings(context).nickname)
        assertEquals("chochrun.eu", Settings(context).address)
        prefs("settings").edit().remove("defaults161").remove("nickname").putString("address", "keep.example").commit()
        initialize()
        assertEquals("AlfredTS_VaseJmeno", Settings(context).nickname)
        assertEquals("keep.example", Settings(context).address)
    }

    @Test fun malformedFavoritesAreNeverOverwritten() {
        for (raw in listOf("broken", "{}", "[42]", "[{}]", "[{\"address\":null}]")) {
            prefs("favorites").edit().putString("list", raw).commit()
            initialize()
            assertEquals(raw, prefs("favorites").getString("list", null))
            assertFalse(prefs("settings").getBoolean("defaults161", false))
            assertTrue(prefs("channelPreferences").all.isEmpty())
        }
        prefs("favorites").edit().putString("list", "[]").commit()
        initialize()
        assertEquals(1, Favorites.load(context).size)
    }

    @Test fun encryptedCredentialsAndLegacyPasswordsStillLoadAfterUpgrade() {
        val original = Secrets.box
        try {
            Secrets.box = SecretBox(SoftwareKey())
            val id = io.github.korppujauho.pocketts.proto.Identity.generate(securityLevel = 0)
            Settings(context).saveIdentity(id)
            Settings(context).serverPassword = "server password"
            Favorites.save(context, listOf(Favorite("other.example", password = "favourite password", auto = true)))
            val before = prefs("secrets").all.toMap()
            initialize()
            assertEquals(before, prefs("secrets").all)
            assertEquals(id.uid, Settings(context).identity().uid)
            assertEquals("server password", Settings(context).serverPassword)
            assertEquals(listOf("favourite password", ""), Favorites.load(context).map { it.password })
            // Also cover the legacy inline-password representation without moving it during initialization.
            prefs("settings").edit().remove("defaults161").commit()
            prefs("favorites").edit().putString("list", """[{"address":"legacy.example","password":"legacy password"}]""").commit()
            initialize()
            assertEquals(listOf("legacy password", ""), Favorites.load(context).map { it.password })
        } finally { Secrets.box = original }
    }
}
