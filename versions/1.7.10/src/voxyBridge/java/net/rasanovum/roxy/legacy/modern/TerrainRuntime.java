package net.rasanovum.roxy.legacy.modern;

import static org.lwjgl.opengl.GL45C.*;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import me.cortex.voxy.client.core.RenderResourceReuse;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.gl.Capabilities;
import me.cortex.voxy.client.core.rendering.Viewport;

/** Connects the original renderer to legacy matrices and framebuffer attachments. */
public final class TerrainRuntime implements AutoCloseable {

    private static final boolean DIAGNOSTICS = Boolean.getBoolean("roxy.rendererDiagnostics")
        || System.getProperty("roxy.developmentBridgeJar") != null;

    private final WorldRuntime world;
    private VoxyRenderSystem renderer;
    private Method setupViewport;
    private AngelicaSections nearSections;
    private int bridgeFramebuffer;
    private int colourTexture;
    private int depthTexture;
    private int width;
    private int height;
    private int depthFormat;
    private boolean closed;
    private int frames;

    public TerrainRuntime(Path gameDirectory, Path database) throws Exception {
        PlatformGame.gameDirectory = gameDirectory;
        world = new WorldRuntime(database);
    }

    public String[] requiredModels() {
        WorldMapper mapper = (WorldMapper) (Object) world.engine.getMapper();
        return java.util.Arrays.stream(mapper.getStateEntries())
            .filter(entry -> entry.id != 0)
            .map(entry -> entry.state.name() + "\t" + entry.state.metadata())
            .toArray(String[]::new);
    }

    public void start(int vanillaDistance, int mipLevels) throws Exception {
        PlatformGame.renderDistance = vanillaDistance;
        PlatformGame.mipLevels = mipLevels;
        // Leave room for GTNH's atlas and Angelica. Honour an explicit user override.
        if (System.getProperty("voxy.geometryBufferSizeOverrideMB") == null)
            System.setProperty("voxy.geometryBufferSizeOverrideMB", "512");
        try (GlStateScope ignored = new GlStateScope()) {
            Capabilities.init();
            me.cortex.voxy.client.RenderStatistics.enabled = DIAGNOSTICS;
            renderer = new VoxyRenderSystem(world.engine, world.pool.serviceManager);
            nearSections = new AngelicaSections();
            setupViewport = VoxyRenderSystem.class.getMethod(
                "setupViewport",
                Matrix4fc.class,
                Matrix4fc.class,
                PlatformClient.Fog.class,
                double.class,
                double.class,
                double.class);
        }
    }

    public int registerBlock(String name, int metadata, int opacity) {
        return world.registerBlock(name, metadata, opacity);
    }

    public void setDistance(int chunks) {
        PlatformConfig.CONFIG.sectionRenderDistance = chunks / 32f;
        if (renderer != null) renderer.setRenderDistance(PlatformConfig.CONFIG.sectionRenderDistance);
    }

    public int registerBiome(int biome) {
        return world.registerBiome("gtnh:biome/" + biome);
    }

    public void ingest(int x, int y, int z, long[] data) {
        world.ingest(x, y, z, data);
    }

    public void sectionLoaded(int x, int y, int z, boolean loaded) {
        long packed = ((long) (x & 0x3fffff) << 42) | ((long) (z & 0x3fffff) << 20) | (y & 0xfffffL);
        if (loaded) renderer.chunkBoundRenderer.addSection(packed);
        else renderer.chunkBoundRenderer.removeSection(packed);
    }

    public String debug() {
        ArrayList<String> result = new ArrayList<>();
        if (renderer != null) renderer.addDebugInfo(result);
        return result.toString();
    }

