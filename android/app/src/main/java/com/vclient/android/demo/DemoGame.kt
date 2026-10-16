package com.vclient.android.demo

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Everything that changes while playing: the world, the player, the backpack, mining and placing, and crafting.
 * Nothing is handed out: blocks are broken with the right tool, collected, and crafted into new things.
 */
class DemoGame(val world: DemoWorld, val player: Player, val inventory: Inventory = Inventory(), selectedSlot: Int = 0) {
    var selectedSlot: Int = selectedSlot.coerceIn(0, Inventory.HOTBAR - 1)
        set(value) { field = value.coerceIn(0, Inventory.HOTBAR - 1) }

    // Controls, written by the touch UI and read every tick.
    var moveForward = 0.0
    var moveRight = 0.0
    var jumpHeld = false
    var breakHeld = false
    var placeHeld = false

    /** The block the crosshair is on, or null. */
    var target: BlockHit? = null
        private set

    /** 0..1 progress of the block currently being broken (0 when idle). */
    var breakProgress = 0.0
        private set

    /** A short message for the player ("Needs a pickaxe"), shown for a couple of seconds. */
    var hint: String? = null
        private set

    /** Counts up whenever the backpack changes, so the UI knows to redraw it. */
    var revision = 0
        private set

    /** True once the player changed something since it was last saved. */
    var dirty = false
        private set

    private var hintTime = 0.0
    private var elapsed = 0.0 // seconds spent digging the current block
    private var breakingIndex = -1
    private var placeCooldown = 0.0

    val heldItem: Item? get() = inventory.slots[selectedSlot]?.item

    fun camera() = Camera(player.x, player.eyeY, player.z, player.yaw, player.pitch)

    fun look(deltaYaw: Double, deltaPitch: Double) {
        player.yaw = (player.yaw + deltaYaw) % (2 * Math.PI)
        player.pitch = (player.pitch + deltaPitch).coerceIn(-1.5, 1.5)
    }

    fun showHint(text: String) {
        hint = text
        hintTime = HINT_SECONDS
    }

    fun tick(dt: Double) {
        player.update(dt, moveForward, moveRight, jumpHeld, world)
        target = aim()
        if (hintTime > 0) {
            hintTime -= dt
            if (hintTime <= 0) hint = null
        }
        updateBreaking(dt)
        placeCooldown -= dt
        if (placeHeld && placeCooldown <= 0) {
            placeBlock()
            placeCooldown = REPEAT_SECONDS
        }
    }

    private fun aim(): BlockHit? {
        val cp = cos(player.pitch)
        return world.raycast(player.x, player.eyeY, player.z, sin(player.yaw) * cp, sin(player.pitch), cos(player.yaw) * cp, REACH)
    }

    /** Holding Break digs the targeted block; letting go or looking away starts over. */
    private fun updateBreaking(dt: Double) {
        val hit = target
        if (!breakHeld || hit == null) {
            breakProgress = 0.0
            breakingIndex = -1
            elapsed = 0.0
            return
        }
        val block = world.get(hit.x, hit.y, hit.z)
        val index = world.index(hit.x, hit.y, hit.z)
        if (index != breakingIndex) {
            breakingIndex = index
            elapsed = 0.0
            breakProgress = 0.0
        }
        val needed = Mining.timeToBreak(block, heldItem)
        if (hit.y == 0 || needed == null) {
            breakProgress = 0.0
            elapsed = 0.0
            showHint(if (hit.y == 0) "That layer can't be broken" else Mining.requirement(block))
            return
        }
        elapsed += dt
        breakProgress = (elapsed / needed).coerceIn(0.0, 1.0)
        if (elapsed >= needed) finishBreak(hit, block)
    }

    private fun finishBreak(hit: BlockHit, block: Block) {
        world.set(hit.x, hit.y, hit.z, Block.AIR)
        Mining.drop(block, hit.x, hit.y, hit.z)?.let { (item, count) ->
            if (inventory.add(item, count) > 0) showHint("Backpack is full") else revision++
        }
        dirty = true
        breakProgress = 0.0
        breakingIndex = -1
        elapsed = 0.0
        target = aim()
    }

    /** Breaks the targeted block immediately, as if dug long enough. Returns whether something was broken. */
    fun breakBlock(): Boolean {
        val hit = aim() ?: return false
        if (hit.y == 0) return false
        val block = world.get(hit.x, hit.y, hit.z)
        if (Mining.timeToBreak(block, heldItem) == null) {
            showHint(Mining.requirement(block))
            return false
        }
        finishBreak(hit, block)
        return true
    }

