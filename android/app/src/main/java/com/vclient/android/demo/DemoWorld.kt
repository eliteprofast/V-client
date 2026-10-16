package com.vclient.android.demo

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A block world held in one byte array. The default constructor makes a small world for tests; [generate] builds the
 * full island of 32 x 32 chunks (512 x 512 blocks) from a seed. Only the player's changes are remembered in [edits],
 * so saving is tiny: the seed plus those edits rebuild the same world.
 */
class DemoWorld(val sx: Int = 64, val sy: Int = 40, val sz: Int = 64, val seed: Long = DEFAULT_SEED) {
    val blocks = ByteArray(sx * sy * sz)
    private val edits = HashMap<Int, Byte>()

    /** When true, every [set] is recorded as a player edit. Turned on once generation is finished. */
    var tracking = false

    /** The village's well: x, ground level y, z. Null in small test worlds. */
    var villageCenter: Triple<Int, Int, Int>? = null

    fun index(x: Int, y: Int, z: Int) = (y * sz + z) * sx + x

    fun inBounds(x: Int, y: Int, z: Int) = x in 0 until sx && y in 0 until sy && z in 0 until sz

    /** Block id at a position; anything outside the world counts as air. */
    fun getId(x: Int, y: Int, z: Int): Int = if (inBounds(x, y, z)) blocks[index(x, y, z)].toInt() else 0

    fun get(x: Int, y: Int, z: Int): Block = Block.of(getId(x, y, z))

    fun set(x: Int, y: Int, z: Int, block: Block) {
        if (!inBounds(x, y, z)) return
        val i = index(x, y, z)
        blocks[i] = block.id.toByte()
        if (tracking) edits[i] = block.id.toByte()
    }

    /** The player's changes as (block index, block id) pairs. */
    fun editEntries(): List<Pair<Int, Byte>> = edits.entries.map { it.key to it.value }

    /** Re-applies a saved edit. */
    fun applyEdit(index: Int, id: Byte) {
        if (index !in blocks.indices) return
        blocks[index] = id
        edits[index] = id
    }

    val editCount: Int get() = edits.size

    fun biomeAt(x: Int, z: Int): Biome = if (isCherry(seed, x, z)) Biome.CHERRY_BLOSSOM else Biome.OAK_FOREST

    /** Highest solid block in a column, or -1 when the column is empty. */
    fun surfaceY(x: Int, z: Int): Int {
        for (y in sy - 1 downTo 0) if (get(x, y, z).solid) return y
        return -1
    }

    /** Where a new player starts: on the village road if there is one, else on grass near the middle. */
    fun findSpawn(): Triple<Double, Double, Double> {
        villageCenter?.let { (vx, vy, vz) -> return Triple(vx + 0.5, vy + 1.0, vz + 4.5) }
        val cx = sx / 2
        val cz = sz / 2
        for (r in 0..24) {
            for (dx in -r..r) for (dz in -r..r) {
                if (maxOf(abs(dx), abs(dz)) != r) continue
                val x = cx + dx
                val z = cz + dz
                val y = surfaceY(x, z)
                val top = if (y >= 0) get(x, y, z) else Block.AIR
                if (y > SEA_LEVEL + 1 && (top == Block.GRASS || top == Block.BLOSSOM_GRASS) &&
                    get(x, y + 1, z) == Block.AIR && get(x, y + 2, z) == Block.AIR
                ) return Triple(x + 0.5, y + 1.0, z + 0.5)
            }
        }
        return Triple(cx + 0.5, (sy - 2).toDouble(), cz + 0.5)
    }

