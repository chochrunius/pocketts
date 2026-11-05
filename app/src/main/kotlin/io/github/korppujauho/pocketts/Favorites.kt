package io.github.korppujauho.pocketts

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * WebTS's favourites: a server's address, a name for the lists, the nickname used there, its password (kept only on
 * this phone), and whether to connect to it when the app opens (one at most).
 */
data class Favorite(
    val address: String,
    val name: String = "",
    val nickname: String = "",
    val password: String = "",
    val auto: Boolean = false,
    val filePort: Int? = null, // the server's file transfer port when it isn't the one the server tells (advanced)
    val rememberChannel: Boolean = false,
    val defaultChannel: String = "",
) {
    val label: String get() = name.ifBlank { address }
}

object Favorites {
    private fun prefs(c: Context) = c.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    /** Append without decrypting/re-encrypting passwords or normalizing existing entries.
     * Passwords are indexed by position; an absent trailing password already reads as empty.
     * Invalid storage is left intact and initialization can retry after it is repaired.
     */
    internal fun initializeDefault(c: Context): Boolean = try {
        val p = prefs(c)
        val list = JSONArray(p.getString("list", "[]"))
        val entries = (0 until list.length()).map { list.getJSONObject(it) }
        require(entries.all { it.opt("address") is String && it.getString("address").isNotBlank() })
        if (entries.any { same(it.getString("address"), Settings.DEFAULT_ADDRESS) }) true
        else {
            list.put(JSONObject().put("address", Settings.DEFAULT_ADDRESS).put("name", "Ludmila Chochrun.eu")
                .put("nickname", "").put("auto", false).put("rememberChannel", true).put("defaultChannel", "")
                // Keep the legacy loader's password mode when any old entry contains a password.
                .apply { if (entries.any { it.has("password") }) put("password", "") })
            p.edit().putString("list", list.toString()).commit()
        }
    } catch (_: Exception) {
        AppLog.log("Default favourite initialization deferred: existing favourites could not be read")
        false
    }

    private const val PASSWORD = "favorites/password" // one in the list, as an earlier version kept it
    private const val PASSWORDS = "favorites/passwords"

    /**
     * The passwords are kept apart from the list, encrypted with the other secrets (see [secretPrefs]): the list comes
     * along when the app's data is copied to a new phone, the passwords don't. Ones an earlier version kept in the list
     * are moved there now.
     */
    fun load(c: Context): List<Favorite> = try {
        val a = JSONArray(prefs(c).getString("list", "[]"))
        val inList = (0 until a.length()).any { a.getJSONObject(it).has("password") }
        val passwords = if (inList) JSONArray() else passwords(c)
        val list = (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Favorite(
                o.optString("address"), o.optString("name"), o.optString("nickname"),
                if (inList) password(o.optString("password")) else passwords.optString(i), o.optBoolean("auto"),
                o.optInt("filePort", 0).takeIf { it in 1..65535 },
                o.optBoolean("rememberChannel"), o.optString("defaultChannel"),
            )
        }.filter { it.address.isNotBlank() }
        if (inList) save(c, list)
        list
    } catch (_: Exception) {
        emptyList()
    }

    /** The passwords, in the list's order. */
    private fun passwords(c: Context): JSONArray = try {
        JSONArray(secretPrefs(c).getSecret("favorites", PASSWORDS) ?: "[]")
    } catch (e: SecretBox.Unreadable) {
        AppLog.log("The favourites' passwords can't be read: ${e.message}")
        JSONArray()
    }

    private fun password(stored: String) = try {
        Secrets.box.open(stored, PASSWORD)
    } catch (e: SecretBox.Unreadable) {
        AppLog.log("A favourite's password can't be read: ${e.message}")
        ""
    }

    fun save(c: Context, list: List<Favorite>) {
        val a = JSONArray()
        // At most one connects automatically: the last one marked
        val autoAt = list.indexOfLast { it.auto }
        list.forEachIndexed { i, f ->
            a.put(
                JSONObject().put("address", f.address.trim()).put("name", f.name.trim()).put("nickname", f.nickname.trim())
                    .put("rememberChannel", f.rememberChannel).put("defaultChannel", f.defaultChannel)
                    .put("auto", i == autoAt).apply { f.filePort?.let { put("filePort", it) } }
            )
        }
        val secrets = secretPrefs(c).edit()
        if (list.all { it.password.isEmpty() }) secrets.remove("favorites")
        else secrets.putSecret("favorites", PASSWORDS, JSONArray(list.map { it.password }).toString())
        secrets.commit()
        prefs(c).edit().putString("list", a.toString()).apply()
    }

    /** The same server, however it was typed ("Example.com:9987" is "example.com"). */
    fun same(a: String, b: String): Boolean = normal(a) == normal(b)

    private fun normal(s: String) = s.trim().lowercase().removePrefix("ts3server://").removeSuffix("/").removeSuffix(":9987")
}
