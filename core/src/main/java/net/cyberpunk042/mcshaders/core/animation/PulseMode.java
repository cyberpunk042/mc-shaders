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
package net.cyberpunk042.mcshaders.core.animation;

/**
 * Defines what property the pulse animation affects.
 * 
 * <ul>
 *   <li>{@link #SCALE} - Pulse affects size/scale</li>
 *   <li>{@link #ALPHA} - Pulse affects transparency</li>
 *   <li>{@link #GLOW} - Pulse affects glow intensity</li>
 *   <li>{@link #COLOR} - Pulse affects color (hue shift)</li>
 * </ul>
 */
public enum PulseMode {
    SCALE("Scale"),
    ALPHA("Alpha"),
    GLOW("Glow"),
    COLOR("Color");
    
    private final String label;
    
    PulseMode(String label) {
        this.label = label;
    }
    
    public String label() {
        return label;
    }
    
    @Override
    public String toString() {
        return label;
    }
    
    /**
     * Parse from string (case-insensitive).
     */
    public static PulseMode fromString(String s) {
        if (s == null) return SCALE;
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SCALE;
        }
    }
}

