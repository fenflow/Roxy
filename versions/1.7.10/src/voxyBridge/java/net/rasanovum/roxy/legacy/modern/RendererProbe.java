package net.rasanovum.roxy.legacy.modern;

import java.nio.file.Path;
import java.util.ArrayList;

import me.cortex.voxy.client.core.RenderResourceReuse;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.gl.Capabilities;

/** Constructs the actual GPU terrain pipeline. This diagnostic does not enable terrain rendering. */
public final class RendererProbe {

    private RendererProbe() {}

    public static String run(Path directory) throws Exception {
        PlatformGame.gameDirectory = directory;
        String previous = System.getProperty("voxy.geometryBufferSizeOverrideMB");
        System.setProperty("voxy.geometryBufferSizeOverrideMB", "512");
        try (GlStateScope ignored = new GlStateScope();
            WorldRuntime world = new WorldRuntime(directory.resolve("roxy/renderer-probe-db"))) {
            Capabilities.init();
            world.registerBiome("minecraft:plains");
            VoxyRenderSystem renderer = new VoxyRenderSystem(world.engine, world.pool.serviceManager);
            try {
                ArrayList<String> debug = new ArrayList<>();
                renderer.addDebugInfo(debug);
                return "Original Voxy GPU terrain pipeline constructed: " + debug;
            } finally {
                renderer.shutdown();
                RenderResourceReuse.clearResources();
            }
        } finally {
            if (previous == null) System.clearProperty("voxy.geometryBufferSizeOverrideMB");
            else System.setProperty("voxy.geometryBufferSizeOverrideMB", previous);
        }
    }
}
