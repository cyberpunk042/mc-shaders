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
package net.cyberpunk042.mcshaders.core.util;

/**
 * Color utilities for field appearance and visual effects.
 * 
 * <p>This class is the field-facing vocabulary over {@link ColorSupport}, which
 * holds the packed-ARGB arithmetic itself.</p>
 * 
 * <h2>Color Format</h2>
 * <p>All colors are in ARGB format: {@code 0xAARRGGBB}</p>
 * <ul>
 *   <li>AA = Alpha (00 = transparent, FF = opaque)</li>
 *   <li>RR = Red (00-FF)</li>
 *   <li>GG = Green (00-FF)</li>
 *   <li>BB = Blue (00-FF)</li>
 * </ul>
 * 
 * <h2>Color Creation</h2>
 * <ul>
 *   <li>{@link #argb(int, int, int, int)} - From ARGB components</li>
 *   <li>{@link #rgb(int, int, int)} - From RGB (full alpha)</li>
 *   <li>{@link #fromFloats(float, float, float, float)} - From 0.0-1.0 floats</li>
 *   <li>{@link #fromHsv(float, float, float, float)} - From HSV</li>
 * </ul>
 * 
 * <h2>Color Modification</h2>
 * <ul>
 *   <li>{@link #withAlpha(int, float)} - Change alpha</li>
 *   <li>{@link #withBrightness(int, float)} - Adjust brightness</li>
 *   <li>{@link #scaleRgb(int, float)} - Scale RGB uniformly</li>
 * </ul>
 * 
 * <h2>Color Blending</h2>
 * <ul>
 *   <li>{@link #lerp(float, int, int)} - Interpolate between colors</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Create a semi-transparent cyan
 * int color = FieldColor.argb(200, 0, 255, 255);
 * 
 * // Fade to red on damage
 * int flashColor = FieldColor.lerp(damageProgress, baseColor, RED);
 * 
 * // Pulse brightness
 * int glowing = FieldColor.withBrightness(color, 1.0f + glowAmount);
 * }</pre>
 * 
 * @see ColorSupport
 * @see FieldMath
 * @since 1.0.0
 */
public final class FieldColor {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PRESET COLORS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Fully transparent. */
    public static final int TRANSPARENT = 0x00000000;
    
    /** Opaque white. */
    public static final int WHITE = 0xFFFFFFFF;
    
    /** Opaque black. */
    public static final int BLACK = 0xFF000000;
    
    /** Opaque red - for damage effects. */
    public static final int RED = 0xFFFF0000;
    
    /** Opaque green - for heal effects. */
    public static final int GREEN = 0xFF00FF00;
    
    /** Opaque blue. */
    public static final int BLUE = 0xFF0000FF;
    
    /** Opaque cyan - default field color. */
    public static final int CYAN = 0xFF00FFFF;
    
    /** Opaque magenta. */
    public static final int MAGENTA = 0xFFFF00FF;
    
    /** Opaque yellow - for warning effects. */
    public static final int YELLOW = 0xFFFFFF00;
    
    /** Opaque orange. */
    public static final int ORANGE = 0xFFFF8800;
    
    private FieldColor() {}
    
    // ═══════════════════════════════════════════════════════════════════════════
    // COLOR CREATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a color from ARGB components (0-255 each).
     * 
     * @param alpha Alpha component (0 = transparent, 255 = opaque)
     * @param red Red component (0-255)
     * @param green Green component (0-255)
     * @param blue Blue component (0-255)
     * @return ARGB color integer
     */
    public static int argb(int alpha, int red, int green, int blue) {
        return ColorSupport.argb(alpha, red, green, blue);
    }
    
    /**
     * Creates an opaque color from RGB components (0-255 each).
     * 
     * @param red Red component (0-255)
     * @param green Green component (0-255)
     * @param blue Blue component (0-255)
     * @return ARGB color integer with full alpha
     */
    public static int rgb(int red, int green, int blue) {
        return ColorSupport.argb(255, red, green, blue);
    }
    
    /**
     * Creates a color from float components (0.0-1.0 each).
     * 
     * @param alpha Alpha (0.0 = transparent, 1.0 = opaque)
     * @param red Red (0.0-1.0)
     * @param green Green (0.0-1.0)
     * @param blue Blue (0.0-1.0)
     * @return ARGB color integer
     */
    public static int fromFloats(float alpha, float red, float green, float blue) {
        return ColorSupport.fromFloats(alpha, red, green, blue);
    }
    
    /**
     * Creates a color from HSV (Hue, Saturation, Value) with alpha.
     * 
     * <p>Useful for generating color variations or rainbow effects.</p>
     * 
     * @param hue Hue (0.0-1.0, where 0=red, 0.33=green, 0.66=blue)
     * @param saturation Saturation (0.0 = gray, 1.0 = vivid)
     * @param value Brightness (0.0 = black, 1.0 = full brightness)
     * @param alpha Alpha (0.0 = transparent, 1.0 = opaque)
     * @return ARGB color integer
     */
    public static int fromHsv(float hue, float saturation, float value, float alpha) {
        int alphaInt = (int) (alpha * 255) & 0xFF;
        return ColorSupport.hsvToArgb(hue, saturation, value, alphaInt);
    }
    
