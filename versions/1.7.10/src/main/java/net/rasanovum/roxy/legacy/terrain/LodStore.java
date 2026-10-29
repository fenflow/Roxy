package net.rasanovum.roxy.legacy.terrain;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Derived client cache only. Never reads or modifies Minecraft region/save data. */
public final class LodStore {

    private static final int MAGIC = 0x524c4431;
    private final Path directory;

    public LodStore(Path directory) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory);
    }

    public Path path(int x, int z) {
        return directory.resolve(x + "_" + z + ".rlod");
    }

    public void write(LodChunk chunk) throws IOException {
        Path target = path(chunk.x, chunk.z);
        Path temporary = Files.createTempFile(directory, "chunk-", ".tmp");
        try {
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(temporary)))) {
                out.writeInt(MAGIC);
                out.writeInt(chunk.x);
                out.writeInt(chunk.z);
                out.writeInt(chunk.names.length);
                for (int i = 0; i < chunk.names.length; i++) {
                    out.writeUTF(chunk.names[i]);
                    out.writeInt(chunk.metadata[i]);
                }
                for (int i = 0; i < LodChunk.CELLS; i++) {
                    out.writeInt(chunk.states[i]);
                    out.writeInt(chunk.colors[i]);
                    out.writeByte(chunk.lights[i]);
                }
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public LodChunk read(int x, int z) throws IOException {
        Path file = path(x, z);
        if (!Files.isRegularFile(file)) return null;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC || in.readInt() != x || in.readInt() != z) {
                throw new IOException("Invalid LOD cache header");
            }
            int size = in.readInt();
            if (size < 1 || size > 65536) throw new IOException("Invalid LOD cache palette size");
            String[] names = new String[size];
            int[] metadata = new int[size];
            for (int i = 0; i < size; i++) {
                names[i] = in.readUTF();
                metadata[i] = in.readInt();
                if (names[i].length() > 256 || metadata[i] < 0) throw new IOException("Invalid cached block state");
            }
            int[] states = new int[LodChunk.CELLS];
            int[] colors = new int[LodChunk.CELLS];
            byte[] lights = new byte[LodChunk.CELLS];
            for (int i = 0; i < states.length; i++) {
                states[i] = in.readInt();
                colors[i] = in.readInt();
                lights[i] = in.readByte();
                if (states[i] < 0 || states[i] >= size) throw new IOException("Invalid cached palette reference");
            }
            if (in.read() != -1) throw new IOException("Trailing data in LOD cache");
            return new LodChunk(x, z, names, metadata, states, colors, lights);
        }
    }
}
