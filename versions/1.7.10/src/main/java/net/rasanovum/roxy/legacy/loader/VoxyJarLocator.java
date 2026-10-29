package net.rasanovum.roxy.legacy.loader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Locates the separately installed Fabric mod by metadata, not its filename. */
public final class VoxyJarLocator {

    private VoxyJarLocator() {}

    public static Path find(Path gameDirectory) throws IOException {
        Path root = gameDirectory.toAbsolutePath()
            .normalize();
        List<Path> candidates = new ArrayList<Path>();
        collect(root.resolve("mods"), candidates);
        collect(root.resolve("mods/1.7.10"), candidates);
        Collections.sort(candidates);
        for (Path candidate : candidates) {
            if (VoxyRuntime.SUPPORTED_SHA256.equals(VoxyRuntime.sha256(candidate))) return candidate;
        }
        // Let VoxyRuntime explain an unsupported candidate, including its hash and required release.
        if (!candidates.isEmpty()) return candidates.get(0);
        Path legacy = root.resolve("roxy/voxy.jar");
        if (Files.isRegularFile(legacy)) return legacy;
        throw new IOException(
            "No external Voxy JAR found in " + root.resolve("mods")
                + " or its 1.7.10 subfolder. "
                + "Download Voxy 0.2.16-beta for MC 1.21.11 (Modrinth H3w2nVdU) and put the original JAR in mods. "
                + "Keep its original filename. The old roxy/voxy.jar location is also supported.");
    }

    private static void collect(Path directory, List<Path> candidates) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                if (Files.isRegularFile(file) && file.getFileName()
                    .toString()
                    .toLowerCase(Locale.ROOT)
                    .endsWith(".jar") && isVoxy(file)) candidates.add(file);
            }
        }
    }

    private static boolean isVoxy(Path path) {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry metadata = zip.getEntry("fabric.mod.json");
            if (metadata == null || metadata.getSize() > 65536) return false;
            try (InputStream input = zip.getInputStream(metadata)) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > 65536) return false;
                    bytes.write(buffer, 0, count);
                }
                JsonElement parsed = new JsonParser().parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                if (!parsed.isJsonObject()) return false;
                JsonObject object = parsed.getAsJsonObject();
                JsonElement id = object.get("id");
                return id != null && id.isJsonPrimitive() && "voxy".equals(id.getAsString());
            }
        } catch (IOException | RuntimeException ignored) {
            // Unrelated or broken mods must not prevent finding a valid Voxy JAR elsewhere.
            return false;
        }
    }
}
