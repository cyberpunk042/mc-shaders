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
package net.cyberpunk042.mcshaders.core.shape;

/**
 * Defines the geometric shape of an individual ray line.
 * 
 * <p>This controls how the ray is BENT or CURVED as a line, not its position
 * in the field. A ray can be straight, wavy, coiled like a spring, etc.</p>
 * 
 * <h2>Categories</h2>
 * <ul>
 *   <li><b>Linear</b>: STRAIGHT - default, no curvature</li>
 *   <li><b>Wavy</b>: SINE_WAVE, ZIGZAG, SAWTOOTH, SQUARE_WAVE - 2D undulation</li>
 *   <li><b>Curved</b>: ARC, S_CURVE - smooth bends</li>
 *   <li><b>Helical</b>: CORKSCREW, SPRING, DOUBLE_HELIX - 3D spirals</li>
 *   <li><b>Visual</b>: TAPERED - thickness variation</li>
 * </ul>
 * 
 * @see RaysShape
 * @see RayArrangement
 */
public enum RayLineShape {
    
    /** Default straight line from start to end. No curvature. */
    STRAIGHT("Straight"),
    
    /** Ray twists around its own axis like a helix/drill bit. Requires multiple segments. */
    CORKSCREW("Corkscrew"),
    
    /** Ray coils like a spring/slinky. Circular helix pattern. */
    SPRING("Spring"),
    
    /** Ray undulates side-to-side in a smooth sine wave pattern (2D). */
    SINE_WAVE("Sine Wave"),
    
    /** Ray has sharp angular bends in a zigzag pattern. */
    ZIGZAG("Zigzag"),
    
    /** Ray has sawtooth wave pattern (gradual rise, sharp drop). */
    SAWTOOTH("Sawtooth"),
    
    /** Ray has square wave pattern (sharp 90-degree bends). */
    SQUARE_WAVE("Square Wave"),
    
    /** Ray curves in a single smooth arc (bow shape). */
    ARC("Arc"),
    
    /** Ray has an S-shaped double curve. */
    S_CURVE("S-Curve"),
    
    /** Two intertwined corkscrew rays (DNA double-helix pattern). */
    DOUBLE_HELIX("Double Helix");
    
    private final String displayName;
    
    RayLineShape(String displayName) {
        this.displayName = displayName;
    }
    
    /**
     * Human-readable display name for UI.
     */
    public String displayName() {
        return displayName;
    }
    
    /**
     * Whether this shape requires multiple segments to render properly.
     * Shapes like CORKSCREW, SPRING, and waves need intermediate vertices.
     */
    public boolean requiresMultipleSegments() {
        return this != STRAIGHT;
    }
    
    /**
     * Suggested minimum segment count for this shape to look good.
     * Higher values = smoother curves, more GPU cost.
     */
    public int suggestedMinSegments() {
        return switch (this) {
            case STRAIGHT -> 1;
            case ZIGZAG, SQUARE_WAVE, SAWTOOTH -> 8;
            case SINE_WAVE, ARC, S_CURVE -> 16;
            case CORKSCREW, SPRING -> 32;
            case DOUBLE_HELIX -> 64;
        };
    }
    
    /**
     * Parses from string, case-insensitive.
     * @param value String to parse
     * @return Matching shape or STRAIGHT if not found
     */
    public static RayLineShape fromString(String value) {
        if (value == null || value.isEmpty()) return STRAIGHT;
        try {
            return valueOf(value.toUpperCase().replace(" ", "_").replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return STRAIGHT;
        }
    }
}
