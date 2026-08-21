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
 * Rotation animation over time.
 * 
 * <p>Per ARCHITECTURE.md (line 86):
 * <pre>
 * visual/animation/
 * ├── Spin.java    # rotation over time
 * </pre>
 * 
 * <h2>Usage</h2>
 * <pre>
 * // Spin around Y axis at 0.5 radians per tick
 * Spin spin = Spin.y(0.5f);
 * 
 * // Spin around custom axis
 * Spin spin = new Spin(0.1f, 0.5f, 0.0f);
 * </pre>
 * 
 * @param x rotation speed around X axis (radians per tick)
 * @param y rotation speed around Y axis (radians per tick)
 * @param z rotation speed around Z axis (radians per tick)
 * 
 * @see Animator
 * @see net.cyberpunk042.mcshaders.core.animation.Animation
 */
public record Spin(float x, float y, float z) {
    
    /**
     * No rotation.
     */
    public static final Spin NONE = new Spin(0, 0, 0);
    
    /**
     * Default spin (slow Y rotation).
     */
    public static final Spin DEFAULT = new Spin(0, 0.02f, 0);
    
    /**
     * Creates a spin around the Y axis only.
     */
    public static Spin y(float speed) {
        return new Spin(0, speed, 0);
    }
    
    /**
     * Creates a spin around the X axis only.
     */
    public static Spin x(float speed) {
        return new Spin(speed, 0, 0);
    }
    
    /**
     * Creates a spin around the Z axis only.
     */
    public static Spin z(float speed) {
        return new Spin(0, 0, speed);
    }
    
    /**
     * Calculates rotation angles at a given time.
     * 
     * @param time world time in ticks
     * @return rotation angles [rx, ry, rz] in radians
     */
    public float[] at(long time) {
        return new float[] {
            x * time,
            y * time,
            z * time
        };
    }
    
    /**
     * Returns whether this spin has any rotation.
     */
    public boolean isActive() {
        return x != 0 || y != 0 || z != 0;
    }
    
    /**
     * Creates a scaled version of this spin.
     */
    public Spin scaled(float factor) {
        return new Spin(x * factor, y * factor, z * factor);
    }
}

