package net.rasanovum.roxy.legacy.terrain;

/** Immutable, registry-name based output of the external Voxy reduction stage. */
public final class LodChunk {

    public static final int CELL_SIZE = 2;
    public static final int SIDE = 16 / CELL_SIZE;
    public static final int HEIGHT = 256 / CELL_SIZE;
    public static final int CELLS = SIDE * SIDE * HEIGHT;
    public final int x;
    public final int z;
    public final String[] names;
    public final int[] metadata;
    public final int[] states;
    public final int[] colors;
    public final byte[] lights;

    public LodChunk(int x, int z, String[] names, int[] metadata, int[] states, int[] colors, byte[] lights) {
        if (names.length < 1 || names.length > 65536
            || names.length != metadata.length
            || states.length != CELLS
            || colors.length != CELLS
            || lights.length != CELLS) {
            throw new IllegalArgumentException("Invalid LOD chunk dimensions");
        }
        for (int state : states) {
            if (state < 0 || state >= names.length) throw new IllegalArgumentException("Invalid palette reference");
        }
        this.x = x;
        this.z = z;
        this.names = names;
        this.metadata = metadata;
        this.states = states;
        this.colors = colors;
        this.lights = lights;
    }

    public static int index(int x, int y, int z) {
        return x + SIDE * (z + SIDE * y);
    }

    public static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    public int state(int x, int y, int z) {
        return x < 0 || x >= SIDE || y < 0 || y >= HEIGHT || z < 0 || z >= SIDE ? 0 : states[index(x, y, z)];
    }

    public int faceLight(int x, int y, int z, int nx, int ny, int nz) {
        int own = lights[index(x, y, z)] & 255;
        // At an unknown horizontal neighbor, use the air above this column instead of
        // inventing skylight (which would make Nether chunk boundaries glow).
        int adjacent = nx < 0 || nx >= SIDE || nz < 0 || nz >= SIDE
            ? lights[index(x, Math.min(HEIGHT - 1, y + 1), z)] & 255
            : ny < 0 || ny >= HEIGHT ? own : lights[index(nx, ny, nz)] & 255;
        return Math.max(own & 15, adjacent & 15) | (Math.max(own >>> 4, adjacent >>> 4) << 4);
    }
}
