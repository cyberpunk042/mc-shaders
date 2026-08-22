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
 * Defines the winding direction for helix shapes.
 * 
 * <p>Determines whether the helix spirals clockwise or
 * counter-clockwise when viewed from above.</p>
 * 
 */
public enum HeightDirection {
    /** Clockwise when viewed from above */
    CW("cw"),
    
    /** Counter-clockwise when viewed from above */
    CCW("ccw");
    
    private final String id;
    
    HeightDirection(String id) {
        this.id = id;
    }
    
    /** String identifier for JSON */
    public String id() { return id; }
    
    /** Returns the angular direction multiplier (-1 or 1) */
    public int multiplier() {
        return this == CW ? 1 : -1;
    }
    
    /**
     * Parse from string (case-insensitive).
     * @param id Direction identifier
     * @return Matching direction, or CCW as default
     */
    public static HeightDirection fromId(String id) {
        if (id == null || id.isEmpty()) {
            return CCW;
        }
        if ("cw".equalsIgnoreCase(id) || "clockwise".equalsIgnoreCase(id)) {
            return CW;
        }
        return CCW;
    }
}
