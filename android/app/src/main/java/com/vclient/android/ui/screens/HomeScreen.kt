package com.vclient.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vclient.android.MainViewModel
import com.vclient.android.Minecraft
import com.vclient.android.data.PlayMode
import com.vclient.android.ui.AnnouncementBanner
import com.vclient.android.ui.Panel
import com.vclient.android.ui.VLogo
import com.vclient.android.ui.theme.Offline

@Composable
fun HomeScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mode = viewModel.playMode
    var message by remember { mutableStateOf<String?>(null) }
    var offerStore by remember { mutableStateOf(false) }
    var showJavaHelp by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VLogo(44.dp)
            Column {
                Text("V-Client", style = MaterialTheme.typography.headlineMedium)
                Text("Minecraft launcher", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        viewModel.announcements.forEach { AnnouncementBanner(it) }

        viewModel.update?.let { info ->
            val release = info.latest ?: return@let
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Update ${release.version} available", style = MaterialTheme.typography.titleMedium)
                        if (info.mandatory) Text("Required to keep using V-Client", style = MaterialTheme.typography.bodyMedium, color = Offline)
                    }
                }
                if (release.notes.isNotBlank()) Text(release.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val progress = viewModel.updateProgress
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.installUpdate(context) }) { Text("Update") }
                        if (!info.mandatory) TextButton(onClick = viewModel::dismissUpdate) { Text("Later") }
                    }
                }
            }
        }

        // One PLAY button. Premium opens the real Bedrock app; Cracked opens Java Edition in an offline-capable launcher.
        Button(
            onClick = {
                message = null
                offerStore = false
                when (mode) {
                    PlayMode.PREMIUM ->
                        if (Minecraft.launchApp(context, Minecraft.PACKAGE)) viewModel.reportJoin()
                        else {
                            message = "Minecraft isn't installed on this phone."
                            offerStore = true
                        }
                    PlayMode.OFFLINE -> {
                        val launcher = Minecraft.JAVA_LAUNCHERS.firstOrNull { Minecraft.isInstalled(context, it.first) }
                        if (launcher != null) {
                            viewModel.reportJoin()
                            Minecraft.launchApp(context, launcher.first)
                        } else {
                            showJavaHelp = true
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("PLAY", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            when (mode) {
                PlayMode.PREMIUM -> "Premium: opens Minecraft (Bedrock) from the Play Store."
                PlayMode.OFFLINE -> "Cracked: opens Java Edition in PojavLauncher or MojoLauncher with an offline account."
            } + " Change this in the Account tab.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        message?.let {
            Panel {
                Text(it, style = MaterialTheme.typography.bodyMedium)
                if (offerStore) OutlinedButton(onClick = { Minecraft.openStorePage(context) }) { Text("Get Minecraft") }
            }
        }
        if (showJavaHelp) JavaHelpDialog(context) { showJavaHelp = false }

        // Bedrock can join any server by address; Java launchers pick servers inside the game.
        if (mode == PlayMode.PREMIUM) {
            ServersPanel(viewModel) { text, missing -> message = text; offerStore = missing }
        }

        val config = viewModel.serverConfig
        if (config.discordUrl.isNotBlank() || viewModel.backendConfigured) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { Minecraft.openWebLink(context, config.discordUrl) },
                    enabled = config.discordUrl.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text("Discord") }
                OutlinedButton(
                    onClick = { Minecraft.openWebLink(context, config.websiteUrl.ifBlank { viewModel.backendUrl }) },
                    enabled = viewModel.backendConfigured,
                    modifier = Modifier.weight(1f),
                ) { Text("Website") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
