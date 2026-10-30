package net.rasanovum.roxy.legacy.shader;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.block.Block;
import net.rasanovum.roxy.legacy.terrain.ModernTerrain;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.google.common.collect.ImmutableList;

/** Parent-loader state shared by Angelica and the isolated original Voxy engine. */
public final class ShaderHooks {

    private static final Map<Object, Object[]> SOURCES = new WeakHashMap<Object, Object[]>();
    private static final Map<Object, Object[]> PIPELINES = new WeakHashMap<Object, Object[]>();
    public static final Map<String, float[]> MATRICES = new HashMap<String, float[]>();
    public static int opaqueDepth, translucentDepth;
    private static int emptyDepth;
    public static float partialTick;

    private ShaderHooks() {}

    public static ImmutableList<String> sources(ImmutableList<String> original) {
        return ImmutableList.<String>builder()
            .addAll(original)
            .add("voxy.json", "voxy_opaque.glsl", "voxy_translucent.glsl", "voxy_taa.glsl")
            .build();
    }

    public static ImmutableList<?> defines(ImmutableList<?> original) {
        try {
            List<Object> values = new ArrayList<Object>(original);
            values.add(
                Class.forName("net.coderbot.iris.shaderpack.StringPair")
                    .getConstructor(String.class, String.class)
                    .newInstance("VOXY", "2"));
            return ImmutableList.copyOf(values);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot register Voxy shader define", error);
        }
    }

    public static void programSet(Object programs, Object directory, Function<?, ?> provider, Object pack) {
        SOURCES.put(programs, new Object[] { pack, directory, provider });
    }

    public static void pipeline(Object pipeline, Object programs) {
        PIPELINES.put(pipeline, SOURCES.get(programs));
    }

    public static Object[] sourcesFor(Object pipeline) {
        return PIPELINES.get(pipeline);
    }

    public static Object activePipeline() {
        try {
            Object manager = Class.forName("net.coderbot.iris.Iris")
                .getMethod("getPipelineManager")
                .invoke(null);
            Object pipeline = manager.getClass()
                .getMethod("getPipelineNullable")
                .invoke(manager);
            return pipeline != null && pipeline.getClass()
                .getName()
                .endsWith("DeferredWorldRenderingPipeline") ? pipeline : null;
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot inspect Angelica pipeline", error);
        }
    }

    public static boolean shadowPass() {
        try {
            return (Boolean) Class.forName("net.coderbot.iris.shadows.ShadowRenderingState")
                .getMethod("areShadowsCurrentlyBeingRendered")
                .invoke(null);
        } catch (ClassNotFoundException absent) {
            return false;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void uniforms(Object holder) {
        try {
            Class<?> frequency = Class.forName("net.coderbot.iris.gl.uniform.UniformUpdateFrequency");
            Object frame = Enum.valueOf((Class) frequency, "PER_FRAME");
            Class<?> uniformHolder = Class.forName("net.coderbot.iris.gl.uniform.UniformHolder");
            Method matrix = uniformHolder.getMethod("uniformMatrixFromArray", frequency, String.class, Supplier.class);
            for (final String name : new String[] { "vxViewProj", "vxViewProjInv", "vxViewProjPrev", "vxModelView",
                "vxModelViewInv", "vxModelViewPrev", "vxProj", "vxProjInv", "vxProjPrev" }) {
                final float[] identity = { 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1 };
                matrix.invoke(holder, frame, name, (Supplier<float[]>) () -> MATRICES.getOrDefault(name, identity));
            }
            uniformHolder.getMethod("uniform1i", frequency, String.class, IntSupplier.class)
                .invoke(holder, frame, "vxRenderDistance", (IntSupplier) () -> ModernTerrain.INSTANCE.distance());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot register Voxy shader uniforms", error);
        }
    }

    public static void samplers(Object holder) {
        try {
            Method method = Class.forName("net.coderbot.iris.gl.sampler.SamplerHolder")
                .getMethod("addDynamicSampler", IntSupplier.class, String[].class);
            method.invoke(holder, (IntSupplier) () -> depth(false), new String[] { "vxDepthTexOpaque" });
            method.invoke(holder, (IntSupplier) () -> depth(true), new String[] { "vxDepthTexTrans" });
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot register Voxy shader depth", error);
        }
    }

    public static int depth(boolean translucent) {
        int depth = translucent ? translucentDepth : opaqueDepth;
        if (depth != 0) return depth;
        if (emptyDepth == 0) {
            int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            emptyDepth = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, emptyDepth);
            java.nio.FloatBuffer pixel = BufferUtils.createFloatBuffer(1);
            pixel.put(1)
                .flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, 0x81A6, 1, 1, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, pixel);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        }
        return emptyDepth;
    }

    public static int blockId(String name, int metadata) {
        try {
            Class<?> settingsType = Class.forName("net.coderbot.iris.block_rendering.BlockRenderingSettings");
            Object settings = settingsType.getField("INSTANCE")
                .get(null);
            Map<?, ?> map = (Map<?, ?>) settingsType.getMethod("getBlockMetaMatches")
                .invoke(settings);
            Object entries = map == null ? null : map.get(Block.blockRegistry.getObject(name));
            if (entries == null) return -1;
            return (Integer) Class.forName("net.coderbot.iris.block_rendering.BlockMaterialMapping")
                .getMethod("resolveId", Class.forName("it.unimi.dsi.fastutil.ints.Int2IntMap"), int.class)
                .invoke(null, entries, metadata);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot map shader material", error);
        }
    }
}
