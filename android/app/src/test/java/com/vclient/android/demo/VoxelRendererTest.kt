package com.vclient.android.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoxelRendererTest {
    private val world = DemoWorld.generate(chunks = 12)

    private fun render(cam: Camera, w: Int = 320, h: Int = 180, selected: BlockHit? = null, progress: Double = 0.0): IntArray {
        val renderer = VoxelRenderer(world)
        renderer.selected = selected
        renderer.breakProgress = progress
        return IntArray(w * h).also { renderer.render(it, w, h, cam) }
    }

    private fun spawnCamera(yaw: Double, pitch: Double): Camera {
        val (x, y, z) = world.findSpawn()
        return Camera(x, y + Player.EYE_HEIGHT, z, yaw, pitch)
    }

    private fun aim(cam: Camera, reach: Double = 8.0): BlockHit? {
        val cp = Math.cos(cam.pitch)
        return world.raycast(cam.x, cam.y, cam.z, Math.sin(cam.yaw) * cp, Math.sin(cam.pitch), Math.cos(cam.yaw) * cp, reach)
    }

    @Test
    fun rendersTerrainAndSkyNotAFlatColour() {
        val pixels = render(spawnCamera(0.8, 0.0))
        assertTrue("many distinct colours expected, got ${pixels.toSet().size}", pixels.toSet().size > 150)
        val sky = pixels.count { (it and 0xff) > ((it shr 16) and 0xff) + 30 && (it and 0xff) > ((it shr 8) and 0xff) }
        assertTrue("some of the view is night sky: $sky pixels", sky > pixels.size / 25)
    }

    @Test
    fun movingTheCameraChangesTheImage() {
        assertTrue(!render(spawnCamera(0.0, 0.0)).contentEquals(render(spawnCamera(2.0, 0.0))))
    }

    @Test
    fun selectionOutlineAppearsOnTheTargetedBlock() {
        val cam = spawnCamera(0.8, -0.35)
        val hit = aim(cam)
        assertTrue("test setup: must be looking at a block", hit != null)
        assertTrue("outline must change some pixels", !render(cam).contentEquals(render(cam, selected = hit)))
    }

    @Test
    fun cracksAppearAsDiggingProgresses() {
        val cam = spawnCamera(0.8, -0.35)
        val hit = aim(cam)!!
        val none = render(cam, selected = hit, progress = 0.0)
        val some = render(cam, selected = hit, progress = 0.5)
        val most = render(cam, selected = hit, progress = 0.95)
        fun darker(a: IntArray, b: IntArray) = a.indices.count { ((b[it] shr 8) and 0xff) < ((a[it] shr 8) and 0xff) - 10 }
        assertTrue("cracks at 50%: ${darker(none, some)}", darker(none, some) > 20)
        assertTrue("more cracks at 95% than at 50%", darker(none, most) > darker(none, some))
    }

    @Test
    fun placedGoldBlockShowsItsOwnColour() {
        val cam = spawnCamera(0.8, -0.4)
        val hit = aim(cam)!!
        val before = render(cam)
        world.set(hit.x + hit.nx, hit.y + hit.ny, hit.z + hit.nz, Block.GOLD)
        val after = render(cam)
        val gold = after.count { ((it shr 16) and 0xff) > 180 && ((it shr 8) and 0xff) > 140 && (it and 0xff) < 140 }
        assertTrue("gold pixels appear: $gold", gold > 40 && !before.contentEquals(after))
    }

    @Test
    fun everyBlockTypeHasItsOwnLookingTexture() {
        val looks = Block.entries.filter { it != Block.AIR }.associateWith { block ->
            (0 until 64).map { Textures.texel(block, Textures.TOP, it % 8, it / 8) }
        }
        val seen = HashSet<List<Int>>()
        for ((block, texture) in looks) assertTrue("${block.label} duplicates another block's texture", seen.add(texture))
        // The two leaf types are clearly different colours: green vs pink.
        val oak = Textures.swatch(Block.LEAVES)
        val cherry = Textures.swatch(Block.CHERRY_LEAVES)
        assertTrue("cherry leaves are pink (red > green)", ((cherry shr 16) and 0xff) > ((cherry shr 8) and 0xff))
        assertTrue("oak leaves are green", ((oak shr 8) and 0xff) > ((oak shr 16) and 0xff))
    }

    @Test
    fun underwaterViewIsTintedBlue() {
        var found: Triple<Int, Int, Int>? = null
        loop@ for (x in 0 until world.sx) for (z in 0 until world.sz) {
            if (world.get(x, DemoWorld.SEA_LEVEL, z) == Block.WATER && world.get(x, DemoWorld.SEA_LEVEL - 2, z) == Block.WATER) { found = Triple(x, DemoWorld.SEA_LEVEL - 1, z); break@loop }
        }
        val (x, y, z) = found!!
        val pixels = IntArray(100 * 60).also { VoxelRenderer(world).render(it, 100, 60, Camera(x + 0.5, y + 0.5, z + 0.5, 0.0, 0.0)) }
        val blue = pixels.count { (it and 0xff) > ((it shr 16) and 0xff) + 20 }
        assertTrue("most of the underwater view is blue: $blue/${pixels.size}", blue > pixels.size * 0.6)
    }

    @Test
    fun renderFitsAFrameBudgetOnThisMachine() {
        val cam = spawnCamera(0.8, -0.1)
        render(cam, 280, 126) // warm up the JIT
        val start = System.nanoTime()
        repeat(5) { render(cam, 280, 126) }
        val ms = (System.nanoTime() - start) / 5 / 1e6
        println("RENDER_MS_PER_FRAME 280x126 = $ms")
        assertTrue("a 280x126 frame took ${ms}ms", ms < 120)
    }
}

