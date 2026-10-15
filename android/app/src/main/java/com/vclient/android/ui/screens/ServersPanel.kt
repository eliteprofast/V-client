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

    Panel {
        SectionLabel("Minecraft")
        Button(
            onClick = {
                if (Minecraft.launchApp(context, Minecraft.PACKAGE)) onMessage(null, false)
                else onMessage("Minecraft isn't installed on this phone.", true)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Launch Minecraft (Bedrock)") }

        val javaLauncher = Minecraft.JAVA_LAUNCHERS.firstOrNull { Minecraft.isInstalled(context, it.first) }
        OutlinedButton(
            onClick = { javaLauncher?.let { Minecraft.launchApp(context, it.first) } },
            enabled = javaLauncher != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (javaLauncher != null) "Open ${javaLauncher.second} (Java Edition)" else "Java Edition: no Android Java launcher installed") }
        if (javaLauncher == null) {
            Text(
                "Java Edition doesn't run natively on Android. Install PojavLauncher or MojoLauncher and V-Client will open it from here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

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
