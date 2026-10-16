package com.vclient.android.demo

/** One ingredient: [count] of any of the [options] (for example "any planks"). */
class Ingredient(val options: List<Item>, val count: Int, val label: String = options.first().label)

/** Turns ingredients into [count] of [result]. Recipes marked [needsTable] only work near a Crafting Table. */
class Recipe(val result: Item, val count: Int, val needs: List<Ingredient>, val needsTable: Boolean) {
    val ingredientText: String get() = needs.joinToString(" + ") { "${it.count}× ${it.label}" }

    /** Why this can't be crafted right now, or null when it can. */
    fun blocker(inventory: Inventory, nearTable: Boolean): String? {
        if (needsTable && !nearTable) return "Stand near a Crafting Table"
        val lacking = needs.firstOrNull { inventory.countAny(it.options) < it.count }
        if (lacking != null) return "Need ${lacking.count}× ${lacking.label}"
        if (!inventory.canFit(result, count)) return "Backpack is full"
        return null
    }
}

/** Everything that can be made. Nothing in the Demo World is handed out: it is gathered, then crafted. */
object Recipes {
    private val ANY_PLANKS = listOf(Item.PLANKS, Item.CHERRY_PLANKS)

    private fun planks(n: Int) = Ingredient(ANY_PLANKS, n, "Planks")
    private fun stick(n: Int) = Ingredient(listOf(Item.STICK), n)

    val all: List<Recipe> = buildList {
        add(Recipe(Item.PLANKS, 4, listOf(Ingredient(listOf(Item.OAK_LOG), 1)), needsTable = false))
        add(Recipe(Item.CHERRY_PLANKS, 4, listOf(Ingredient(listOf(Item.CHERRY_LOG), 1)), needsTable = false))
        add(Recipe(Item.STICK, 4, listOf(planks(2)), needsTable = false))
        add(Recipe(Item.TABLE, 1, listOf(planks(4)), needsTable = false))

        add(Recipe(Item.WOOD_PICK, 1, listOf(planks(3), stick(2)), needsTable = true))
        add(Recipe(Item.WOOD_AXE, 1, listOf(planks(3), stick(2)), needsTable = true))
        add(Recipe(Item.WOOD_SHOVEL, 1, listOf(planks(1), stick(2)), needsTable = true))

        val cobble = { n: Int -> Ingredient(listOf(Item.COBBLE), n) }
        add(Recipe(Item.STONE_PICK, 1, listOf(cobble(3), stick(2)), needsTable = true))
        add(Recipe(Item.STONE_AXE, 1, listOf(cobble(3), stick(2)), needsTable = true))
        add(Recipe(Item.STONE_SHOVEL, 1, listOf(cobble(1), stick(2)), needsTable = true))

        add(Recipe(Item.IRON_INGOT, 1, listOf(Ingredient(listOf(Item.IRON_ORE), 1), Ingredient(listOf(Item.COAL), 1)), needsTable = true))
        val iron = { n: Int -> Ingredient(listOf(Item.IRON_INGOT), n) }
        add(Recipe(Item.IRON_PICK, 1, listOf(iron(3), stick(2)), needsTable = true))
        add(Recipe(Item.IRON_AXE, 1, listOf(iron(3), stick(2)), needsTable = true))
        add(Recipe(Item.IRON_SHOVEL, 1, listOf(iron(1), stick(2)), needsTable = true))

        add(Recipe(Item.V_BLOCK, 1, listOf(Ingredient(listOf(Item.GOLD_NUGGET), 4)), needsTable = true))
    }

    /** Crafts once if possible. Returns whether it worked. */
    fun craft(inventory: Inventory, recipe: Recipe, nearTable: Boolean): Boolean {
        if (recipe.blocker(inventory, nearTable) != null) return false
        for (need in recipe.needs) inventory.removeAny(need.options, need.count)
        inventory.add(recipe.result, recipe.count)
        return true
    }
}
