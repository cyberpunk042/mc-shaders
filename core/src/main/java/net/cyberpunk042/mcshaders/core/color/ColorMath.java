/*
 * Ported from the-virus-block-mc (net.cyberpunk042.visual), where it is
 * geometry model code with no Minecraft or graphics-API dependency.
 * Relicensed to MIT here by the author, per the engine/content split
 * recorded in docs/PORTING.md.
 *
 * JSON binding was removed on the way across: the model stays free of
 * serialisation so it can be loaded from external content. The @JsonField
 * metadata is retained for a codec layer above core.
 */
package net.cyberpunk042.mcshaders.core.color;

/**
 * Static utility methods for color manipulation.
 * 
 * <h2>Color Format</h2>
 * <p>All colors are ARGB integers: 0xAARRGGBB
 * 
 * <h2>Usage</h2>
 * <pre>
 * int lighter = ColorMath.lighten(color, 0.2f);
 * int blended = ColorMath.blend(color1, color2, 0.5f);
 * int withAlpha = ColorMath.withAlpha(color, 0.8f);
 * </pre>
 */
public final class ColorMath {
    
    private ColorMath() {}
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Component extraction
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Extracts alpha component (0-255). */
    public static int alpha(int argb) {
        return (argb >> 24) & 0xFF;
    }
    
