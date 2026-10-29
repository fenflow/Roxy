package net.rasanovum.roxy.legacy.terrain;

import java.util.Map;

/** Resolves adjacent cells across chunk boundaries without exposing solid chunk interiors. */
public final class LodNeighborhood {

    private final LodChunk center;
    private final LodChunk west;
    private final LodChunk east;
    private final LodChunk north;
    private final LodChunk south;

    public LodNeighborhood(LodChunk center, Map<Long, LodChunk> chunks) {
        this.center = center;
        west = chunks.get(LodChunk.key(center.x - 1, center.z));
        east = chunks.get(LodChunk.key(center.x + 1, center.z));
        north = chunks.get(LodChunk.key(center.x, center.z - 1));
        south = chunks.get(LodChunk.key(center.x, center.z + 1));
    }

    public boolean matches(LodChunk chunk, Map<Long, LodChunk> chunks) {
        return center == chunk && west == chunks.get(LodChunk.key(chunk.x - 1, chunk.z))
            && east == chunks.get(LodChunk.key(chunk.x + 1, chunk.z))
            && north == chunks.get(LodChunk.key(chunk.x, chunk.z - 1))
            && south == chunks.get(LodChunk.key(chunk.x, chunk.z + 1));
    }

    private LodChunk neighbor(int x, int z) {
        return x < 0 ? west : x >= LodChunk.SIDE ? east : z < 0 ? north : z >= LodChunk.SIDE ? south : center;
    }

    public boolean isExposed(int x, int y, int z) {
        if (y < 0) return false;
        if (y >= LodChunk.HEIGHT) return true;
        LodChunk chunk = neighbor(x, z);
        // An uncaptured chunk is unknown, not air. Drawing a cap here exposes a dark
        // cross-section all the way down to bedrock at the near/far terrain seam.
        return chunk != null && chunk.state(Math.floorMod(x, LodChunk.SIDE), y, Math.floorMod(z, LodChunk.SIDE)) == 0;
    }

    public int faceLight(int x, int y, int z, int nx, int ny, int nz) {
        int own = center.lights[LodChunk.index(x, y, z)] & 255;
        LodChunk chunk = neighbor(nx, nz);
        int adjacent = chunk == null || ny < 0 || ny >= LodChunk.HEIGHT ? own
            : chunk.lights[LodChunk.index(Math.floorMod(nx, LodChunk.SIDE), ny, Math.floorMod(nz, LodChunk.SIDE))]
                & 255;
        return Math.max(own & 15, adjacent & 15) | (Math.max(own >>> 4, adjacent >>> 4) << 4);
    }
}
