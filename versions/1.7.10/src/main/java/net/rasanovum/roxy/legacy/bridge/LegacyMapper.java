package net.rasanovum.roxy.legacy.bridge;

/** The small mapping ABI needed by Voxy's original Mipper, implemented for legacy states. */
public final class LegacyMapper {

    private final int[] opacities;

    public LegacyMapper(int[] opacities) {
        this.opacities = opacities.clone();
    }

    public static long pack(int block, int biome, int skyLight, int blockLight) {
        if (block < 0 || block >= (1 << 20)
            || biome < 0
            || biome >= 512
            || skyLight < 0
            || skyLight > 15
            || blockLight < 0
            || blockLight > 15) {
            throw new IllegalArgumentException("Voxel component exceeds Voxy's format");
        }
        return ((long) block << 27) | ((long) biome << 47) | ((long) (skyLight | (blockLight << 4)) << 56);
    }

    public static boolean isAir(long voxel) {
        return blockId(voxel) == 0;
    }

    public static int blockId(long voxel) {
        return (int) ((voxel >>> 27) & 0xFFFFF);
    }

    public static int getLightId(long voxel) {
        return (int) (voxel >>> 56);
    }

    public static long withLight(long voxel, int light) {
        return (voxel & 0x00FFFFFFFFFFFFFFL) | ((long) (light & 255) << 56);
    }

    public int getBlockStateOpacity(long voxel) {
        int id = blockId(voxel);
        if (id >= opacities.length) {
            throw new IllegalArgumentException("Unmapped legacy state " + id);
        }
        return opacities[id];
    }
}
