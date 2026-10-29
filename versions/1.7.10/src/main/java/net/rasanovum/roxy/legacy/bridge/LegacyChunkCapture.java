package net.rasanovum.roxy.legacy.bridge;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/** Captures on the client thread; no live world objects cross the Voxy boundary. */
public final class LegacyChunkCapture {

    private LegacyChunkCapture() {}

    public static long[] capture(World world, Chunk chunk, int sectionY, LegacyPalette palette) {
        if (!world.isRemote || sectionY < 0 || sectionY > 15) {
            throw new IllegalArgumentException("Expected a client chunk section in the 1.7.10 height range");
        }
        long[] result = new long[4096];
        Map<Integer, Integer> biomes = new LinkedHashMap<Integer, Integer>();
        int[] biomeColumns = new int[256];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int biome = world.getBiomeGenForCoords(chunk.xPosition * 16 + x, chunk.zPosition * 16 + z).biomeID;
                Integer mapped = biomes.get(biome);
                if (mapped == null) {
                    mapped = biomes.size();
                    biomes.put(biome, mapped);
                }
                biomeColumns[x + 16 * z] = mapped;
            }
        }
        // This probe uses a section-local biome palette. A persistent renderer must use world-scoped biome IDs.
        for (int y = 0; y < 16; y++) {
            int worldY = sectionY * 16 + y;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int worldX = chunk.xPosition * 16 + x;
                    int worldZ = chunk.zPosition * 16 + z;
                    Block block = chunk.getBlock(x, worldY, z);
                    int state = 0;
                    if (!block.isAir(world, worldX, worldY, worldZ)) {
                        String name = (String) Block.blockRegistry.getNameForObject(block);
                        state = palette.register(
                            name,
                            chunk.getBlockMetadata(x, worldY, z),
                            block.getLightOpacity(world, worldX, worldY, worldZ));
                    }
                    int sky = world.provider.hasNoSky ? 0 : chunk.getSavedLightValue(EnumSkyBlock.Sky, x, worldY, z);
                    int light = chunk.getSavedLightValue(EnumSkyBlock.Block, x, worldY, z);
                    result[x + 16 * (z + 16 * y)] = LegacyMapper.pack(state, biomeColumns[x + 16 * z], sky, light);
                }
            }
        }
        return result;
    }
}
