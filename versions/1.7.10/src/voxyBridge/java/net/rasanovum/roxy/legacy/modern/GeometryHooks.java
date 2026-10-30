package net.rasanovum.roxy.legacy.modern;

import static org.lwjgl.opengl.GL45C.*;

import net.rasanovum.roxy.legacy.bridge.LegacyFaceLighting;
import net.rasanovum.roxy.legacy.bridge.LegacyModelInputs;

import me.cortex.voxy.client.core.model.bakery.ReuseVertexConsumer;
import me.cortex.voxy.client.core.model.bakery.SoftwareRasterizer;

/** Supplies captured GTNH input to the external baker; no baking algorithm lives here. */
public final class GeometryHooks {

    private GeometryHooks() {}

    public static long adaptLighting(long metadata, BlockState state) {
        return LegacyFaceLighting.adapt(metadata, state.input().vertices);
    }

    public static void setupTexture(SoftwareRasterizer rasterizer) {
        int texture = LegacyModelInputs.atlasTexture();
        int width = glGetTextureLevelParameteri(texture, 0, GL_TEXTURE_WIDTH);
        int height = glGetTextureLevelParameteri(texture, 0, GL_TEXTURE_HEIGHT);
        if (width <= 0 || height <= 0 || (long) width * height > 268435456)
            throw new IllegalStateException("Invalid GTNH block atlas dimensions");
        int[] pixels = new int[width * height];
        int packBuffer = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        int[] keys = { GL_PACK_ALIGNMENT, GL_PACK_ROW_LENGTH, GL_PACK_IMAGE_HEIGHT, GL_PACK_SKIP_PIXELS,
            GL_PACK_SKIP_ROWS, GL_PACK_SKIP_IMAGES };
        int[] saved = new int[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = glGetInteger(keys[i]);
        try {
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
            for (int key : keys) glPixelStorei(key, key == GL_PACK_ALIGNMENT ? 4 : 0);
            glGetTextureImage(texture, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        } finally {
            for (int i = 0; i < keys.length; i++) glPixelStorei(keys[i], saved[i]);
            glBindBuffer(GL_PIXEL_PACK_BUFFER, packBuffer);
        }
        rasterizer.setSamplerTexture(pixels, width, height);
    }

    public static void append(ReuseVertexConsumer consumer, BlockState state, PlatformTypes.Layer layer, int face) {
        var input = state.input();
        float[] data = input.fluidFaces != null && face >= 0 ? input.fluidFaces[face] : input.vertices;
        for (int i = 0; i < data.length; i += 6) {
            int metadata = (int) data[i + 5];
            consumer.addVertex(data[i], data[i + 1], data[i + 2])
                .setUv(data[i + 3], data[i + 4])
                .meta(metadata);
            consumer.anyShaded |= (metadata & 2) != 0;
        }
    }
}
