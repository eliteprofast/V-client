package com.vclient.android.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

class VoxelRendererTest {
    private val world = DemoWorld.generate()

    private fun render(cam: Camera, w: Int = 320, h: Int = 180, selected: BlockHit? = null): IntArray {
        val renderer = VoxelRenderer(world)
        renderer.selected = selected
        return IntArray(w * h).also { renderer.render(it, w, h, cam) }
    }

    /** Writes an 8-bit RGB PNG (nearest-neighbour upscaled), with no image library needed. */
    private fun savePng(name: String, pixels: IntArray, w: Int, h: Int, scale: Int = 3) {
        val ow = w * scale
        val oh = h * scale
        val raw = ByteArrayOutputStream()
        for (y in 0 until oh) {
            raw.write(0) // filter: none
            for (x in 0 until ow) {
                val c = pixels[(y / scale) * w + x / scale]
                raw.write((c shr 16) and 0xff); raw.write((c shr 8) and 0xff); raw.write(c and 0xff)
            }
        }
        fun chunk(out: DataOutputStream, type: String, data: ByteArray) {
            out.writeInt(data.size)
            val body = type.toByteArray() + data
            out.write(body)
            out.writeInt(CRC32().also { it.update(body) }.value.toInt())
        }
        val file = File("build/demo-previews/$name.png").also { it.parentFile.mkdirs() }
        DataOutputStream(file.outputStream()).use { out ->
            out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
            chunk(out, "IHDR", ByteArrayOutputStream().also { b -> DataOutputStream(b).apply { writeInt(ow); writeInt(oh); write(byteArrayOf(8, 2, 0, 0, 0)) } }.toByteArray())
            val compressed = ByteArrayOutputStream()
            DeflaterOutputStream(compressed).use { it.write(raw.toByteArray()) }
            chunk(out, "IDAT", compressed.toByteArray())
            chunk(out, "IEND", ByteArray(0))
        }
    }

    private fun spawnCamera(yaw: Double, pitch: Double): Camera {
        val (x, y, z) = world.findSpawn()
        return Camera(x, y + Player.EYE_HEIGHT, z, yaw, pitch)
    }

    @Test
    fun rendersTerrainAndSkyNotAFlatColour() {
        val pixels = render(spawnCamera(0.8, 0.0))
        assertTrue("many distinct colours expected, got ${pixels.toSet().size}", pixels.toSet().size > 150)
        assertTrue("top rows should be sky (blue dominant)", (pixels[10] and 0xff) > ((pixels[10] shr 16) and 0xff))
        val greens = pixels.count { ((it shr 8) and 0xff) > ((it shr 16) and 0xff) + 30 && ((it shr 8) and 0xff) > (it and 0xff) + 30 }
        assertTrue("grass should show up, green pixels: $greens", greens > 300)
    }

    @Test
    fun movingTheCameraChangesTheImage() {
        val a = render(spawnCamera(0.0, 0.0))
        val b = render(spawnCamera(2.0, 0.0))
        assertTrue(!a.contentEquals(b))
    }

    @Test
    fun selectionOutlineAppearsOnTheTargetedBlock() {
        val cam = spawnCamera(0.8, -0.35)
        val cp = Math.cos(cam.pitch)
        val hit = world.raycast(cam.x, cam.y, cam.z, Math.sin(cam.yaw) * cp, Math.sin(cam.pitch), Math.cos(cam.yaw) * cp, 8.0)
        assertTrue("test setup: must be looking at a block", hit != null)
        val plain = render(cam)
        val outlined = render(cam, selected = hit)
        assertTrue("outline must change some pixels", !plain.contentEquals(outlined))
    }

    @Test
    fun placedGoldBlockShowsItsOwnColour() {
        val cam = spawnCamera(0.8, -0.4)
        val cp = Math.cos(cam.pitch)
        val hit = world.raycast(cam.x, cam.y, cam.z, Math.sin(cam.yaw) * cp, Math.sin(cam.pitch), Math.cos(cam.yaw) * cp, 8.0)!!
        val before = render(cam)
        world.set(hit.x + hit.nx, hit.y + hit.ny, hit.z + hit.nz, Block.GOLD)
        val after = render(cam)
        val gold = after.count { ((it shr 16) and 0xff) > 180 && ((it shr 8) and 0xff) > 140 && (it and 0xff) < 140 }
        assertTrue("gold pixels appear: $gold", gold > 40 && !before.contentEquals(after))
    }

    @Test
    fun underwaterViewIsTintedBlue() {
        val w = DemoWorld.generate()
        // Find a water column and put the camera in it.
        var found: Triple<Int, Int, Int>? = null
        loop@ for (x in 0 until w.sx) for (z in 0 until w.sz) {
            if (w.get(x, DemoWorld.SEA_LEVEL, z) == Block.WATER && w.get(x, DemoWorld.SEA_LEVEL - 2, z) == Block.WATER) { found = Triple(x, DemoWorld.SEA_LEVEL - 1, z); break@loop }
        }
        val (x, y, z) = found!!
        val pixels = IntArray(100 * 60).also { VoxelRenderer(w).render(it, 100, 60, Camera(x + 0.5, y + 0.5, z + 0.5, 0.0, 0.0)) }
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

    /** Renders the pictures used on the website. Not asserting looks, only that files are produced. */
    @Test
    fun writePreviewImages() {
        val w = 320
        val h = 180
        savePng("demo-spawn", render(spawnCamera(0.8, -0.05), w, h), w, h)
        savePng("demo-sky", render(spawnCamera(3.6, 0.45), w, h), w, h)
        // A hilltop view down toward the beach and water.
        var topX = 0; var topZ = 0; var top = 0
        for (x in 0 until world.sx) for (z in 0 until world.sz) if (world.surfaceY(x, z) > top) { top = world.surfaceY(x, z); topX = x; topZ = z }
        savePng("demo-hilltop", render(Camera(topX + 0.5, top + 1 + Player.EYE_HEIGHT, topZ + 0.5, 2.4, -0.18), w, h), w, h)
        // After building: a small gold tower, with the target outlined.
        val built = DemoGame(DemoWorld.generate(), Player(world.findSpawn().first, world.findSpawn().second, world.findSpawn().third, yaw = 0.8, pitch = -0.2))
        for (i in 0 until 3) {
            built.player.pitch = -0.3
            built.tick(1 / 60.0)
            built.selectedSlot = Block.HOTBAR.indexOf(Block.GOLD)
            built.placeBlock()
        }
        val renderer = VoxelRenderer(built.world).also { it.selected = built.target }
        val pixels = IntArray(w * h).also { renderer.render(it, w, h, built.camera()) }
        savePng("demo-build", pixels, w, h)
        assertEquals(true, File("build/demo-previews/demo-build.png").length() > 5_000)
    }
}