/** Renders the pictures used on the website from the full island. They only have to be produced, not matched. */
class DemoPreviewTest {
    private val world = DemoWorld.generate()
    private val w = 320
    private val h = 180

    private fun shot(name: String, cam: Camera, selected: BlockHit? = null) {
        val renderer = VoxelRenderer(world).also { it.selected = selected }
        val pixels = IntArray(w * h).also { renderer.render(it, w, h, cam) }
        PngWriter.save(name, pixels, w, h)
        assertTrue(File("build/demo-previews/$name.png").length() > 5_000)
    }

    /** The ground (not a tree top) of a column: the highest block that is soil, sand, path or rock. */
    private fun groundY(x: Int, z: Int): Int {
        for (y in world.sy - 1 downTo 0) {
            val b = world.get(x, y, z)
            if (b == Block.GRASS || b == Block.BLOSSOM_GRASS || b == Block.SAND || b == Block.DIRT || b == Block.STONE || b == Block.PATH) return y
        }
        return 0
    }

    /** A camera about [distance] blocks from (tx, tz), standing on the open ground, looking at the point. */
    private fun cameraTowards(tx: Int, ty: Int, tz: Int, distance: Int, pitch: Double = 0.12): Camera {
        // Try spots around the target until one is clear of branches and leaves.
        for (angle in 0 until 16) {
            val a = angle * Math.PI / 8
            val cx = (tx + Math.round(Math.cos(a) * distance)).toInt().coerceIn(2, world.sx - 3)
            val cz = (tz + Math.round(Math.sin(a) * distance)).toInt().coerceIn(2, world.sz - 3)
            val ground = groundY(cx, cz)
            val clear = (1..3).all { world.get(cx, ground + it, cz) == Block.AIR }
            if (!clear || ground < DemoWorld.SEA_LEVEL + 1) continue
            val yaw = Math.atan2((tx - cx).toDouble(), (tz - cz).toDouble())
            return Camera(cx + 0.5, ground + 1 + Player.EYE_HEIGHT, cz + 0.5, yaw, pitch)
        }
        return Camera(tx + 0.5, ty + 12.0, tz + 12.5, Math.PI, -0.4)
    }

    @Test
    fun writePreviewImages() {
        val (vx, vy, vz) = world.villageCenter!!
        val (sx, sy, sz) = world.findSpawn()

        // The village from the road, looking north past the well to the first row of houses.
        shot("demo-village", Camera(vx + 11.5, vy + 1 + Player.EYE_HEIGHT, vz + 0.5, -Math.PI / 2 + 0.05, 0.02))
        // And from the air.
        shot("demo-village-aerial", Camera(vx + 0.5, vy + 17.0, vz + 34.0, Math.PI, -0.42))

        // A cherry tree in the Cherry Blossom biome.
        var cherry: Triple<Int, Int, Int>? = null
        var oak: Triple<Int, Int, Int>? = null
        loop@ for (x in 20 until world.sx - 20) for (z in 20 until world.sz - 20) {
            if (Math.hypot((x - vx).toDouble(), (z - vz).toDouble()) < 40) continue
            for (y in DemoWorld.SEA_LEVEL + 3 until 30) {
                if (cherry == null && world.get(x, y, z) == Block.BLOSSOM_GRASS && world.get(x, y + 1, z) == Block.CHERRY_LOG) cherry = Triple(x, y + 3, z)
                if (oak == null && world.get(x, y, z) == Block.GRASS && world.get(x, y + 1, z) == Block.WOOD) oak = Triple(x, y + 3, z)
            }
            if (cherry != null && oak != null) break@loop
        }
        val (cx, cy, cz) = cherry!!
        shot("demo-cherry", cameraTowards(cx, cy - 2, cz, 22, 0.08))
        val (ox, oy, oz) = oak!!
        shot("demo-oak", cameraTowards(ox, oy, oz, 13, 0.18))

        // Mining: the first iron ore found underground, seen from a small tunnel face.
        val ore = (20 until world.sx - 20).asSequence().flatMap { x -> (20 until world.sz - 20 step 3).asSequence().map { z -> x to z } }
            .firstNotNullOfOrNull { (x, z) ->
                val top = world.surfaceY(x, z)
                (4 until top - 2).firstOrNull { world.get(x, it, z) == Block.IRON_ORE }?.let { Triple(x, it, z) }
            }
        assertTrue("iron ore exists to show", ore != null)
    }
}
