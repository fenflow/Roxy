package net.rasanovum.roxy.legacy.loader;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.junit.Assume;
import org.junit.Test;

public class FullEngineIntegrationTest {

    @Test
    public void legacyHeightProducesAVisibleTopLevelTraversalRange() throws Exception {
        String configured = System.getProperty("roxy.voxyJar");
        Assume.assumeNotNull(configured);
        try (ExternalVoxyLoader loader = new ExternalVoxyLoader(
            Paths.get(configured),
            Files.createTempDirectory("roxy-height-"))) {
            Class<?> game = loader.loadClass("net.rasanovum.roxy.legacy.modern.PlatformGame");
            Object world = game.getField("field_1687")
                .get(
                    game.getMethod("method_1551")
                        .invoke(null));
            // These are the exact intermediary calls in the pinned VoxyRenderSystem constructor.
            int min = (Integer) world.getClass()
                .getMethod("method_32891")
                .invoke(world);
            int max = (Integer) world.getClass()
                .getMethod("method_31597")
                .invoke(world);
            assertEquals(0, min);
            assertEquals(16, max);
            assertTrue(
                "GTNH's 256-block height must create at least one top-level render node",
                (min >> 5) <= ((max - 1) >> 5));
        }
    }

    @Test
    public void rendererAndBakerDescriptorsResolveWithoutModernMinecraft() throws Exception {
        String configured = System.getProperty("roxy.voxyJar");
        Assume.assumeNotNull(configured);
        Path root = Files.createTempDirectory("roxy-render-abi-");
        try (ExternalVoxyLoader loader = new ExternalVoxyLoader(Paths.get(configured), root)) {
            String[] classes = { "VoxyRenderSystem", "NormalRenderPipeline", "model.ModelFactory", "model.ModelStore",
                "model.bakery.SoftwareModelTextureBakery", "model.bakery.ReuseVertexConsumer",
                "rendering.section.backend.mdic.MDICSectionRenderer" };
            for (String name : classes) {
                Class<?> type = loader.loadClass("me.cortex.voxy.client.core." + name);
                assertSame(loader, type.getClassLoader());
                assertTrue(type.getDeclaredMethods().length > 0);
                assertTrue(type.getDeclaredConstructors().length > 0);
            }
        }
    }

    @Test
    public void originalWorldEnginePersistsAllLodLevelsAndLegacyRegistry() throws Exception {
        String configured = System.getProperty("roxy.voxyJar");
        Assume.assumeNotNull(configured);
        Path jar = Paths.get(configured);
        String before = VoxyRuntime.sha256(jar);
        Path root = Files.createTempDirectory("roxy-external-engine-");
        Path database = root.resolve("world");
        try (ExternalVoxyLoader loader = new ExternalVoxyLoader(jar, root.resolve("libraries"))) {
            Class<?> runtime = loader.loadClass("net.rasanovum.roxy.legacy.modern.WorldRuntime");
            Method register = runtime.getMethod("registerBlock", String.class, int.class, int.class);
            Method ingest = runtime.getMethod("ingest", int.class, int.class, int.class, long[].class);
            Method read = runtime.getMethod("readSection", int.class, int.class, int.class, int.class);
            Method close = runtime.getMethod("close");
            int block;
            long voxel;
            Object first = runtime.getConstructor(Path.class)
                .newInstance(database);
            try {
                int biome = (Integer) runtime.getMethod("registerBiome", String.class)
                    .invoke(first, "gtnh:test-biome");
                block = (Integer) register.invoke(first, "gregtech:test-block", 60000, 15);
                assertNotEquals(block, register.invoke(first, "gregtech:test-block", 0, 15));
                voxel = (0x8fL << 56) | ((long) biome << 47) | ((long) block << 27);
                long[] full = new long[4096];
                Arrays.fill(full, voxel);
                ingest.invoke(first, -3, 5, -7, full);
                assertLevels(first, read, voxel);
                assertTrue(loader.externalClassesLoaded() > 15);
                assertSame(
                    loader,
                    loader.loadClass("me.cortex.voxy.common.world.WorldEngine")
                        .getClassLoader());
            } finally {
                close.invoke(first);
            }
            assertTrue(
                "Original RocksDB backend created its database",
                Files.isRegularFile(database.resolve("CURRENT")));
            Object second = runtime.getConstructor(Path.class)
                .newInstance(database);
            try {
                assertEquals(block, register.invoke(second, "gregtech:test-block", 60000, 15));
                assertEquals(
                    3,
                    runtime.getMethod("blockCount")
                        .invoke(second));
                assertLevels(second, read, voxel);
                ingest.invoke(second, -3, 5, -7, new long[4096]);
                for (int level = 0; level <= 4; level++) {
                    long[] data = (long[]) read
                        .invoke(second, level, -3 >> (level + 1), 5 >> (level + 1), -7 >> (level + 1));
                    // Voxy can retain skylight in an air voxel; occupancy is the block field.
                    if (data != null) for (long value : data) assertEquals(0, (value >>> 27) & 0xfffff);
                }
            } finally {
                close.invoke(second);
            }
        }
        assertEquals("The external JAR must remain unchanged", before, VoxyRuntime.sha256(jar));
    }

    private static void assertLevels(Object runtime, Method read, long voxel) throws Exception {
        for (int level = 0; level <= 4; level++) {
            long[] data = (long[]) read.invoke(runtime, level, -3 >> (level + 1), 5 >> (level + 1), -7 >> (level + 1));
            assertNotNull("Missing Voxy level " + level, data);
            assertEquals(32768, data.length);
            int occupied = 0;
            for (long value : data) {
                if (((value >>> 27) & 0xfffff) != 0) {
                    assertEquals(voxel, value);
                    occupied++;
                }
            }
            assertEquals("Occupied cells at level " + level, 4096 >> (level * 3), occupied);
        }
    }
}
