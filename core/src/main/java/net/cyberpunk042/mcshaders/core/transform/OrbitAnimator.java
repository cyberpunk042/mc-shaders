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

import net.cyberpunk042.mcshaders.core.animation.Axis;
import org.joml.Vector3f;

/**
 * Calculates orbit positions for primitives with {@link OrbitConfig}.
 * 
 * <p>Orbit creates a circular path around the anchor point:</p>
 * <ul>
 *   <li>radius - Distance from center</li>
 *   <li>speed - Radians per tick (0.02 = ~3 seconds per revolution)</li>
 *   <li>axis - Plane of orbit (Y = horizontal, X/Z = vertical)</li>
 *   <li>phase - Starting angle offset (0-1 = 0-360°)</li>
 * </ul>
 * 
 * @see OrbitConfig
 * @see Transform
 * @see TransformApplier
 */
public final class OrbitAnimator {
    
    private OrbitAnimator() {}
    
    // =========================================================================
    // Calculation
    // =========================================================================
    
    /**
     * Calculates the orbit offset at a given time.
     * 
     * @param config The orbit configuration
     * @param time Current time in ticks (or any time unit matching speed)
     * @return Offset vector from center
     */
    public static Vector3f getOffset(OrbitConfig config, float time) {
        if (config == null || !config.isActive()) {
            return new Vector3f();
        }
        
        // Calculate angle
        float phaseOffset = config.phase() * (float) (Math.PI * 2);
        float angle = phaseOffset + (time * config.speed());
        
        float radius = config.radius();
        float cos = (float) Math.cos(angle) * radius;
        float sin = (float) Math.sin(angle) * radius;
        
        // Apply to correct plane based on axis
        Vector3f offset = switch (config.axis()) {
            case X -> new Vector3f(0, cos, sin);      // YZ plane
            case Y -> new Vector3f(cos, 0, sin);      // XZ plane (horizontal)
            case Z -> new Vector3f(cos, sin, 0);      // XY plane
            case CUSTOM -> new Vector3f(cos, 0, sin); // Default to XZ
        };

        return offset;
    }
    
    /**
     * Gets the current angle in degrees.
     * 
     * @param config The orbit configuration
     * @param time Current time
     * @return Angle in degrees (0-360)
     */
    public static float getAngleDegrees(OrbitConfig config, float time) {
        if (config == null) return 0;
        float phaseOffset = config.phase() * 360f;
        float angle = phaseOffset + (float) Math.toDegrees(time * config.speed());
        return angle % 360f;
    }
}
