package net.rasanovum.roxy.legacy.loader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import net.rasanovum.roxy.legacy.bridge.LegacyMapper;
import net.rasanovum.roxy.legacy.bridge.LegacyPalette;

import org.junit.Assume;
import org.junit.Test;

public class VoxyRuntimeTest {

    @Test(expected = IOException.class)
    public void rejectsUnknownBinaryBeforeLoadingAnyCode() throws Exception {
        Path invalid = Files.createTempFile("roxy-invalid-", ".jar");
        try {
            new VoxyRuntime(invalid);
        } finally {
            Files.deleteIfExists(invalid);
        }
    }

    @Test
    public void runsOriginalVoxySectionReductionWithoutMinecraftOrFabric() throws Exception {
        String externalJar = System.getProperty("roxy.voxyJar");
        Assume.assumeTrue("Supply -ProxyVoxyJar=/path/to/original.jar to run binary integration", externalJar != null);
        Path path = Paths.get(externalJar);
        String before = VoxyRuntime.sha256(path);
        LegacyPalette palette = new LegacyPalette();
        int stone = palette.register("minecraft:stone", 0, 255);
        long solid = LegacyMapper.pack(stone, 7, 15, 0);
        long[] input = new long[4096];
        Arrays.fill(input, solid);
        try (VoxyRuntime runtime = new VoxyRuntime(path)) {
            Object section = runtime.convert(-2, 15, 3, input, palette.snapshotMapper());
            long[] data = runtime.copyData(section);
            assertEquals(4681, data.length);
            for (long value : data) {
                assertEquals(solid, value);
            }
            assertEquals(
                -2,
                section.getClass()
                    .getField("x")
                    .getInt(section));
            assertEquals(
                15,
                section.getClass()
                    .getField("y")
                    .getInt(section));
            assertEquals(
                3,
                section.getClass()
                    .getField("z")
                    .getInt(section));
            assertEquals(
                4096,
                section.getClass()
                    .getField("lvl0NonAirCount")
                    .getInt(section));

            // Directional light is deliberately asymmetric to catch swapped nibbles.
            Arrays.fill(input, LegacyMapper.pack(0, 0, 4, 12));
            Object empty = runtime.convert(0, 0, 0, input, palette.snapshotMapper());
            for (long value : runtime.copyData(empty)) {
                assertTrue(LegacyMapper.isAir(value));
                assertEquals(0xC4, LegacyMapper.getLightId(value));
            }
            assertEquals(
                0,
                empty.getClass()
                    .getField("lvl0NonAirCount")
                    .getInt(empty));

            Arrays.fill(input, 0L);
            input[15 + 16 * (7 + 16 * 3)] = solid;
            Object sparse = runtime.convert(0, 0, 0, input, palette.snapshotMapper());
            assertEquals(
                1,
                sparse.getClass()
                    .getField("lvl0NonAirCount")
                    .getInt(sparse));
            assertEquals(
                solid,
                ((Long) sparse.getClass()
                    .getMethod("get", int.class, int.class, int.class, int.class)
                    .invoke(sparse, 0, 15, 3, 7)).longValue());

            // Each coarse cell gets a distinct uniform source region. This checks the renderer's
            // external ABI offset and x/z/y ordering without reimplementing Voxy's reduction.
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int biome = (x / 4) + 4 * ((z / 4) + 4 * (y / 4));
                        input[x + 16 * (z + 16 * y)] = LegacyMapper.pack(stone, biome, 15, 0);
                    }
                }
            }
            Object patterned = runtime.convert(0, 0, 0, input, palette.snapshotMapper());
            long[] reduced = runtime.copyData(patterned);
            for (int i = 0; i < 64; i++) {
                assertEquals(LegacyMapper.pack(stone, i, 15, 0), reduced[4608 + i]);
            }
        }
        assertEquals("The original JAR must remain unchanged", before, VoxyRuntime.sha256(path));
    }
}