    private String materialDiagnostics() {
        try {
            var field = VoxyRenderSystem.class.getDeclaredField("modelService");
            field.setAccessible(true);
            var service = (me.cortex.voxy.client.core.model.ModelBakerySubsystem) field.get(renderer);
            var store = service.getStore();
            var textures = store.getClass()
                .getDeclaredField("textures");
            textures.setAccessible(true);
            int atlas = ((me.cortex.voxy.client.core.gl.GlTexture) textures.get(store)).id;
            var buffer = store.getClass()
                .getDeclaredField("modelBuffer");
            buffer.setAccessible(true);
            int modelBuffer = ((me.cortex.voxy.client.core.gl.GlBuffer) buffer.get(store)).id;
            StringBuilder result = new StringBuilder();
            for (var entry : ((WorldMapper) (Object) world.engine.getMapper()).getStateEntries()) {
                String name = entry.state.name();
                if (!name.equals("minecraft:ice") && !name.equals("minecraft:water")) continue;
                int model = service.factory.getModelId(entry.id);
                long metadata = service.factory.getModelMetadataFromClientId(model);
                result.append("\nmaterial=")
                    .append(entry.state)
                    .append(" inputTranslucent=")
                    .append(entry.state.input().translucent)
                    .append(" model=")
                    .append(model)
                    .append(" metadata=")
                    .append(Long.toHexString(metadata))
                    .append(" translucent=")
                    .append(me.cortex.voxy.client.core.model.ModelQueries.isTranslucent(metadata));
                int[] flags = new int[1];
                glGetNamedBufferSubData(modelBuffer, model * 64L + 24, flags);
                result.append(" gpuFlags=")
                    .append(Integer.toHexString(flags[0]));
                result.append(" gpuTopPixel=")
                    .append(texturePixel(atlas, (model & 255) * 48 + 8, (model >>> 8) * 32 + 24));
            }
            var pipelineField = VoxyRenderSystem.class.getDeclaredField("pipeline");
            pipelineField.setAccessible(true);
            Object pipeline = pipelineField.get(renderer);
            for (String name : new String[] { "colourTex", "colourSSAOTex" }) {
                var textureField = pipeline.getClass()
                    .getDeclaredField(name);
                textureField.setAccessible(true);
                int texture = ((me.cortex.voxy.client.core.gl.GlTexture) textureField.get(pipeline)).id;
                int w = glGetTextureLevelParameteri(texture, 0, GL_TEXTURE_WIDTH);
                int h = glGetTextureLevelParameteri(texture, 0, GL_TEXTURE_HEIGHT);
                result.append("\n")
                    .append(name)
                    .append(" sample=")
                    .append(texturePixel(texture, w * 7 / 8, h * 9 / 16));
            }
            return result.toString();
        } catch (Exception error) {
            return "\nmaterial diagnostic unavailable: " + error;
        }
    }

