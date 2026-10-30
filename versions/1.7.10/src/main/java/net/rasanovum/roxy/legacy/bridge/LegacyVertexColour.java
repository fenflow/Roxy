package net.rasanovum.roxy.legacy.bridge;

/** RenderBlocks writes directional shade into vertex colour before GTNHLib captures the quad. */
public final class LegacyVertexColour {

    private LegacyVertexColour() {}

    public static boolean hasDirectionalShade(int abgr, int tint) {
        for (float factor : new float[] { 0.5f, 0.6f, 0.8f }) {
            if (matches(abgr, 0xffffff, factor) || matches(abgr, tint, factor)) return true;
        }
        return false;
    }

    private static boolean matches(int abgr, int rgb, float factor) {
        if ((rgb & 0xffffff) == 0) return false;
        for (int channel = 0; channel < 3; channel++) {
            int actual = (abgr >>> (channel * 8)) & 255;
            int expected = (int) (((rgb >>> ((2 - channel) * 8)) & 255) * factor);
            if (Math.abs(actual - expected) > 1) return false;
        }
        return true;
    }
}
