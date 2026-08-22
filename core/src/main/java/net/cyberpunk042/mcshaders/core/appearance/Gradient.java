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

import net.cyberpunk042.mcshaders.core.util.MathSupport;

import net.cyberpunk042.mcshaders.core.color.ColorMath;

/**
 * Defines a color gradient for primitives.
 * 
 * <h2>Gradient Types</h2>
 * <ul>
 *   <li><b>LINEAR</b> - Gradient along an axis (Y by default)</li>
 *   <li><b>RADIAL</b> - Gradient from center outward</li>
 *   <li><b>ANGULAR</b> - Gradient around the circumference</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>
 * Gradient g = Gradient.linear(0xFF00FF00, 0xFF0000FF);
 * int color = g.getColor(0.5f); // 50% between green and blue
 * </pre>
 */
public record Gradient(
    Type type,
    int startColor,
    int endColor,
    float bias,      // 0.5 = linear, <0.5 = more start, >0.5 = more end
    boolean smooth   // true = smooth interpolation, false = hard edge
) {
    
    public enum Type {
        LINEAR,   // Top to bottom (or configurable axis)
        RADIAL,   // Center to edge
        ANGULAR   // Around circumference
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // Factory Methods
    // ─────────────────────────────────────────────────────────────────────────
    
    /**
     * Creates a linear gradient from start to end color.
     */
    public static Gradient linear(int startColor, int endColor) {
        return new Gradient(Type.LINEAR, startColor, endColor, 0.5f, true);
    }
    
    /**
     * Creates a radial gradient from center to edge.
     */
    public static Gradient radial(int centerColor, int edgeColor) {
        return new Gradient(Type.RADIAL, centerColor, edgeColor, 0.5f, true);
    }
    
    /**
     * Creates an angular gradient around the primitive.
     */
    public static Gradient angular(int startColor, int endColor) {
        return new Gradient(Type.ANGULAR, startColor, endColor, 0.5f, true);
    }
    
    /**
     * Creates a solid (no gradient) with a single color.
     */
    public static Gradient solid(int color) {
        return new Gradient(Type.LINEAR, color, color, 0.5f, true);
    }
    
    /**
     * Creates a gradient with hard edge at midpoint.
     */
    public static Gradient hardEdge(int startColor, int endColor) {
        return new Gradient(Type.LINEAR, startColor, endColor, 0.5f, false);
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // Color Calculation
    // ─────────────────────────────────────────────────────────────────────────
    
    /**
     * Gets the color at the given position in the gradient.
     * 
     * @param t Position in gradient (0.0 = start, 1.0 = end)
     * @return Interpolated ARGB color
     */
    public int getColor(float t) {
        t = MathSupport.clamp(t, 0.0f, 1.0f);
        
        if (!smooth) {
            // Hard edge at bias point
            return t < bias ? startColor : endColor;
        }
        
        // Apply bias curve
        if (bias != 0.5f) {
            // Shift the midpoint
            if (t < bias) {
                t = t / (bias * 2.0f);
            } else {
                t = 0.5f + (t - bias) / ((1.0f - bias) * 2.0f);
            }
            t = MathSupport.clamp(t, 0.0f, 1.0f);
        }
        
        return ColorMath.blend(startColor, endColor, t);
    }
    
    /**
     * Gets the color for a position in 3D space.
     * Interpretation depends on gradient type.
     * 
     * @param x X position (-1 to 1 normalized)
     * @param y Y position (-1 to 1 normalized)
     * @param z Z position (-1 to 1 normalized)
     */
    public int getColor(float x, float y, float z) {
        float t = switch (type) {
            case LINEAR -> (y + 1.0f) / 2.0f; // Y-axis by default
            case RADIAL -> (float) Math.sqrt(x*x + y*y + z*z); // Distance from center
            case ANGULAR -> (float) (Math.atan2(z, x) / Math.PI + 1.0f) / 2.0f; // Angle
        };
        return getColor(t);
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // Modifiers
    // ─────────────────────────────────────────────────────────────────────────
    
    /**
     * Returns a gradient with swapped colors.
     */
    public Gradient reversed() {
        return new Gradient(type, endColor, startColor, 1.0f - bias, smooth);
    }
    
    /**
     * Returns a gradient with adjusted bias.
     */
    public Gradient withBias(float newBias) {
        return new Gradient(type, startColor, endColor, MathSupport.clamp(newBias, 0, 1), smooth);
    }
    
    /**
     * Checks if this is effectively a solid color (no gradient).
     */
    public boolean isSolid() {
        return startColor == endColor;
    }
}
