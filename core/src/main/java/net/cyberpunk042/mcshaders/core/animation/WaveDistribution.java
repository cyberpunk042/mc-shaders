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
 * Controls how ray phase offsets are distributed for wave animations.
 * 
 * <p>Used with RADIATE/ABSORB to control spatial pattern of visibility.</p>
 */
public enum WaveDistribution {
    
    /** Rays are phased sequentially by index - creates coherent rotating wedge/wave. */
    SEQUENTIAL("Sequential"),
    
    /** Rays have randomized (but consistent) phase offsets - scattered pattern. */
    RANDOM("Random"),
    
    /** Phases spread using golden ratio - aesthetically pleasing non-repeating distribution. */
    GOLDEN_RATIO("Golden Ratio"),
    
    /** All rays animate in parallel with the same phase - 360° simultaneous action. */
    CONTINUOUS("Continuous");
    
    private final String displayName;
    
    WaveDistribution(String displayName) {
        this.displayName = displayName;
    }
    
    public String displayName() {
        return displayName;
    }
    
    public static WaveDistribution fromString(String value) {
        if (value == null || value.isEmpty()) return CONTINUOUS;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CONTINUOUS;
        }
    }
}
