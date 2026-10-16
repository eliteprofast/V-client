package com.vclient.android.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.vclient.android.demo.Block
import com.vclient.android.demo.DemoGame
import com.vclient.android.demo.Textures
import com.vclient.android.demo.VoxelRenderer
import com.vclient.android.ui.theme.Gold
import com.vclient.android.ui.theme.Midnight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

const val DEMO_NOTICE = "This is a V-Client Demo World. Install Minecraft separately for the full game experience."

/**
 * The offline Demo World: an original voxel sandbox with no Minecraft content. Left thumb walks, the right side
 * looks around, and the buttons jump, break and place blocks. The world is saved on the device automatically.
 */
@Composable
fun DemoScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val file = remember { File(context.filesDir, "demo_world.vcw") }
    val game = remember { DemoGame.loadOrCreate(file) }
    val renderer = remember { VoxelRenderer(game.world) }
    var showWelcome by rememberSaveable { mutableStateOf(true) }
    val welcomeOpen by rememberUpdatedState(showWelcome)
    var frameId by remember { mutableIntStateOf(0) }
    var fps by remember { mutableIntStateOf(0) }
    var slot by remember { mutableIntStateOf(game.selectedSlot) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }

    fun leave() {
        game.breakHeld = false; game.placeHeld = false; game.jumpHeld = false
        game.save(file, force = true)
        onExit()
    }
    BackHandler { leave() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { game.save(file) }
    DisposableEffect(Unit) { onDispose { game.save(file) } }

    Box(Modifier.fillMaxSize().background(Midnight)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val aspect = constraints.maxWidth.toFloat() / constraints.maxHeight.coerceAtLeast(1)
            val renderWidth = 288
            val renderHeight = (renderWidth / aspect).toInt().coerceIn(120, 420)

            // The game loop: advance the simulation, ray-cast a low-resolution frame, show it scaled up.
            LaunchedEffect(renderWidth, renderHeight) {
                val pixels = IntArray(renderWidth * renderHeight)
                val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                image = bitmap.asImageBitmap()
                var last = withFrameNanos { it }
                var frames = 0
                var clock = 0.0
                var sinceSave = 0.0
                while (isActive) {
                    val now = withFrameNanos { it }
                    val dt = ((now - last) / 1_000_000_000.0).coerceAtMost(0.05)
                    last = now
                    if (!welcomeOpen) game.tick(dt)
                    withContext(Dispatchers.Default) {
                        renderer.selected = game.target
                        renderer.render(pixels, renderWidth, renderHeight, game.camera())
                    }
                    bitmap.setPixels(pixels, 0, renderWidth, 0, 0, renderWidth, renderHeight)
                    frameId++
                    frames++
                    clock += dt
                    sinceSave += dt
                    if (clock >= 1.0) { fps = frames; frames = 0; clock = 0.0 }
                    if (sinceSave >= 20.0) { game.save(file); sinceSave = 0.0 }
                }
            }

            // The scene, scaled with hard edges so the blocks stay crisp.
            Canvas(Modifier.fillMaxSize()) {
                val current = frameId
                image?.let { drawImage(it, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.None) }
                if (current < 0) return@Canvas
                val c = Offset(size.width / 2, size.height / 2)
                val arm = 9.dp.toPx()
                drawLine(Color.White.copy(alpha = 0.85f), Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), strokeWidth = 2.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.85f), Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), strokeWidth = 2.dp.toPx())
            }

            // Look around: drag anywhere on the right side.
            Box(
                Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.55f).fillMaxHeight().pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        game.look(drag.x * 0.0045, -drag.y * 0.0045)
                    }
                },
            )

            // Walk: a floating stick on the left side.
            var stickCenter by remember { mutableStateOf<Offset?>(null) }
            var stickKnob by remember { mutableStateOf(Offset.Zero) }
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth(0.45f).fillMaxHeight(0.65f).pointerInput(Unit) {
                    val radius = 56.dp.toPx()
                    fun stop() { stickCenter = null; game.moveRight = 0.0; game.moveForward = 0.0 }
                    detectDragGestures(
                        onDragStart = { stickCenter = it; stickKnob = it },
                        onDragEnd = { stop() },
                        onDragCancel = { stop() },
                        onDrag = { change, _ ->
                            change.consume()
                            val origin = stickCenter ?: return@detectDragGestures
                            var d = change.position - origin
                            val length = d.getDistance()
                            if (length > radius) d = d * (radius / length)
                            stickKnob = origin + d
                            game.moveRight = (d.x / radius).toDouble()
                            game.moveForward = (-d.y / radius).toDouble()
                        },
                    )
                },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val origin = stickCenter ?: return@Canvas
                    drawCircle(Color.White.copy(alpha = 0.18f), radius = 56.dp.toPx(), center = origin)
                    drawCircle(Color.White.copy(alpha = 0.7f), radius = 56.dp.toPx(), center = origin, style = Stroke(2.dp.toPx()))
                    drawCircle(Gold.copy(alpha = 0.9f), radius = 24.dp.toPx(), center = stickKnob)
                }
            }

            // Action buttons.
            HoldButton("Jump", 76, Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 76.dp)) { game.jumpHeld = it }
            HoldButton("Break", 60, Modifier.align(Alignment.BottomEnd).padding(end = 112.dp, bottom = 64.dp)) { game.breakHeld = it }
            HoldButton("Place", 60, Modifier.align(Alignment.BottomEnd).padding(end = 112.dp, bottom = 136.dp)) { game.placeHeld = it }

            // Hotbar.
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Block.HOTBAR.forEachIndexed { index, block ->
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF000000.toInt() or Textures.swatch(block)))
                            .border(if (index == slot) 3.dp else 1.dp, if (index == slot) Gold else Color.White.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .clickable { game.selectedSlot = index; slot = index },
                    )
                }
            }

            // Top bar: exit, the notice, and frame rate.
            Column(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { leave() }) { Text("Exit", color = Color.White, fontWeight = FontWeight.Bold) }
                    Text(
                        "${game.selectedBlock.label} · $fps fps",
                        color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                }
                Text(
                    DEMO_NOTICE,
                    color = Color.White, fontSize = 11.sp, lineHeight = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Midnight.copy(alpha = 0.72f)).padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        if (showWelcome) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("V-Client Demo World") },
                text = {
                    Text(
                        "$DEMO_NOTICE\n\n" +
                            "Drag with your left thumb to walk and with your right thumb to look around. " +
                            "Use Jump, Break and Place, and pick a block on the bar at the bottom. " +
                            "Your world is saved on this phone and works without internet.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = { TextButton(onClick = { showWelcome = false }) { Text("Start exploring") } },
                dismissButton = { TextButton(onClick = { leave() }) { Text("Back") } },
            )
        }
    }
}

@Composable
private fun HoldButton(label: String, sizeDp: Int, modifier: Modifier, onHold: (Boolean) -> Unit) {
    Box(
        modifier.size(sizeDp.dp).clip(CircleShape)
            .background(Midnight.copy(alpha = 0.55f))
            .border(2.dp, Gold, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onHold(true)
                    tryAwaitRelease()
                    onHold(false)
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
