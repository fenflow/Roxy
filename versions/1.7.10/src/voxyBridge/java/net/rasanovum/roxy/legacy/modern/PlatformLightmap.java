package net.rasanovum.roxy.legacy.modern;

import static org.lwjgl.opengl.GL45C.*;

import net.rasanovum.roxy.legacy.bridge.LegacyModelInputs;

/** Binds the live legacy lightmap, including day/night, dimension and gamma changes. */
public final class PlatformLightmap {

    private PlatformLightmap() {}

    public static int getLightmapTextureId() {
        return LegacyModelInputs.lightmapTexture();
    }

    public static void bind(int index) {
        glBindSampler(index, 0);
        glBindTextureUnit(index, getLightmapTextureId());
    }
}
