package com.vclient.android

import android.app.Application
import android.content.pm.PackageInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.vclient.android.data.PlayMode
import com.vclient.android.ui.screens.AccountScreen
import com.vclient.android.ui.screens.HomeScreen
import com.vclient.android.ui.screens.NewsScreen
import com.vclient.android.ui.screens.ServersScreen
import com.vclient.android.ui.screens.SettingsScreen
import com.vclient.android.ui.theme.VClientTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Renders the real screens to PNG files (build/screenshots) so they can be looked at without a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1500dp-xhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    /** The app draws inside a Scaffold that supplies the page background and text color; do the same here. */
    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        VClientTheme {
            androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) { content() }
        }
    }

    private fun save(name: String) {
        val file = File("build/screenshots/$name.png").also { it.parentFile.mkdirs() }
        val width = 822
        val height = 3000
        val view = compose.activity.window.decorView
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.parseColor("#080B16"))
        view.draw(android.graphics.Canvas(bitmap))
        // Trim the empty space under the content (rows that are still pure background).
        val background = android.graphics.Color.parseColor("#080B16")
        var lastRow = height - 1
        val row = IntArray(width)
        while (lastRow > 0) {
            bitmap.getPixels(row, 0, width, 0, lastRow, width, 1)
            if (row.any { it != background }) break
            lastRow--
        }
        val cropped = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, width, minOf(height, lastRow + 48))
        FileOutputStream(file).use { cropped.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("$name.png was written", file.length() > 5_000)
    }

    private fun install(pkg: String, version: String) {
        shadowOf(app.packageManager).installPackage(PackageInfo().apply { packageName = pkg; versionName = version })
    }

    @Test
    fun homeNothingInstalled() {
        val vm = MainViewModel(app)
        show { HomeScreen(vm, onOpenServers = {}) }
        compose.waitForIdle()
        save("1-home-bedrock-preferred-nothing-installed")
    }

    @Test
    fun homeJavaPreferredBothInstalled() {
        install(Minecraft.JAVA_PACKAGE, "3.4.5")
        install(Minecraft.PACKAGE, "1.21.50")
        val vm = MainViewModel(app)
        vm.updatePlayMode(PlayMode.JAVA)
        show { HomeScreen(vm, onOpenServers = {}) }
        compose.waitForIdle()
        save("2-home-java-preferred-both-installed")
    }

    @Test
    fun serversScreen() {
        install(Minecraft.PACKAGE, "1.21.50")
        val vm = MainViewModel(app)
        vm.addServer("Friend's server", "friend.example.com:19133")
        show { ServersScreen(vm) }
        compose.waitForIdle()
        save("3-bedrock-server-settings")
    }

    @Test
    fun demoWorldInGame() {
        compose.mainClock.autoAdvance = false
        show { com.vclient.android.ui.screens.DemoScreen(onExit = {}) }
        compose.onNodeWithText("V-Client Demo World").assertExists()
        save("7-demo-welcome")
        compose.onNodeWithText("Start exploring").performClick()
        // The renderer works on a background thread, so give real time between simulated frames.
        repeat(25) { compose.mainClock.advanceTimeBy(50); Thread.sleep(40) }
        save("8-demo-in-game")
    }

    @Test
    fun accountScreen() {
        val vm = MainViewModel(app)
        show { AccountScreen(vm) }
        compose.waitForIdle()
        save("4-account")
    }

    @Test
    fun settingsScreen() {
        val vm = MainViewModel(app)
        show { SettingsScreen(vm) }
        compose.waitForIdle()
        save("5-settings")
    }

    @Test
    fun newsScreen() {
        val vm = MainViewModel(app)
        show { NewsScreen(vm) }
        compose.waitForIdle()
        save("6-news")
    }
}
