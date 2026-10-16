package com.vclient.android.data

import android.content.Context
import com.vclient.android.BuildConfig
import org.json.JSONObject
import java.util.UUID

/** Small local store for settings, the sign-in token and the last known server details. */
class Prefs(context: Context) {
    private val store = context.getSharedPreferences("vclient", Context.MODE_PRIVATE)

    var backendUrl: String
        get() = store.getString(KEY_BACKEND, null)?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_BACKEND_URL
        set(value) = store.edit().putString(KEY_BACKEND, value).apply()

    var token: String
        get() = store.getString(KEY_TOKEN, "").orEmpty()
        set(value) = store.edit().putString(KEY_TOKEN, value).apply()

    var autoUpdate: Boolean
        get() = store.getBoolean(KEY_AUTO_UPDATE, true)
        set(value) = store.edit().putBoolean(KEY_AUTO_UPDATE, value).apply()

    /** Random id used for anonymous usage counts. It is not derived from the phone or the player. */
    val installId: String
        get() = store.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also {
            store.edit().putString(KEY_INSTALL_ID, it).apply()
        }

    var serverConfig: ServerConfig
        get() = runCatching {
            val json = JSONObject(store.getString(KEY_SERVER, "") ?: "")
            ServerConfig(
                name = json.getString("name"),
                java = Endpoint(json.getString("javaHost"), json.getInt("javaPort")),
                bedrock = Endpoint(json.getString("bedrockHost"), json.getInt("bedrockPort")),
                discordUrl = json.optString("discordUrl"),
                websiteUrl = json.optString("websiteUrl"),
            )
        }.getOrDefault(ServerConfig())
        set(value) = store.edit().putString(
            KEY_SERVER,
            JSONObject().put("name", value.name)
                .put("javaHost", value.java.host).put("javaPort", value.java.port)
                .put("bedrockHost", value.bedrock.host).put("bedrockPort", value.bedrock.port)
                .put("discordUrl", value.discordUrl).put("websiteUrl", value.websiteUrl)
                .toString(),
        ).apply()

    /** Servers the player added; VSMP itself comes from [serverConfig] and is always listed first. */
    var savedServers: List<SavedServer>
        get() = runCatching {
            val array = org.json.JSONArray(store.getString(KEY_SAVED, "[]"))
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                SavedServer(o.getString("name"), Endpoint(o.getString("host"), o.getInt("port")))
            }
        }.getOrDefault(emptyList())
        set(value) = store.edit().putString(
            KEY_SAVED,
            org.json.JSONArray(value.map {
                JSONObject().put("name", it.name).put("host", it.endpoint.host).put("port", it.endpoint.port)
            }).toString(),
        ).apply()

    var playMode: PlayMode
        get() = if (store.getString(KEY_MODE, "") == PlayMode.OFFLINE.key) PlayMode.OFFLINE else PlayMode.PREMIUM
        set(value) = store.edit().putString(KEY_MODE, value.key).apply()

    private companion object {
        const val KEY_MODE = "playMode"
        const val KEY_SAVED = "savedServers"
        const val KEY_BACKEND = "backendUrl"
        const val KEY_TOKEN = "token"
        const val KEY_AUTO_UPDATE = "autoUpdate"
        const val KEY_INSTALL_ID = "installId"
        const val KEY_SERVER = "server"
    }
}
