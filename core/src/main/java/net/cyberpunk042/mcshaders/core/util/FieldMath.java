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
package net.cyberpunk042.mcshaders.core.util;

import org.joml.Vector3d;

/**
 * Math utilities for field animations and transformations.
 * 
 * <p>This class is the field-facing vocabulary over {@link MathSupport}, adding
 * the animation patterns the model reaches for repeatedly.</p>
 * 
 * <h2>Interpolation</h2>
 * <ul>
 *   <li>{@link #lerp(float, float, float)} - Linear interpolation</li>
 *   <li>{@link #lerp(double, Vector3d, Vector3d)} - Vector3d interpolation</li>
 *   <li>{@link #smoothStep(float)} - Smooth cubic interpolation</li>
 *   <li>{@link #catmullRom(float, float, float, float, float)} - Spline curve</li>
 * </ul>
 * 
 * <h2>Easing Functions</h2>
 * <ul>
 *   <li>{@link #easeIn(float)} - Accelerating from zero velocity</li>
 *   <li>{@link #easeOut(float)} - Decelerating to zero velocity</li>
 *   <li>{@link #easeInOut(float)} - Acceleration until halfway, then deceleration</li>
 * </ul>
 * 
 * <h2>Animation Helpers</h2>
 * <ul>
 *   <li>{@link #pulse(float, float)} - Sine wave oscillation (0 to 1)</li>
 *   <li>{@link #breathe(float, float, float, float)} - Smooth breathing effect</li>
 *   <li>{@link #bounce(float)} - Bouncing effect</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Fade in over time
 * float alpha = FieldMath.easeOut(fadeProgress);
 * 
 * // Pulsing glow effect
 * float glow = FieldMath.pulse(time, 2.0f); // 2 cycles per second
 * 
 * // Smooth position interpolation
 * Vector3d pos = FieldMath.lerp(0.1, currentPos, targetPos);
 * }</pre>
 * 
 * @see MathSupport
 * @see FieldColor
 * @since 1.0.0
 */
public final class FieldMath {
    
    /** Two times PI, for full rotation calculations. */
    public static final float TAU = (float) (Math.PI * 2);
    
    /** PI constant. */
    public static final float PI = (float) Math.PI;
    
    /** Half PI, for quarter rotations. */
    public static final float HALF_PI = (float) (Math.PI / 2);
    
    private FieldMath() {}
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INTERPOLATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Linear interpolation between two values.
     * 
     * <p>When {@code t = 0}, returns {@code a}. When {@code t = 1}, returns {@code b}.
     * Values of {@code t} outside [0, 1] will extrapolate.</p>
     * 
     * @param t Interpolation factor (0.0 to 1.0)
     * @param a Start value
     * @param b End value
     * @return Interpolated value
     */
    public static float lerp(float t, float a, float b) {
        return MathSupport.lerp(t, a, b);
    }
    
    /**
     * Linear interpolation between two double values.
     * 
     * @param t Interpolation factor (0.0 to 1.0)
     * @param a Start value
     * @param b End value
     * @return Interpolated value
     */
    public static double lerp(double t, double a, double b) {
        return MathSupport.lerp(t, a, b);
    }
    
    /**
     * Linear interpolation between two Vector3d positions.
     * 
     * <p>Useful for smooth position transitions in follow modes.</p>
     * 
     * @param t Interpolation factor (0.0 to 1.0)
     * @param a Start position
     * @param b End position
     * @return Interpolated position
     */
    public static Vector3d lerp(double t, Vector3d a, Vector3d b) {
        // JOML's lerp is componentwise and extrapolates outside [0, 1], which is
        // what the scalar MathSupport.lerp does too.
        return new Vector3d(a).lerp(b, t);
    }
    
