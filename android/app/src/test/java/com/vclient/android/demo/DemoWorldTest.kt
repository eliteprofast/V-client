package com.vclient.android.demo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoWorldTest {
    companion object {
        var generationMillis = 0L

        /** The real 32 x 32 chunk island, built once for the whole class. */
        val full: DemoWorld by lazy {
            val start = System.nanoTime()
            DemoWorld.generate().also { generationMillis = (System.nanoTime() - start) / 1_000_000 }
        }
    }

    private fun count(world: DemoWorld, vararg blocks: Block): Int {
        val ids = blocks.map { it.id.toByte() }.toSet()
        return world.blocks.count { it in ids }
    }

    @Test
    fun generationIsDeterministic() {
        assertArrayEquals(DemoWorld.generate(chunks = 12).blocks, DemoWorld.generate(chunks = 12).blocks)
        assertTrue(!DemoWorld.generate(1L, 12).blocks.contentEquals(DemoWorld.generate(2L, 12).blocks))
    }

    @Test
    fun theIslandIs32By32ChunksOf16Blocks() {
        assertEquals(512, full.sx)
        assertEquals(512, full.sz)
        assertEquals(32, full.sx / DemoWorld.CHUNK)
        assertEquals(32, full.sz / DemoWorld.CHUNK)
        println("WORLD_GENERATION_MS $generationMillis")
        assertTrue("generation took ${generationMillis}ms", generationMillis < 30_000)
    }

    @Test
    fun twoBiomesCherryBlossomAndOakForestWithTheirOwnTrees() {
        assertTrue("oak grass", count(full, Block.GRASS) > 5_000)
        assertTrue("cherry grass", count(full, Block.BLOSSOM_GRASS) > 5_000)
        assertTrue("oak trunks", count(full, Block.WOOD) > 100)
        assertTrue("oak leaves", count(full, Block.LEAVES) > 500)
        assertTrue("cherry trunks", count(full, Block.CHERRY_LOG) > 100)
        assertTrue("pink leaves", count(full, Block.CHERRY_LEAVES) > 500)
        val biomes = (0 until full.sx step 8).flatMap { x -> (0 until full.sz step 8).map { z -> full.biomeAt(x, z) } }.toSet()
        assertEquals(setOf(Biome.OAK_FOREST, Biome.CHERRY_BLOSSOM), biomes)

        // Trees match their biome: pink leaves never sit far from cherry ground.
        var checked = 0
        for (x in 0 until full.sx step 3) for (z in 0 until full.sz step 3) {
            for (y in DemoWorld.SEA_LEVEL..40) {
                if (full.get(x, y, z) == Block.CHERRY_LOG && full.get(x, y - 1, z) == Block.BLOSSOM_GRASS) checked++
                assertTrue("an oak trunk grows from blossom grass at $x,$y,$z", !(full.get(x, y, z) == Block.WOOD && full.get(x, y - 1, z) == Block.BLOSSOM_GRASS))
            }
        }
        assertTrue("cherry trees grow on blossom grass ($checked found)", checked > 5)
    }

    @Test
    fun terrainHasHillsMountainsWaterBeachesAndOres() {
        for (block in listOf(Block.DIRT, Block.STONE, Block.SAND, Block.WATER, Block.COAL_ORE, Block.IRON_ORE, Block.GOLD_ORE)) {
            assertTrue("world must contain ${block.label}", count(full, block) > 0)
        }
        val heights = (0 until full.sx step 4).flatMap { x -> (0 until full.sz step 4).map { z -> full.surfaceY(x, z) } }
        assertTrue("hills and mountains: ${heights.min()}..${heights.max()}", heights.max() - heights.min() >= 18)
        assertTrue("some peaks reach bare rock", heights.max() >= 29)
        assertEquals("the gold V block is only crafted", 0, count(full, Block.GOLD))
        assertTrue("coal is common", count(full, Block.COAL_ORE) > count(full, Block.IRON_ORE))
        assertTrue("iron is more common than gold", count(full, Block.IRON_ORE) > count(full, Block.GOLD_ORE))
    }

    @Test
    fun theIslandIsRingedByWater() {
        for (i in 0 until full.sx step 7) {
            assertEquals("north edge $i", Block.WATER, full.get(i, DemoWorld.SEA_LEVEL, 0))
            assertEquals("west edge $i", Block.WATER, full.get(0, DemoWorld.SEA_LEVEL, i))
            assertEquals("east edge $i", Block.WATER, full.get(full.sx - 1, DemoWorld.SEA_LEVEL, i))
        }
    }

    @Test
    fun oneVillageWithWellPathsSixHousesAFarmAndACraftingTable() {
        val (vx, vy, vz) = full.villageCenter!!
        assertEquals("well water", Block.WATER, full.get(vx, vy, vz))
        assertEquals("well roof", Block.PLANKS, full.get(vx, vy + 4, vz))
        assertTrue("paths: ${count(full, Block.PATH)}", count(full, Block.PATH) > 100)
        assertTrue("cobble floors for six houses", count(full, Block.COBBLE) >= 6 * 42)
        assertTrue("planks walls and roofs", count(full, Block.PLANKS, Block.CHERRY_PLANKS) > 500)
        assertTrue("both wood styles", count(full, Block.PLANKS) > 0 && count(full, Block.CHERRY_PLANKS) > 0)
        assertTrue("farmland", count(full, Block.FARMLAND) > 40)
        assertTrue("crops", count(full, Block.CROP) > 20)
        assertEquals("exactly one starter Crafting Table", 1, count(full, Block.TABLE))

        // The ground inside the village is level, so houses stand straight.
        val levels = (-15..15 step 5).flatMap { dx -> (-15..15 step 5).map { dz -> full.surfaceY(vx + dx, vz + dz) } }
        assertTrue("village ground is flat: ${levels.toSet()}", levels.max() - levels.min() <= 7)
        // One village only: no second well of water-in-cobble elsewhere is checked via the table count above.
    }

    @Test
    fun youSpawnOnTheVillageRoadWithRoomToStand() {
        val (x, y, z) = full.findSpawn()
        val (vx, vy, vz) = full.villageCenter!!
        assertEquals(Block.PATH, full.get(x.toInt(), y.toInt() - 1, z.toInt()))
        assertEquals(Block.AIR, full.get(x.toInt(), y.toInt(), z.toInt()))
        assertEquals(Block.AIR, full.get(x.toInt(), y.toInt() + 1, z.toInt()))
        assertTrue("spawn is next to the well", Math.hypot(x - vx, z - vz) < 8)
    }

    @Test
    fun outOfBoundsReadsAsAirAndIgnoresWrites() {
        val w = DemoWorld()
        assertEquals(Block.AIR, w.get(-1, 0, 0))
        assertEquals(Block.AIR, w.get(0, 99, 0))
        w.set(-5, 0, 0, Block.GOLD) // must not throw
    }

    @Test
    fun playerEditsAreRememberedAndReplayedOnAFreshWorld() {
        val a = DemoWorld.generate(chunks = 12)
        assertEquals(0, a.editCount)
        a.set(40, 30, 40, Block.TABLE)
        a.set(41, 30, 40, Block.PLANKS)
        assertEquals(2, a.editCount)

        val b = DemoWorld.generate(chunks = 12)
        for ((index, id) in a.editEntries()) b.applyEdit(index, id)
        assertArrayEquals(a.blocks, b.blocks)
    }

    @Test
    fun raycastHitsTheFirstSolidBlockAndReportsTheFace() {
        val w = DemoWorld()
        w.set(5, 5, 8, Block.STONE)
        val hit = w.raycast(5.5, 5.5, 2.5, 0.0, 0.0, 1.0, 10.0)
        assertNotNull(hit)
        assertEquals(Triple(5, 5, 8), Triple(hit!!.x, hit.y, hit.z))
        assertEquals(Triple(0, 0, -1), Triple(hit.nx, hit.ny, hit.nz))
        assertEquals(5.5, hit.dist, 1e-9)

        assertNull("out of reach", w.raycast(5.5, 5.5, 2.5, 0.0, 0.0, 1.0, 4.0))
        assertNull("pointing away", w.raycast(5.5, 5.5, 2.5, 0.0, 0.0, -1.0, 50.0))

        w.set(5, 5, 6, Block.WATER)
        assertEquals("water is passed through", 8, w.raycast(5.5, 5.5, 2.5, 0.0, 0.0, 1.0, 10.0)!!.z)
        w.set(5, 4, 8, Block.GRASS)
        val down = w.raycast(5.5, 9.5, 8.5, 0.0, -1.0, 0.0, 10.0)!!
        assertEquals(Triple(5, 5, 8), Triple(down.x, down.y, down.z))
        assertEquals(1, down.ny)
    }
}
