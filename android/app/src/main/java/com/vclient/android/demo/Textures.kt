package com.vclient.android.demo

/**
 * Procedural 8 x 8 block textures, drawn in code. They are original V-Client artwork: flat colours with hashed
 * speckle, so there are no image files and nothing is copied from any game.
 */
object Textures {
    const val TOP = 0
    const val BOTTOM = 1
    const val SIDE = 2

    private fun rgb(r: Int, g: Int, b: Int) = (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    private fun noise(a: Int, b: Int, c: Int, d: Int): Int {
        var h = a * 374761393 + b * 668265263 + c * 1274126177 + d * 362437
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return h and 0x7fffffff
    }

    /** Colour (0xRRGGBB) of texel (ix, iy) in 0..7 on the given face (TOP, BOTTOM or SIDE) of a block. */
    fun texel(block: Block, face: Int, ix: Int, iy: Int): Int {
        val n = noise(block.id, face, ix, iy)
        val speck = (n % 29) - 14 // -14..14
        return when (block) {
            Block.GRASS -> when {
                face == TOP -> rgb(84 + speck, 158 + speck, 66 + speck / 2)
                face == SIDE && iy < 2 -> rgb(84 + speck, 150 + speck, 62)
                else -> rgb(118 + speck, 84 + speck, 58 + speck / 2)
            }
            Block.DIRT -> rgb(118 + speck, 84 + speck, 58 + speck / 2)
            Block.STONE -> {
                val dark = if (n % 7 == 0) -22 else 0
                rgb(124 + speck + dark, 129 + speck + dark, 140 + speck + dark)
            }
            Block.SAND -> rgb(224 + speck / 2, 208 + speck / 2, 150 + speck / 2)
            Block.WOOD -> if (face == SIDE) {
                val stripe = if (ix % 3 == 0) -18 else 0
                rgb(112 + stripe + speck / 2, 80 + stripe + speck / 2, 46 + stripe / 2)
            } else {
                val ring = maxOf(Math.abs(ix - 3.5), Math.abs(iy - 3.5)).toInt()
                val tone = if (ring % 2 == 0) 22 else -6
                rgb(150 + tone, 112 + tone, 66 + tone / 2)
            }
            Block.LEAVES -> if (n % 5 == 0) rgb(28, 78, 42) else rgb(44 + speck, 118 + speck, 58 + speck / 2)
            Block.GOLD -> {
                // V-Client's own block: gold with a dark "V" on every face.
                val onV = (iy in 0..5 && (ix == iy / 2 || ix == 7 - iy / 2)) || (iy in 6..7 && (ix == 3 || ix == 4))
                if (onV) rgb(16, 19, 33) else rgb(231 + speck / 2, 191 + speck / 2, 104 + speck / 2)
            }
            Block.WATER -> rgb(40, 110, 190)
            Block.AIR -> rgb(0, 0, 0)
        }
    }

    /** Average colour of a block's top face, for hotbar swatches. */
    fun swatch(block: Block): Int {
        var r = 0
        var g = 0
        var b = 0
        for (iy in 0..7) for (ix in 0..7) {
            val c = texel(block, TOP, ix, iy)
            r += (c shr 16) and 0xff; g += (c shr 8) and 0xff; b += c and 0xff
        }
        return rgb(r / 64, g / 64, b / 64)
    }
}
