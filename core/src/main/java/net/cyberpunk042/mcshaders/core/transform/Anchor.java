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
package net.cyberpunk042.mcshaders.core.transform;

import org.joml.Vector3f;

/**
 * Defines anchor positions RELATIVE to player center (chest height).
 * 
 * <p>The rendering origin is at player center (y+1 from feet).
 * Anchor offsets are applied relative to this center position.</p>
 * 
 * <h3>Example</h3>
 * <pre>
 * anchor: FEET + offset: {y: 0.5} = 0.5 blocks above feet
 * </pre>
 * 
 * @see Transform
 */
public enum Anchor {
    /** Player chest/center height (0, 0, 0) - DEFAULT (no offset) */
    CENTER("Center", 0, 0, 0),
    
    /** Player feet level (0, -1, 0) - 1 block below center */
    FEET("Feet", 0, -1, 0),
    
    /** Player head level (0, 1, 0) - 1 block above center */
    HEAD("Head", 0, 1, 0),
    
    /** Above player head (0, 2, 0) - 2 blocks above center */
    ABOVE("Above Head", 0, 2, 0),
    
    /** Below feet / underground (0, -2, 0) - 2 blocks below center */
    BELOW("Below Feet", 0, -2, 0),
    
    /** In front of player (0, 0, 1) - at center height, 1 forward */
    FRONT("Front", 0, 0, 1),
    
    /** Behind player (0, 0, -1) - at center height, 1 back */
    BACK("Back", 0, 0, -1),
    
    /** Left side of player (-1, 0, 0) - at center height, 1 left */
    LEFT("Left", -1, 0, 0),
    
    /** Right side of player (1, 0, 0) - at center height, 1 right */
    RIGHT("Right", 1, 0, 0);
    
    private final String label;
    private final float x, y, z;
    
    Anchor(String label, float x, float y, float z) {
        this.label = label;
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    /** Display label for GUI */
    public String label() {
        return label;
    }
    
    @Override
    public String toString() {
        return label;
    }
    
    /**
     * Returns the offset vector for this anchor.
     * @return A new Vector3f with the anchor's offset
     */
    public Vector3f getOffset() {
        return new Vector3f(x, y, z);
    }
    
    /**
     * Returns the X component of this anchor's offset.
     */
    public float getX() { return x; }
    
    /**
     * Returns the Y component of this anchor's offset.
     */
    public float getY() { return y; }
    
    /**
     * Returns the Z component of this anchor's offset.
     */
    public float getZ() { return z; }

    
    /**
     * Parse from string (case-insensitive).
     * @param id The string identifier
     * @return Matching Anchor, or CENTER if not found
     */
    public static Anchor fromId(String id) {
        if (id == null || id.isEmpty()) return CENTER;
        try {
            return valueOf(id.toUpperCase().replace("-", "_").replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return CENTER;
        }
    }
}
