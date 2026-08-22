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
package net.cyberpunk042.mcshaders.core.appearance;

/**
 * Predefined color sets for CYCLING and RANDOM modes.
 * 
 * <p>Each set contains a curated palette of colors that work well together.</p>
 */
public enum ColorSet {
    /**
     * Classic rainbow spectrum (ROYGBIV).
     * Red → Orange → Yellow → Green → Blue → Indigo → Violet
     */
    RAINBOW(new int[] {
        0xFFFF0000, // Red
        0xFFFF7F00, // Orange
        0xFFFFFF00, // Yellow
        0xFF00FF00, // Green
        0xFF0000FF, // Blue
        0xFF4B0082, // Indigo
        0xFF9400D3  // Violet
    }),
    
    /**
     * Theme colors from the field system.
     * Uses semantic colors: primary, secondary, accent, highlight.
     */
    THEME(new int[] {
        0xFF00FFFF, // Cyan (primary)
        0xFFFF00FF, // Magenta (secondary)
        0xFFFFFF00, // Yellow (accent)
        0xFF00FF00, // Green (highlight)
        0xFFFF6600  // Orange (warning)
    }),
    
    /**
     * Neon/vivid colors for high-energy effects.
     */
    NEON(new int[] {
        0xFFFF00FF, // Hot Pink
        0xFF00FFFF, // Cyan
        0xFFFF0080, // Neon Rose
        0xFF80FF00, // Lime
        0xFFFF8000, // Neon Orange
        0xFF8000FF  // Electric Purple
    }),
    
    /**
     * Soft pastel colors for subtle effects.
     */
    PASTEL(new int[] {
        0xFFFFB3BA, // Light Pink
        0xFFFFDFBA, // Light Peach
        0xFFFFFFBA, // Light Yellow
        0xFFBAFFC9, // Light Mint
        0xFFBAE1FF, // Light Blue
        0xFFE8BAFF  // Light Lavender
    }),
    
    /**
     * Grayscale from white to black.
     */
    GRAYSCALE(new int[] {
        0xFFFFFFFF, // White
        0xFFCCCCCC, // Light Gray
        0xFF999999, // Medium Gray
        0xFF666666, // Dark Gray
        0xFF333333, // Charcoal
        0xFF000000  // Black
    }),
    
    /**
     * Fire/heat colors.
     */
    FIRE(new int[] {
        0xFFFFFF00, // Yellow (hottest)
        0xFFFFCC00, // Gold
        0xFFFF9900, // Orange
        0xFFFF6600, // Dark Orange
        0xFFFF3300, // Red-Orange
        0xFFCC0000  // Deep Red (coolest)
    }),
    
    /**
     * Ocean/water colors.
     */
    OCEAN(new int[] {
        0xFF00FFFF, // Cyan
        0xFF00CCFF, // Light Blue
        0xFF0099FF, // Sky Blue
        0xFF0066CC, // Ocean Blue
        0xFF003399, // Deep Blue
        0xFF001166  // Midnight Blue
    }),
    
    /**
     * Forest/nature colors.
     */
    FOREST(new int[] {
        0xFF90EE90, // Light Green
        0xFF32CD32, // Lime Green
        0xFF228B22, // Forest Green
        0xFF006400, // Dark Green
        0xFF8B4513, // Saddle Brown
        0xFF654321  // Dark Brown
    });
    
    private final int[] colors;
    
    ColorSet(int[] colors) {
        this.colors = colors;
    }
    
    /**
     * Get the colors in this set.
     */
    public int[] colors() {
        return colors;
    }
    
    /**
     * Get the number of colors in this set.
     */
    public int size() {
        return colors.length;
    }
    
    /**
     * Get a color by index (wraps around).
     */
    public int color(int index) {
        return colors[Math.floorMod(index, colors.length)];
    }
    
    /**
     * Get interpolated color at position t (0-1).
     * Smoothly blends between adjacent colors.
     */
    public int interpolate(float t) {
        t = Math.max(0, Math.min(1, t));
        float scaled = t * (colors.length - 1);
        int index = (int) scaled;
        float frac = scaled - index;
        
        if (index >= colors.length - 1) {
            return colors[colors.length - 1];
        }
        
        return net.cyberpunk042.mcshaders.core.color.ColorMath.blend(
            colors[index], colors[index + 1], frac);
    }
    
    /**
     * Get interpolated color at position t (0-1) for rainbow/spectrum modes.
     * For RAINBOW: uses full HSB spectrum (continuous hue).
     * For other sets: uses palette interpolation.
     * 
     * @param t position 0-1
     * @return ARGB color
     */
    public int interpolateSpectrum(float t) {
        t = Math.max(0, Math.min(1, t));
        
        if (this == RAINBOW) {
            // Full HSB spectrum for true rainbow
            int rgb = java.awt.Color.HSBtoRGB(t, 1f, 1f);
            return 0xFF000000 | (rgb & 0x00FFFFFF);
        }
        
        // For all other palettes, interpolate through the defined colors
        return interpolate(t);
    }
    
    /**
     * Get a random color from this set.
     */
    public int random(java.util.Random rng) {
        return colors[rng.nextInt(colors.length)];
    }
    
    /**
     * Default color set.
     */
    public static ColorSet defaultSet() {
        return RAINBOW;
    }
    
    /**
     * Parse from string (case-insensitive).
     */
    public static ColorSet fromString(String s) {
        if (s == null) return RAINBOW;
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return RAINBOW;
        }
    }
}
