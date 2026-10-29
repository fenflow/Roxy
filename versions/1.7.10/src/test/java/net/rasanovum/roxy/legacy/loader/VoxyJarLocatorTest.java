package net.rasanovum.roxy.legacy.loader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class VoxyJarLocatorTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void findsMetadataRegardlessOfFilenameAndIgnoresUnrelatedMods() throws Exception {
        Path game = temporary.newFolder()
            .toPath();
        jar(game.resolve("mods/voxy-addon.jar"), "{\"id\":\"some_addon\",\"description\":\"voxy\"}");
        jar(game.resolve("mods/broken-metadata.jar"), "broken");
        Files.write(game.resolve("mods/broken-zip.jar"), new byte[] { 1, 2, 3 });
        Path external = jar(game.resolve("mods/renamed-external.JAR"), "{\"id\":\"voxy\"}");
        assertEquals(external, VoxyJarLocator.find(game));
    }

    @Test
    public void searchesVersionedModsFolder() throws Exception {
        Path game = temporary.newFolder()
            .toPath();
        Path external = jar(game.resolve("mods/1.7.10/original-name.jar"), "{\"id\":\"voxy\"}");
        assertEquals(external, VoxyJarLocator.find(game));
    }

    @Test
    public void preservesOldInstallationLocationAsFallback() throws Exception {
        Path game = temporary.newFolder()
            .toPath();
        Path external = jar(game.resolve("roxy/voxy.jar"), "{\"id\":\"voxy\"}");
        assertEquals(external, VoxyJarLocator.find(game));
    }

    @Test
    public void prefersModsOverLegacyLocation() throws Exception {
        Path game = temporary.newFolder()
            .toPath();
        jar(game.resolve("roxy/voxy.jar"), "{\"id\":\"voxy\"}");
        Path external = jar(game.resolve("mods/original-name.jar"), "{\"id\":\"voxy\"}");
        assertEquals(external, VoxyJarLocator.find(game));
    }

    @Test
    public void explainsMissingModWithoutBlamingJavaOrRequiringRename() throws Exception {
        Path game = temporary.newFolder()
            .toPath();
        jar(game.resolve("mods/voxy-worldgen.jar"), "{\"id\":\"voxy_worldgen\"}");
        try {
            VoxyJarLocator.find(game);
            fail("Expected missing Voxy error");
        } catch (IOException error) {
            assertTrue(
                error.getMessage()
                    .contains("No external Voxy JAR found"));
            assertTrue(
                error.getMessage()
                    .contains(
                        game.resolve("mods")
                            .toString()));
            assertTrue(
                error.getMessage()
                    .contains("Keep its original filename"));
            assertFalse(
                error.getMessage()
                    .contains("Java"));
        }
    }

    @Test
    public void selectsSupportedOriginalAmongMultipleVoxyVersionsAndLoadsIt() throws Exception {
        String configured = System.getProperty("roxy.voxyJar");
        Assume.assumeTrue("Supply roxyVoxyJar for binary integration", configured != null);
        Path game = temporary.newFolder()
            .toPath();
        jar(game.resolve("mods/a-unsupported.jar"), "{\"id\":\"voxy\"}");
        Path external = game.resolve("mods/z-voxy-0.2.16-beta+1.21.11.jar");
        Files.copy(Paths.get(configured), external);
        Path found = VoxyJarLocator.find(game);
        assertEquals(external, found);
        try (VoxyRuntime runtime = new VoxyRuntime(found)) {
            assertTrue(runtime != null);
        }
        assertEquals(VoxyRuntime.SUPPORTED_SHA256, VoxyRuntime.sha256(external));
    }

    private static Path jar(Path path, String metadata) throws IOException {
        Files.createDirectories(path.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            zip.write(metadata.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return path;
    }
}
