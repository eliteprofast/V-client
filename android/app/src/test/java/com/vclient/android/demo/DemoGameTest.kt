package com.vclient.android.demo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DemoGameTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun settle(game: DemoGame, seconds: Double = 2.0) {
        var t = 0.0
        while (t < seconds) { game.tick(1 / 60.0); t += 1 / 60.0 }
    }

    /** A flat two-layer floor of [floor] at y=8..9 in an otherwise empty 64-block world, player standing on it. */
    private fun flatGame(floor: Block = Block.STONE): DemoGame {
        val world = DemoWorld()
        for (x in 0 until world.sx) for (z in 0 until world.sz) {
            world.set(x, 8, z, floor)
            world.set(x, 9, z, floor)
        }
        return DemoGame(world, Player(32.5, 10.0, 32.5))
    }

    private fun lookDown(game: DemoGame) { game.player.pitch = -1.2; settle(game, 0.1) }

    @Test
    fun youStartWithAnEmptyBackpackAndNoToolsAreHandedOut() {
        val game = DemoGame.create(chunks = 12)
        assertTrue(game.inventory.slots.all { it == null })
        assertNull(game.heldItem)
    }

    @Test
    fun playerLandsAndStaysOnTheGround() {
        val game = DemoGame.create(chunks = 12)
        val startY = game.player.y
        settle(game)
        assertTrue(game.player.onGround)
        assertEquals(startY, game.player.y, 0.05)
    }

    @Test
    fun jumpRisesAboutAMeterAndAHalfThenLands() {
        val game = flatGame()
        settle(game, 0.5)
        val ground = game.player.y
        game.jumpHeld = true
        var peak = ground
        for (i in 0 until 90) {
            game.tick(1 / 60.0)
            game.jumpHeld = false
            peak = maxOf(peak, game.player.y)
        }
        assertTrue("jump height ${peak - ground}", peak - ground in 1.0..1.6)
        settle(game, 1.0)
        assertEquals(ground, game.player.y, 0.05)
    }

    @Test
    fun walkingMovesInTheDirectionLookedAndWallsStopYou() {
        val game = flatGame()
        game.player.yaw = 0.0
        game.moveForward = 1.0
        settle(game, 1.0)
        assertTrue("moved forward ${game.player.z - 32.5}", game.player.z - 32.5 in 3.5..4.6)
        game.world.set(32, 10, 40, Block.STONE); game.world.set(32, 11, 40, Block.STONE)
        settle(game, 3.0)
        assertTrue("stopped by the wall, was ${game.player.z}", game.player.z < 40 - 0.29)
    }

    @Test
    fun swimmingWorksInWater() {
        val world = DemoWorld()
        for (x in 0 until world.sx) for (z in 0 until world.sz) {
            world.set(x, 2, z, Block.STONE)
            for (y in 3..11) world.set(x, y, z, Block.WATER)
        }
        val game = DemoGame(world, Player(10.5, 8.0, 10.5))
        settle(game, 0.3)
        assertTrue(game.player.inWater)
        val before = game.player.y
        game.jumpHeld = true
        settle(game, 0.6)
        assertTrue("swam up ${game.player.y - before}", game.player.y > before + 0.5)
    }

    @Test
    fun dirtBreaksByHandAndLandsInTheBackpack() {
        val game = flatGame(Block.DIRT)
        lookDown(game)
        game.breakHeld = true
        settle(game, 1.0) // dirt takes 0.5 s by hand
        assertTrue("dirt collected: ${game.inventory.count(Item.DIRT)}", game.inventory.count(Item.DIRT) >= 1)
        assertTrue(game.dirty)
    }

    @Test
    fun breakingTakesTimeAndShowsProgress() {
        val game = flatGame(Block.DIRT)
        lookDown(game)
        game.breakHeld = true
        for (i in 0 until 10) game.tick(1 / 60.0) // 0.17 s of a 0.5 s job
        assertTrue("in progress: ${game.breakProgress}", game.breakProgress in 0.1..0.9)
        assertEquals("nothing collected yet", 0, game.inventory.count(Item.DIRT))
        game.breakHeld = false
        game.tick(1 / 60.0)
        assertEquals("letting go resets the digging", 0.0, game.breakProgress, 1e-9)
    }

    @Test
    fun stoneCannotBeBrokenByHandButAPickaxeWorks() {
        val game = flatGame(Block.STONE)
        lookDown(game)
        game.breakHeld = true
        settle(game, 6.0)
        assertEquals("no cobblestone by hand", 0, game.inventory.count(Item.COBBLE))
        assertEquals("Needs a pickaxe", game.hint)

        game.inventory.add(Item.WOOD_PICK, 1)
        game.selectedSlot = 0
        settle(game, 2.5) // 3 s / 2 = 1.5 s with a wooden pickaxe
        assertTrue("cobblestone collected: ${game.inventory.count(Item.COBBLE)}", game.inventory.count(Item.COBBLE) >= 1)
    }

    @Test
    fun oresNeedBetterAndBetterPickaxes() {
        fun mine(ore: Block, tool: Item?): Int {
            val game = flatGame(ore)
            tool?.let { game.inventory.add(it, 1) }
            lookDown(game)
            game.breakHeld = true
            settle(game, 4.0)
            return game.inventory.slots.filterNotNull().filter { !it.item.isTool }.sumOf { it.count }
        }
        assertEquals("iron with a wooden pickaxe", 0, mine(Block.IRON_ORE, Item.WOOD_PICK))
        assertTrue("iron with a stone pickaxe", mine(Block.IRON_ORE, Item.STONE_PICK) >= 1)
        assertEquals("gold with a stone pickaxe", 0, mine(Block.GOLD_ORE, Item.STONE_PICK))
        assertTrue("gold with an iron pickaxe", mine(Block.GOLD_ORE, Item.IRON_PICK) >= 2)
        assertTrue("coal with a wooden pickaxe", mine(Block.COAL_ORE, Item.WOOD_PICK) >= 1)
    }

    @Test
    fun placingUsesUpTheItemFromTheSelectedSlot() {
        val game = flatGame(Block.STONE)
        lookDown(game)
        assertFalse("an empty hand places nothing", game.placeBlock())

        game.inventory.add(Item.PLANKS, 2)
        game.selectedSlot = 0
        val target = game.target!!
        assertTrue(game.placeBlock())
        assertEquals(Block.PLANKS, game.world.get(target.x + target.nx, target.y + target.ny, target.z + target.nz))
        assertEquals(1, game.inventory.count(Item.PLANKS))

        game.player.x += 3.0 // step aside so the next block goes somewhere else
        settle(game, 0.1)
        game.inventory.slots[0]?.let { }
        lookDown(game)
        assertTrue(game.placeBlock())
        assertEquals("the stack is used up and the slot empties", null, game.inventory.slots[0])
    }

    @Test
    fun toolsAndMaterialsAreNotPlaceable() {
        val game = flatGame()
        lookDown(game)
        game.inventory.add(Item.WOOD_PICK, 1)
        game.inventory.add(Item.STICK, 1)
        game.selectedSlot = 0
        assertFalse(game.placeBlock())
        game.selectedSlot = 1
        assertFalse(game.placeBlock())
    }

    @Test
    fun cannotPlaceABlockInsideThePlayer() {
        val game = flatGame()
        game.inventory.add(Item.DIRT, 5)
        game.player.pitch = -1.5
        settle(game, 0.2)
        val before = game.world.blocks.copyOf()
        assertFalse("placing against the floor under your feet would trap you", game.placeBlock())
        assertArrayEquals(before, game.world.blocks)
        assertEquals(5, game.inventory.count(Item.DIRT))
    }

    @Test
    fun theBottomLayerCannotBeDugOut() {
        val world = DemoWorld()
        for (x in 0 until world.sx) for (z in 0 until world.sz) world.set(x, 0, z, Block.DIRT)
        val game = DemoGame(world, Player(32.5, 1.0, 32.5))
        game.player.pitch = -1.5
        game.breakHeld = true
        settle(game, 2.0)
        assertEquals(Block.DIRT, game.world.get(32, 0, 32))
    }

    @Test
    fun aCraftingTableIsFoundOnlyWhenNearby() {
        val game = flatGame()
        assertFalse(game.nearTable())
        game.world.set(34, 10, 32, Block.TABLE) // two blocks away
        assertTrue(game.nearTable())
        game.player.x = 60.5
        assertFalse(game.nearTable())
    }

    @Test
    fun craftingThroughTheGameUsesTheTableRule() {
        val game = flatGame()
        game.inventory.add(Item.PLANKS, 3)
        game.inventory.add(Item.STICK, 2)
        val pick = Recipes.all.first { it.result == Item.WOOD_PICK }
        assertFalse("no table nearby", game.craft(pick))
        game.world.set(33, 10, 32, Block.TABLE)
        assertTrue(game.craft(pick))
        assertEquals(1, game.inventory.count(Item.WOOD_PICK))
    }

    @Test
    fun saveAndLoadKeepsEditsBackpackAndPosition() {
        val file = folder.newFile("world.vcw")
        val game = DemoGame.create(chunks = 12)
        game.player.yaw = 1.0
        game.player.pitch = -0.5
        game.inventory.add(Item.PLANKS, 7)
        game.inventory.add(Item.WOOD_PICK, 1)
        game.selectedSlot = 1
        settle(game, 0.5)
        game.world.set(40, 40, 40, Block.TABLE)
        game.world.set(41, 40, 40, Block.COBBLE)
        game.save(file, force = true)
        assertFalse(game.dirty)

        val loaded = DemoGame.load(file)
        assertNotNull(loaded)
        assertArrayEquals(game.world.blocks, loaded!!.world.blocks)
        assertEquals(game.player.x, loaded.player.x, 1e-9)
        assertEquals(game.player.yaw, loaded.player.yaw, 1e-9)
        assertEquals(7, loaded.inventory.count(Item.PLANKS))
        assertEquals(1, loaded.inventory.count(Item.WOOD_PICK))
        assertEquals(1, loaded.selectedSlot)
        assertTrue("saves are small: ${file.length()} bytes", file.length() < 5_000)
    }

    @Test
    fun damagedMissingOrOldSavesFallBackToANewWorld() {
        val missing = folder.newFolder().resolve("nope.vcw")
        assertNull(DemoGame.load(missing))
        assertNotNull(DemoGame.loadOrCreate(missing, 12))

        val junk = folder.newFile("junk.vcw").also { it.writeBytes(ByteArray(50) { (it * 7).toByte() }) }
        assertNull(DemoGame.load(junk))
        assertNotNull(DemoGame.loadOrCreate(junk, 12))

        val good = folder.newFile("good.vcw")
        DemoGame.create(12).save(good, force = true)
        good.writeBytes(good.readBytes().copyOf(good.length().toInt() / 2)) // truncated
        assertNull(DemoGame.load(good))
    }
}
