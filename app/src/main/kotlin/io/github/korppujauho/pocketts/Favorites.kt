package io.github.korppujauho.pocketts

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * WebTS's favourites: a server's address, a name for the lists, the nickname used there, its password (kept only on
 * this phone), whether to connect to it when the app opens (one at most), and the channel to join there (its name, or
 * its path for a subchannel; the server's default channel when empty) with that channel's password. The channel's id is
 * learned when it's first joined, so it's found again after it's renamed or moved. The folder it's shown in is just its
 * name ("" is the first one, shown as "Favourites" until renamed): a folder is there while a favourite names it.
 */
data class Favorite(
    val address: String,
    val name: String = "",
    val nickname: String = "",
    val password: String = "",
    val auto: Boolean = false,
    val filePort: Int? = null, // the server's file transfer port when it isn't the one the server tells (advanced)
    val channel: String = "",
    val channelPassword: String = "",
    val channelId: Int = 0,
    val folder: String = "",
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
    @Synchronized
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
    private const val CHANNEL_PASSWORDS = "favorites/channelPasswords"
    private const val SECRET_SLOT = "secretSlot"

    internal fun secretKey(name: String, slot: Boolean) = if (slot) "$name.next" else name

    private fun entries(raw: String?): List<JSONObject> {
        val list = JSONArray(raw)
        return (0 until list.length()).map { list.getJSONObject(it) }.also { entries ->
            require(entries.all { it.opt("address") is String && it.getString("address").isNotBlank() })
        }
    }

    /**
     * The passwords (the servers' and the channels') are kept apart from the list, encrypted with the other secrets (see [secretPrefs]): the list comes
     * along when the app's data is copied to a new phone, the passwords don't. Ones an earlier version kept in the list
     * are moved there now.
     */
    @Synchronized
    fun load(c: Context): List<Favorite> = try {
        val p = prefs(c)
        val entries = entries(p.getString("list", "[]"))
        val slot = p.getBoolean(SECRET_SLOT, false)
        val inList = entries.any { it.has("password") }
        val passwords = if (inList) JSONArray() else passwords(c, secretKey("favorites", slot), PASSWORDS)
        val channelPasswords = passwords(c, secretKey("favoriteChannels", slot), CHANNEL_PASSWORDS)
        val list = entries.mapIndexed { i, o ->
            Favorite(
                o.optString("address"), o.optString("name"), o.optString("nickname"),
                if (inList) password(o.optString("password")) else passwords.optString(i), o.optBoolean("auto"),
                o.optInt("filePort", 0).takeIf { it in 1..65535 }, o.optString("channel"), channelPasswords.optString(i),
                o.optInt("channelId", 0), o.optString("folder"),
                o.optBoolean("rememberChannel"), o.optString("defaultChannel"),
            )
        }
        if (inList) save(c, list)
        list
    } catch (_: Exception) {
        emptyList()
    }

    /** The passwords saved under [name], in the list's order. */
    private fun passwords(c: Context, name: String, label: String): JSONArray = try {
        JSONArray(secretPrefs(c).getSecret(name, label) ?: "[]")
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

    @Synchronized
    fun save(c: Context, list: List<Favorite>): Boolean = try {
        write(c, list)
    } catch (_: Exception) {
        AppLog.log("Could not save favourites: existing data is unreadable or encryption failed")
        false
    }

    private fun write(c: Context, list: List<Favorite>): Boolean {
        val p = prefs(c)
        val previousList = p.getString("list", "[]")
        val original = entries(previousList)
        val secrets = secretPrefs(c)
        val slot = p.getBoolean(SECRET_SLOT, false)
        // Validate the original credentials before writing; blank substitutes from load must not erase them.
        original.forEach { entry -> entry.optString("password").takeIf { it.isNotEmpty() }?.let {
            Secrets.box.open(it, PASSWORD)
        } }
        val a = JSONArray()
        // At most one connects automatically: the last one marked
        val autoAt = list.indexOfLast { it.auto }
        list.forEachIndexed { i, f ->
            a.put(
                JSONObject().put("address", f.address.trim()).put("name", f.name.trim()).put("nickname", f.nickname.trim())
                    .put("rememberChannel", f.rememberChannel).put("defaultChannel", f.defaultChannel)
                    .put("auto", i == autoAt).apply {
                        f.filePort?.let { put("filePort", it) }
                        if (f.channel.isNotBlank()) put("channel", f.channel.trim())
                        if (f.channel.isNotBlank() && f.channelId > 0) put("channelId", f.channelId)
                        if (f.folder.isNotBlank()) put("folder", f.folder.trim())
                    }
            )
        }
        val next = !slot
        val staged = secrets.edit()
        val oldKeys = listOf("favorites", "favoriteChannels").map { secretKey(it, slot) }
        fun stage(name: String, label: String, values: List<String>) {
            val key = secretKey(name, next)
            val old = secrets.getString(secretKey(name, slot), null)
            val decoded = old?.let { JSONArray(Secrets.box.open(it, label)) }
            val same = decoded != null && values.indices.all { values[it] == decoded.optString(it) }
                && (values.size until decoded.length()).all { decoded.optString(it).isEmpty() }
            when {
                values.all { it.isEmpty() } -> staged.remove(key)
                same && SecretBox.isSealed(old) -> staged.putString(key, old)
                else -> staged.putString(key, Secrets.box.seal(JSONArray(values).toString(), label))
            }
        }
        stage("favorites", PASSWORDS, list.map { it.password })
        stage("favoriteChannels", CHANNEL_PASSWORDS, list.map { it.channelPassword })
        // Keep the old pair available until the metadata and its new slot are published together.
        if (!staged.commit()) return false
        if (!p.edit().putString("list", a.toString()).putBoolean(SECRET_SLOT, next).commit()) {
            // commit updates memory even when the disk write fails. Restore the previous selection and keep both slots.
            p.edit().putString("list", previousList).putBoolean(SECRET_SLOT, slot).commit()
            return false
        }
        secrets.edit().apply { oldKeys.forEach { remove(it) } }.apply()
        return true
    }

    /** The favourites by folder: the folders in the order their first favourite comes, each one's favourites in theirs. */
    fun byFolder(list: List<Favorite>): List<Pair<String, List<Favorite>>> = list.groupBy { it.folder }.toList()

    /** A favourite edited: in its place, or last when it went to another folder (a new folder then comes last). */
    fun edited(list: List<Favorite>, old: Favorite, new: Favorite): List<Favorite> {
        // At most one connects automatically
        val others = if (new.auto) list.map { if (it == old) it else it.copy(auto = false) } else list
        return if (new.folder == old.folder) others.map { if (it == old) new else it } else others.filter { it != old } + new
    }

    /** A folder renamed; a name another folder has joins the two (the favourites keep their order). */
    fun renamed(list: List<Favorite>, folder: String, name: String): List<Favorite> {
        val to = name.trim()
        return list.map { if (it.folder == folder) it.copy(folder = to) else it }
    }

    /**
     * A favourite dragged to place [to] among [folder]'s other favourites: in its own folder, or into another one (a
     * folder its last favourite left is gone). The folders keep their order.
     */
    fun moved(list: List<Favorite>, f: Favorite, folder: String, to: Int): List<Favorite> {
        val at = list.indexOf(f)
        if (at < 0) return list
        // By folder, so a folder's place doesn't depend on which of its favourites is left
        val folders = byFolder(list).map { it.first to it.second.toMutableList() }
        folders.first { it.first == f.folder }.second.remove(f)
        val into = folders.firstOrNull { it.first == folder }?.second ?: return list
        into.add(to.coerceIn(0, into.size), f.copy(folder = folder))
        return folders.flatMap { it.second }
    }

    /** A folder moved one place up or down among the folders. */
    fun folderMoved(list: List<Favorite>, folder: String, up: Boolean): List<Favorite> {
        val folders = byFolder(list).toMutableList()
        val from = folders.indexOfFirst { it.first == folder }
        val to = if (up) from - 1 else from + 1
        if (from < 0 || to !in folders.indices) return list
        folders.add(to, folders.removeAt(from))
        return folders.flatMap { it.second }
    }

    /** The folders folded shut on the connect screen. */
    fun folded(c: Context): Set<String> = prefs(c).getStringSet("folded", emptySet()).orEmpty().toSet()

    fun setFolded(c: Context, folders: Set<String>) = prefs(c).edit().putStringSet("folded", folders).apply()

    /** The same server, however it was typed ("Example.com:9987" is "example.com"). */
    fun same(a: String, b: String): Boolean = normal(a) == normal(b)

    private fun normal(s: String) = s.trim().lowercase().removePrefix("ts3server://").removeSuffix("/").removeSuffix(":9987")
}
