package net.rasanovum.roxy.legacy.modern;

import java.lang.reflect.Method;
import java.util.Collection;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cortex.voxy.client.core.rendering.ChunkBoundRenderer;

/** Supplies Voxy's near-terrain occlusion boxes from sections Angelica has actually built. */
public final class AngelicaSections {

    private final Method instance;
    private final Method manager;
    private final Method sections;
    private final Method built;
    private final Method x, y, z;
    private LongOpenHashSet previous = new LongOpenHashSet();
    private LongOpenHashSet next = new LongOpenHashSet();

    public AngelicaSections() throws ReflectiveOperationException {
        Class<?> renderer = Class.forName("com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer");
        Class<?> managerType = Class.forName("org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager");
        Class<?> section = Class.forName("org.embeddedt.embeddium.impl.render.chunk.RenderSection");
        instance = renderer.getMethod("getInstanceOrNull");
        manager = renderer.getMethod("getRenderSectionManager");
        sections = managerType.getMethod("getAllRenderSections");
        built = section.getMethod("isBuilt");
        x = section.getMethod("getChunkX");
        y = section.getMethod("getChunkY");
        z = section.getMethod("getChunkZ");
    }

    public void sync(ChunkBoundRenderer bounds) throws ReflectiveOperationException {
        next.clear();
        Object renderer = instance.invoke(null);
        Object sectionManager = renderer == null ? null : manager.invoke(renderer);
        if (sectionManager != null) for (Object section : (Collection<?>) sections.invoke(sectionManager)) {
            if (!(Boolean) built.invoke(section)) continue;
            int sx = (Integer) x.invoke(section), sy = (Integer) y.invoke(section), sz = (Integer) z.invoke(section);
            next.add(((long) (sx & 0x3fffff) << 42) | ((long) (sz & 0x3fffff) << 20) | (sy & 0xfffffL));
        }
        for (long pos : previous) if (!next.contains(pos)) bounds.removeSection(pos);
        for (long pos : next) if (!previous.contains(pos)) bounds.addSection(pos);
        LongOpenHashSet spare = previous;
        previous = next;
        next = spare;
    }

    public int count() {
        return previous.size();
    }
}
