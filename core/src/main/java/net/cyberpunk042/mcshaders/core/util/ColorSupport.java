package net.cyberpunk042.mcshaders.core.util;

import net.cyberpunk042.mcshaders.core.api.Stable;

/**
 * Packed-ARGB helpers, replacing Minecraft's {@code ColorHelper} and the HSV
 * conversion from {@code MathHelper}.
 *
 * <p>Colours in the ported model are packed into a single {@code int} as
 * {@code 0xAARRGGBB}. That is Minecraft's convention and the model is full of it, so
 * it is kept rather than converted at every boundary.
 *
 * <p>Reimplemented rather than copied, and therefore tested.
 */
@Stable(since = "0.3.0")
public final class ColorSupport {

    private ColorSupport() {
    }

    /** Packs 0-255 components into {@code 0xAARRGGBB}. Components are masked, not clamped. */
    public static int argb(int alpha, int red, int green, int blue) {
        return ((alpha & 0xFF) << 24)
                | ((red & 0xFF) << 16)
                | ((green & 0xFF) << 8)
                | (blue & 0xFF);
    }

    /** Packs 0-1 components into {@code 0xAARRGGBB}, clamping first. */
    public static int fromFloats(float alpha, float red, float green, float blue) {
        return argb(toByte(alpha), toByte(red), toByte(green), toByte(blue));
    }

    public static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    public static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    public static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    /**
     * Converts HSV to packed ARGB.
     *
     * @param hue        in turns, {@code [0, 1)}; values outside wrap
     * @param saturation {@code [0, 1]}
     * @param value      {@code [0, 1]}
     * @param alpha      0-255
     */
    public static int hsvToArgb(float hue, float saturation, float value, int alpha) {
        // Wrap rather than clamp: hue is angular, so 1.1 turns is 0.1, not 1.0.
        float h = hue - (float) Math.floor(hue);
        float s = MathSupport.clamp(saturation, 0f, 1f);
        float v = MathSupport.clamp(value, 0f, 1f);

        int sector = (int) (h * 6.0f) % 6;
        float offset = h * 6.0f - (float) Math.floor(h * 6.0f);

        float p = v * (1.0f - s);
        float q = v * (1.0f - s * offset);
        float t = v * (1.0f - s * (1.0f - offset));

        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return argb(alpha & 0xFF, toByte(r), toByte(g), toByte(b));
    }

    /** Opaque-white with the given alpha, {@code alpha} in {@code [0, 1]}. */
    public static int white(float alpha) {
        return argb(toByte(alpha), 0xFF, 0xFF, 0xFF);
    }

    /** Replaces the alpha channel, {@code alpha} in {@code [0, 1]}. */
    public static int withAlpha(int argb, float alpha) {
        return (argb & 0x00FFFFFF) | (toByte(alpha) << 24);
    }

    /** Replaces the alpha channel, {@code alpha} in {@code [0, 255]}. */
    public static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /**
     * Scales the three colour channels by one factor, preserving alpha.
     *
     * <p>Factors above 1 brighten, so the result is clamped rather than masked:
     * masking would wrap an over-bright channel back to black.
     */
    public static int scaleRgb(int argb, float scale) {
        return scaleRgb(argb, scale, scale, scale);
    }

    /** Scales each colour channel independently, preserving alpha. */
    public static int scaleRgb(int argb, float redScale, float greenScale, float blueScale) {
        return argb(
                alpha(argb),
                scaleChannel(red(argb), redScale),
                scaleChannel(green(argb), greenScale),
                scaleChannel(blue(argb), blueScale));
    }

    /**
     * Interpolates every channel, alpha included.
     *
     * <p>{@code t} is not clamped, matching {@link MathSupport#lerp}; the channels
     * are, so extrapolating past either end saturates instead of wrapping.
     */
    public static int lerp(float t, int from, int to) {
        return argb(
                lerpChannel(t, alpha(from), alpha(to)),
                lerpChannel(t, red(from), red(to)),
                lerpChannel(t, green(from), green(to)),
                lerpChannel(t, blue(from), blue(to)));
    }

    private static int scaleChannel(int channel, float scale) {
        return MathSupport.clamp(Math.round(channel * scale), 0, 0xFF);
    }

    private static int lerpChannel(float t, int from, int to) {
        return MathSupport.clamp(Math.round(MathSupport.lerp(t, from, to)), 0, 0xFF);
    }

    private static int toByte(float component) {
        return (int) (MathSupport.clamp(component, 0f, 1f) * 255.0f + 0.5f);
    }
}
