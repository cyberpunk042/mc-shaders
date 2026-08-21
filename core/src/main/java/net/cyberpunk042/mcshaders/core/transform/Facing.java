package net.cyberpunk042.mcshaders.core.transform;

/**
 * Defines which direction a primitive's "front" (normal) faces.
 * 
 * <p>Facing controls the static orientation of a shape. For example,
 * a disc with FRONT facing will be vertical like a shield, while
 * TOP facing makes it horizontal like a platform.</p>
 * 
 * <p>This is different from {@link Billboard} which dynamically
 * rotates to face the camera.</p>
 * 
 * @see Transform
 * @see Billboard
 * @see Anchor
 */
public enum Facing {
    /** No rotation applied - shape uses its default orientation (DEFAULT) */
    FIXED("Fixed", 0, 0, 0),
    
    /** Shape normal points up (+Y) - e.g., disc flat like a platform */
    TOP("Top", 0, 0, 0),
    
    /** Shape normal points down (-Y) */
    BOTTOM("Bottom", 180, 0, 0),
    
    /** Shape normal points forward (+Z, player look direction) - e.g., disc vertical like a shield */
    FRONT("Front", 90, 0, 0),
    
    /** Shape normal points backward (-Z) */
    BACK("Back", -90, 0, 0),
    
    /** Shape normal points left (-X) */
    LEFT("Left", 90, 90, 0),
    
    /** Shape normal points right (+X) */
    RIGHT("Right", 90, -90, 0);
    
    private final String label;
    private final float pitchDeg;  // X rotation
    private final float yawDeg;    // Y rotation
    private final float rollDeg;   // Z rotation
    
    Facing(String label, float pitch, float yaw, float roll) {
        this.label = label;
        this.pitchDeg = pitch;
        this.yawDeg = yaw;
        this.rollDeg = roll;
    }
    
    /** Display label for GUI */
    public String label() {
        return label;
    }
    
    /** X rotation (pitch) in degrees */
    public float pitch() {
        return pitchDeg;
    }
    
    /** Y rotation (yaw) in degrees */
    public float yaw() {
        return yawDeg;
    }
    
    /** Z rotation (roll) in degrees */
    public float roll() {
        return rollDeg;
    }
    
    @Override
    public String toString() {
        return label;
    }
    
    /**
     * Parse from string (case-insensitive).
     * @param id The string identifier
     * @return Matching Facing, or FIXED if not found
     */
    public static Facing fromId(String id) {
        if (id == null || id.isEmpty()) return FIXED;
        try {
            return valueOf(id.toUpperCase().replace("-", "_").replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return FIXED;
        }
    }
}