    companion object {
        const val SEA_LEVEL = 12
        const val DEFAULT_SEED = 20251017L
        const val CHUNK = 16
        const val HEIGHT = 48

        /** Builds the whole island: [chunks] x [chunks] chunks of 16 x 16 blocks (32 x 32 by default). */
        fun generate(seed: Long = DEFAULT_SEED, chunks: Int = 32): DemoWorld {
            val world = DemoWorld(chunks * CHUNK, HEIGHT, chunks * CHUNK, seed)
            val heights = IntArray(world.sx * world.sz)
            for (z in 0 until world.sz) for (x in 0 until world.sx) {
                heights[z * world.sx + x] = terrainHeight(seed, x, z, world)
            }
            val site = Village.chooseSite(world, heights)
            Village.flatten(world, heights, site)
            for (z in 0 until world.sz) for (x in 0 until world.sx) fillColumn(world, seed, x, z, heights[z * world.sx + x])
            Village.build(world, site)
            plantTrees(world, seed, heights, site)
            world.villageCenter = Triple(site.cx, site.level, site.cz)
            world.tracking = true
            return world
        }

        private fun fillColumn(world: DemoWorld, seed: Long, x: Int, z: Int, h: Int) {
            val beach = h <= SEA_LEVEL + 1
            val rock = h >= ROCK_LEVEL
            val grass = if (isCherry(seed, x, z)) Block.BLOSSOM_GRASS else Block.GRASS
            for (y in 0..h) {
                val block = when {
                    y == h -> if (beach) Block.SAND else if (rock) Block.STONE else grass
                    y >= h - 3 && !rock -> if (beach) Block.SAND else Block.DIRT
                    else -> {
                        val r = hash3(seed, x, y, z)
                        when {
                            y in 1..29 && r < 0.020 -> Block.COAL_ORE
                            y in 1..21 && r in 0.020..0.030 -> Block.IRON_ORE
                            y in 1..11 && r in 0.030..0.034 -> Block.GOLD_ORE
                            else -> Block.STONE
                        }
                    }
                }
                world.set(x, y, z, block)
            }
            for (y in h + 1..SEA_LEVEL) world.set(x, y, z, Block.WATER)
        }

        private const val ROCK_LEVEL = 29

        /** Ground height of a column: rolling hills and a few mountains, sinking into water toward the island's edge. */
        fun terrainHeight(seed: Long, x: Int, z: Int, world: DemoWorld): Int {
            val n = 0.55 * valueNoise(seed, x / 90.0, z / 90.0) +
                0.30 * valueNoise(seed + 1, x / 40.0, z / 40.0) +
                0.15 * valueNoise(seed + 2, x / 16.0, z / 16.0)
            val land = 6.0 + n * 28.0 // roughly 6..34
            val dx = abs(x - world.sx / 2.0) / (world.sx / 2.0)
            val dz = abs(z - world.sz / 2.0) / (world.sz / 2.0)
            val edge = maxOf(dx, dz)
            val fall = smooth(((1.0 - edge) / 0.16).coerceIn(0.0, 1.0))
            val h = 3.0 + (land - 3.0) * fall
            return h.roundToInt().coerceIn(1, world.sy - 12)
        }

        /** Two biomes in broad regions with a ragged border: Cherry Blossom and Oak Forest. */
        fun isCherry(seed: Long, x: Int, z: Int): Boolean =
            valueNoise(seed + 9, x / 150.0, z / 150.0) + (valueNoise(seed + 10, x / 22.0, z / 22.0) - 0.5) * 0.14 > 0.5

        private fun plantTrees(world: DemoWorld, seed: Long, heights: IntArray, site: Village.Site) {
            val keepOut = (Village.RADIUS + 6) * (Village.RADIUS + 6)
            for (z in 4 until world.sz - 4) for (x in 4 until world.sx - 4) {
                val h = heights[z * world.sx + x]
                if (h <= SEA_LEVEL + 1 || h >= ROCK_LEVEL - 2) continue
                val dxv = x - site.cx
                val dzv = z - site.cz
                if (dxv * dxv + dzv * dzv <= keepOut) continue
                val cherry = isCherry(seed, x, z)
                val chance = if (cherry) 0.020 else 0.030
                if (hash2(seed + 77, x, z) > chance) continue
                if (cherry) cherryTree(world, seed, x, h, z) else oakTree(world, seed, x, h, z)
            }
        }

        private fun oakTree(world: DemoWorld, seed: Long, x: Int, h: Int, z: Int) {
            val trunk = 4 + (hash2(seed + 78, x, z) * 2).toInt()
            val top = h + trunk
            for (dy in -2..2) for (dx in -2..2) for (dz in -2..2) {
                val r = dx * dx + dz * dz + (dy * 1.2) * (dy * 1.2)
                if (r <= 5.6 && world.get(x + dx, top + dy, z + dz) == Block.AIR) world.set(x + dx, top + dy, z + dz, Block.LEAVES)
            }
            for (y in h + 1..top) world.set(x, y, z, Block.WOOD)
        }

        /** Cherry trees: a slim trunk under a wide, flat canopy of pink leaves. */
        private fun cherryTree(world: DemoWorld, seed: Long, x: Int, h: Int, z: Int) {
            val trunk = 5 + (hash2(seed + 79, x, z) * 2).toInt()
            val top = h + trunk
            for (dy in -1..1) for (dx in -3..3) for (dz in -3..3) {
                val r = dx * dx + dz * dz + (dy * 2.0) * (dy * 2.0)
                if (r <= 10.5 && world.get(x + dx, top + dy, z + dz) == Block.AIR) world.set(x + dx, top + dy, z + dz, Block.CHERRY_LEAVES)
            }
            if (world.get(x, top + 2, z) == Block.AIR) world.set(x, top + 2, z, Block.CHERRY_LEAVES)
            for (y in h + 1..top) world.set(x, y, z, Block.CHERRY_LOG)
        }

        /** Deterministic value in [0, 1) for a lattice point. */
        fun hash2(seed: Long, x: Int, z: Int): Double {
            var h = seed xor (x * 374761393L) xor (z * 668265263L)
            h = (h xor (h ushr 13)) * 1274126177L
            h = h xor (h ushr 16)
            return (h and 0xFFFFFF).toDouble() / 0x1000000
        }

        fun hash3(seed: Long, x: Int, y: Int, z: Int): Double {
            var h = seed xor (x * 374761393L) xor (y * 668265263L) xor (z * 2147483647L)
            h = (h xor (h ushr 13)) * 1274126177L
            h = h xor (h ushr 16)
            return (h and 0xFFFFFF).toDouble() / 0x1000000
        }

        private fun smooth(t: Double) = t * t * (3 - 2 * t)

        private fun valueNoise(seed: Long, x: Double, z: Double): Double {
            val x0 = floor(x).toInt()
            val z0 = floor(z).toInt()
            val fx = smooth(x - x0)
            val fz = smooth(z - z0)
            val a = hash2(seed, x0, z0)
            val b = hash2(seed, x0 + 1, z0)
            val c = hash2(seed, x0, z0 + 1)
            val d = hash2(seed, x0 + 1, z0 + 1)
            return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz
        }

        internal fun hypotenuse(a: Int, b: Int) = hypot(a.toDouble(), b.toDouble())
    }
}
