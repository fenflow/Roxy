package net.rasanovum.roxy.legacy.modern;

import net.rasanovum.roxy.legacy.shader.ShaderHooks;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/** Uses Angelica's existing material mappings, including its legacy metadata conversion. */
public final class ShaderMaterials {

    public static final ShaderMaterials INSTANCE = new ShaderMaterials();

    public Object2IntMap<BlockState> getBlockStateIds() {
        return new Object2IntOpenHashMap<BlockState>() {

            @Override
            public int getInt(Object key) {
                BlockState state = (BlockState) key;
                return ShaderHooks.blockId(state.name(), state.metadata());
            }

            @Override
            public boolean containsKey(Object key) {
                return getInt(key) >= 0;
            }
        };
    }
}
