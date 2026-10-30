package net.rasanovum.roxy.legacy.modern;

import static net.rasanovum.roxy.legacy.modern.ShaderReflection.*;
import static org.lwjgl.opengl.GL45C.*;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import net.rasanovum.roxy.legacy.bridge.LegacyModelInputs;
import net.rasanovum.roxy.legacy.shader.ShaderHooks;

import org.joml.Matrix4fc;
import org.joml.Vector2fc;
import org.joml.Vector2ic;
import org.joml.Vector3fc;
import org.joml.Vector3ic;
import org.joml.Vector4fc;
import org.joml.Vector4ic;
import org.lwjgl.system.MemoryUtil;

import me.cortex.voxy.client.core.IrisVoxyRenderPipeline;

/** Adapts Angelica resources to the original external Voxy shader pipeline's data ABI. */
public final class ShaderPipelineData {

    public IrisVoxyRenderPipeline thePipeline;
    public final int[] opaqueDrawTargets, translucentDrawTargets;
    public final boolean renderToVanillaDepth, useViewportDims, skipShaderDepthHackFix;
    public final float[] resolutionScale;
    public final String TAA;
    private final Object iris, patch;
    private final StructLayout uniforms;
    private final ImageSet images;
    private final SSBOSet buffers;

    public record StructLayout(int size, String layout, LongConsumer updater) {}

    public record ImageSet(String layout, IntConsumer bindingFunction) {}

    public record SSBOSet(String layout, IntConsumer bindingFunction) {}

    public ShaderPipelineData(Object iris) {
        this.iris = iris;
        Object[] sources = ShaderHooks.sourcesFor(iris);
        if (sources == null) throw new IllegalStateException("Angelica did not expose this shader pack's sources");
        patch = call(type("me.cortex.voxy.client.iris.IrisShaderPatch"), "makePatch", sources);
        if (patch == null)
            throw new IllegalStateException("This shader pack does not provide the Voxy shader interface (voxy.json)");
        opaqueDrawTargets = targets((int[]) call(patch, "getOpqaueTargets"));
        translucentDrawTargets = targets((int[]) call(patch, "getTranslucentTargets"));
        renderToVanillaDepth = (Boolean) call(patch, "emitToVanillaDepth");
        useViewportDims = (Boolean) call(patch, "useViewportDims");
        skipShaderDepthHackFix = (Boolean) call(patch, "skipShaderDepthHackFix");
        resolutionScale = (float[]) call(patch, "getRenderScale");
        TAA = (String) call(patch, "getTAAShift");
        uniforms = uniforms();
        images = images();
        buffers = buffers();
    }

    public StructLayout getUniforms() {
        return uniforms;
    }

    public ImageSet getImageSet() {
        return images;
    }

    public SSBOSet getSsboSet() {
        return buffers;
    }

    public Runnable getBlender() {
        return (Runnable) call(patch, "createBlendSetup");
    }

    public String opaqueFragPatch() {
        return (String) call(patch, "getPatchOpaqueSource");
    }

    public String translucentFragPatch() {
        return (String) call(patch, "getPatchTranslucentSource");
    }

    public boolean shouldDeferTranslucency() {
        return false;
    }

    private int[] targets(int[] indices) {
        Object targets = field(iris, "renderTargets");
        Set<?> flipped = (Set<?>) call(iris, "getFlippedAfterPrepare");
        int[] result = new int[indices.length];
        for (int i = 0; i < result.length; i++) {
            Object target = call(targets, "get", indices[i]);
            result[i] = (Integer) call(target, flipped.contains(indices[i]) ? "getAltTexture" : "getMainTexture");
        }
        return result;
    }

