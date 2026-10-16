package com.vclient.android.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CraftingTest {
    private fun recipe(result: Item) = Recipes.all.first { it.result == result }

    @Test
    fun inventoryStacksFillsAndReportsWhatDidNotFit() {
        val inv = Inventory()
        assertEquals(0, inv.add(Item.DIRT, 100))
        assertEquals(64, inv.slots[0]!!.count)
        assertEquals(36, inv.slots[1]!!.count)
        assertEquals("new items fill the hotbar first", Item.DIRT, inv.slots[0]!!.item)

        val tiny = Inventory(2)
        assertEquals(0, tiny.add(Item.WOOD_PICK, 1))
        assertEquals(0, tiny.add(Item.WOOD_AXE, 1))
        assertEquals("tools do not stack, so the third does not fit", 1, tiny.add(Item.WOOD_SHOVEL, 1))
        assertFalse(tiny.canFit(Item.WOOD_SHOVEL, 1))
    }

    @Test
    fun removingTakesFromSeveralStacksAndNeverGoesNegative() {
        val inv = Inventory()
        inv.add(Item.COBBLE, 70)
        assertFalse(inv.remove(Item.COBBLE, 71))
        assertEquals("a failed removal changes nothing", 70, inv.count(Item.COBBLE))
        assertTrue(inv.remove(Item.COBBLE, 66))
        assertEquals(4, inv.count(Item.COBBLE))
        assertNull("the emptied first stack disappears", inv.slots[0])
        assertEquals("the remainder stays in the second stack", 4, inv.slots[1]!!.count)
        inv.swap(1, 5)
        assertNull(inv.slots[1])
        assertEquals(4, inv.slots[5]!!.count)
    }

    @Test
    fun nothingIsHandedOutEverythingNeedsIngredients() {
        val inv = Inventory()
        for (recipe in Recipes.all) {
            assertNotNull("${recipe.result.label} must be blocked with an empty backpack", recipe.blocker(inv, nearTable = true))
            assertFalse(Recipes.craft(inv, recipe, nearTable = true))
        }
        assertTrue(inv.slots.all { it == null })
    }

    @Test
    fun craftingConsumesExactlyTheIngredients() {
        val inv = Inventory()
        inv.add(Item.OAK_LOG, 2)
        assertTrue(Recipes.craft(inv, recipe(Item.PLANKS), nearTable = false))
        assertEquals(1, inv.count(Item.OAK_LOG))
        assertEquals(4, inv.count(Item.PLANKS))
        assertTrue(Recipes.craft(inv, recipe(Item.STICK), nearTable = false))
        assertEquals(2, inv.count(Item.PLANKS))
        assertEquals(4, inv.count(Item.STICK))
    }

    @Test
    fun toolsNeedACraftingTableNearby() {
        val inv = Inventory()
        inv.add(Item.PLANKS, 3)
        inv.add(Item.STICK, 2)
        val pick = recipe(Item.WOOD_PICK)
        assertEquals("Stand near a Crafting Table", pick.blocker(inv, nearTable = false))
        assertFalse(Recipes.craft(inv, pick, nearTable = false))
        assertEquals("nothing was used up", 3, inv.count(Item.PLANKS))
        assertTrue(Recipes.craft(inv, pick, nearTable = true))
        assertEquals(1, inv.count(Item.WOOD_PICK))
        assertEquals(0, inv.count(Item.PLANKS))
    }

    @Test
    fun woodenToolsAcceptEitherKindOfPlanks() {
        val inv = Inventory()
        inv.add(Item.PLANKS, 1)
        inv.add(Item.CHERRY_PLANKS, 2)
        inv.add(Item.STICK, 2)
        assertTrue(Recipes.craft(inv, recipe(Item.WOOD_AXE), nearTable = true))
        assertEquals(0, inv.count(Item.PLANKS) + inv.count(Item.CHERRY_PLANKS))
    }

    @Test
    fun aFullBackpackBlocksCraftingInsteadOfLosingItems() {
        val inv = Inventory(2)
        inv.add(Item.OAK_LOG, 1)
        inv.add(Item.WOOD_PICK, 1) // both slots are now taken
        val planks = recipe(Item.PLANKS)
        assertEquals("Backpack is full", planks.blocker(inv, nearTable = false))
        assertFalse(Recipes.craft(inv, planks, nearTable = false))
        assertEquals("the log was not used up", 1, inv.count(Item.OAK_LOG))
    }

    @Test
    fun theFullToolChainWorksFromLogsToIronAndGold() {
        val inv = Inventory()
        fun craft(item: Item, table: Boolean = true) = assertTrue("craft ${item.label}", Recipes.craft(inv, recipe(item), table))

        inv.add(Item.OAK_LOG, 4) // chopped from a tree
        repeat(4) { craft(Item.PLANKS, false) } // 16 planks
        repeat(3) { craft(Item.STICK, false) } // 12 sticks from 6 planks
        craft(Item.TABLE, false)
        craft(Item.WOOD_PICK)

        inv.add(Item.COBBLE, 7) // mined with the wooden pickaxe
        craft(Item.STONE_PICK)
        craft(Item.STONE_AXE)

        inv.add(Item.IRON_ORE, 4); inv.add(Item.COAL, 4) // mined with the stone pickaxe
        for (i in 0 until 4) craft(Item.IRON_INGOT)
        craft(Item.IRON_PICK)
        craft(Item.IRON_SHOVEL)

        inv.add(Item.GOLD_NUGGET, 4) // mined with the iron pickaxe
        craft(Item.V_BLOCK)
        assertEquals(1, inv.count(Item.V_BLOCK))
        assertEquals(1, inv.count(Item.IRON_PICK))
    }

    @Test
    fun everyIngredientCanActuallyBeObtained() {
        val drops = Block.entries.mapNotNull { Mining.drop(it, 0, 0, 0)?.first }.toMutableSet()
        // Leaves only sometimes drop sticks, so find one position that does.
        for (x in 0..200) Mining.drop(Block.LEAVES, x, 0, 0)?.let { drops += it.first }
        val obtainable = drops + Recipes.all.map { it.result }
        for (recipe in Recipes.all) for (need in recipe.needs) {
            assertTrue("${need.label} for ${recipe.result.label} can be gathered or crafted", need.options.any { it in obtainable })
        }
    }

    @Test
    fun theRightToolIsNeededAndMakesDiggingFaster() {
        assertEquals(0.6, Mining.timeToBreak(Block.GRASS, null)!!, 1e-9)
        assertTrue("shovel is faster on dirt", Mining.timeToBreak(Block.DIRT, Item.WOOD_SHOVEL)!! < Mining.timeToBreak(Block.DIRT, null)!!)
        assertEquals(2.0, Mining.timeToBreak(Block.WOOD, null)!!, 1e-9)
        assertEquals(1.0, Mining.timeToBreak(Block.WOOD, Item.WOOD_AXE)!!, 1e-9)
        assertTrue("an iron axe beats a wooden axe", Mining.timeToBreak(Block.WOOD, Item.IRON_AXE)!! < Mining.timeToBreak(Block.WOOD, Item.WOOD_AXE)!!)
        assertTrue("a pickaxe does not speed up wood", Mining.timeToBreak(Block.WOOD, Item.IRON_PICK) == 2.0)

        assertNull("stone by hand", Mining.timeToBreak(Block.STONE, null))
        assertNull("stone with an axe", Mining.timeToBreak(Block.STONE, Item.IRON_AXE))
        assertNotNull(Mining.timeToBreak(Block.STONE, Item.WOOD_PICK))

        assertNull(Mining.timeToBreak(Block.IRON_ORE, Item.WOOD_PICK))
        assertNotNull(Mining.timeToBreak(Block.IRON_ORE, Item.STONE_PICK))
        assertNull(Mining.timeToBreak(Block.GOLD_ORE, Item.STONE_PICK))
        assertNotNull(Mining.timeToBreak(Block.GOLD_ORE, Item.IRON_PICK))
    }

    @Test
    fun dropsAreGatheredResourcesNotThePlacedBlockEverywhere() {
        assertEquals(Item.DIRT to 1, Mining.drop(Block.GRASS, 0, 0, 0))
        assertEquals(Item.DIRT to 1, Mining.drop(Block.BLOSSOM_GRASS, 0, 0, 0))
        assertEquals(Item.COBBLE to 1, Mining.drop(Block.STONE, 0, 0, 0))
        assertEquals(Item.COAL to 1, Mining.drop(Block.COAL_ORE, 0, 0, 0))
        assertEquals(Item.GOLD_NUGGET to 2, Mining.drop(Block.GOLD_ORE, 0, 0, 0))
        assertEquals(Item.CHERRY_LOG to 1, Mining.drop(Block.CHERRY_LOG, 0, 0, 0))
        assertNull(Mining.drop(Block.WATER, 0, 0, 0))
    }
}