    private static String texturePixel(int texture, int x, int y) {
        int oldBuffer = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        int[] pixel = new int[1];
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        try {
            glGetTextureSubImage(texture, 0, x, y, 0, 1, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            return Integer.toHexString(pixel[0]);
        } finally {
            glBindBuffer(GL_PIXEL_PACK_BUFFER, oldBuffer);
        }
    }

    public void render(float[] projection, float[] view, double x, double y, double z, int distance, float partialTick)
        throws Exception {
        if (renderer == null || closed) return;
        try (GlStateScope ignored = new GlStateScope()) {
            int incomingStencilMask = glGetInteger(GL_STENCIL_WRITEMASK);
            boolean incomingDepthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
            int incomingBlendEquation = glGetInteger(GL_BLEND_EQUATION_RGB);
            // Named framebuffer clears still obey write masks and scissor state inherited from Minecraft.
            glDisable(GL_SCISSOR_TEST);
            glDisable(GL_RASTERIZER_DISCARD);
            glDisable(GL_BLEND);
            glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
            glStencilMask(0xff);
            glDepthMask(true);
            glColorMask(true, true, true, true);
            glDepthRange(0, 1);
            glFrontFace(GL_CCW);
            int source = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
            if (source == 0) throw new IllegalStateException("Voxy requires the game's offscreen framebuffer");
            int[] viewport = new int[4];
            glGetIntegerv(GL_VIEWPORT, viewport);
            int attachment = glGetNamedFramebufferAttachmentParameteri(
                source,
                GL_DEPTH_ATTACHMENT,
                GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            int type = glGetNamedFramebufferAttachmentParameteri(
                source,
                GL_DEPTH_ATTACHMENT,
                GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            boolean copy = type == GL_RENDERBUFFER;
            if (copy) {
                int format = glGetNamedRenderbufferParameteri(attachment, GL_RENDERBUFFER_INTERNAL_FORMAT);
                resize(viewport[2], viewport[3], format);
                glBlitNamedFramebuffer(
                    source,
                    bridgeFramebuffer,
                    0,
                    0,
                    width,
                    height,
                    0,
                    0,
                    width,
                    height,
                    GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT,
                    GL_NEAREST);
                glBindFramebuffer(GL_FRAMEBUFFER, bridgeFramebuffer);
            } else
                if (type != GL_TEXTURE) throw new IllegalStateException("Unsupported game depth attachment: " + type);
            PlatformGame.renderDistance = distance;
            PlatformGame.partialTick = partialTick;
            PlatformGame.projection = new Matrix4f().set(projection);
            Viewport<?> target = (Viewport<?>) setupViewport.invoke(
                renderer,
                PlatformGame.projection,
                new Matrix4f().set(view),
                new PlatformClient.Fog(0, 0, 0, 0, Float.MAX_VALUE, Float.MAX_VALUE),
                x,
                y,
                z);
            nearSections.sync(renderer.chunkBoundRenderer);
            String depthDiagnostic = "";
            if (frames % 120 == 119 && DIAGNOSTICS) {
                int tex = copy ? depthTexture : attachment;
                int tw = glGetTextureLevelParameteri(tex, 0, GL_TEXTURE_WIDTH);
                int th = glGetTextureLevelParameteri(tex, 0, GL_TEXTURE_HEIGHT);
                float[] pixels = new float[tw * th];
                glGetTextureImage(tex, 0, GL_DEPTH_COMPONENT, GL_FLOAT, pixels);
                int far = 0, zero = 0;
                float min = 1, max = 0;
                for (float value : pixels) {
                    if (value == 1) far++;
                    if (value == 0) zero++;
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
                depthDiagnostic = "\nsourceDepth=" + tw
                    + "x"
                    + th
                    + " far="
                    + far
                    + " zero="
                    + zero
                    + " min="
                    + min
                    + " max="
                    + max;
            }
            renderer.renderOpaque(target);
            if (++frames % 120 == 0 && DIAGNOSTICS) {
                java.nio.file.Files.writeString(
                    PlatformGame.gameDirectory.resolve("roxy/render-diagnostics.txt"),
                    "frame=" + frames
                        + " source="
                        + source
                        + " depthType="
                        + type
                        + " incomingStencilMask="
                        + incomingStencilMask
                        + " incomingDepthMask="
                        + incomingDepthMask
                        + " incomingBlendEquation="
                        + incomingBlendEquation
                        + " glError="
                        + glGetError()
                        + "\ncamera="
                        + x
                        + ","
                        + y
                        + ","
                        + z
                        + " nearSections="
                        + nearSections.count()
                        + "\nprojection="
                        + new Matrix4f().set(projection)
                        + "\nview="
                        + new Matrix4f().set(view)
                        + depthDiagnostic
                        + "\n"
                        + debug()
                        + materialDiagnostics());
            }
            if (copy) glBlitNamedFramebuffer(
                bridgeFramebuffer,
                source,
                0,
                0,
                width,
                height,
                0,
                0,
                width,
                height,
                GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT,
                GL_NEAREST);
        }
    }

    private void resize(int newWidth, int newHeight, int format) {
        if (bridgeFramebuffer != 0 && width == newWidth && height == newHeight && depthFormat == format) return;
        releaseFramebuffer();
        width = newWidth;
        height = newHeight;
        depthFormat = format;
        bridgeFramebuffer = glCreateFramebuffers();
        colourTexture = glCreateTextures(GL_TEXTURE_2D);
        depthTexture = glCreateTextures(GL_TEXTURE_2D);
        glTextureStorage2D(colourTexture, 1, GL_RGBA8, width, height);
        glTextureStorage2D(depthTexture, 1, depthFormat, width, height);
        glNamedFramebufferTexture(bridgeFramebuffer, GL_COLOR_ATTACHMENT0, colourTexture, 0);
        glNamedFramebufferTexture(bridgeFramebuffer, GL_DEPTH_ATTACHMENT, depthTexture, 0);
        if (glCheckNamedFramebufferStatus(bridgeFramebuffer, GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Incomplete legacy depth-copy framebuffer");
    }

    private void releaseFramebuffer() {
        if (bridgeFramebuffer != 0) glDeleteFramebuffers(bridgeFramebuffer);
        if (colourTexture != 0) glDeleteTextures(colourTexture);
        if (depthTexture != 0) glDeleteTextures(depthTexture);
        bridgeFramebuffer = colourTexture = depthTexture = 0;
    }

    public void close() {
        if (closed) return;
        closed = true;
        try (GlStateScope ignored = new GlStateScope()) {
            try {
                if (renderer != null) renderer.shutdown();
            } finally {
                releaseFramebuffer();
                RenderResourceReuse.clearResources();
                world.close();
            }
        }
    }
}