    /** Extracts red component (0-255). */
    public static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }
    
    /** Extracts green component (0-255). */
    public static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }
    
    /** Extracts blue component (0-255). */
    public static int blue(int argb) {
        return argb & 0xFF;
    }
    
    /** Extracts alpha as float (0.0-1.0). */
    public static float alphaF(int argb) {
        return alpha(argb) / 255f;
    }
    
    /** Extracts red as float (0.0-1.0). */
    public static float redF(int argb) {
        return red(argb) / 255f;
    }
    
    /** Extracts green as float (0.0-1.0). */
    public static float greenF(int argb) {
        return green(argb) / 255f;
    }
    
    /** Extracts blue as float (0.0-1.0). */
    public static float blueF(int argb) {
        return blue(argb) / 255f;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Color construction
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Creates ARGB from components (0-255). */
    public static int argb(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
    
    /** Creates ARGB from float components (0.0-1.0). */
    public static int argb(float a, float r, float g, float b) {
        return argb(
            (int)(a * 255),
            (int)(r * 255),
            (int)(g * 255),
            (int)(b * 255)
        );
    }
    
    /** Creates RGB with full alpha. */
    public static int rgb(int r, int g, int b) {
        return argb(255, r, g, b);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Alpha modification
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Returns color with new alpha (0.0-1.0). */
    public static int withAlpha(int argb, float alpha) {
        int a = (int)(alpha * 255);
        return (argb & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }
    
    /** Returns color with new alpha (0-255). */
    public static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
    
    /** Multiplies alpha by factor. */
    public static int multiplyAlpha(int argb, float factor) {
        int a = (int)(alpha(argb) * factor);
        return withAlpha(argb, Math.min(255, Math.max(0, a)));
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Color modification
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Lightens a color.
     * @param argb the color
     * @param amount 0.0 = no change, 1.0 = white
     */
    public static int lighten(int argb, float amount) {
        int a = alpha(argb);
        int r = (int)(red(argb) + (255 - red(argb)) * amount);
        int g = (int)(green(argb) + (255 - green(argb)) * amount);
        int b = (int)(blue(argb) + (255 - blue(argb)) * amount);
        return argb(a, r, g, b);
    }
    
    /**
     * Darkens a color.
     * @param argb the color
     * @param amount 0.0 = no change, 1.0 = black
     */
    public static int darken(int argb, float amount) {
        int a = alpha(argb);
        int r = (int)(red(argb) * (1 - amount));
        int g = (int)(green(argb) * (1 - amount));
        int b = (int)(blue(argb) * (1 - amount));
        return argb(a, r, g, b);
    }
    
    /**
     * Saturates or desaturates a color.
     * @param argb the color
     * @param amount positive = more saturated, negative = less saturated
     */
    public static int saturate(int argb, float amount) {
        float[] hsl = toHSL(argb);
        hsl[1] = Math.max(0, Math.min(1, hsl[1] + amount));
        return fromHSL(hsl[0], hsl[1], hsl[2], alphaF(argb));
    }
    
    /**
     * Desaturates a color (reduces saturation).
     * @param argb the color
     * @param amount 0.0 = no change, 1.0 = grayscale
     */
    public static int desaturate(int argb, float amount) {
        return saturate(argb, -amount);
    }
    
    /**
     * Sets absolute saturation level.
     * @param argb the color
     * @param saturation 0.0 = grayscale, 1.0 = full saturation
     */
    public static int setSaturation(int argb, float saturation) {
        float[] hsl = toHSL(argb);
        hsl[1] = Math.max(0, Math.min(1, saturation));
        return fromHSL(hsl[0], hsl[1], hsl[2], alphaF(argb));
    }
    
    /**
     * Multiplies saturation by factor.
     * @param argb the color
     * @param factor 0.0 = grayscale, 1.0 = no change, 2.0 = double saturation
     */
    public static int multiplySaturation(int argb, float factor) {
        float[] hsl = toHSL(argb);
        hsl[1] = Math.max(0, Math.min(1, hsl[1] * factor));
        return fromHSL(hsl[0], hsl[1], hsl[2], alphaF(argb));
    }
    
    /**
     * Shifts hue by degrees.
     * @param argb the color
     * @param degrees hue shift (-360 to 360)
     */
    public static int shiftHue(int argb, float degrees) {
        float[] hsl = toHSL(argb);
        hsl[0] = (hsl[0] + degrees + 360) % 360;
        return fromHSL(hsl[0], hsl[1], hsl[2], alphaF(argb));
    }
    
    /**
     * Multiplies brightness/lightness by factor.
     * @param argb the color
     * @param factor 0.0 = black, 1.0 = no change, 2.0 = brighter (clamped)
     */
    public static int multiplyBrightness(int argb, float factor) {
        float[] hsl = toHSL(argb);
        hsl[2] = Math.max(0, Math.min(1, hsl[2] * factor));
        return fromHSL(hsl[0], hsl[1], hsl[2], alphaF(argb));
    }
    
    /**
     * Blends two colors.
     * @param a first color
     * @param b second color
     * @param factor 0.0 = first, 1.0 = second
     */
    public static int blend(int a, int b, float factor) {
        float inv = 1 - factor;
        // Note: alpha/red/green/blue return 0-255, need to divide by 255 for float argb()
        return argb(
            (alpha(a) * inv + alpha(b) * factor) / 255f,
            (red(a) * inv + red(b) * factor) / 255f,
            (green(a) * inv + green(b) * factor) / 255f,
            (blue(a) * inv + blue(b) * factor) / 255f
        );
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // HSL conversion
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Converts ARGB to HSL.
     * @return float[3] = {hue 0-360, saturation 0-1, lightness 0-1}
     */
    public static float[] toHSL(int argb) {
        float r = redF(argb);
        float g = greenF(argb);
        float b = blueF(argb);
        
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2;
        
        float h, s;
        if (max == min) {
            h = s = 0; // achromatic
        } else {
            float d = max - min;
            s = l > 0.5f ? d / (2 - max - min) : d / (max + min);
            
            if (max == r) {
                h = (g - b) / d + (g < b ? 6 : 0);
            } else if (max == g) {
                h = (b - r) / d + 2;
            } else {
                h = (r - g) / d + 4;
            }
            h /= 6;
        }
        
        return new float[]{h * 360, s, l};
    }
    
    /**
     * Converts HSL to ARGB.
     * @param h hue (0-360)
     * @param s saturation (0-1)
     * @param l lightness (0-1)
     * @param a alpha (0-1)
     */
    public static int fromHSL(float h, float s, float l, float a) {
        float r, g, b;
        
        if (s == 0) {
            r = g = b = l; // achromatic
        } else {
            float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
            float p = 2 * l - q;
            float hNorm = h / 360f;
            r = hue2rgb(p, q, hNorm + 1f/3f);
            g = hue2rgb(p, q, hNorm);
            b = hue2rgb(p, q, hNorm - 1f/3f);
        }
        
        return argb(a, r, g, b);
    }
    
    private static float hue2rgb(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1f/6f) return p + (q - p) * 6 * t;
        if (t < 1f/2f) return q;
        if (t < 2f/3f) return p + (q - p) * (2f/3f - t) * 6;
        return p;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Parsing
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Parses a hex color string.
     * @param hex "#RGB", "#RRGGBB", or "#AARRGGBB"
     * @return ARGB color, or 0 if invalid
     */
    public static int parseHex(String hex) {
        if (hex == null || hex.isEmpty()) {
            return 0;
        }
        
        // Remove # prefix
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        
        try {
            if (hex.length() == 3) {
                // #RGB -> #RRGGBB
                int r = Integer.parseInt(hex.substring(0, 1), 16);
                int g = Integer.parseInt(hex.substring(1, 2), 16);
                int b = Integer.parseInt(hex.substring(2, 3), 16);
                return rgb(r * 17, g * 17, b * 17);
            } else if (hex.length() == 6) {
                // #RRGGBB
                int rgb = Integer.parseInt(hex, 16);
                return 0xFF000000 | rgb;
            } else if (hex.length() == 8) {
                // #AARRGGBB
                return (int) Long.parseLong(hex, 16);
            }
        } catch (NumberFormatException e) {
            // Fall through to return 0
        }
        
        return 0;
    }
}
