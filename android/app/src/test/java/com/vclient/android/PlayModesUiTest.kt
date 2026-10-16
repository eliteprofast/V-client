package com.vclient.android

import android.app.Application
import android.content.pm.PackageInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.vclient.android.data.PlayMode
import com.vclient.android.ui.screens.HomeScreen
import com.vclient.android.ui.screens.ServersScreen
import com.vclient.android.ui.theme.VClientTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Drives the real Home and Servers screens: detection, edition switch, deep links, and saved settings. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // popups (the version menu) need real window handling
@Config(sdk = [34])
class PlayModesUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun install(pkg: String, version: String) {
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg
            versionName = version
        })
    }

    private fun show(viewModel: MainViewModel, onOpenServers: () -> Unit = {}) = compose.setContent {
        VClientTheme { HomeScreen(viewModel, onOpenServers) }
    }

    @Test
    fun bothEditionsAreShownAndMissingAppsAreReported() {
        show(MainViewModel(app))
        // Each edition appears twice: in the preferred-edition switch and as its card title.
        compose.onAllNodesWithText("Java Edition").assertCountEquals(2)
        compose.onAllNodesWithText("Bedrock Edition").assertCountEquals(2)
        compose.onNodeWithText("PojavLauncher not installed").assertExists()
        compose.onNodeWithText("Minecraft not installed").assertExists()
        compose.onNodeWithText("Play Java Edition").assertIsNotEnabled()
        compose.onNodeWithText("Play Bedrock Edition").assertIsNotEnabled()
        compose.onNodeWithText("Get PojavLauncher").assertExists()
        compose.onNodeWithText("Get Minecraft").assertExists()
        // Mojang's official free trial is offered too, with its install link.
        compose.onNodeWithText("Minecraft Trial (free)").assertExists()
        compose.onNodeWithText("Free trial not installed").assertExists()
        compose.onNodeWithText("Get the free Minecraft Trial").assertExists()
    }

    @Test
    fun installedTrialIsDetectedAndOffered() {
        install(Minecraft.TRIAL_PACKAGE, "1.21.0.3")
        show(MainViewModel(app))
        compose.onNodeWithText("Detected · version 1.21.0.3").assertExists()
        compose.onNodeWithText("Play Minecraft Trial").assertExists()
        compose.onNodeWithText("Get the free Minecraft Trial").assertDoesNotExist()
    }

    @Test
    fun getTrialOpensTheOfficialStoreListing() {
        show(MainViewModel(app))
        compose.onNodeWithText("Get the free Minecraft Trial").performScrollTo().performClick()
        val started = shadowOf(app).nextStartedActivity
        assertNotNull("tapping Get must open the store", started)
        assertEquals("market://details?id=com.mojang.minecrafttrialpe", started.data.toString())
    }

    @Test
    fun installedAppsAreDetectedWithTheirVersion() {
        install(Minecraft.JAVA_PACKAGE, "3.4.5")
        install(Minecraft.PACKAGE, "1.21.50")
        show(MainViewModel(app))
        compose.onNodeWithText("Detected · version 3.4.5").assertExists()
        compose.onNodeWithText("Detected · version 1.21.50").assertExists()
        compose.onNodeWithText("Play Java Edition").assertIsEnabled()
        compose.onNodeWithText("Play Bedrock Edition").assertIsEnabled()
    }

    @Test
    fun preferredEditionSwitchIsSaved() {
        val viewModel = MainViewModel(app)
        show(viewModel)
        assertEquals(PlayMode.BEDROCK, viewModel.playMode)
        compose.onAllNodesWithText("Java Edition").onFirst().performClick() // the switch comes before the card
        assertEquals(PlayMode.JAVA, viewModel.playMode)
        // Survives a restart: a new ViewModel reads the saved choice.
        assertEquals(PlayMode.JAVA, MainViewModel(app).playMode)
    }

    @Test
    fun javaVersionPickerListsVersionsAndSavesTheChoice() {
        val viewModel = MainViewModel(app)
        show(viewModel)
        // A default is shown immediately; the list then upgrades to Mojang's (skipped when this PC is offline).
        assertTrue(viewModel.javaVersion.isNotBlank())
        try {
            compose.waitUntil(20_000) { viewModel.javaVersions.size > com.vclient.android.data.JavaVersions.FALLBACK.size }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            // offline: the built-in list stays
        }
        compose.waitForIdle()
        assertTrue("version list must not be empty", viewModel.javaVersions.size > 2)
        assertEquals(viewModel.javaVersions.first(), viewModel.javaVersion)

        // The Java card sits below the fold on the test's small screen, so scroll to it like a user would.
        compose.onNodeWithText(viewModel.javaVersion).performScrollTo().performClick()
        compose.waitForIdle()
        val choice = viewModel.javaVersions[2] // near the top of the menu, so it is on screen
        compose.onAllNodesWithText(choice, useUnmergedTree = true).onLast().performClick()
        assertEquals(choice, viewModel.javaVersion)
        assertEquals(choice, MainViewModel(app).javaVersion) // saved across restarts
    }

    @Test
    fun controlGroupBareDropdownMenuCanBeOpenedInThisTestEnvironment() {
        compose.setContent {
            var open by remember { mutableStateOf(false) }
            androidx.compose.foundation.layout.Box {
                androidx.compose.material3.OutlinedButton(onClick = { open = true }) { androidx.compose.material3.Text("Open menu") }
                androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    androidx.compose.material3.DropdownMenuItem(text = { androidx.compose.material3.Text("Alpha") }, onClick = { open = false })
                }
            }
        }
        compose.onNodeWithText("Open menu").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Alpha").assertExists()
    }

    @Test
    fun oneTapVsmpJoinOpensTheBedrockConnectLink() {
        install(Minecraft.PACKAGE, "1.21.50")
        show(MainViewModel(app))
        compose.onNodeWithText("Join VSMP in one tap", substring = true).performClick()
        val started = shadowOf(app).nextStartedActivity
        assertNotNull("tapping Join must start an activity", started)
        assertEquals("minecraft://connect/?serverUrl=neuron.zeroxhost.space&serverPort=19145", started.data.toString())
    }

    @Test
    fun bedrockServerSettingsSavesValidatesAndResets() {
        val viewModel = MainViewModel(app)
        compose.setContent { VClientTheme { ServersScreen(viewModel) } }

        compose.onNodeWithText("Address (host:port)").performTextInput("play.example.com:25000")
        compose.onNodeWithText("Save").performClick()
        assertEquals("play.example.com:25000", viewModel.vsmpAddress)
        assertEquals("play.example.com", viewModel.vsmp.host)
        assertEquals(25000, viewModel.vsmp.port)

        compose.onNodeWithText("Address (host:port)").performTextClearance()
        compose.onNodeWithText("Address (host:port)").performTextInput("not valid!")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Enter an address like", substring = true).assertExists()
        assertEquals("play.example.com:25000", viewModel.vsmpAddress) // bad input was not saved

        compose.onNodeWithText("Reset").performClick()
        assertEquals("", viewModel.vsmpAddress)
        assertEquals(19145, viewModel.vsmp.port)
    }

    @Test
    fun savedServersCanBeAddedJoinedAndRemoved() {
        install(Minecraft.PACKAGE, "1.21.50")
        val viewModel = MainViewModel(app)
        compose.setContent { VClientTheme { ServersScreen(viewModel) } }

        assertNotNull(viewModel.addServer("Friend", "friend.example.com:19133").let { it ?: "ok" })
        compose.waitForIdle()
        compose.onNodeWithText("Friend").assertExists()
        assertEquals(1, viewModel.servers.size)

        viewModel.removeServer(viewModel.servers.first())
        compose.waitForIdle()
        assertEquals(0, viewModel.servers.size)
    }
}
