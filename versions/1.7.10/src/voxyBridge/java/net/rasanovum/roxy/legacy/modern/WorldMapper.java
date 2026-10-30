package net.rasanovum.roxy.legacy.modern;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import me.cortex.voxy.common.config.IMappingStorage;

/** Legacy registry persistence adapter. Voxy owns the database, sections and mip hierarchy. */
public final class WorldMapper {

    private static final int FORMAT = 0x524d5031;
    private final IMappingStorage storage;
    private final List<StateEntry> states = new ArrayList<>();
    private final List<BiomeEntry> biomes = new ArrayList<>();
    private final Map<BlockState, Integer> stateIds = new HashMap<>();
    private final Map<String, Integer> biomeIds = new HashMap<>();
    private Consumer<StateEntry> stateCallback;
    private Consumer<BiomeEntry> biomeCallback;

    public static final class StateEntry {

        public final int id;
        public final BlockState state;
        public final int opacity;

        StateEntry(int id, BlockState state) {
            this.id = id;
            this.state = state;
            this.opacity = state.opacity();
        }
    }

    public static final class BiomeEntry {

        public final int id;
        public final String biome;

        BiomeEntry(int id, String biome) {
            this.id = id;
            this.biome = biome;
        }
    }

    public WorldMapper(IMappingStorage storage) {
        this.storage = storage;
        BlockState air = new BlockState("minecraft:air", 0, 0);
        states.add(new StateEntry(0, air));
        stateIds.put(air, 0);
        var mappings = storage.getIdMappingsData();
        int[] keys = mappings.keySet()
            .toIntArray();
        Arrays.sort(keys);
        // Unsigned type bits put biomes before states in signed order; each list is independent.
        for (int key : keys) {
            int type = key >>> 30;
            int id = key & 0x3fffffff;
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(mappings.get(key)))) {
                if (in.readInt() != FORMAT) throw new IOException("Not a GTNH registry mapping");
                String name = in.readUTF();
                if (type == 1 && id == states.size()) {
                    BlockState state = new BlockState(name, in.readInt(), in.readUnsignedByte());
                    if (stateIds.put(state, id) != null) throw new IOException("Duplicate legacy state");
                    states.add(new StateEntry(id, state));
                } else if (type == 2 && id == biomes.size()) {
                    if (biomeIds.put(name, id) != null) throw new IOException("Duplicate legacy biome");
                    biomes.add(new BiomeEntry(id, name));
                } else throw new IOException("Invalid legacy registry sequence: " + key);
                if (in.read() != -1) throw new IOException("Trailing registry bytes");
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        }
    }

    public synchronized int getIdForBlockState(BlockState state) {
        if (state.isAir()) return 0;
        Integer existing = stateIds.get(state);
        if (existing != null) return existing;
        if (states.size() >= (1 << 20)) throw new IllegalStateException("Voxy block palette is full");
        int id = states.size();
        persist((1 << 30) | id, state.name(), state);
        StateEntry entry = new StateEntry(id, state);
        states.add(entry);
        stateIds.put(state, id);
        if (stateCallback != null) stateCallback.accept(entry);
        return id;
    }

    public synchronized int registerBiome(String name) {
        Integer existing = biomeIds.get(name);
        if (existing != null) return existing;
        if (name == null || name.isEmpty() || name.length() > 256 || biomes.size() >= 512)
            throw new IllegalArgumentException("Invalid/full Voxy biome palette");
        int id = biomes.size();
        persist((2 << 30) | id, name, null);
        BiomeEntry entry = new BiomeEntry(id, name);
        biomes.add(entry);
        biomeIds.put(name, id);
        if (biomeCallback != null) biomeCallback.accept(entry);
        return id;
    }

    private void persist(int key, String name, BlockState state) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(FORMAT);
            out.writeUTF(name);
            if (state != null) {
                out.writeInt(state.metadata());
                out.writeByte(state.opacity());
            }
            byte[] data = bytes.toByteArray();
            ByteBuffer direct = ByteBuffer.allocateDirect(data.length);
            direct.put(data)
                .flip();
            storage.putIdMapping(key, direct);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    public synchronized int getBlockStateOpacity(long voxel) {
        return getBlockStateOpacity(getBlockId(voxel));
    }

    public synchronized int getBlockStateOpacity(int id) {
        return states.get(id).opacity;
    }

    public synchronized BlockState getBlockStateFromBlockId(int id) {
        return states.get(id).state;
    }

    public synchronized int getBlockStateCount() {
        return states.size();
    }

    public synchronized StateEntry[] getStateEntries() {
        return states.toArray(StateEntry[]::new);
    }

    public synchronized BiomeEntry[] getBiomeEntries() {
        return biomes.toArray(BiomeEntry[]::new);
    }

    public synchronized void setStateCallback(Consumer<StateEntry> callback) {
        stateCallback = callback;
    }

    public synchronized void setBiomeCallback(Consumer<BiomeEntry> callback) {
        biomeCallback = callback;
    }

    public void close() {}

    public static boolean isAir(long voxel) {
        return getBlockId(voxel) == 0;
    }

    public static int getBlockId(long voxel) {
        return (int) ((voxel >>> 27) & 0xfffff);
    }

    public static int getBiomeId(long voxel) {
        return (int) ((voxel >>> 47) & 511);
    }

    public static int getLightId(long voxel) {
        return (int) (voxel >>> 56);
    }

    public static long withLight(long voxel, int light) {
        return (voxel & 0x00ffffffffffffffL) | ((long) (light & 255) << 56);
    }

    public static long airWithLight(int light) {
        return (long) (light & 255) << 56;
    }

    public static long composeMappingId(byte light, int block, int biome) {
        return ((long) (light & 255) << 56) | ((long) block << 27) | (block == 0 ? 0 : (long) biome << 47);
    }

    public static long withBlockBiome(long voxel, int block, int biome) {
        return composeMappingId((byte) getLightId(voxel), block, biome);
    }
}
