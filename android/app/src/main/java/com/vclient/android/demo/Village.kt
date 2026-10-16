package com.vclient.android.demo

import kotlin.math.abs

/**
 * The island's one village: a flattened clearing with a well in the middle, crossing paths, six houses and a small
 * farm. One house holds a Crafting Table so a new player can start crafting right away.
 */
object Village {
    const val RADIUS = 22

    class Site(val cx: Int, val cz: Int, val level: Int)

    /** Finds a spot near the middle of the island where the ground is dry and fairly level. */
    fun chooseSite(world: DemoWorld, heights: IntArray): Site {
        val mx = world.sx / 2
        val mz = world.sz / 2
        fun heightAt(x: Int, z: Int) = heights[z.coerceIn(0, world.sz - 1) * world.sx + x.coerceIn(0, world.sx - 1)]
        var r = 0
        while (r <= 160) {
            for (dx in -r..r step 6) for (dz in -r..r step 6) {
                if (maxOf(abs(dx), abs(dz)) != r) continue
                val cx = mx + dx
                val cz = mz + dz
                if (cx - RADIUS - 8 < 0 || cz - RADIUS - 8 < 0 || cx + RADIUS + 8 >= world.sx || cz + RADIUS + 8 >= world.sz) continue
                var lo = Int.MAX_VALUE
                var hi = Int.MIN_VALUE
                var sum = 0
                var count = 0
                for (sx in -2..2) for (sz in -2..2) {
                    val h = heightAt(cx + sx * 9, cz + sz * 9)
                    lo = minOf(lo, h); hi = maxOf(hi, h); sum += h; count++
                }
                if (lo >= DemoWorld.SEA_LEVEL + 2 && hi <= 26 && hi - lo <= 7) {
                    return Site(cx, cz, (sum / count).coerceIn(DemoWorld.SEA_LEVEL + 3, 24))
                }
            }
            r += 6
        }
        return Site(mx, mz, DemoWorld.SEA_LEVEL + 4)
    }

    /** Levels the ground inside the village and blends it into the surrounding hills. */
    fun flatten(world: DemoWorld, heights: IntArray, site: Site) {
        val reach = RADIUS + 6
        for (dz in -reach..reach) for (dx in -reach..reach) {
            val x = site.cx + dx
            val z = site.cz + dz
            if (x !in 0 until world.sx || z !in 0 until world.sz) continue
            val dist = DemoWorld.hypotenuse(dx, dz)
            val i = z * world.sx + x
            if (dist <= RADIUS) {
                heights[i] = site.level
            } else if (dist <= reach) {
                val t = (dist - RADIUS) / 6.0
                heights[i] = (site.level * (1 - t) + heights[i] * t).toInt()
            }
        }
    }

    fun build(world: DemoWorld, site: Site) {
        val g = site.level
        val cx = site.cx
        val cz = site.cz

        fun inside(x: Int, z: Int) = DemoWorld.hypotenuse(x - cx, z - cz) <= RADIUS - 1

        // Two crossing paths, three blocks wide.
        for (d in -(RADIUS - 2)..(RADIUS - 2)) for (w in -1..1) {
            if (inside(cx + d, cz + w)) world.set(cx + d, g, cz + w, Block.PATH)
            if (inside(cx + w, cz + d)) world.set(cx + w, g, cz + d, Block.PATH)
        }

        well(world, cx, cz, g)

        // Six houses in two rows facing the east-west road; the middle house on the north side has a Crafting Table.
        val xs = intArrayOf(cx - 14, cx - 5, cx + 4)
        var index = 0
        for (x0 in xs) {
            house(world, g, x0, cz - 13, 7, 6, doorSouth = true, cherry = index % 2 == 1, table = x0 == cx - 5)
            connector(world, g, x0 + 3, cz - 7, cz - 2)
            index++
        }
        for (x0 in xs) {
            house(world, g, x0, cz + 8, 7, 6, doorSouth = false, cherry = index % 2 == 1, table = false)
            connector(world, g, x0 + 3, cz + 7, cz + 2)
            index++
        }

        farm(world, g, cx + 14, cz - 6)
    }

    private fun well(world: DemoWorld, cx: Int, cz: Int, g: Int) {
        for (dx in -1..1) for (dz in -1..1) {
            world.set(cx + dx, g, cz + dz, Block.COBBLE)
            if (dx != 0 || dz != 0) world.set(cx + dx, g + 1, cz + dz, Block.COBBLE)
        }
        world.set(cx, g, cz, Block.WATER)
        world.set(cx, g + 1, cz, Block.AIR)
        for (corner in listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)) {
            world.set(cx + corner.first, g + 2, cz + corner.second, Block.WOOD)
            world.set(cx + corner.first, g + 3, cz + corner.second, Block.WOOD)
        }
        for (dx in -1..1) for (dz in -1..1) world.set(cx + dx, g + 4, cz + dz, Block.PLANKS)
    }

    private fun connector(world: DemoWorld, g: Int, x: Int, fromZ: Int, toZ: Int) {
        for (z in minOf(fromZ, toZ)..maxOf(fromZ, toZ)) world.set(x, g, z, Block.PATH)
    }

    private fun house(world: DemoWorld, g: Int, x0: Int, z0: Int, w: Int, d: Int, doorSouth: Boolean, cherry: Boolean, table: Boolean) {
        val wall = if (cherry) Block.CHERRY_PLANKS else Block.PLANKS
        val post = if (cherry) Block.CHERRY_LOG else Block.WOOD
        for (x in x0 until x0 + w) for (z in z0 until z0 + d) {
            world.set(x, g, z, Block.COBBLE)
            val edgeX = x == x0 || x == x0 + w - 1
            val edgeZ = z == z0 || z == z0 + d - 1
            for (y in g + 1..g + 3) {
                world.set(x, y, z, if (edgeX && edgeZ) post else if (edgeX || edgeZ) wall else Block.AIR)
            }
        }
        val doorX = x0 + w / 2
        val doorZ = if (doorSouth) z0 + d - 1 else z0
        world.set(doorX, g + 1, doorZ, Block.AIR)
        world.set(doorX, g + 2, doorZ, Block.AIR)
        world.set(x0, g + 2, z0 + d / 2, Block.AIR) // windows
        world.set(x0 + w - 1, g + 2, z0 + d / 2, Block.AIR)
        for (x in x0 until x0 + w) for (z in z0 until z0 + d) world.set(x, g + 4, z, wall)
        for (x in x0 + 1 until x0 + w - 1) for (z in z0 + 1 until z0 + d - 1) world.set(x, g + 5, z, wall)
        for (x in x0 + 2 until x0 + w - 2) for (z in z0 + 2 until z0 + d - 2) world.set(x, g + 6, z, wall)
        if (table) world.set(x0 + 1, g + 1, z0 + 1, Block.TABLE)
    }

    /** A 7 x 12 plot of farmland with a water ditch down the middle. */
    private fun farm(world: DemoWorld, g: Int, x0: Int, z0: Int) {
        for (x in x0 until x0 + 7) for (z in z0 until z0 + 12) {
            val ditch = z == z0 + 5 || z == z0 + 6
            world.set(x, g, z, if (ditch) Block.WATER else Block.FARMLAND)
            world.set(x, g + 1, z, if (ditch || x == x0 || x == x0 + 6) Block.AIR else Block.CROP)
            for (y in g + 2..g + 6) world.set(x, y, z, Block.AIR)
        }
    }
}
