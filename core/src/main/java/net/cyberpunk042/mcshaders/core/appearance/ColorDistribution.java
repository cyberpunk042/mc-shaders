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
 * Distribution mode for color application.
 * 
 * <p>Determines how colors are distributed across cells/vertices:</p>
 * <ul>
 *   <li>{@link #GRADIENT} - Smooth gradient based on position</li>
 *   <li>{@link #INDEXED} - Each cell gets color based on index (stepped pattern)</li>
 *   <li>{@link #RANDOM} - Each cell gets a random color from the palette</li>
 * </ul>
 * 
 * <p>Used with MESH_RAINBOW and RANDOM color modes.</p>
 */
public enum ColorDistribution {
    /**
     * Gradient/uniform distribution.
     * Smooth color transition based on vertex position.
     * For MESH_RAINBOW: smooth rainbow gradient across mesh.
     * For MESH_GRADIENT: smooth primary→secondary gradient.
     */
    GRADIENT,
    
    /**
     * Indexed per-cell distribution.
     * Each cell gets a color based on its index (golden ratio distribution).
     * Creates a varied but repeatable pattern.
     */
    INDEXED,
    
    /**
     * Random per-cell distribution.
     * Each cell gets a truly random color from the ColorSet.
     * Creates a colorful, chaotic pattern.
     */
    RANDOM;
    
    // Backward compatibility aliases
    public static final ColorDistribution UNIFORM = GRADIENT;
    public static final ColorDistribution PER_CELL = INDEXED;
    
    /**
     * Default distribution.
     */
    public static ColorDistribution defaultDistribution() {
        return GRADIENT;
    }
    
    /**
     * Parse from string (case-insensitive, handles old names).
     */
    public static ColorDistribution fromString(String s) {
        if (s == null) return GRADIENT;
        String upper = s.toUpperCase().replace("-", "_");
        // Handle old names
        if ("UNIFORM".equals(upper)) return GRADIENT;
        if ("PER_CELL".equals(upper)) return INDEXED;
        try {
            return valueOf(upper);
        } catch (IllegalArgumentException e) {
            return GRADIENT;
        }
    }
}
