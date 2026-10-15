package com.vclient.android.data

data class Endpoint(val host: String, val port: Int) {
    val address: String get() = "$host:$port"
}

data class SavedServer(val name: String, val endpoint: Endpoint)

/** VSMP connection details served by the backend, cached so the Play button works offline. */
data class ServerConfig(
    val name: String = "VSMP",
    val java: Endpoint = Endpoint("neuron.zeroxhost.space", 19145),
    val bedrock: Endpoint = Endpoint("neuron.zeroxhost.space", 19145),
    val discordUrl: String = "",
    val websiteUrl: String = "",
)

data class NewsPost(
    val id: Int,
    val title: String,
    val body: String,
    val category: String,
    val pinned: Boolean,
    val createdAt: String,
)

data class Announcement(val id: Int, val message: String, val level: String)

data class EditionStatus(
    val online: Boolean,
    val players: Int = 0,
    val maxPlayers: Int = 0,
    val version: String = "",
    val latencyMs: Long = 0,
)

data class ServerStatus(val java: EditionStatus, val bedrock: EditionStatus) {
    val anyOnline: Boolean get() = java.online || bedrock.online
    val playersOnline: Int get() = if (java.online) java.players else bedrock.players
    val playersMax: Int get() = if (java.online) java.maxPlayers else bedrock.maxPlayers
}

data class Release(
    val version: String,
    val notes: String,
    val mandatory: Boolean,
    val size: Long,
    val sha256: String,
    val url: String,
)

data class UpdateInfo(val updateAvailable: Boolean, val mandatory: Boolean, val latest: Release?)

data class Session(
    val token: String,
    val username: String,
    val minecraftName: String,
    val elybyName: String,
)
