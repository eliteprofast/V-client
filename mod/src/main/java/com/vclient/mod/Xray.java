package com.vclient.mod;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Decides which blocks X-Ray keeps visible. Chunk meshing runs on several threads, so the cache is concurrent. */
public final class Xray {
    private static final Set<String> EXTRA = Set.of("ancient_debris", "chest", "trapped_chest", "ender_chest",
            "spawner", "trial_spawner", "beacon", "end_portal_frame", "end_portal", "nether_portal",
            "diamond_block", "emerald_block", "gold_block", "iron_block", "netherite_block", "lapis_block",
            "redstone_block", "raw_iron_block", "raw_gold_block", "raw_copper_block");
    private static final Map<Block, Boolean> CACHE = new ConcurrentHashMap<>();

    private Xray() {}

    public static boolean isVisible(Block block) {
        return CACHE.computeIfAbsent(block, key -> {
            String path = Registries.BLOCK.getId(key).getPath();
            return path.endsWith("_ore") || EXTRA.contains(path);
        });
    }
}
