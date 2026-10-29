package net.rasanovum.roxy.legacy.terrain;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.rasanovum.roxy.legacy.loader.LoadDiagnostics;
import net.rasanovum.roxy.legacy.loader.VoxyJarLocator;
import net.rasanovum.roxy.legacy.loader.VoxyRuntime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Owns client world lifetime; live Minecraft objects and all GL work stay on the client thread. */
public final class LegacyTerrain implements IResourceManagerReloadListener {

    public static final LegacyTerrain INSTANCE = new LegacyTerrain();
    private static final Logger LOG = LogManager.getLogger("Roxy");
    private final Minecraft mc = Minecraft.getMinecraft();
    private final LegacyTerrainRenderer renderer = new LegacyTerrainRenderer();
    private final Map<Long, LodChunk> chunks = new LinkedHashMap<Long, LodChunk>();
    private final Map<Long, Long> captured = new HashMap<Long, Long>();
    private final Set<Long> queued = new HashSet<Long>();
    private final Set<Long> requested = new HashSet<Long>();
    private final ArrayDeque<Chunk> captureQueue = new ArrayDeque<Chunk>();
    private final ConcurrentLinkedQueue<LoadedChunk> loaded = new ConcurrentLinkedQueue<LoadedChunk>();
    private final ThreadPoolExecutor io = new ThreadPoolExecutor(
        1,
        1,
        0,
        TimeUnit.SECONDS,
        new ArrayBlockingQueue<Runnable>(128),
        runnable -> {
            Thread thread = new Thread(runnable, "Roxy cache IO");
            thread.setDaemon(true);
            return thread;
        });
    private Configuration config;
    private WorldClient world;
    private VoxyRuntime runtime;
    private LodStore store;
    private ChunkBuild building;
    private long ticks;
    private int generation;
    private int distance = 32;
    private boolean enabled = true;
    private boolean failed;
    private String failureReason;
    private boolean hud;
    private int converted;
    private int restored;
    private double lastCaptureMs;
    private int errors;
    private java.lang.reflect.Method shaderPack;
    private boolean shaders;

    private static final class LoadedChunk {

        private final int generation;
        private final LodChunk chunk;

        private LoadedChunk(int generation, LodChunk chunk) {
            this.generation = generation;
            this.chunk = chunk;
        }
    }

    private LegacyTerrain() {}

    public void initialize() {
        config = new Configuration(
            mc.mcDataDir.toPath()
                .resolve("config/roxy.cfg")
                .toFile());
        config.load();
        enabled = config.getBoolean("enabled", "rendering", true, "Render cached distant terrain.");
        distance = config
            .getInt("distanceChunks", "rendering", 32, 8, 64, "Maximum cached terrain distance in chunks.");
        config.save();
        try {
            shaderPack = Class.forName("net.coderbot.iris.Iris")
                .getMethod("getCurrentPack");
        } catch (ReflectiveOperationException absent) {
            LOG.debug("No Angelica Iris shader API present");
        }
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        ((IReloadableResourceManager) mc.getResourceManager()).registerReloadListener(this);
        Runtime.getRuntime()
            .addShutdownHook(new Thread(() -> {
                io.shutdown();
                try {
                    io.awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread()
                        .interrupt();
                }
            }, "Roxy cache shutdown"));
    }

    public void setEnabled(boolean value) {
        enabled = value;
        config.get("rendering", "enabled", true)
            .set(value);
        config.save();
    }

    public void setDistance(int value) {
        distance = Math.max(8, Math.min(64, value));
        config.get("rendering", "distanceChunks", 32)
            .set(distance);
        config.save();
        requested.clear();
    }

    public void toggleHud() {
        hud = !hud;
    }

