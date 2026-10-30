package net.rasanovum.roxy.legacy.modern;

import java.util.function.BooleanSupplier;

import net.rasanovum.roxy.legacy.shader.ShaderHooks;

import me.cortex.voxy.client.core.AbstractRenderPipeline;
import me.cortex.voxy.client.core.IrisVoxyRenderPipeline;
import me.cortex.voxy.client.core.NormalRenderPipeline;
import me.cortex.voxy.client.core.RenderProperties;
import me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager;
import me.cortex.voxy.client.core.rendering.hierachical.HierarchicalOcclusionTraverser;
import me.cortex.voxy.client.core.rendering.hierachical.NodeCleaner;
import me.cortex.voxy.client.iris.IrisVoxyRenderPipelineData;

/** Selects Voxy's original normal or shader pipeline, supplying the legacy Iris adapter. */
public final class ShaderPipelineFactory {

    public static ShaderPipelineData current;

    public static void prepare() {
        Object iris = ShaderHooks.activePipeline();
        current = iris == null ? null : new ShaderPipelineData(iris);
    }

    public static AbstractRenderPipeline createPipeline(RenderProperties properties, AsyncNodeManager nodes,
        NodeCleaner cleaner, HierarchicalOcclusionTraverser traversal, BooleanSupplier work) {
        if (current != null) {
            return new IrisVoxyRenderPipeline(
                properties,
                (IrisVoxyRenderPipelineData) (Object) current,
                nodes,
                cleaner,
                traversal,
                work);
        }
        try {
            var constructor = NormalRenderPipeline.class.getDeclaredConstructor(
                RenderProperties.class,
                AsyncNodeManager.class,
                NodeCleaner.class,
                HierarchicalOcclusionTraverser.class,
                BooleanSupplier.class);
            constructor.setAccessible(true);
            return constructor.newInstance(properties, nodes, cleaner, traversal, work);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }
}
