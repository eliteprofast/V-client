package com.vclient.android

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.vclient.android.data.Endpoint

/** Hands the player over to Minecraft: Bedrock Edition is the only edition that exists on Android. */
object Minecraft {
    const val PACKAGE = "com.mojang.minecraftpe"

    sealed interface Result {
        data object Launched : Result
        data object NotInstalled : Result
    }

    /**
     * Opens Minecraft and connects straight to [server] with a `minecraft://connect` link. Older builds that don't
     * handle it fall back to the add-server link, so the player always ends up with VSMP in their server list.
     */
    fun joinBedrock(context: Context, name: String, server: Endpoint): Result {
        val connect = Uri.parse("minecraft://connect/?serverUrl=${Uri.encode(server.host)}&serverPort=${server.port}")
        if (open(context, connect)) return Result.Launched
        val add = Uri.parse("minecraft://?addExternalServer=${Uri.encode("$name|${server.address}")}")
        if (open(context, add)) return Result.Launched
        return Result.NotInstalled
    }

    fun openStorePage(context: Context) {
        val market = Uri.parse("market://details?id=$PACKAGE")
        if (!open(context, market)) open(context, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE"))
    }

    fun copy(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    /** Opens http(s) links only; used for the Discord and website buttons whose URLs come from the backend. */
    fun openWebLink(context: Context, url: String): Boolean {
        val uri = Uri.parse(url)
        if (uri.scheme != "https" && uri.scheme != "http") return false
        return open(context, uri)
    }

    private fun open(context: Context, uri: Uri): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
