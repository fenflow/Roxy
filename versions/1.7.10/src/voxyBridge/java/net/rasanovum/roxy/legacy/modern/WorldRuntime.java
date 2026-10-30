package net.rasanovum.roxy.legacy.modern;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import me.cortex.voxy.common.config.compressors.ZSTDCompressor;
import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.storage.other.CompressionStorageAdaptor;
import me.cortex.voxy.common.config.storage.rocksdb.RocksDBStorageBackend;
import me.cortex.voxy.common.thread.UnifiedServiceThreadPool;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldVoxilizedSectionMipper;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.WorldUpdater;
import me.cortex.voxy.common.world.service.SectionSavingService;

/** Lifecycle adapter around the external world engine, reducer, serializer and RocksDB backend. */
public final class WorldRuntime implements AutoCloseable {

    public final WorldEngine engine;
    public final UnifiedServiceThreadPool pool;
    private final SectionSavingService saving;
    private final WorldMapper mapper;
    private boolean closed;

    public WorldRuntime(Path directory) throws IOException {
        Files.createDirectories(directory);
        var backend = new RocksDBStorageBackend(
            directory.toAbsolutePath()
                .toString());
        var storage = new SectionSerializationStorage(new CompressionStorageAdaptor(new ZSTDCompressor(3), backend));
        try {
            engine = new WorldEngine(storage);
        } catch (Throwable error) {
            storage.close();
            throw error;
        }
        mapper = (WorldMapper) (Object) engine.getMapper();
        pool = new UnifiedServiceThreadPool();
        saving = new SectionSavingService(pool.serviceManager);
        engine.setSaveCallback(saving::enqueueSave);
        pool.setNumThreads(2);
    }

    public int registerBlock(String name, int metadata, int opacity) {
        return mapper.getIdForBlockState(new BlockState(name, metadata, opacity));
    }

    public int registerBiome(String name) {
        return mapper.registerBiome(name);
    }

    public int blockCount() {
        return mapper.getBlockStateCount();
    }

    public int pendingSaves() {
        return saving.getTaskCount();
    }

    public synchronized void ingest(int x, int y, int z, long[] blocks) {
        if (closed) throw new IllegalStateException("Voxy world is closed");
        if (blocks.length != 4096) throw new IllegalArgumentException("Expected a 16-cubed section");
        VoxelizedSection section = VoxelizedSection.createEmpty()
            .setPosition(x, y, z);
        System.arraycopy(blocks, 0, section.section, 0, blocks.length);
        int occupied = 0;
        for (long voxel : blocks) if (!WorldMapper.isAir(voxel)) occupied++;
        section.lvl0NonAirCount = occupied;
        // The cast exists only at compile time: the loader redirects the Mapper descriptor.
        WorldVoxilizedSectionMipper.mipSection(section, engine.getMapper());
        WorldUpdater.insertUpdate(engine, section);
    }

    public synchronized long[] readSection(int level, int x, int y, int z) {
        WorldSection section = engine.acquireIfExists(level, x, y, z);
        if (section == null) return null;
        try {
            return section.copyData();
        } finally {
            section.release();
        }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        saving.shutdown();
        // A renderer constructor can fail after registering a mesh service. Stop any such
        // orphan before shutting down the pool, otherwise it leaves the database locked.
        try {
            var services = me.cortex.voxy.common.thread.ServiceManager.class.getDeclaredField("services");
            services.setAccessible(true);
            for (var service : (me.cortex.voxy.common.thread.Service[]) services.get(pool.serviceManager))
                if (service.isLive()) service.shutdown();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not close remaining Voxy services", error);
        }
        pool.shutdown();
        engine.free();
    }
}
