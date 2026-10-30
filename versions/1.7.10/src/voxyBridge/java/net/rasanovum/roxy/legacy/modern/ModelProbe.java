package net.rasanovum.roxy.legacy.modern;

import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.lwjgl.system.MemoryUtil;

/** Runs the original six-view baker with actual GTNH geometry and exports its colour/depth results. */
public final class ModelProbe {

    private ModelProbe() {}

    public static String run(Path directory, String[] names, int[] metadata, int[] opacity) throws Exception {
        Class<?> type = Class.forName("me.cortex.voxy.client.core.model.bakery.SoftwareModelTextureBakery");
        Object bakery = type.getConstructor()
            .newInstance();
        Method render = type.getMethod("renderToOutput", BlockState.class, long.class);
        long output = MemoryUtil.nmemAlloc(6 * 256 * 8);
        Files.createDirectories(directory);
        StringBuilder summary = new StringBuilder();
        try {
            type.getMethod("setupTexture")
                .invoke(bakery);
            for (int model = 0; model < names.length; model++) {
                BlockState state = new BlockState(names[model], metadata[model], opacity[model]);
                render.invoke(bakery, state, output);
                BufferedImage colour = new BufferedImage(96 * 4, 16 * 4, BufferedImage.TYPE_INT_ARGB);
                BufferedImage depth = new BufferedImage(96 * 4, 16 * 4, BufferedImage.TYPE_INT_ARGB);
                int[] occupied = new int[6];
                for (int face = 0; face < 6; face++) {
                    for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                        long pixel = MemoryUtil.memGetLong(output + ((face * 256 + y * 16 + x) * 8L));
                        int rgba = (int) pixel;
                        int argb = (rgba & 0xff00ff00) | ((rgba & 255) << 16) | ((rgba >>> 16) & 255);
                        if ((rgba >>> 24) != 0) occupied[face]++;
                        int d = (int) (pixel >>> 56);
                        for (int dy = 0; dy < 4; dy++) for (int dx = 0; dx < 4; dx++) {
                            colour.setRGB((face * 16 + x) * 4 + dx, y * 4 + dy, argb);
                            depth.setRGB((face * 16 + x) * 4 + dx, y * 4 + dy, 0xff000000 | (d * 0x010101));
                        }
                    }
                }
                String stem = names[model].replace(':', '_') + "_" + metadata[model];
                ImageIO.write(
                    colour,
                    "png",
                    directory.resolve(stem + "-colour.png")
                        .toFile());
                ImageIO.write(
                    depth,
                    "png",
                    directory.resolve(stem + "-depth.png")
                        .toFile());
                summary.append(stem)
                    .append(' ')
                    .append(Arrays.toString(occupied))
                    .append("; ");
                if (!state.isAir() && Arrays.stream(occupied)
                    .sum() == 0) throw new IllegalStateException("Empty baked model: " + state);
            }
            return "Original Voxy model baker: " + summary;
        } finally {
            MemoryUtil.nmemFree(output);
            type.getMethod("free")
                .invoke(bakery);
        }
    }
}
