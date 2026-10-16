package com.vclient.android.demo

/**
 * Block types of the V-Client Demo World. Everything here is original to V-Client: the names, the colours and
 * the procedural textures in [Textures]. Nothing comes from Minecraft.
 */
enum class Block(val id: Int, val label: String, val solid: Boolean) {
    AIR(0, "Air", false),
    GRASS(1, "Grass", true),
    DIRT(2, "Dirt", true),
    STONE(3, "Stone", true),
    SAND(4, "Sand", true),
    WOOD(5, "Wood", true),
    LEAVES(6, "Leaves", true),
    WATER(7, "Water", false),
    GOLD(8, "V Block", true);

    companion object {
        private val byId = entries.associateBy { it.id }
        fun of(id: Int): Block = byId[id] ?: AIR

        /** What the player can place, in hotbar order. */
        val HOTBAR = listOf(GRASS, DIRT, STONE, SAND, WOOD, LEAVES, GOLD)
    }
}
