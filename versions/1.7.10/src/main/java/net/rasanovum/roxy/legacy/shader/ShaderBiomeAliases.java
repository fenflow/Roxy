package net.rasanovum.roxy.legacy.shader;

import java.util.HashMap;
import java.util.Map;

/** Modern shader biome names resolved against the host's legacy biome definitions. */
public final class ShaderBiomeAliases {

    private ShaderBiomeAliases() {}

    public static Map<String, String> extend(Map<String, String> original) {
        Map<String, String> result = new HashMap<String, String>(original);
        for (String pair : new String[] { "SNOWY_PLAINS:ICE_PLAINS", "SNOWY_BEACH:COLD_BEACH", "SNOWY_TAIGA:COLD_TAIGA",
            "WINDSWEPT_HILLS:EXTREME_HILLS", "WINDSWEPT_FOREST:EXTREME_HILLS_PLUS", "STONY_SHORE:STONE_BEACH",
            "OLD_GROWTH_PINE_TAIGA:MEGA_TAIGA", "BADLANDS:MESA", "WOODED_BADLANDS:MESA_PLATEAU_F",
            "MUSHROOM_FIELDS:MUSHROOM_ISLAND", "SPARSE_JUNGLE:JUNGLE_EDGE", "DARK_FOREST:ROOFED_FOREST",
            "NETHER_WASTES:HELL" }) {
            String[] names = pair.split(":");
            alias(result, names[0], names[1], 0);
        }
        alias(result, "ICE_SPIKES", "ICE_PLAINS", 128);
        alias(result, "ERODED_BADLANDS", "MESA", 128);
        alias(result, "WINDSWEPT_GRAVELLY_HILLS", "EXTREME_HILLS", 128);
        alias(result, "OLD_GROWTH_SPRUCE_TAIGA", "MEGA_TAIGA", 128);
        int absent = -2000;
        for (String name : new String[] { "BAMBOO_JUNGLE", "MANGROVE_SWAMP", "CHERRY_GROVE", "GROVE", "FROZEN_PEAKS",
            "JAGGED_PEAKS", "SNOWY_SLOPES", "MEADOW", "STONY_PEAKS", "LUSH_CAVES", "DRIPSTONE_CAVES", "DEEP_DARK",
            "WARM_OCEAN", "LUKEWARM_OCEAN", "COLD_OCEAN", "DEEP_FROZEN_OCEAN", "DEEP_LUKEWARM_OCEAN", "DEEP_COLD_OCEAN",
            "PALE_GARDEN", "SULFUR_CAVES" }) result.putIfAbsent("BIOME_" + name, Integer.toString(absent--));
        return result;
    }

    private static void alias(Map<String, String> values, String modern, String legacy, int offset) {
        String existing = values.get("BIOME_" + modern), source = values.get("BIOME_" + legacy);
        if (source != null && (existing == null || existing.startsWith("-")))
            values.put("BIOME_" + modern, Integer.toString(Integer.parseInt(source) + offset));
    }
}
