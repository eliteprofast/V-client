package com.vclient.android

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.vclient.android.ui.screens.DEMO_NOTICE
import com.vclient.android.ui.screens.DemoScreen
import com.vclient.android.ui.screens.HomeScreen
import com.vclient.android.ui.theme.VClientTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DemoUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun homeOffersThePlayDemoWorldButtonWithTheRequiredMessage() {
        var opened = 0
        compose.setContent {
            VClientTheme { HomeScreen(MainViewModel(app), onOpenServers = {}, onPlayDemo = { opened++ }) }
        }
        assertEquals("This is a V-Client Demo World. Install Minecraft separately for the full game experience.", DEMO_NOTICE)
        compose.onNodeWithText(DEMO_NOTICE).performScrollTo().assertExists()
        compose.onNodeWithText("Play Demo World").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun demoWorldShowsTheNoticeStartsAndSavesWhenLeaving() {
        compose.mainClock.autoAdvance = false // the game loop asks for a new frame forever; step time by hand
        var exited = 0
        compose.setContent { VClientTheme { DemoScreen(onExit = { exited++ }) } }

        compose.onNodeWithText("V-Client Demo World").assertExists()
        // The notice is in the always-visible banner and again in the welcome dialog.
        compose.onAllNodesWithText(DEMO_NOTICE, substring = true).assertCountEquals(2)
        compose.onNodeWithText("Start exploring").performClick()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("Start exploring").assertDoesNotExist()
        compose.onAllNodesWithText(DEMO_NOTICE, substring = true).assertCountEquals(1) // banner stays
        compose.onNodeWithText("Exit").assertExists()

        compose.onNodeWithText("Exit").performClick()
        assertEquals(1, exited)
        val saved = File(app.filesDir, "demo_world.vcw")
        assertTrue("world saved on the device: ${saved.length()} bytes", saved.length() > 1_000)
    }
}
