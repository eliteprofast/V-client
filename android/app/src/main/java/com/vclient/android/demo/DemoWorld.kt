package com.vclient.android.demo

import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

/** A small block world: 64 x 40 x 64 blocks held in one byte array (about 160 KB), generated from a seed. */
class DemoWorld(val sx: Int = 64, val sy: Int = 40, val sz: Int = 64) {
    val blocks = ByteArray(sx * sy * sz)

    private fun index(x: Int, y: Int, z: Int) = (y * sz + z) * sx + x

    fun inBounds(x: Int, y: Int, z: Int) = x in 0 until sx && y in 0 until sy && z in 0 until sz

    /** Block id at a position; anything outside the world counts as air. */
    fun getId(x: Int, y: Int, z: Int): Int = if (inBounds(x, y, z)) blocks[index(x, y, z)].toInt() else 0

    fun get(x: Int, y: Int, z: Int): Block = Block.of(getId(x, y, z))

    fun set(x: Int, y: Int, z: Int, block: Block) {
        if (inBounds(x, y, z)) blocks[index(x, y, z)] = block.id.toByte()
    }

    /** Highest solid, non-water block in a column, or -1 when the column is empty. */
    fun surfaceY(x: Int, z: Int): Int {
        for (y in sy - 1 downTo 0) if (get(x, y, z).solid) return y
        return -1
    }

    /** A grassy spot near the middle of the island, standing height included. */
    fun findSpawn(): Triple<Double, Double, Double> {
        val cx = sx / 2
        val cz = sz / 2
        for (r in 0..24) {
            for (dx in -r..r) for (dz in -r..r) {
                if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) != r) continue
                val x = cx + dx
                val z = cz + dz
                val y = surfaceY(x, z)
                if (y > SEA_LEVEL + 1 && get(x, y, z) == Block.GRASS && get(x, y + 1, z) == Block.AIR && get(x, y + 2, z) == Block.AIR) {
                    return Triple(x + 0.5, y + 1.0, z + 0.5)
                }
            }
        }
        return Triple(cx + 0.5, (sy - 2).toDouble(), cz + 0.5)
    }

    companion object {
        const val SEA_LEVEL = 11
        const val DEFAULT_SEED = 20251016L

        /** Builds the island: hills of grass over dirt over stone, sand beaches, a ring of water, and trees. */
        fun generate(seed: Long = DEFAULT_SEED): DemoWorld {
            val world = DemoWorld()
            val heights = IntArray(world.sx * world.sz)
            for (z in 0 until world.sz) for (x in 0 until world.sx) {
                heights[z * world.sx + x] = terrainHeight(seed, x, z, world)
            }
            for (z in 0 until world.sz) for (x in 0 until world.sx) {
                val h = heights[z * world.sx + x]
                val beach = h <= SEA_LEVEL + 1
                for (y in 0..h) {
                    val block = when {
                        y == h -> if (beach) Block.SAND else Block.GRASS
                        y >= h - 3 -> if (beach) Block.SAND else Block.DIRT
                        else -> Block.STONE
                    }
                    world.set(x, y, z, block)
                }
                for (y in h + 1..SEA_LEVEL) world.set(x, y, z, Block.WATER)
            }
            plantTrees(world, seed, heights)
            return world
        }

        fun terrainHeight(seed: Long, x: Int, z: Int, world: DemoWorld): Int {
            val n = 0.5 * valueNoise(seed, x / 24.0, z / 24.0) +
                0.3 * valueNoise(seed + 1, x / 12.0, z / 12.0) +
                0.2 * valueNoise(seed + 2, x / 6.0, z / 6.0)
            val land = 5.0 + n * 20.0 // hills between roughly 5 and 25
            // Fade toward the edges so the island is ringed by water.
            val d = hypot(x - world.sx / 2.0, z - world.sz / 2.0) / (world.sx / 2.0)
            val fall = smooth((1.15 - d).coerceIn(0.0, 1.0))
            val h = 3.0 + (land - 3.0) * fall
            return h.roundToInt().coerceIn(1, world.sy - 12)
        }

        private fun plantTrees(world: DemoWorld, seed: Long, heights: IntArray) {
            for (z in 4 until world.sz - 4) for (x in 4 until world.sx - 4) {
                val h = heights[z * world.sx + x]
                if (world.get(x, h, z) != Block.GRASS || h + 9 >= world.sy) continue
                if (hash2(seed + 77, x, z) > 0.014) continue
                val trunk = 4 + (hash2(seed + 78, x, z) * 2).toInt()
                val top = h + trunk
                for (dy in -2..2) for (dx in -2..2) for (dz in -2..2) {
                    val r = dx * dx + dz * dz + (dy * 1.2) * (dy * 1.2)
                    if (r <= 5.6 && world.get(x + dx, top + dy, z + dz) == Block.AIR) {
                        world.set(x + dx, top + dy, z + dz, Block.LEAVES)
                    }
                }
                for (y in h + 1..top) world.set(x, y, z, Block.WOOD)
            }
        }

        /** Deterministic value in [0, 1) for a lattice point. */
        fun hash2(seed: Long, x: Int, z: Int): Double {
            var h = seed xor (x * 374761393L) xor (z * 668265263L)
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
    }
}
