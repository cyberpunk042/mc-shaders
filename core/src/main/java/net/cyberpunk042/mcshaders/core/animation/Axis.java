package net.cyberpunk042.mcshaders.core.animation;

import org.joml.Vector3f;

/**
 * Defines rotation/animation axes.
 * 
 * <p>Used by SpinConfig and other animation configurations
 * to specify which axis the animation operates on.</p>
 * 
 * @see SpinConfig
 */
public enum Axis {
    /** X-axis (pitch) */
    X(1, 0, 0),
    
    /** Y-axis (yaw) - most common for spinning */
    Y(0, 1, 0),
    
    /** Z-axis (roll) */
    Z(0, 0, 1),
    
    /** Custom axis - use SpinConfig.customAxis */
    CUSTOM(0, 0, 0);
    
    private final float x, y, z;
    
    Axis(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    /**
     * Returns the axis as a normalized vector.
     * @return A new Vector3f representing this axis
     */
    public Vector3f toVector() {
        return new Vector3f(x, y, z);
    }
    
    /**
     * Returns true if this is the CUSTOM axis.
     */
    public boolean isCustom() {
        return this == CUSTOM;
    }

    
    /**
     * Parse from string (case-insensitive).
     * @param id The string identifier
     * @return Matching Axis, or Y if not found
     */
    public static Axis fromId(String id) {
        if (id == null || id.isEmpty()) return Y;
        try {
            return valueOf(id.toUpperCase().replace("-", "_").replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return Y;
        }
    }
}