    /**
     * Creates an opaque color from HSV.
     * 
     * @param hue Hue (0.0-1.0)
     * @param saturation Saturation (0.0-1.0)
     * @param value Brightness (0.0-1.0)
     * @return ARGB color integer with full alpha
     */
    public static int fromHsv(float hue, float saturation, float value) {
        return fromHsv(hue, saturation, value, 1.0f);
    }
    
    /**
     * Creates a grayscale color with the given brightness.
     * 
     * @param brightness Brightness (0.0 = black, 1.0 = white)
     * @return ARGB grayscale color
     */
    public static int gray(float brightness) {
        int v = (int) (brightness * 255) & 0xFF;
        return argb(255, v, v, v);
    }
    
    /**
     * Creates a white color with the given alpha.
     * 
     * @param alpha Alpha (0.0 = transparent, 1.0 = opaque)
     * @return White color with specified alpha
     */
    public static int white(float alpha) {
        return ColorSupport.white(alpha);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // COMPONENT EXTRACTION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Extracts the alpha component (0-255).
     * 
     * @param color ARGB color
     * @return Alpha value (0-255)
     */
    public static int getAlpha(int color) {
        return ColorSupport.alpha(color);
    }
    
    /**
     * Extracts the red component (0-255).
     * 
     * @param color ARGB color
     * @return Red value (0-255)
     */
    public static int getRed(int color) {
        return ColorSupport.red(color);
    }
    
    /**
     * Extracts the green component (0-255).
     * 
     * @param color ARGB color
     * @return Green value (0-255)
     */
    public static int getGreen(int color) {
        return ColorSupport.green(color);
    }
    
    /**
     * Extracts the blue component (0-255).
     * 
     * @param color ARGB color
     * @return Blue value (0-255)
     */
    public static int getBlue(int color) {
        return ColorSupport.blue(color);
    }
    
    /**
     * Extracts the alpha as a float (0.0-1.0).
     * 
     * @param color ARGB color
     * @return Alpha value (0.0-1.0)
     */
    public static float getAlphaFloat(int color) {
        return getAlpha(color) / 255f;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // COLOR MODIFICATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Returns the color with a new alpha value.
     * 
     * @param color Original ARGB color
     * @param alpha New alpha (0.0 = transparent, 1.0 = opaque)
     * @return Color with modified alpha
     */
    public static int withAlpha(int color, float alpha) {
        return ColorSupport.withAlpha(color, alpha);
    }
    
    /**
     * Returns the color with a new alpha value (0-255).
     * 
     * @param color Original ARGB color
     * @param alpha New alpha (0-255)
     * @return Color with modified alpha
     */
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
    
    /**
     * Adjusts the brightness of a color.
     * 
     * <p>Values &gt; 1.0 brighten, values &lt; 1.0 darken.</p>
     * 
     * @param color Original ARGB color
     * @param factor Brightness factor (1.0 = unchanged)
     * @return Brightness-adjusted color
     */
    public static int withBrightness(int color, float factor) {
        return ColorSupport.scaleRgb(color, factor);
    }
    
    /**
     * Scales the RGB components uniformly.
     * 
     * <p>Alpha is preserved. Factor of 0.5 = 50% brightness.</p>
     * 
     * @param color Original ARGB color
     * @param scale Scale factor (0.0-1.0+)
     * @return Scaled color
     */
    public static int scaleRgb(int color, float scale) {
        return ColorSupport.scaleRgb(color, scale);
    }
    
    /**
     * Scales RGB components independently.
     * 
     * @param color Original ARGB color
     * @param redScale Red scale factor
     * @param greenScale Green scale factor
     * @param blueScale Blue scale factor
     * @return Scaled color
     */
    public static int scaleRgb(int color, float redScale, float greenScale, float blueScale) {
        return ColorSupport.scaleRgb(color, redScale, greenScale, blueScale);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // COLOR BLENDING
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Linearly interpolates between two colors.
     * 
     * <p>When t=0, returns colorA. When t=1, returns colorB.
     * All components (including alpha) are interpolated.</p>
     * 
     * @param t Interpolation factor (0.0-1.0)
     * @param colorA Start color
     * @param colorB End color
     * @return Interpolated color
     */
    public static int lerp(float t, int colorA, int colorB) {
        return ColorSupport.lerp(t, colorA, colorB);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Converts a color to a hex string (e.g., "#FF00FFFF").
     * 
     * @param color ARGB color
     * @return Hex string representation
     */
    public static String toHex(int color) {
        return String.format("#%08X", color);
    }
    
    /**
     * Parses a hex color string.
     * 
     * <p>Supports formats: "#AARRGGBB", "#RRGGBB", "RRGGBB"</p>
     * 
     * @param hex Hex color string
     * @return ARGB color integer
     * @throws IllegalArgumentException if format is invalid
     */
    public static int fromHex(String hex) {
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        if (clean.length() == 6) {
            return 0xFF000000 | Integer.parseInt(clean, 16);
        } else if (clean.length() == 8) {
            return (int) Long.parseLong(clean, 16);
        }
        throw new IllegalArgumentException("Invalid hex color: " + hex);
    }
    
    /**
     * Creates a rainbow color based on time/position.
     * 
     * <p>Useful for debug visualization or effects.</p>
     * 
     * @param t Position in rainbow (0.0-1.0, wraps)
     * @param alpha Alpha value (0.0-1.0)
     * @return Rainbow color at position t
     */
    public static int rainbow(float t, float alpha) {
        return fromHsv(t % 1.0f, 1.0f, 1.0f, alpha);
    }
}
