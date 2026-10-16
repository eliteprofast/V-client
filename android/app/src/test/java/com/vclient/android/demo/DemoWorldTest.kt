package com.vclient.android.demo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoWorldTest {
    private val world = DemoWorld.generate()

    private fun count(block: Block): Int = world.blocks.count { it.toInt() == block.id }

    @Test
    fun generationIsDeterministic() {
        assertArrayEquals(DemoWorld.generate().blocks, DemoWorld.generate().blocks)
        assertTrue(!DemoWorld.generate(1L).blocks.contentEquals(DemoWorld.generate(2L).blocks))
    }

    @Test
    fun worldHasGrassStoneTreesHillsAndWater() {
        for (block in listOf(Block.GRASS, Block.DIRT, Block.STONE, Block.SAND, Block.WATER, Block.WOOD, Block.LEAVES)) {
            assertTrue("world must contain ${block.label}", count(block) > 0)
        }
        assertEquals("the gold V block is only placed by the player", 0, count(Block.GOLD))
        val heights = (0 until world.sx).flatMap { x -> (0 until world.sz).map { z -> world.surfaceY(x, z) } }
        assertTrue("hills: heights must vary by at least 8 blocks", heights.max() - heights.min() >= 8)
        assertTrue("some land must rise above the water", heights.max() > DemoWorld.SEA_LEVEL + 3)
    }

    @Test
    fun theIslandIsRingedByWater() {
        for (i in 0 until world.sx) {
            assertEquals("north edge $i", Block.WATER, world.get(i, DemoWorld.SEA_LEVEL, 0))
            assertEquals("south edge $i", Block.WATER, world.get(i, DemoWorld.SEA_LEVEL, world.sz - 1))
        }
    }

    @Test
    fun treesAreTrunksWithLeavesOnTop() {
        var trees = 0
        for (x in 0 until world.sx) for (z in 0 until world.sz) for (y in 0 until world.sy - 1) {
            if (world.get(x, y, z) == Block.WOOD && world.get(x, y - 1, z) == Block.GRASS) {
                trees++
                var top = y
                while (world.get(x, top + 1, z) == Block.WOOD) top++
                assertTrue("trunk at $x,$z must carry leaves", world.get(x, top + 1, z) == Block.LEAVES || world.get(x, top, z + 1) == Block.LEAVES || world.get(x + 1, top, z) == Block.LEAVES)
            }
        }
        assertTrue("expected several trees, found $trees", trees >= 3)
    }

    @Test
    fun spawnStandsOnGrassWithRoomAbove() {
        val (x, y, z) = world.findSpawn()
        assertEquals(Block.GRASS, world.get(x.toInt(), y.toInt() - 1, z.toInt()))
        assertEquals(Block.AIR, world.get(x.toInt(), y.toInt(), z.toInt()))
        assertEquals(Block.AIR, world.get(x.toInt(), y.toInt() + 1, z.toInt()))
    }

    @Test
    fun outOfBoundsReadsAsAirAndIgnoresWrites() {
        assertEquals(Block.AIR, world.get(-1, 0, 0))
        assertEquals(Block.AIR, world.get(0, 99, 0))
        world.set(-5, 0, 0, Block.GOLD) // must not throw
    }

    @Test
    fun raycastHitsTheFirstSolidBlockAndReportsTheFace() {
        val w = DemoWorld()
        w.set(5, 5, 8, Block.STONE)
        val hit = w.raycast(5.5, 5.5, 2.5, 0.0, 0.0, 1.0, 10.0)
        assertNotNull(hit)
        assertEquals(Triple(5, 5, 8), Triple(hit!!.x, hit.y, hit.z))
        assertEquals(Triple(0, 0, -1), Triple(hit.nx, hit.ny, hit.nz)) // entered through its -Z face
        assertEquals(5.5, hit.dist, 1e-9)

        assertNull("out of reach", w.raycast(5.5, 5.5, 2.5, 0.0, 0.0, 1.0, 4.0))
        assertNull("pointing away", w.raycast(5.5, 5.5, 2.5, 0.0, 0.0, -1.0, 50.0))

        w.set(5, 5, 6, Block.WATER)
        assertEquals("water is passed through", 8, w.raycast(5.5, 5.5, 2.5, 0.0, 0.0, 1.0, 10.0)!!.z)
        w.set(5, 4, 8, Block.GRASS)
        val down = w.raycast(5.5, 9.5, 8.5, 0.0, -1.0, 0.0, 10.0)!!
        assertEquals(Triple(5, 5, 8), Triple(down.x, down.y, down.z))
        assertEquals(1, down.ny) // entered through the top
    }
}
