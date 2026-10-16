package com.vclient.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vclient.android.MainViewModel
import com.vclient.android.Minecraft
import com.vclient.android.ui.Panel
import com.vclient.android.ui.SectionLabel
import com.vclient.android.ui.theme.Offline

/** Bedrock server settings: the VSMP address and any other servers the player saved. */
@Composable
fun ServersScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var address by remember(viewModel.vsmpAddress) { mutableStateOf(viewModel.vsmpAddress) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var offerStore by remember { mutableStateOf(false) }
    val name = viewModel.serverConfig.name

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Bedrock servers", style = MaterialTheme.typography.headlineMedium)

        Panel {
            SectionLabel("$name connection")
            Text(
                "Used by the one-tap Join button. Leave empty to use the server's own address (${viewModel.serverConfig.bedrock.address}).",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = address,
                onValueChange = { address = it; error = null; saved = false },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Address (host:port)") },
                placeholder = { Text(viewModel.serverConfig.bedrock.address) },
                isError = error != null,
                supportingText = (error ?: if (saved) "Saved." else null)?.let { { Text(it) } },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    error = viewModel.saveVsmpAddress(address)
                    saved = error == null
                }) { Text("Save") }
                OutlinedButton(onClick = {
                    viewModel.saveVsmpAddress("")
                    address = ""
                    error = null
                    saved = true
                }) { Text("Reset") }
            }
            Button(
                onClick = {
                    viewModel.reportJoin()
                    if (Minecraft.joinBedrock(context, name, viewModel.vsmp) == Minecraft.Result.NotInstalled) {
                        message = "Minecraft isn't installed on this phone."
                        offerStore = true
                    } else {
                        message = null
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Join $name  ·  ${viewModel.vsmp.address}") }
        }

        message?.let {
            Panel {
                Text(it, color = Offline, style = MaterialTheme.typography.bodyMedium)
                if (offerStore) OutlinedButton(onClick = { Minecraft.openStorePage(context) }) { Text("Get Minecraft") }
            }
        }

        ServersPanel(viewModel) { text, missing -> message = text; offerStore = missing }
    }
}
