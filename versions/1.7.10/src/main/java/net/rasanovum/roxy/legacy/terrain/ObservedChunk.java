package net.rasanovum.roxy.legacy.terrain;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.ForgeDirection;

/** Read-only view of a received chunk, still usable after the client provider unloads it. */
final class ObservedChunk implements IBlockAccess {

    final Chunk chunk;
    final BiomeGenBase[] biomes = new BiomeGenBase[256];
    final boolean noSky;
    private final World world;
    boolean ready;

    ObservedChunk(Chunk chunk, World world) {
        this.chunk = chunk;
        this.world = world;
        noSky = world.provider.hasNoSky;
    }

    void prepare() {
        if (ready) return;
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
            biomes[x + z * 16] = chunk.getBiomeGenForWorldCoords(x, z, world.getWorldChunkManager());
        ready = true;
    }

    private boolean inside(int x, int y, int z) {
        return y >= 0 && y < 256 && x >> 4 == chunk.xPosition && z >> 4 == chunk.zPosition;
    }

    public Block getBlock(int x, int y, int z) {
        return inside(x, y, z) ? chunk.getBlock(x & 15, y, z & 15) : Blocks.air;
    }

    public int getBlockMetadata(int x, int y, int z) {
        return inside(x, y, z) ? chunk.getBlockMetadata(x & 15, y, z & 15) : 0;
    }

    public TileEntity getTileEntity(int x, int y, int z) {
        return inside(x, y, z) ? (TileEntity) chunk.chunkTileEntityMap.get(new ChunkPosition(x & 15, y, z & 15)) : null;
    }

    int light(EnumSkyBlock kind, int x, int y, int z) {
        if (kind == EnumSkyBlock.Sky && noSky) return 0;
        if (y < 0) return 0;
        if (y >= 256) return kind.defaultLightValue;
        if (inside(x, y, z)) return chunk.getSavedLightValue(kind, x & 15, y, z & 15);
        // Never load a neighbor just to sample light. Missing neighbors must not introduce full
        // skylight along underground chunk edges; use the nearest received edge as a fallback.
        if (world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4))
            return world.getChunkFromChunkCoords(x >> 4, z >> 4)
                .getSavedLightValue(kind, x & 15, y, z & 15);
        int localX = Math.max(0, Math.min(15, x - chunk.xPosition * 16));
        int localZ = Math.max(0, Math.min(15, z - chunk.zPosition * 16));
        return chunk.getSavedLightValue(kind, localX, y, localZ);
    }

    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int emission) {
        int sky = light(EnumSkyBlock.Sky, x, y, z);
        int block = Math.max(emission, light(EnumSkyBlock.Block, x, y, z));
        return sky << 20 | block << 4;
    }

    public boolean isAirBlock(int x, int y, int z) {
        return getBlock(x, y, z) == Blocks.air;
    }

    public BiomeGenBase getBiomeGenForCoords(int x, int z) {
        return biomes[(x & 15) + (z & 15) * 16];
    }

    public int getHeight() {
        return 256;
    }

    public boolean extendedLevelsInChunkCache() {
        return false;
    }

    public int isBlockProvidingPowerTo(int x, int y, int z, int side) {
        return 0;
    }

    public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean fallback) {
        return getBlock(x, y, z).isOpaqueCube();
    }
}
