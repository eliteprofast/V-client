package com.vclient.android.demo

class ItemStack(val item: Item, var count: Int)

/** The player's backpack: 27 slots, the first 9 of which are the hotbar. New items fill the hotbar first. */
class Inventory(val size: Int = SIZE) {
    val slots = arrayOfNulls<ItemStack>(size)

    /** Adds items, topping up existing stacks first. Returns how many did NOT fit. */
    fun add(item: Item, amount: Int): Int {
        var left = amount
        for (stack in slots) {
            if (left == 0) break
            if (stack != null && stack.item == item && stack.count < item.maxStack) {
                val take = minOf(left, item.maxStack - stack.count)
                stack.count += take
                left -= take
            }
        }
        for (i in slots.indices) {
            if (left == 0) break
            if (slots[i] == null) {
                val take = minOf(left, item.maxStack)
                slots[i] = ItemStack(item, take)
                left -= take
            }
        }
        return left
    }

    fun canFit(item: Item, amount: Int): Boolean {
        var room = 0
        for (stack in slots) {
            room += when {
                stack == null -> item.maxStack
                stack.item == item -> item.maxStack - stack.count
                else -> 0
            }
        }
        return room >= amount
    }

    fun count(item: Item): Int = slots.sumOf { if (it != null && it.item == item) it.count else 0 }

    fun countAny(items: List<Item>): Int = items.sumOf { count(it) }

    /** Takes [amount] of [item]; returns false (and changes nothing) when there isn't enough. */
    fun remove(item: Item, amount: Int): Boolean = removeAny(listOf(item), amount)

    /** Takes [amount] items drawn from [options] in order; returns false (and changes nothing) when short. */
    fun removeAny(options: List<Item>, amount: Int): Boolean {
        if (countAny(options) < amount) return false
        var left = amount
        for (item in options) {
            for (i in slots.indices) {
                val stack = slots[i] ?: continue
                if (stack.item != item || left == 0) continue
                val take = minOf(left, stack.count)
                stack.count -= take
                left -= take
                if (stack.count == 0) slots[i] = null
            }
        }
        return true
    }

    fun swap(a: Int, b: Int) {
        val t = slots[a]
        slots[a] = slots[b]
        slots[b] = t
    }

    /** Uses up one item from a slot (placing a block). */
    fun consumeOne(index: Int) {
        val stack = slots[index] ?: return
        stack.count -= 1
        if (stack.count <= 0) slots[index] = null
    }

    companion object {
        const val SIZE = 27
        const val HOTBAR = 9
    }
}
