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

    /** A flat floor of stone at y=9 in an otherwise empty world, with the player standing on it. */
    private fun flatGame(): DemoGame {
        val world = DemoWorld()
        for (x in 0 until world.sx) for (z in 0 until world.sz) {
            world.set(x, 8, z, Block.STONE)
            world.set(x, 9, z, Block.STONE)
        }
        return DemoGame(world, Player(32.5, 10.0, 32.5))
    }

    @Test
    fun playerLandsAndStaysOnTheGround() {
        val game = DemoGame.create()
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
        assertTrue(game.player.onGround)
    }

    @Test
    fun walkingMovesInTheDirectionLookedAndWallsStopYou() {
        val game = flatGame()
        game.player.yaw = 0.0 // facing +Z
        game.moveForward = 1.0
        settle(game, 1.0)
        assertTrue("moved forward ${game.player.z - 32.5}", game.player.z - 32.5 in 3.5..4.6)
        assertEquals(32.5, game.player.x, 0.01)

        game.world.set(32, 10, 40, Block.STONE); game.world.set(32, 11, 40, Block.STONE)
        settle(game, 3.0)
        assertTrue("stopped by the wall at z=40, was ${game.player.z}", game.player.z < 40 - 0.29)
    }

    @Test
    fun theIslandSidesAreSolid() {
        val game = flatGame()
        game.player.yaw = Math.PI / 2 // facing +X
        game.moveForward = 1.0
        settle(game, 15.0)
        assertTrue("x=${game.player.x}", game.player.x in 0.0..64.0)
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
    fun breakAndPlaceBlocks() {
        val game = flatGame()
        game.player.pitch = -1.2 // look down at the floor
        settle(game, 0.2)
        val hit = game.target
        assertNotNull("must be aiming at the floor", hit)
        assertEquals(9, hit!!.y)

        assertTrue(game.breakBlock())
        assertEquals(Block.AIR, game.world.get(hit.x, hit.y, hit.z))
        assertTrue(game.dirty)

        game.selectedSlot = Block.HOTBAR.indexOf(Block.GOLD)
        assertTrue(game.placeBlock())
        val placedAt = game.world.get(hit.x, hit.y, hit.z)
        assertTrue("a block was put into the hole, got $placedAt", placedAt == Block.GOLD)
    }

    @Test
    fun cannotPlaceABlockInsideThePlayer() {
        val game = flatGame()
        game.player.pitch = -1.5 // straight down at the feet
        settle(game, 0.2)
        val before = game.world.blocks.copyOf()
        assertFalse("placing against the floor under your feet would trap you", game.placeBlock())
        assertArrayEquals(before, game.world.blocks)
    }

    @Test
    fun holdingBreakRepeatsButNeverDigsTheFloorLayerOut() {
        val game = flatGame()
        for (x in 0 until game.world.sx) for (z in 0 until game.world.sz) game.world.set(x, 0, z, Block.STONE)
        game.player.pitch = -1.5
        game.breakHeld = true
        settle(game, 2.0)
        assertEquals("bottom layer stays", Block.STONE, game.world.get(32, 0, 32))
    }

    @Test
    fun saveAndLoadRoundTrip() {
        val file = folder.newFile("world.vcw")
        val game = DemoGame.create()
        game.player.yaw = 1.0
        game.selectedSlot = 4
        game.player.pitch = -0.5
        settle(game, 0.5)
        game.breakBlock()
        game.save(file, force = true)
        assertFalse(game.dirty)

        val loaded = DemoGame.load(file)
        assertNotNull(loaded)
        assertArrayEquals(game.world.blocks, loaded!!.world.blocks)
        assertEquals(game.player.x, loaded.player.x, 1e-9)
        assertEquals(game.player.y, loaded.player.y, 1e-9)
        assertEquals(game.player.yaw, loaded.player.yaw, 1e-9)
        assertEquals(4, loaded.selectedSlot)
    }

    @Test
    fun damagedOrMissingSaveFallsBackToANewWorld() {
        val missing = folder.newFolder().resolve("nope.vcw")
        assertNull(DemoGame.load(missing))
        assertNotNull(DemoGame.loadOrCreate(missing))

        val junk = folder.newFile("junk.vcw").also { it.writeBytes(ByteArray(50) { (it * 7).toByte() }) }
        assertNull(DemoGame.load(junk))
        assertNotNull(DemoGame.loadOrCreate(junk))

        val good = folder.newFile("good.vcw")
        DemoGame.create().save(good, force = true)
        good.writeBytes(good.readBytes().copyOf(good.length().toInt() / 2)) // truncated
        assertNull(DemoGame.load(good))
    }
}