    /** Places the block in the selected slot against the targeted face. Returns whether a block was placed. */
    fun placeBlock(): Boolean {
        val stack = inventory.slots[selectedSlot] ?: return false
        val block = stack.item.block ?: return false
        val hit = aim() ?: return false
        val x = hit.x + hit.nx
        val y = hit.y + hit.ny
        val z = hit.z + hit.nz
        if (!world.inBounds(x, y, z)) return false
        val existing = world.get(x, y, z)
        if (existing != Block.AIR && existing != Block.WATER) return false
        if (player.overlapsBlock(x, y, z)) return false
        world.set(x, y, z, block)
        inventory.consumeOne(selectedSlot)
        revision++
        dirty = true
        target = aim()
        return true
    }

    /** True when a Crafting Table is within a few blocks of the player. */
    fun nearTable(): Boolean {
        val px = floor(player.x).toInt()
        val py = floor(player.y).toInt()
        val pz = floor(player.z).toInt()
        for (x in px - TABLE_RANGE..px + TABLE_RANGE) for (y in py - 2..py + 3) for (z in pz - TABLE_RANGE..pz + TABLE_RANGE) {
            if (world.getId(x, y, z) == Block.TABLE.id) return true
        }
        return false
    }

    /** Crafts a recipe if the player has the ingredients (and a table when needed). */
    fun craft(recipe: Recipe): Boolean {
        val done = Recipes.craft(inventory, recipe, nearTable())
        if (done) {
            revision++
            dirty = true
        }
        return done
    }

    fun swapSlots(a: Int, b: Int) {
        inventory.swap(a, b)
        revision++
        dirty = true
    }

    /** Saves seed, player, backpack and the player's edits to [file] (gzip). Does nothing if nothing changed. */
    fun save(file: File, force: Boolean = false) {
        if (!dirty && !force && file.exists()) return
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.path + ".tmp")
            DataOutputStream(GZIPOutputStream(temp.outputStream().buffered())).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
                out.writeLong(world.seed)
                out.writeInt(world.sx / DemoWorld.CHUNK)
                out.writeDouble(player.x); out.writeDouble(player.y); out.writeDouble(player.z)
                out.writeDouble(player.yaw); out.writeDouble(player.pitch)
                out.writeInt(selectedSlot)
                out.writeInt(inventory.size)
                for (stack in inventory.slots) {
                    out.writeInt(stack?.item?.id ?: 0)
                    out.writeInt(stack?.count ?: 0)
                }
                val edits = world.editEntries()
                out.writeInt(edits.size)
                for ((index, id) in edits) { out.writeInt(index); out.writeByte(id.toInt()) }
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
        const val HINT_SECONDS = 2.2
        const val TABLE_RANGE = 4
        private const val MAGIC = 0x56434c57 // "VCLW"
        private const val VERSION = 2

        fun create(chunks: Int = 32): DemoGame {
            val world = DemoWorld.generate(chunks = chunks)
            val (x, y, z) = world.findSpawn()
            return DemoGame(world, Player(x, y, z, yaw = Math.PI * 0.25, pitch = -0.1))
        }

        /** Loads a saved game, or null when the file is missing, damaged or from an older version. */
        fun load(file: File): DemoGame? {
            if (!file.isFile) return null
            return try {
                DataInputStream(GZIPInputStream(file.inputStream().buffered())).use { input ->
                    if (input.readInt() != MAGIC || input.readInt() != VERSION) return null
                    val seed = input.readLong()
                    val chunks = input.readInt()
                    if (chunks !in 1..64) return null
                    val x = input.readDouble(); val y = input.readDouble(); val z = input.readDouble()
                    val yaw = input.readDouble(); val pitch = input.readDouble()
                    val slot = input.readInt()
                    val inventory = Inventory()
                    val slots = input.readInt()
                    if (slots != inventory.size) return null
                    for (i in 0 until slots) {
                        val id = input.readInt()
                        val count = input.readInt()
                        val item = Item.of(id)
                        if (item != null && count > 0) inventory.slots[i] = ItemStack(item, count.coerceAtMost(item.maxStack))
                    }
                    val world = DemoWorld.generate(seed, chunks)
                    val edits = input.readInt()
                    if (edits < 0 || edits > world.blocks.size) return null
                    for (i in 0 until edits) world.applyEdit(input.readInt(), input.readByte())
                    DemoGame(world, Player(x, y, z, yaw, pitch), inventory, slot)
                }
            } catch (e: IOException) {
                null
            }
        }

        fun loadOrCreate(file: File, chunks: Int = 32): DemoGame = load(file) ?: create(chunks)
    }
}
