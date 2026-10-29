package net.rasanovum.roxy.legacy.terrain;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class LodNeighborhoodTest {

    private static LodChunk chunk(int x, int z, int fill) {
        int[] states = new int[LodChunk.CELLS];
        Arrays.fill(states, fill);
        return new LodChunk(
            x,
            z,
            new String[] { "minecraft:air", "minecraft:stone" },
            new int[] { 0, 0 },
            states,
            new int[LodChunk.CELLS],
            new byte[LodChunk.CELLS]);
    }

    @Test
    public void doesNotDrawInteriorWallsAtAnySolidChunkBoundary() {
        Map<Long, LodChunk> chunks = new HashMap<Long, LodChunk>();
        LodChunk center = chunk(-2, -3, 1);
        for (int[] offset : new int[][] { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } }) {
            LodChunk neighbor = chunk(center.x + offset[0], center.z + offset[1], 1);
            chunks.put(LodChunk.key(neighbor.x, neighbor.z), neighbor);
        }
        LodNeighborhood neighborhood = new LodNeighborhood(center, chunks);
        for (int y = 0; y < LodChunk.HEIGHT; y++) {
            assertFalse(neighborhood.isExposed(-1, y, 2));
            assertFalse(neighborhood.isExposed(LodChunk.SIDE, y, 2));
            assertFalse(neighborhood.isExposed(2, y, -1));
            assertFalse(neighborhood.isExposed(2, y, LodChunk.SIDE));
        }
    }

    @Test
    public void unknownNeighborDoesNotBecomeABedrockToSurfaceWall() {
        LodNeighborhood neighborhood = new LodNeighborhood(chunk(0, 0, 1), new HashMap<Long, LodChunk>());
        assertFalse(neighborhood.isExposed(-1, 30, 2));
        assertFalse(neighborhood.isExposed(2, -1, 2));
        assertTrue(neighborhood.isExposed(2, LodChunk.HEIGHT, 2));
    }

    @Test
    public void exposesRealCliffAndUsesItsAdjacentAirLightAcrossChunks() {
        Map<Long, LodChunk> chunks = new HashMap<Long, LodChunk>();
        LodChunk center = chunk(-1, 0, 1);
        LodChunk east = chunk(0, 0, 0);
        center.lights[LodChunk.index(LodChunk.SIDE - 1, 30, 2)] = (byte) 0xb0;
        east.lights[LodChunk.index(0, 30, 2)] = 15;
        chunks.put(LodChunk.key(0, 0), east);
        LodNeighborhood neighborhood = new LodNeighborhood(center, chunks);
        assertTrue(neighborhood.isExposed(LodChunk.SIDE, 30, 2));
        assertEquals(0xbf, neighborhood.faceLight(LodChunk.SIDE - 1, 30, 2, LodChunk.SIDE, 30, 2));
        assertTrue(neighborhood.matches(center, chunks));
        chunks.put(LodChunk.key(0, 0), chunk(0, 0, 1));
        assertFalse(neighborhood.matches(center, chunks));
    }
}
