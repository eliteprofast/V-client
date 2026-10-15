package com.vclient.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.vclient.android.data.Endpoint
import com.vclient.android.ui.Panel
import com.vclient.android.ui.SectionLabel

/** Launch Minecraft itself, and join VSMP or any server the player added. */
@Composable
fun ServersPanel(viewModel: MainViewModel, onMessage: (String?, Boolean) -> Unit) {
    val context = LocalContext.current
    var adding by remember { mutableStateOf(false) }

    fun join(name: String, endpoint: Endpoint) {
        viewModel.reportJoin()
        when (Minecraft.joinBedrock(context, name, endpoint)) {
            Minecraft.Result.Launched -> onMessage(null, false)
            Minecraft.Result.NotInstalled -> onMessage("Minecraft isn't installed on this phone.", true)
        }
    }

    var showJavaHelp by remember { mutableStateOf(false) }
    Panel {
        SectionLabel("Minecraft")
        Text(
            "Real: Bedrock Edition from the Play Store, signed in with your Microsoft account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                if (Minecraft.launchApp(context, Minecraft.PACKAGE)) onMessage(null, false)
                else onMessage("Minecraft isn't installed on this phone.", true)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Launch Minecraft (Bedrock)") }

        val javaLauncher = Minecraft.JAVA_LAUNCHERS.firstOrNull { Minecraft.isInstalled(context, it.first) }
        Text(
            "Offline: Java Edition through PojavLauncher or MojoLauncher, which support offline (local) accounts and Fabric mods.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { if (javaLauncher != null) Minecraft.launchApp(context, javaLauncher.first) else showJavaHelp = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (javaLauncher != null) "Open ${javaLauncher.second} (Java Edition)" else "Set up Java Edition (offline)") }
        if (javaLauncher != null) TextButton(onClick = { showJavaHelp = true }) { Text("How to set it up") }
    }
    if (showJavaHelp) JavaHelpDialog(context) { showJavaHelp = false }

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { SectionLabel("Join a server") }
            IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, contentDescription = "Add server") }
        }
        ServerRow(viewModel.serverConfig.name, viewModel.serverConfig.bedrock.address, onRemove = null) {
            join(viewModel.serverConfig.name, viewModel.serverConfig.bedrock)
        }
        viewModel.servers.forEach { server ->
            ServerRow(server.name, server.endpoint.address, onRemove = { viewModel.removeServer(server) }) {
                join(server.name, server.endpoint)
            }
        }
    }

    if (adding) AddServerDialog(viewModel) { adding = false }
}

@Composable
private fun JavaHelpDialog(context: android.content.Context, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Java Edition on Android") },
        text = {
            Text(
                "Java Edition can't run inside V-Client, so it runs through a community launcher:\n\n" +
                    "1. Install PojavLauncher (or MojoLauncher).\n" +
                    "2. Add an account: a Microsoft account for the real game, or a local account for offline play.\n" +
                    "3. Install a version (for example 1.21.11), optionally with Fabric to use mods.\n" +
                    "4. Press play. Next time V-Client opens it for you from this screen.\n\n" +
                    "Offline accounts only work on offline-mode servers and singleplayer.",
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
        dismissButton = {
            TextButton(onClick = {
                Minecraft.openWebLink(context, "https://github.com/PojavLauncherTeam/PojavLauncher")
            }) { Text("Get PojavLauncher") }
        },
    )
}

@Composable
private fun ServerRow(name: String, address: String, onRemove: (() -> Unit)?, onJoin: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = onJoin) { Text("Join") }
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove $name") }
        }
    }
}

@Composable
private fun AddServerDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name (optional)") })
                OutlinedTextField(
                    value = address, onValueChange = { address = it; error = null }, singleLine = true,
                    label = { Text("Address, e.g. play.example.com:19132") },
                    isError = error != null, supportingText = error?.let { { Text(it) } },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = viewModel.addServer(name, address)
                if (error == null) onDismiss()
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