    public String status() {
        if (failed) return "Roxy disabled: " + failureReason;
        return "Roxy " + (enabled ? "ON" : "OFF")
            + ": "
            + chunks.size()
            + " cached, "
            + converted
            + " captured, "
            + restored
            + " restored; "
            + renderer.drawnChunks
            + " LOD chunks / "
            + renderer.drawnQuads
            + " quads drawn; range "
            + distance
            + "; capture "
            + String.format(java.util.Locale.ROOT, "%.1f", lastCaptureMs)
            + " ms; errors "
            + errors;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (mc.theWorld != world) switchWorld(mc.theWorld);
        if (world == null || mc.thePlayer == null || failed || runtime == null) return;
        ticks++;
        LoadedChunk result;
        while ((result = loaded.poll()) != null) {
            long key = LodChunk.key(result.chunk.x, result.chunk.z);
            if (result.generation == generation && !chunks.containsKey(key)) {
                chunks.put(key, result.chunk);
                restored++;
            }
        }
        if (ticks % 20 == 1) {
            if (shaderPack != null) {
                try {
                    boolean active = ((java.util.Optional<?>) shaderPack.invoke(null)).isPresent();
                    if (active && !shaders)
                        LOG.warn("Roxy distant drawing is suspended while an Iris shader pack is active");
                    shaders = active;
                } catch (ReflectiveOperationException error) {
                    shaders = true;
                    LOG.warn("Cannot determine shader state; distant drawing suspended", error);
                }
            }
            scan();
        }
        if (mc.isGamePaused()) return;
        long start = System.nanoTime();
        try {
            int steps = 0;
            do {
                if (building == null) {
                    Chunk next = captureQueue.poll();
                    if (next == null) break;
                    if (!world.getChunkProvider()
                        .chunkExists(next.xPosition, next.zPosition)
                        || world.getChunkFromChunkCoords(next.xPosition, next.zPosition) != next) {
                        queued.remove(LodChunk.key(next.xPosition, next.zPosition));
                        continue;
                    }
                    building = new ChunkBuild(next);
                }
                if (building.step(world, runtime)) {
                    LodChunk chunk = building.finish();
                    long key = LodChunk.key(chunk.x, chunk.z);
                    chunks.put(key, chunk);
                    captured.put(key, ticks);
                    queued.remove(key);
                    building = null;
                    converted++;
                    final LodStore target = store;
                    submit(() -> {
                        try {
                            target.write(chunk);
                        } catch (Exception error) {
                            LOG.warn("Could not save derived Roxy cache", error);
                        }
                    });
                }
            } while (++steps < 2 && System.nanoTime() - start < 4000000);
        } catch (Exception | LinkageError error) {
            errors++;
            if (building != null) {
                long key = LodChunk.key(building.chunk.xPosition, building.chunk.zPosition);
                captured.put(key, ticks);
                queued.remove(key);
                building = null;
            }
            if (errors <= 5) LOG.error("Roxy could not capture a legacy chunk", error);
        }
        lastCaptureMs = (System.nanoTime() - start) / 1000000.0;
    }

