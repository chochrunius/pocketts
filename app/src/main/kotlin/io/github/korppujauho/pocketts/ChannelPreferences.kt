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
    fun joined(address: String, path: String) {
        val old = load(address)
        if (old.remember && path.isNotEmpty() && old.lastChannel != path) save(address, old.copy(lastChannel = path))
    }
}
