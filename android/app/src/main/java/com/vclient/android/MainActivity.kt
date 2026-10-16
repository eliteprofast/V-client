package com.vclient.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.vclient.android.ui.screens.AccountScreen
import com.vclient.android.ui.screens.DemoScreen
import com.vclient.android.ui.screens.HomeScreen
import com.vclient.android.ui.screens.NewsScreen
import com.vclient.android.ui.screens.ServersScreen
import com.vclient.android.ui.screens.SettingsScreen
import com.vclient.android.ui.theme.VClientTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            VClientTheme { VClientApp(viewModel) }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Servers("Servers", Icons.Filled.Dns),
    News("News", Icons.Filled.Newspaper),
    Account("Account", Icons.Filled.AccountCircle),
    Settings("Settings", Icons.Filled.Settings),
}

@Composable
private fun VClientApp(viewModel: MainViewModel) {
    var selected by rememberSaveable { mutableStateOf(Tab.Home) }
    var demoOpen by rememberSaveable { mutableStateOf(false) }
    if (demoOpen) {
        DemoScreen(onExit = { demoOpen = false })
        return
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        val content = Modifier.padding(padding)
        when (selected) {
            Tab.Home -> HomeScreen(viewModel, onOpenServers = { selected = Tab.Servers }, modifier = content, onPlayDemo = { demoOpen = true })
            Tab.Servers -> ServersScreen(viewModel, content)
            Tab.News -> NewsScreen(viewModel, content)
            Tab.Account -> AccountScreen(viewModel, content)
            Tab.Settings -> SettingsScreen(viewModel, content)
        }
    }
}