    private StructLayout uniforms() {
        String[] requested = (String[]) call(patch, "getUniformList");
        if (requested.length == 0) return null;
        Object custom = call(iris, "getCustomUniforms");
        Map<String, Object> available = new HashMap<>();
        for (Object value : (Collection<?>) call(field(custom, "inputHolder"), "getAll"))
            available.put((String) call(value, "getName"), value);
        for (Object value : ((Map<?, ?>) field(custom, "variables")).values())
            available.put((String) call(value, "getName"), value);
        // Angelica optimizes its update list before the external Voxy pipeline is constructed.
        // Retain Voxy-only uniforms and their dependencies in that same ordered update list.
        @SuppressWarnings("unchecked")
        List<Object> order = (List<Object>) field(custom, "uniformOrder");
        Map<?, ?> dependencies = (Map<?, ?>) field(custom, "dependsOn");
        List<LongConsumer> writers = new ArrayList<>();
        StringBuilder layout = new StringBuilder("{\n");
        int size = 0;
        for (String name : requested) {
            Object cached = available.get(name);
            if (cached == null) throw new IllegalStateException("Shader uniform unavailable from Angelica: " + name);
            retainUniform(cached, order, dependencies);
            String kind = cached.getClass()
                .getSimpleName();
            String glsl;
            int alignment, length;
            if (kind.equals("Float4MatrixCachedUniform")) {
                glsl = "mat4";
                alignment = 16;
                length = 64;
            } else if (kind.contains("Vector")) {
                int n = kind.charAt(kind.startsWith("Float") ? 5 : 3) - '0';
                glsl = (kind.startsWith("Int") ? "ivec" : "vec") + n;
                alignment = n == 2 ? 8 : 16;
                length = n * 4;
            } else {
                glsl = kind.startsWith("Float") ? "float" : kind.startsWith("Boolean") ? "bool" : "int";
                alignment = 4;
                length = 4;
            }
            size = (size + alignment - 1) & -alignment;
            final int offset = size;
            size += length;
            layout.append("layout(offset = ")
                .append(offset)
                .append(") ")
                .append(glsl)
                .append(' ')
                .append(name)
                .append(";\n");
            Object result;
            try {
                result = type("kroppeb.stareval.function.FunctionReturn").getConstructor()
                    .newInstance();
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
            Method write;
            try {
                write = cached.getClass()
                    .getMethod("writeTo", result.getClass());
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
            writers.add(pointer -> {
                try {
                    write.invoke(cached, result);
                    long address = pointer + offset;
                    if (kind.equals("FloatCachedUniform")) MemoryUtil.memPutFloat(
                        address,
                        result.getClass()
                            .getField("floatReturn")
                            .getFloat(result));
                    else if (kind.equals("IntCachedUniform")) MemoryUtil.memPutInt(
                        address,
                        result.getClass()
                            .getField("intReturn")
                            .getInt(result));
                    else if (kind.equals("BooleanCachedUniform")) MemoryUtil.memPutInt(
                        address,
                        result.getClass()
                            .getField("booleanReturn")
                            .getBoolean(result) ? 1 : 0);
                    else {
                        Object vector = result.getClass()
                            .getField("objectReturn")
                            .get(result);
                        if (vector instanceof Matrix4fc matrix) matrix.getToAddress(address);
                        else if (vector instanceof Vector2fc v) v.getToAddress(address);
                        else if (vector instanceof Vector3fc v) v.getToAddress(address);
                        else if (vector instanceof Vector4fc v) v.getToAddress(address);
                        else if (vector instanceof Vector2ic v) v.getToAddress(address);
                        else if (vector instanceof Vector3ic v) v.getToAddress(address);
                        else if (vector instanceof Vector4ic v) v.getToAddress(address);
                        else throw new IllegalStateException("Unsupported shader uniform value " + vector);
                    }
                } catch (ReflectiveOperationException error) {
                    throw new IllegalStateException(name, error);
                }
            });
        }
        return new StructLayout(
            (size + 15) & -16,
            layout.append('}')
                .toString(),
            pointer -> writers.forEach(writer -> writer.accept(pointer)));
    }

    private static void retainUniform(Object uniform, List<Object> order, Map<?, ?> dependencies) {
        if (order.contains(uniform)) return;
        Object inputs = dependencies.get(uniform);
        if (inputs instanceof List<?>list) for (Object input : list) retainUniform(input, order, dependencies);
        order.add(uniform);
    }

    @SuppressWarnings("unchecked")
    private ImageSet images() {
        Map<String, String> requested = (Map<String, String>) call(patch, "getSamplerSet");
        if (requested == null || requested.isEmpty()) return null;
        Map<String, IntSupplier> textures = new HashMap<>();
        Map<String, Integer> samplers = new HashMap<>();
        Class<?> samplerType = type("net.coderbot.iris.gl.sampler.SamplerHolder");
        Object sampler = Proxy
            .newProxyInstance(samplerType.getClassLoader(), new Class<?>[] { samplerType }, (proxy, method, args) -> {
                if (method.getName()
                    .equals("hasSampler")) return requested.containsKey(args[0]);
                if (!method.getName()
                    .startsWith("add")) return null;
                String[] names = (String[]) args[args.length - 1];
                IntSupplier texture = null;
                int samplerId = 0;
                if (method.getName()
                    .equals("addExternalSampler")) {
                    texture = (Integer) args[0] == 1 ? LegacyModelInputs::lightmapTexture
                        : LegacyModelInputs::atlasTexture;
                } else for (Object arg : args) {
                    if (arg instanceof IntSupplier supplier) texture = supplier;
                    else if (arg != null && arg.getClass()
                        .getName()
                        .endsWith(".GlSampler")) samplerId = (Integer) call(arg, "getId");
                }
                boolean matched = false;
                for (String name : names) if (requested.containsKey(name)) {
                    if (texture == null) throw new IllegalStateException("Missing texture supplier " + name);
                    textures.put(name, texture);
                    samplers.put(name, samplerId);
                    matched = true;
                }
                return method.getReturnType() == boolean.class ? matched : null;
            });
        Class<?> imageType = type("net.coderbot.iris.gl.image.ImageHolder");
        Object image = Proxy.newProxyInstance(
            imageType.getClassLoader(),
            new Class<?>[] { imageType },
            (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
        call(
            iris,
            "addGbufferOrShadowSamplers",
            sampler,
            image,
            (Supplier<Object>) () -> call(iris, "getFlippedAfterPrepare"),
            false,
            true,
            true,
            false);
        // The standard gbuffer helper omits colortex0–3; explicit Voxy requests are supported too.
        Object renderTargets = field(iris, "renderTargets");
        for (String name : requested.keySet()) if (!textures.containsKey(name) && name.matches("colortex[0-9]+")) {
            int index = Integer.parseInt(name.substring(8));
            textures.put(name, () -> {
                Object target = call(renderTargets, "get", index);
                return (Integer) call(
                    target,
                    ((Set<?>) call(iris, "getFlippedAfterPrepare")).contains(index) ? "getAltTexture"
                        : "getMainTexture");
            });
        }
        StringBuilder layout = new StringBuilder();
        List<IntConsumer> bindings = new ArrayList<>();
        for (Map.Entry<String, String> entry : requested.entrySet()) {
            String name = entry.getKey();
            IntSupplier texture = textures.get(name);
            if (texture == null) throw new IllegalStateException("Shader sampler unavailable from Angelica: " + name);
            int offset = bindings.size(), samplerId = samplers.getOrDefault(name, 0);
            layout.append("layout(binding = BASE_SAMPLER_BINDING_INDEX + ")
                .append(offset)
                .append(") uniform ")
                .append(entry.getValue())
                .append(' ')
                .append(name)
                .append(";\n");
            bindings.add(base -> {
                glBindTextureUnit(base + offset, texture.getAsInt());
                glBindSampler(base + offset, samplerId);
            });
        }
        if (bindings.size() + 6 > glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS))
            throw new IllegalStateException("Shader requires too many texture units");
        return new ImageSet(layout.toString(), base -> bindings.forEach(binding -> binding.accept(base)));
    }

    private SSBOSet buffers() {
        Map<?, ?> requested = (Map<?, ?>) call(patch, "getSSBOs");
        if (requested.isEmpty()) return null;
        Object holder = field(iris, "ssboHolder");
        if (holder == null) throw new IllegalStateException("Pack requests storage buffers but Angelica has none");
        List<IntConsumer> bindings = new ArrayList<>();
        StringBuilder layout = new StringBuilder();
        for (Map.Entry<?, ?> entry : requested.entrySet()) {
            int source = ((Number) entry.getKey()).intValue(), slot = bindings.size();
            if (source == -1) {
                layout.insert(0, entry.getValue() + "\n");
                continue;
            }
            layout.append("layout(binding = BUFFER_BINDING_INDEX_BASE + ")
                .append(slot)
                .append(") restrict buffer RoxyBuffer")
                .append(slot)
                .append(' ')
                .append(entry.getValue())
                .append(";\n");
            bindings.add(
                base -> glBindBufferBase(
                    GL_SHADER_STORAGE_BUFFER,
                    base + slot,
                    (Integer) call(holder, "getBufferIndex", source)));
        }
        return new SSBOSet(layout.toString(), base -> bindings.forEach(binding -> binding.accept(base)));
    }
}
