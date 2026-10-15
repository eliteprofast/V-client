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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.vclient.android.ui.AnnouncementBanner
import com.vclient.android.ui.Panel
import com.vclient.android.ui.SectionLabel
import com.vclient.android.ui.StatusDot
import com.vclient.android.ui.VLogo
import com.vclient.android.ui.theme.Offline
import com.vclient.android.ui.theme.Online

@Composable
fun HomeScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val config = viewModel.serverConfig
    var message by remember { mutableStateOf<String?>(null) }
    var notInstalled by remember { mutableStateOf(false) }

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
                Text("The launcher for ${config.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

        // Server status
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val status = viewModel.status
                val online = status?.anyOnline == true
                StatusDot(if (online) Online else if (status == null) MaterialTheme.colorScheme.onSurfaceVariant else Offline)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    SectionLabel("${config.name} server")
                    Text(
                        when {
                            status == null && viewModel.statusError == null -> "Checking…"
                            status == null -> "Status unavailable"
                            online -> "Online · ${status.playersOnline} / ${status.playersMax} players"
                            else -> "Offline right now"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                IconButton(onClick = viewModel::refreshStatus, enabled = !viewModel.statusLoading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh server status")
                }
            }
            viewModel.statusError?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // One-tap join
        Button(
            onClick = {
                viewModel.reportJoin()
                when (Minecraft.joinBedrock(context, config.name, config.bedrock)) {
                    Minecraft.Result.Launched -> { message = null; notInstalled = false }
                    Minecraft.Result.NotInstalled -> {
                        notInstalled = true
                        message = "Minecraft isn't installed on this phone."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("PLAY ${config.name}", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "Opens Minecraft (Bedrock Edition) and connects to ${config.bedrock.address}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        message?.let {
            Panel {
                Text(it, style = MaterialTheme.typography.bodyMedium)
                if (notInstalled) OutlinedButton(onClick = { Minecraft.openStorePage(context) }) { Text("Get Minecraft") }
            }
        }

        // Java players can't run on Android, but can copy the address for their PC.
        Panel {
            SectionLabel("Server addresses")
            AddressRow("Java", config.java.address) { Minecraft.copy(context, "VSMP Java address", config.java.address) }
            AddressRow("Bedrock", config.bedrock.address) { Minecraft.copy(context, "VSMP Bedrock address", config.bedrock.address) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { Minecraft.openWebLink(context, config.discordUrl) },
                enabled = config.discordUrl.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(),
            ) { Text("Discord") }
            OutlinedButton(
                onClick = { Minecraft.openWebLink(context, config.websiteUrl.ifBlank { viewModel.backendUrl }) },
                enabled = viewModel.backendConfigured,
                modifier = Modifier.weight(1f),
            ) { Text("Website") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AddressRow(label: String, address: String, onCopy: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(address, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy $label address") }
    }
}