    /**
     * Catmull-Rom spline interpolation for smooth curves.
     * 
     * <p>Creates a smooth curve that passes through all control points.
     * Useful for complex animations that need to hit specific keyframes.</p>
     * 
     * @param t Interpolation factor (0.0 to 1.0)
     * @param p0 Control point before start
     * @param p1 Start point
     * @param p2 End point
     * @param p3 Control point after end
     * @return Interpolated value on the spline
     */
    public static float catmullRom(float t, float p0, float p1, float p2, float p3) {
        return MathSupport.catmullRom(t, p0, p1, p2, p3);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // EASING FUNCTIONS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Smooth step interpolation (cubic Hermite).
     * 
     * <p>Provides smooth acceleration and deceleration at both ends.
     * Result is 0 at t=0, 1 at t=1, with zero derivative at both ends.</p>
     * 
     * <pre>
     *   1 |      ___
     *     |     /
     *     |    /
     *   0 |___/
     *     0       1
     * </pre>
     * 
     * @param t Input value (0.0 to 1.0)
     * @return Smoothed value (0.0 to 1.0)
     */
    public static float smoothStep(float t) {
        return t * t * (3 - 2 * t);
    }
    
    /**
     * Smoother step interpolation (quintic).
     * 
     * <p>Even smoother than {@link #smoothStep(float)}, with zero
     * first AND second derivatives at endpoints.</p>
     * 
     * @param t Input value (0.0 to 1.0)
     * @return Smoothed value (0.0 to 1.0)
     */
    public static float smootherStep(float t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }
    
    /**
     * Ease-in (quadratic): accelerating from zero velocity.
     * 
     * <p>Slow start, fast end. Good for spawn animations.</p>
     * 
     * @param t Input value (0.0 to 1.0)
     * @return Eased value (0.0 to 1.0)
     */
    public static float easeIn(float t) {
        return t * t;
    }
    
    /**
     * Ease-out (quadratic): decelerating to zero velocity.
     * 
     * <p>Fast start, slow end. Good for despawn/fade-out animations.</p>
     * 
     * @param t Input value (0.0 to 1.0)
     * @return Eased value (0.0 to 1.0)
     */
    public static float easeOut(float t) {
        return 1 - (1 - t) * (1 - t);
    }
    
    /**
     * Ease-in-out (quadratic): acceleration then deceleration.
     * 
     * <p>Smooth start and end. Good for complete lifecycle animations.</p>
     * 
     * @param t Input value (0.0 to 1.0)
     * @return Eased value (0.0 to 1.0)
     */
    public static float easeInOut(float t) {
        return t < 0.5f 
            ? 2 * t * t 
            : 1 - 2 * (1 - t) * (1 - t);
    }
    
    /**
     * Cubic ease-in: stronger acceleration effect.
     * 
     * @param t Input value (0.0 to 1.0)
     * @return Eased value (0.0 to 1.0)
     */
    public static float easeInCubic(float t) {
        return t * t * t;
    }
    
    /**
     * Cubic ease-out: stronger deceleration effect.
     * 
     * @param t Input value (0.0 to 1.0)
     * @return Eased value (0.0 to 1.0)
     */
    public static float easeOutCubic(float t) {
        float inv = 1 - t;
        return 1 - inv * inv * inv;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ANIMATION HELPERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Generates a sine wave pulse oscillating between 0 and 1.
     * 
     * <p>Perfect for breathing effects, glow pulsing, alpha oscillation.</p>
     * 
     * @param time Current time (in seconds or ticks)
     * @param frequency Cycles per time unit (e.g., 2.0 = 2 pulses per second)
     * @return Value between 0.0 and 1.0
     */
    public static float pulse(float time, float frequency) {
        return ((float) Math.sin(time * frequency * TAU) + 1) * 0.5f;
    }
    
    /**
     * Smooth breathing effect with customizable range.
     * 
     * <p>Like {@link #pulse(float, float)} but maps to a custom min/max range.</p>
     * 
     * @param time Current time
     * @param frequency Cycles per time unit
     * @param min Minimum value
     * @param max Maximum value
     * @return Value oscillating between min and max
     */
    public static float breathe(float time, float frequency, float min, float max) {
        float pulse = pulse(time, frequency);
        return min + pulse * (max - min);
    }
    
    /**
     * Simple bounce effect.
     * 
     * <p>Starts at 0, peaks at 1 when t=0.5, returns to 0 at t=1.
     * Good for impact effects.</p>
     * 
     * @param t Input value (0.0 to 1.0)
     * @return Bounce value (0.0 to 1.0, peaks at 0.5)
     */
    public static float bounce(float t) {
        return 4 * t * (1 - t);
    }
    
    /**
     * Elastic bounce effect (overshoot and settle).
     * 
     * <p>Overshoots the target and oscillates back to settle.
     * Good for springy UI effects.</p>
     * 
     * @param t Input value (0.0 to 1.0)
     * @return Elastic value (may exceed 1.0 temporarily)
     */
    public static float elastic(float t) {
        if (t == 0 || t == 1) return t;
        float p = 0.3f;
        float s = p / 4;
        return (float) (Math.pow(2, -10 * t) * Math.sin((t - s) * TAU / p) + 1);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CLAMPING & MAPPING
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Clamps a value between min and max.
     * 
     * @param value Value to clamp
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     * @return Clamped value
     */
    public static float clamp(float value, float min, float max) {
        return MathSupport.clamp(value, min, max);
    }
    
    /**
     * Clamps a value between min and max (double version).
     * 
     * @param value Value to clamp
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     * @return Clamped value
     */
    public static double clamp(double value, double min, double max) {
        return MathSupport.clamp(value, min, max);
    }
    
    /**
     * Clamps a value between min and max (int version).
     * 
     * @param value Value to clamp
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     * @return Clamped value
     */
    public static int clamp(int value, int min, int max) {
        return MathSupport.clamp(value, min, max);
    }
    
    /**
     * Maps a value from one range to another with clamping.
     * 
     * <p>Example: map health (0-20) to alpha (0.3-1.0):
     * {@code clampedMap(health, 0, 20, 0.3, 1.0)}</p>
     * 
     * @param value Input value
     * @param oldMin Original range minimum
     * @param oldMax Original range maximum
     * @param newMin Target range minimum
     * @param newMax Target range maximum
     * @return Mapped and clamped value
     */
    public static double clampedMap(double value, double oldMin, double oldMax, double newMin, double newMax) {
        return MathSupport.clampedMap(value, oldMin, oldMax, newMin, newMax);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TRIGONOMETRY
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Fast sine using lookup table.
     * 
     * @param radians Angle in radians
     * @return Sine of the angle
     */
    public static float sin(float radians) {
        return (float) Math.sin(radians);
    }
    
    /**
     * Fast cosine using lookup table.
     * 
     * @param radians Angle in radians
     * @return Cosine of the angle
     */
    public static float cos(float radians) {
        return (float) Math.cos(radians);
    }
    
    /**
     * Converts degrees to radians.
     * 
     * @param degrees Angle in degrees
     * @return Angle in radians
     */
    public static float toRadians(float degrees) {
        return degrees * (PI / 180f);
    }
    
    /**
     * Converts radians to degrees.
     * 
     * @param radians Angle in radians
     * @return Angle in degrees
     */
    public static float toDegrees(float radians) {
        return radians * (180f / PI);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // COMPARISON
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Checks if two floats are approximately equal.
     * 
     * <p>Uses a small epsilon for floating point comparison.</p>
     * 
     * @param a First value
     * @param b Second value
     * @return True if approximately equal
     */
    public static boolean approximatelyEquals(float a, float b) {
        return MathSupport.approximatelyEquals(a, b);
    }
}
