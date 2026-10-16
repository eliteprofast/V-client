package com.vclient.android.demo

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** The explorer: a 0.6 x 1.8 block box that walks, jumps, swims and collides with solid blocks. */
class Player(var x: Double, var y: Double, var z: Double, var yaw: Double = 0.0, var pitch: Double = 0.0) {
    var vy = 0.0
    var onGround = false
        private set
    var inWater = false
        private set

    val eyeY: Double get() = y + EYE_HEIGHT

    /** Does the player's box, placed with its feet at (px, py, pz), overlap any solid block? */
    fun collides(world: DemoWorld, px: Double, py: Double, pz: Double): Boolean {
        val minX = floor(px - HALF_WIDTH).toInt()
        val maxX = floor(px + HALF_WIDTH).toInt()
        val minY = floor(py).toInt()
        val maxY = floor(py + HEIGHT - 1e-6).toInt()
        val minZ = floor(pz - HALF_WIDTH).toInt()
        val maxZ = floor(pz + HALF_WIDTH).toInt()
        for (bx in minX..maxX) for (by in minY..maxY) for (bz in minZ..maxZ) {
            // The sides of the island are invisible walls; the sky and the void below are open.
            if (bx < 0 || bx >= world.sx || bz < 0 || bz >= world.sz) return true
            if (world.get(bx, by, bz).solid) return true
        }
        return false
    }

    /** True when the box overlaps the given block cell (used to stop the player placing a block inside themself). */
    fun overlapsBlock(bx: Int, by: Int, bz: Int): Boolean =
        bx + 1 > x - HALF_WIDTH && bx < x + HALF_WIDTH &&
            by + 1 > y && by < y + HEIGHT &&
            bz + 1 > z - HALF_WIDTH && bz < z + HALF_WIDTH

    /**
     * Advances one frame. [forward] and [right] are stick values in -1..1; [jump] is whether the jump button is held.
     */
    fun update(dt: Double, forward: Double, right: Double, jump: Boolean, world: DemoWorld) {
        inWater = world.get(floor(x).toInt(), floor(y + 0.4).toInt(), floor(z).toInt()) == Block.WATER

        val speed = if (inWater) SWIM_SPEED else WALK_SPEED
        val fx = sin(yaw)
        val fz = cos(yaw)
        var wishX = fx * forward + fz * right
        var wishZ = fz * forward - fx * right
        val length = Math.hypot(wishX, wishZ)
        if (length > 1.0) { wishX /= length; wishZ /= length }
        val vx = wishX * speed
        val vz = wishZ * speed

        if (inWater) {
            vy -= WATER_GRAVITY * dt
            vy *= 0.92
            if (jump) vy = SWIM_UP
        } else {
            vy -= GRAVITY * dt
            if (jump && onGround) vy = JUMP_SPEED
        }
        if (vy < -TERMINAL) vy = -TERMINAL

        // Move one axis at a time, in small steps, so fast falls can't tunnel through floors.
        val steps = (Math.max(Math.abs(vx), Math.max(Math.abs(vz), Math.abs(vy))) * dt / 0.25).toInt() + 1
        val sdt = dt / steps
        onGround = false
        for (i in 0 until steps) {
            val nx = x + vx * sdt
            if (!collides(world, nx, y, z)) x = nx
            val nz = z + vz * sdt
            if (!collides(world, x, y, nz)) z = nz
            val ny = y + vy * sdt
            if (!collides(world, x, ny, z)) {
                y = ny
            } else {
                if (vy < 0) onGround = true
                vy = 0.0
            }
        }

        if (y < -20.0) { // fell off the island: back to the beach
            val (sx, sy, sz) = world.findSpawn()
            x = sx; y = sy; z = sz; vy = 0.0
        }
    }

    companion object {
        const val HALF_WIDTH = 0.3
        const val HEIGHT = 1.8
        const val EYE_HEIGHT = 1.62
        const val WALK_SPEED = 4.3
        const val SWIM_SPEED = 2.4
        const val GRAVITY = 24.0
        const val WATER_GRAVITY = 6.0
        const val JUMP_SPEED = 8.2
        const val SWIM_UP = 3.4
        const val TERMINAL = 40.0
    }
}
