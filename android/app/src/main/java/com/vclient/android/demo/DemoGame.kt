package com.vclient.android.demo

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.cos
import kotlin.math.sin

/** Everything that changes while playing: the world, the player, the hotbar, and the break/place actions. */
class DemoGame(val world: DemoWorld, val player: Player, selectedSlot: Int = 0) {
    var selectedSlot: Int = selectedSlot.coerceIn(0, Block.HOTBAR.size - 1)
        set(value) { field = value.coerceIn(0, Block.HOTBAR.size - 1) }

    // Controls, written by the touch UI and read every tick.
    var moveForward = 0.0
    var moveRight = 0.0
    var jumpHeld = false
    var breakHeld = false
    var placeHeld = false

    /** The block the crosshair is on, or null. */
    var target: BlockHit? = null
        private set

    /** True once the player changed the world since it was last saved. */
    var dirty = false
        private set

    private var actionCooldown = 0.0

    val selectedBlock: Block get() = Block.HOTBAR[selectedSlot]

    fun camera() = Camera(player.x, player.eyeY, player.z, player.yaw, player.pitch)

    fun look(deltaYaw: Double, deltaPitch: Double) {
        player.yaw = (player.yaw + deltaYaw) % (2 * Math.PI)
        player.pitch = (player.pitch + deltaPitch).coerceIn(-1.5, 1.5)
    }

    fun tick(dt: Double) {
        player.update(dt, moveForward, moveRight, jumpHeld, world)
        target = aim()
        actionCooldown -= dt
        if (actionCooldown <= 0 && (breakHeld || placeHeld)) {
            if (breakHeld) breakBlock() else placeBlock()
            actionCooldown = REPEAT_SECONDS
        }
    }

    private fun aim(): BlockHit? {
        val cp = cos(player.pitch)
        return world.raycast(player.x, player.eyeY, player.z, sin(player.yaw) * cp, sin(player.pitch), cos(player.yaw) * cp, REACH)
    }

    /** Removes the targeted block. Returns whether something was broken. */
    fun breakBlock(): Boolean {
        val hit = aim() ?: return false
        if (hit.y == 0) return false // keep a floor so the island can't be dug into the void
        world.set(hit.x, hit.y, hit.z, Block.AIR)
        dirty = true
        target = aim()
        return true
    }

    /** Puts the selected block against the targeted face. Returns whether a block was placed. */
    fun placeBlock(): Boolean {
        val hit = aim() ?: return false
        val x = hit.x + hit.nx
        val y = hit.y + hit.ny
        val z = hit.z + hit.nz
        if (!world.inBounds(x, y, z)) return false
        val existing = world.get(x, y, z)
        if (existing != Block.AIR && existing != Block.WATER) return false
        if (player.overlapsBlock(x, y, z)) return false
        world.set(x, y, z, selectedBlock)
        dirty = true
        target = aim()
        return true
    }

    /** Saves the world and player to [file] (gzip). Safe to call often; does nothing if nothing changed and the file exists. */
    fun save(file: File, force: Boolean = false) {
        if (!dirty && !force && file.exists()) return
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.path + ".tmp")
            DataOutputStream(GZIPOutputStream(temp.outputStream().buffered())).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
                out.writeInt(world.sx); out.writeInt(world.sy); out.writeInt(world.sz)
                out.writeDouble(player.x); out.writeDouble(player.y); out.writeDouble(player.z)
                out.writeDouble(player.yaw); out.writeDouble(player.pitch)
                out.writeInt(selectedSlot)
                out.write(world.blocks)
            }
            if (!temp.renameTo(file)) {
                file.delete()
                if (!temp.renameTo(file)) throw IOException("could not replace $file")
            }
            dirty = false
        } catch (e: IOException) {
            // Saving is best effort: a full disk must never crash the game.
        }
    }

    companion object {
        const val REACH = 5.5
        const val REPEAT_SECONDS = 0.22
        private const val MAGIC = 0x56434c57 // "VCLW"
        private const val VERSION = 1

        fun create(): DemoGame {
            val world = DemoWorld.generate()
            val (x, y, z) = world.findSpawn()
            return DemoGame(world, Player(x, y, z, yaw = Math.PI * 0.25, pitch = -0.1))
        }

        /** Loads a saved world, or null when the file is missing, damaged or from an unknown version. */
        fun load(file: File): DemoGame? {
            if (!file.isFile) return null
            return try {
                DataInputStream(GZIPInputStream(file.inputStream().buffered())).use { input ->
                    if (input.readInt() != MAGIC || input.readInt() != VERSION) return null
                    val world = DemoWorld(input.readInt(), input.readInt(), input.readInt())
                    if (world.sx != 64 || world.sy != 40 || world.sz != 64) return null
                    val x = input.readDouble(); val y = input.readDouble(); val z = input.readDouble()
                    val yaw = input.readDouble(); val pitch = input.readDouble()
                    val slot = input.readInt()
                    input.readFully(world.blocks)
                    DemoGame(world, Player(x, y, z, yaw, pitch), slot)
                }
            } catch (e: IOException) {
                null
            }
        }

        fun loadOrCreate(file: File): DemoGame = load(file) ?: create()
    }
}
