package net.rasanovum.roxy.legacy.modern;

/** Standard sRGB transfer functions used by the platform colour API. Packed pixels are ABGR. */
public final class ColourSpace {

    private ColourSpace() {}

    public static float srgbToLinear(int channel) {
        double value = (channel & 255) / 255.0;
        return (float) (value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4));
    }

    public static int method_75599(float linear) {
        double value = Math.max(0, Math.min(1, linear));
        double srgb = value <= 0.0031308 ? value * 12.92 : 1.055 * Math.pow(value, 1.0 / 2.4) - 0.055;
        return (int) Math.round(srgb * 255);
    }

    public static int linearToSrgb(float red, float green, float blue, int alpha) {
        return (alpha & 255) << 24 | method_75599(blue) << 16 | method_75599(green) << 8 | method_75599(red);
    }
}
