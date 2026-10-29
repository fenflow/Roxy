package net.rasanovum.roxy.legacy.terrain;

import net.minecraft.block.Block;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.rasanovum.roxy.legacy.bridge.LegacyChunkCapture;
import net.rasanovum.roxy.legacy.bridge.LegacyMapper;
import net.rasanovum.roxy.legacy.bridge.LegacyPalette;
import net.rasanovum.roxy.legacy.loader.VoxyRuntime;

/** Main-thread snapshot, processed one vertical section at a time to bound tick work. */
public final class ChunkBuild {

    public final Chunk chunk;
    private final LegacyPalette palette = new LegacyPalette();
    private final int[] states = new int[LodChunk.CELLS];
    private final int[] colors = new int[LodChunk.CELLS];
    private final byte[] lights = new byte[LodChunk.CELLS];
    private int sectionY;

    public ChunkBuild(Chunk chunk) {
        this.chunk = chunk;
    }

    public boolean step(World world, VoxyRuntime runtime) throws ReflectiveOperationException {
        while (sectionY < 16
            && (chunk.getBlockStorageArray()[sectionY] == null || chunk.getBlockStorageArray()[sectionY].isEmpty())) {
            // Empty sections still illuminate exposed faces, including above the highest allocated section.
            for (int y = 0; y < LodChunk.SIDE; y++) {
                for (int z = 0; z < LodChunk.SIDE; z++) {
                    for (int x = 0; x < LodChunk.SIDE; x++) {
                        int worldY = sectionY * 16 + y * LodChunk.CELL_SIZE + 1;
                        int sky = world.provider.hasNoSky ? 0
                            : chunk.getSavedLightValue(
                                EnumSkyBlock.Sky,
                                x * LodChunk.CELL_SIZE + 1,
                                worldY,
                                z * LodChunk.CELL_SIZE + 1);
                        int block = chunk.getSavedLightValue(
                            EnumSkyBlock.Block,
                            x * LodChunk.CELL_SIZE + 1,
                            worldY,
                            z * LodChunk.CELL_SIZE + 1);
                        lights[LodChunk.index(x, sectionY * LodChunk.SIDE + y, z)] = (byte) (sky | (block << 4));
                    }
                }
            }
            sectionY++;
        }
        if (sectionY == 16) return true;
        long[] captured = LegacyChunkCapture.capture(world, chunk, sectionY, palette);
        Object section = runtime
            .convert(chunk.xPosition, sectionY, chunk.zPosition, captured, palette.snapshotMapper());
        long[] mipmaps = runtime.copyData(section);
        // The original Voxy ABI places the 8^3 level (two-block cells) at offset 4096.
        for (int y = 0; y < LodChunk.SIDE; y++) {
            for (int z = 0; z < LodChunk.SIDE; z++) {
                for (int x = 0; x < LodChunk.SIDE; x++) {
                    long voxel = mipmaps[4096 + x + LodChunk.SIDE * (z + LodChunk.SIDE * y)];
                    int id = LegacyMapper.blockId(voxel);
                    int index = LodChunk.index(x, sectionY * LodChunk.SIDE + y, z);
                    states[index] = id;
                    lights[index] = (byte) LegacyMapper.getLightId(voxel);
                    colors[index] = 0xffffff;
                    if (id != 0) {
                        Block block = Block.getBlockFromName(palette.name(id));
                        if (block != null) {
                            try {
                                colors[index] = block.colorMultiplier(
                                    world,
                                    chunk.xPosition * 16 + x * LodChunk.CELL_SIZE + 1,
                                    sectionY * 16 + y * LodChunk.CELL_SIZE + 1,
                                    chunk.zPosition * 16 + z * LodChunk.CELL_SIZE + 1);
                            } catch (RuntimeException ignored) {
                                // Tile-entity-specific tint handlers may not accept the representative cell position.
                                colors[index] = 0xffffff;
                            }
                        }
                    }
                }
            }
        }
        return ++sectionY == 16;
    }

    public LodChunk finish() {
        String[] names = new String[palette.size()];
        int[] metadata = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = palette.name(i);
            metadata[i] = palette.metadata(i);
        }
        return new LodChunk(chunk.xPosition, chunk.zPosition, names, metadata, states, colors, lights);
    }
}
