package net.rasanovum.roxy.legacy.bridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Session-local IDs. Numeric Forge block IDs must never be persisted across worlds. */
public final class LegacyPalette {

    private final Map<String, Integer> ids = new LinkedHashMap<String, Integer>();
    private final List<Integer> opacities = new ArrayList<Integer>();

    public LegacyPalette() {
        ids.put("minecraft:air@0:0", 0);
        opacities.add(0);
    }

    public int register(String registryName, int metadata, int opacity) {
        if (registryName == null || registryName.isEmpty() || metadata < 0) {
            throw new IllegalArgumentException(
                "Invalid legacy state: registry=" + registryName + ", metadata=" + metadata);
        }
        // EndlessIDs extends metadata beyond the vanilla nibble. Preserve the full value in the key.
        int boundedOpacity = Math.min(15, Math.max(0, opacity));
        // Some legacy blocks depend on their position/tile entity for opacity.
        String key = registryName + "@" + metadata + ":" + boundedOpacity;
        Integer id = ids.get(key);
        if (id != null) {
            return id;
        }
        int next = ids.size();
        if (next >= (1 << 20)) {
            throw new IllegalStateException("Voxy's block palette is full");
        }
        ids.put(key, next);
        opacities.add(boundedOpacity);
        return next;
    }

    public LegacyMapper snapshotMapper() {
        int[] values = new int[opacities.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = opacities.get(i);
        }
        return new LegacyMapper(values);
    }

    public int size() {
        return ids.size();
    }
}
