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
 * Gradient direction for MESH_GRADIENT and MESH_RAINBOW modes.
 * 
 * <p>Determines along which axis the color gradient/rainbow is applied.</p>
 */
public enum GradientDirection {
    /**
     * Vertical gradient (Y-axis).
     * Bottom = start color, Top = end color.
     * Common for spheres, cylinders, prisms.
     */
    Y_AXIS,
    
    /**
     * Horizontal gradient (X-axis).
     * Left = start color, Right = end color.
     */
    X_AXIS,
    
    /**
     * Depth gradient (Z-axis).
     * Front = start color, Back = end color.
     */
    Z_AXIS,
    
    /**
     * Radial gradient from center outward.
     * Center = start color, Edge = end color.
     * Good for rings, spheres, circular patterns.
     */
    RADIAL,
    
    /**
     * Along the length of elongated shapes (rays, jets, beams).
     * Base = start color, Tip = end color.
     * Uses the shape's natural length axis.
     */
    ALONG_LENGTH,
    
    /**
     * Angular/polar around the shape.
     * Creates color bands around curved surfaces.
     * 0° = start color, 360° = wraps back.
     */
    ANGULAR;
    
    /**
     * Default direction. ANGULAR works correctly for all shapes
     * regardless of vertex coordinate centering.
     */
    public static GradientDirection defaultDirection() {
        return ANGULAR;
    }
    
    /**
     * Parse from string (case-insensitive, handles dashes).
     */
    public static GradientDirection fromString(String s) {
        if (s == null) return Y_AXIS;
        try {
            return valueOf(s.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return Y_AXIS;
        }
    }
    
    /**
     * Calculate the t value (0-1) for a vertex position.
     * 
     * @param x Vertex X position (local coordinates)
     * @param y Vertex Y position
     * @param z Vertex Z position
     * @param radius The shape's radius
     * @param height The shape's height
     * @return Value from 0-1 representing position along gradient
     */
    public float calculateT(float x, float y, float z, float radius, float height) {
        return switch (this) {
            case Y_AXIS -> {
                // Normalize Y to 0-1 range using height
                // Assumes shape is centered, so y ranges from -height/2 to +height/2
                float halfHeight = height / 2f;
                if (halfHeight == 0) halfHeight = 1f;
                float t = (y + halfHeight) / (height);
                yield Math.max(0, Math.min(1, t));
            }
            case X_AXIS -> {
                // Normalize X using radius
                float t = (x + radius) / (2f * radius);
                yield Math.max(0, Math.min(1, t));
            }
            case Z_AXIS -> {
                // Normalize Z using radius
                float t = (z + radius) / (2f * radius);
                yield Math.max(0, Math.min(1, t));
            }
            case RADIAL -> {
                // Distance from center to edge
                float dist = (float) Math.sqrt(x * x + z * z);
                yield Math.min(1f, dist / radius);
            }
            case ALONG_LENGTH -> {
                // For rays/jets, y typically represents length (0 to height)
                float t = Math.max(0, Math.min(1, y / height));
                yield t;
            }
            case ANGULAR -> {
                // Angular position around Y axis
                float angle = (float) Math.atan2(z, x);
                yield (angle + (float) Math.PI) / (2f * (float) Math.PI);
            }
        };
    }
}
