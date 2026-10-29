package net.rasanovum.roxy.legacy.terrain;

import static org.junit.Assert.*;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LodStoreTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void persistsNegativeCoordinatesFullMetadataTintAndUnsignedLight() throws Exception {
        LodStore store = new LodStore(
            temporary.newFolder()
                .toPath());
        int[] states = new int[LodChunk.CELLS];
        int[] colors = new int[LodChunk.CELLS];
        byte[] lights = new byte[LodChunk.CELLS];
        int cell = LodChunk.index(3, 63, 1);
        states[cell] = 1;
        colors[cell] = 0x21a3f6;
        lights[cell] = (byte) 0xe7;
        LodChunk original = new LodChunk(
            -4321,
            7654,
            new String[] { "minecraft:air", "gregtech:gt.blockores" },
            new int[] { 0, 65535 },
            states,
            colors,
            lights);
        store.write(original);
        LodChunk restored = store.read(-4321, 7654);
        assertArrayEquals(original.names, restored.names);
        assertArrayEquals(original.metadata, restored.metadata);
        assertArrayEquals(states, restored.states);
        assertArrayEquals(colors, restored.colors);
        assertArrayEquals(lights, restored.lights);
        assertEquals(1, restored.state(3, 63, 1));
        assertEquals(0, restored.state(4, 63, 1));
        assertEquals(0, restored.state(3, 64, 1));
        assertNull(store.read(4321, 7654));
        // Replacing an existing cache must also produce a complete readable file.
        store.write(original);
        assertArrayEquals(states, store.read(-4321, 7654).states);
        try (java.util.stream.Stream<Path> paths = Files.list(
            store.path(-4321, 7654)
                .getParent())) {
            assertEquals(1, paths.count());
        }
    }

    @Test(expected = IOException.class)
    public void rejectsCorruptPaletteBeforeAllocatingUnboundedMemory() throws Exception {
        LodStore store = new LodStore(
            temporary.newFolder()
                .toPath());
        try (DataOutputStream out = new DataOutputStream(
            new GZIPOutputStream(Files.newOutputStream(store.path(0, 0))))) {
            out.writeInt(0x524c4432);
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(Integer.MAX_VALUE);
        }
        store.read(0, 0);
    }

    @Test
    public void exposedFaceUsesNeighborSkyLightAndKeepsEmittedBlockLight() {
        byte[] light = new byte[LodChunk.CELLS];
        light[LodChunk.index(1, 20, 1)] = (byte) 0xb0;
        light[LodChunk.index(1, 21, 1)] = (byte) 0x0f;
        LodChunk chunk = new LodChunk(
            0,
            0,
            new String[] { "minecraft:air" },
            new int[] { 0 },
            new int[LodChunk.CELLS],
            new int[LodChunk.CELLS],
            light);
        assertEquals(0xbf, chunk.faceLight(1, 20, 1, 1, 21, 1));
        assertEquals(0x00, chunk.faceLight(0, 20, 1, -1, 20, 1));
    }

    @Test(expected = IOException.class)
    public void rejectsCacheFromWrongCoordinates() throws Exception {
        LodStore store = new LodStore(
            temporary.newFolder()
                .toPath());
        store.write(
            new LodChunk(
                0,
                0,
                new String[] { "minecraft:air" },
                new int[] { 0 },
                new int[LodChunk.CELLS],
                new int[LodChunk.CELLS],
                new byte[LodChunk.CELLS]));
        Files.copy(store.path(0, 0), store.path(1, 0));
        store.read(1, 0);
    }
}
