package com.vclient.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Minecraft Java release list from Mojang's public version manifest. */
object JavaVersions {
    private const val MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

    /** Shown when the manifest can't be reached, so the selector is never empty. */
    val FALLBACK = listOf("1.21.11", "1.21.4", "1.21.1", "1.20.6", "1.20.4", "1.20.1", "1.19.4", "1.18.2", "1.16.5", "1.12.2", "1.8.9")

    /** Release ids from a version manifest, newest first, snapshots and old betas left out. */
    fun parseReleases(manifestJson: String): List<String> {
        val versions = JSONObject(manifestJson).getJSONArray("versions")
        return List(versions.length()) { versions.getJSONObject(it) }
            .filter { it.optString("type") == "release" }
            .map { it.getString("id") }
    }

    suspend fun load(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(MANIFEST).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 6_000
                connection.readTimeout = 10_000
                val releases = parseReleases(connection.inputStream.bufferedReader().use { it.readText() })
                releases.ifEmpty { FALLBACK }
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(FALLBACK)
    }
}