    private void switchWorld(WorldClient next) {
        generation++;
        renderer.clear();
        chunks.clear();
        captured.clear();
        queued.clear();
        requested.clear();
        captureQueue.clear();
        loaded.clear();
        building = null;
        converted = restored = errors = 0;
        failed = false;
        failureReason = null;
        ticks = 0;
        if (runtime != null) {
            try {
                runtime.close();
            } catch (Exception error) {
                LOG.warn("Could not close external Voxy JAR", error);
            }
            runtime = null;
        }
        world = next;
        if (world == null) return;
        try {
            Path root = mc.mcDataDir.toPath()
                .resolve("roxy");
            Path external = VoxyJarLocator.find(mc.mcDataDir.toPath());
            runtime = new VoxyRuntime(external);
            LOG.info("Roxy loaded external Voxy JAR: {}", external);
            String identity = mc.isIntegratedServerRunning() ? "local:" + mc.getIntegratedServer()
                .getFolderName() : "server:" + (mc.func_147104_D() == null ? "unknown" : mc.func_147104_D().serverIP);
            String id = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8))
                .toString();
            store = new LodStore(
                root.resolve("cache-v2")
                    .resolve(id)
                    .resolve("dim" + world.provider.dimensionId));
            LOG.info(
                "Roxy terrain started: dimension {}, external Voxy SHA-256 {}, cache {}",
                world.provider.dimensionId,
                VoxyRuntime.SUPPORTED_SHA256,
                id);
        } catch (Exception | LinkageError error) {
            failed = true;
            failureReason = LoadDiagnostics.describe(error);
            LOG.error("Roxy terrain disabled: " + failureReason + "; " + LoadDiagnostics.environment(), error);
            mc.ingameGUI.getChatGUI()
                .printChatMessage(new ChatComponentText("Roxy disabled: " + failureReason));
            mc.ingameGUI.getChatGUI()
                .printChatMessage(new ChatComponentText("Roxy runtime: " + LoadDiagnostics.environment()));
        }
    }

    private void scan() {
        final int cx = MathHelper.floor_double(mc.thePlayer.posX) >> 4;
        final int cz = MathHelper.floor_double(mc.thePlayer.posZ) >> 4;
        // GTNH's server can send more chunks than Angelica currently draws. Capture those too.
        // Client provideChunk returns EmptyChunk for absent coordinates; this never requests generation.
        int liveRadius = Math.max(distance, mc.gameSettings.renderDistanceChunks + 1);
        List<long[]> reads = new ArrayList<long[]>();
        for (int radius = 0; radius <= Math.max(distance, liveRadius); radius++) {
            for (int z = -radius; z <= radius; z++) {
                for (int x = -radius; x <= radius; x++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                    int px = cx + x, pz = cz + z;
                    long key = LodChunk.key(px, pz);
                    Long previous = captured.get(key);
                    long refresh = radius <= 1 ? 100 : 1200;
                    if (radius <= liveRadius && (previous == null || ticks - previous > refresh)
                        && !queued.contains(key)
                        && world.getChunkProvider()
                            .chunkExists(px, pz)) {
                        Chunk chunk = world.getChunkFromChunkCoords(px, pz);
                        if (chunk.isChunkLoaded && !chunk.isEmpty()) {
                            queued.add(key);
                            if (radius <= 1 && previous != null) captureQueue.addFirst(chunk);
                            else captureQueue.add(chunk);
                        }
                    }
                    if (radius <= distance && !chunks.containsKey(key) && requested.add(key)) {
                        reads.add(new long[] { px, pz });
                    }
                }
            }
        }
        if (!reads.isEmpty()) {
            final LodStore source = store;
            final int epoch = generation;
            if (!submit(() -> {
                for (long[] pos : reads) {
                    try {
                        LodChunk chunk = source.read((int) pos[0], (int) pos[1]);
                        if (chunk != null) loaded.add(new LoadedChunk(epoch, chunk));
                    } catch (Exception error) {
                        LOG.warn("Ignoring invalid derived Roxy cache at {}, {}: {}", pos[0], pos[1], error.toString());
                    }
                }
            })) {
                for (long[] pos : reads) requested.remove(LodChunk.key((int) pos[0], (int) pos[1]));
            }
        }
        if (chunks.size() > 4096) {
            List<Long> keys = new ArrayList<Long>(chunks.keySet());
            keys.sort((a, b) -> Integer.compare(range(chunks.get(b), cx, cz), range(chunks.get(a), cx, cz)));
            for (int i = 0, remove = chunks.size() - 4096; i < remove; i++) {
                chunks.remove(keys.get(i));
                requested.remove(keys.get(i));
            }
        }
    }

    private static int range(LodChunk chunk, int x, int z) {
        return Math.max(Math.abs(chunk.x - x), Math.abs(chunk.z - z));
    }

    private boolean submit(Runnable job) {
        try {
            io.execute(job);
            return true;
        } catch (RejectedExecutionException busy) {
            LOG.warn("Roxy cache queue is full; this derived cache update will be retried on a later capture");
            return false;
        }
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        if (!enabled || failed || shaders || world == null || runtime == null) {
            renderer.drawnChunks = renderer.drawnQuads = 0;
            return;
        }
        try {
            renderer.render(chunks, distance, event.partialTicks);
        } catch (Exception | LinkageError error) {
            failed = true;
            failureReason = "Renderer failure: " + LoadDiagnostics.describe(error);
            LOG.error("Roxy renderer disabled after an error", error);
        }
    }

    @SubscribeEvent
    public void fog(EntityViewRenderEvent.RenderFogEvent event) {
        if (enabled && !failed
            && !shaders
            && runtime != null
            && !chunks.isEmpty()
            && event.fogMode >= 0
            && !LegacyTerrainRenderer.hasEnvironmentalFog(event.entity)) {
            renderer.prepareProjection(distance);
            GL11.glFogf(GL11.GL_FOG_START, distance * 12);
            GL11.glFogf(GL11.GL_FOG_END, distance * 16);
        }
    }

    @SubscribeEvent
    public void overlay(RenderGameOverlayEvent.Text event) {
        if (world != null && (hud || mc.gameSettings.showDebugInfo)) {
            event.left.add(
                "Roxy " + (failed ? "ERROR" : enabled ? "ON" : "OFF")
                    + " | cached "
                    + chunks.size()
                    + " | drawn "
                    + renderer.drawnChunks
                    + " chunks, "
                    + renderer.drawnQuads
                    + " quads");
            event.left.add(
                "Voxy LOD | captured " + converted
                    + " | restored "
                    + restored
                    + " | errors "
                    + errors
                    + " | range "
                    + distance
                    + " chunks");
            if (shaders) event.left.add("Roxy drawing suspended: disable the Iris shader pack to show LODs");
        }
    }

    @Override
    public void onResourceManagerReload(IResourceManager resources) {
        renderer.reload();
    }
}
