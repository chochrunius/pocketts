package io.github.korppujauho.pocketts

import android.content.Context
import io.github.korppujauho.pocketts.proto.JoinLink
import org.json.JSONObject

data class ChannelPreference(val remember: Boolean = false, val defaultChannel: String = "", val lastChannel: String = "") {
    // A link chooses the initial channel; after joining, reconnect follows actual moves when enabled.
    fun channel(explicit: String = "", reconnecting: Boolean = false): String =
        explicit.takeUnless { reconnecting && remember }.orEmpty().ifBlank {
        lastChannel.takeIf { remember }.orEmpty().ifBlank { defaultChannel }
    }
}

internal data class ConnectionChannel(
    val path: String, val password: String, val id: Int = 0, val explicit: Boolean = false,
)

/** A requested link or favourite wins on first join. A remembered channel wins on reconnect. */
internal fun connectionChannel(
    prefs: ChannelPreference, requested: String, password: String, id: Int, reconnecting: Boolean,
    savedPassword: (Int) -> String,
): ConnectionChannel {
    val path = prefs.channel(requested, reconnecting)
    val explicit = requested.isNotBlank() && !(reconnecting && prefs.remember)
    if (explicit) return ConnectionChannel(path, password, id, explicit = true)
    val channelId = path.takeIf { it.startsWith("/") }?.drop(1)?.toIntOrNull() ?: 0
    val rememberedPassword = if (channelId > 0) savedPassword(channelId) else ""
    val sameChannel = (id > 0 && id == channelId) || (path.isNotEmpty() && path == requested)
    return ConnectionChannel(path, rememberedPassword.ifEmpty { password.takeIf { sameChannel }.orEmpty() }, channelId)
}

/** Independent of credentials; older installations simply have no channel preference. */
class ChannelPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("channelPreferences", Context.MODE_PRIVATE)
    private fun key(address: String): String = (JoinLink.parse(address)?.address ?: address)
        .trim().lowercase(java.util.Locale.ROOT).removeSuffix(":9987")
    fun load(address: String): ChannelPreference {
        val o = JSONObject(prefs.getString(key(address), "{}") ?: "{}")
        return ChannelPreference(o.optBoolean("remember"), o.optString("default"), o.optString("last"))
    }
    fun save(address: String, value: ChannelPreference) {
        prefs.edit().putString(key(address), JSONObject().put("remember", value.remember)
            .put("default", value.defaultChannel).put("last", value.lastChannel).toString()).apply()
    }

    internal fun initializeDefault(address: String): Boolean =
        prefs.contains(key(address)) || prefs.edit().putString(key(address),
            JSONObject().put("remember", true).put("default", "").put("last", "").toString()).commit()
    fun joined(address: String, path: String) {
        val old = load(address)
        if (old.remember && path.isNotEmpty() && old.lastChannel != path) save(address, old.copy(lastChannel = path))
    }
}
