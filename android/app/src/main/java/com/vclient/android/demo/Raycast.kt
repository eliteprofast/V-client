package com.vclient.android.demo

import kotlin.math.abs
import kotlin.math.floor

/** The solid block a ray hit, the face it entered through (nx/ny/nz point out of the block) and the distance. */
class BlockHit(val x: Int, val y: Int, val z: Int, val nx: Int, val ny: Int, val nz: Int, val dist: Double)

/**
 * Walks a ray through the block grid (Amanatides-Woo) and returns the first solid block within [maxDist].
 * Water and air are passed through, so the player can break and place blocks under water.
 */
fun DemoWorld.raycast(ox: Double, oy: Double, oz: Double, dx: Double, dy: Double, dz: Double, maxDist: Double): BlockHit? {
    var x = floor(ox).toInt()
    var y = floor(oy).toInt()
    var z = floor(oz).toInt()
    val stepX = if (dx > 0) 1 else -1
    val stepY = if (dy > 0) 1 else -1
    val stepZ = if (dz > 0) 1 else -1
    val inf = Double.POSITIVE_INFINITY
    val tDeltaX = if (dx == 0.0) inf else abs(1.0 / dx)
    val tDeltaY = if (dy == 0.0) inf else abs(1.0 / dy)
    val tDeltaZ = if (dz == 0.0) inf else abs(1.0 / dz)
    var tMaxX = if (dx == 0.0) inf else if (dx > 0) (x + 1 - ox) / dx else (ox - x) / -dx
    var tMaxY = if (dy == 0.0) inf else if (dy > 0) (y + 1 - oy) / dy else (oy - y) / -dy
    var tMaxZ = if (dz == 0.0) inf else if (dz > 0) (z + 1 - oz) / dz else (oz - z) / -dz

    while (true) {
        val t: Double
        var nx = 0
        var ny = 0
        var nz = 0
        if (tMaxX < tMaxY && tMaxX < tMaxZ) {
            x += stepX; t = tMaxX; tMaxX += tDeltaX; nx = -stepX
        } else if (tMaxY < tMaxZ) {
            y += stepY; t = tMaxY; tMaxY += tDeltaY; ny = -stepY
        } else {
            z += stepZ; t = tMaxZ; tMaxZ += tDeltaZ; nz = -stepZ
        }
        if (t > maxDist) return null
        if (get(x, y, z).solid) return BlockHit(x, y, z, nx, ny, nz, t)
        // Left the world going outward: nothing more to hit.
        if ((x < 0 && stepX < 0) || (x >= sx && stepX > 0) || (z < 0 && stepZ < 0) || (z >= sz && stepZ > 0) ||
            (y < 0 && stepY < 0) || (y >= sy && stepY > 0)
        ) return null
    }
}
