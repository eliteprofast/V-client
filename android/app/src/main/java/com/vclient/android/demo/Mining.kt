package com.vclient.android.demo

/** How long blocks take to break with what the player holds, and what they drop. */
object Mining {
    private fun speed(tier: Int) = when (tier) {
        1 -> 2.0
        2 -> 3.5
        3 -> 6.0
        else -> 1.0
    }

    /**
     * Seconds to break [block] while holding [held], or null when it can't be broken at all: stone, cobblestone and
     * ores need a pickaxe of at least the right tier. The right tool class also makes any block faster.
     */
    fun timeToBreak(block: Block, held: Item?): Double? {
        if (!block.solid) return 0.0
        val heldClass = held?.tool ?: ToolClass.NONE
        val tier = held?.tier ?: 0
        val matches = block.tool != ToolClass.NONE && heldClass == block.tool
        if (block.minTier > 0 && (!matches || tier < block.minTier)) return null
        val factor = if (matches) speed(tier) else 1.0
        return maxOf(0.15, block.hardness / factor)
    }

    /** A short reason for the player when [timeToBreak] is null. */
    fun requirement(block: Block): String = when (block.minTier) {
        1 -> "Needs a pickaxe"
        2 -> "Needs a stone or iron pickaxe"
        3 -> "Needs an iron pickaxe"
        else -> ""
    }

    /** What breaking [block] at (x, y, z) leaves behind, or null for nothing. Leaves sometimes drop a stick. */
    fun drop(block: Block, x: Int, y: Int, z: Int): Pair<Item, Int>? = when (block) {
        Block.GRASS, Block.BLOSSOM_GRASS, Block.DIRT, Block.PATH, Block.FARMLAND -> Item.DIRT to 1
        Block.STONE, Block.COBBLE -> Item.COBBLE to 1
        Block.SAND -> Item.SAND to 1
        Block.WOOD -> Item.OAK_LOG to 1
        Block.CHERRY_LOG -> Item.CHERRY_LOG to 1
        Block.LEAVES, Block.CHERRY_LEAVES ->
            if (DemoWorld.hash3(77, x, y, z) < 0.25) Item.STICK to 1 else null
        Block.PLANKS -> Item.PLANKS to 1
        Block.CHERRY_PLANKS -> Item.CHERRY_PLANKS to 1
        Block.TABLE -> Item.TABLE to 1
        Block.COAL_ORE -> Item.COAL to 1
        Block.IRON_ORE -> Item.IRON_ORE to 1
        Block.GOLD_ORE -> Item.GOLD_NUGGET to 2
        Block.GOLD -> Item.V_BLOCK to 1
        Block.CROP, Block.AIR, Block.WATER -> null
    }
}
