/*
 * Ported from the-virus-block-mc (net.cyberpunk042.visual), where it is
 * geometry model code with no Minecraft or graphics-API dependency.
 * Relicensed to MIT here by the author, per the engine/content split
 * recorded in docs/PORTING.md.
 */
package net.cyberpunk042.mcshaders.core.validation;

/**
 * Defines common value ranges for validation and clamping.
 * 
 * <p>Used throughout the field system to ensure values stay within
 * expected bounds and provide consistent validation.</p>
 * 
 * <h2>Usage</h2>
 * <pre>
 * float alpha = ValueRange.ALPHA.clamp(inputValue);  // Ensures 0-1
 * float degrees = ValueRange.DEGREES.clamp(rotation);  // Ensures 0-360
 * </pre>
 * 
 * @see Range
 * @see net.cyberpunk042.mcshaders.core.animation.AlphaRange
 */
public enum ValueRange {
    
    // ========== NORMALIZED VALUES ==========
    
    /** Alpha/opacity values (0.0 to 1.0) */
    ALPHA(0f, 1f, null),
    
    /** Generic normalized values (0.0 to 1.0) */
    NORMALIZED(0f, 1f, null),
    
    /** Percentage values (0 to 100) */
    PERCENTAGE(0f, 100f, "%"),
    
    // ========== ANGLE VALUES ==========
    
    /** Degree angles (0 to 360) */
    DEGREES(0f, 360f, "°"),
    
    /** Signed degrees (-180 to 180) */
    DEGREES_SIGNED(-180f, 180f, "°"),
    
    /** Full rotation range (-360 to 360) */
    DEGREES_FULL(-360f, 360f, "°"),
    
    // ========== SIZE VALUES ==========
    
    /** Positive only (0 to infinity) */
    POSITIVE(0f, Float.MAX_VALUE, null),
    
    /** Strictly positive (0.001 to infinity) - prevents zero */
    POSITIVE_NONZERO(0.001f, Float.MAX_VALUE, null),
    
    /** Scale values (0.01 to 100) */
    SCALE(0.01f, 100f, "×"),
    
    /** Radius values (0.01 to infinity) */
    RADIUS(0.01f, Float.MAX_VALUE, "blocks"),
    
    // ========== COUNT VALUES ==========
    
    /** Step/segment counts (1 to 1024) */
    STEPS(1f, 1024f, null),
    
    /** Polygon sides (3 to 64) */
    SIDES(3f, 64f, null),
    
    // ========== SPEED VALUES ==========
    
    /** Rotation speed (radians per tick) */
    SPEED(-10f, 10f, "rad/tick"),
    
    /** Ticks (0 to 1000) */
    TICKS(0f, 1000f, "ticks"),
    
    // ========== SPECIAL ==========
    
    /** Unbounded (any value allowed) */
    UNBOUNDED(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, null);
    
    // ========== FIELDS ==========
    
    private final float min;
    private final float max;
    private final String unit;
    
    ValueRange(float min, float max, String unit) {
        this.min = min;
        this.max = max;
        this.unit = unit;
    }
    
    /**
     * @return the display unit (e.g., "blocks", "°") or null if none
     */
    public String unit() {
        return unit;
    }
    
    // ========== CLAMPING ==========
    
    /**
     * Clamp a float value to this range.
     * 
     * @param value the value to clamp
     * @return the clamped value within [min, max]
     */
    public float clamp(float value) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Clamp an int value to this range.
     * 
     * @param value the value to clamp
     * @return the clamped value within [min, max]
     */
    public int clamp(int value) {
        return (int) Math.max(min, Math.min(max, value));
    }
    
    /**
     * Clamp a double value to this range.
     * 
     * @param value the value to clamp
     * @return the clamped value within [min, max]
     */
    public double clamp(double value) {
        return Math.max(min, Math.min(max, value));
    }
    
    // ========== VALIDATION ==========
    
    /**
     * Check if a value is within this range (inclusive).
     * 
     * @param value the value to check
     * @return true if value is within [min, max]
     */
    public boolean isValid(float value) {
        return value >= min && value <= max;
    }
    
    /**
     * Check if a value is within this range (inclusive).
     * 
     * @param value the value to check
     * @return true if value is within [min, max]
     */
    public boolean isValid(int value) {
        return value >= min && value <= max;
    }
    
    // ========== ACCESSORS ==========
    
    /**
     * @return the minimum allowed value
     */
    public float min() {
        return min;
    }
    
    /**
     * @return the maximum allowed value
     */
    public float max() {
        return max;
    }
    
    // ========== NORMALIZATION ==========
    
    /**
     * Normalize a value to 0-1 within this range.
     * 
     * @param value the value to normalize (will be clamped first)
     * @return normalized value between 0.0 and 1.0
     */
    public float normalize(float value) {
        if (max == min) return 0f;
        return (clamp(value) - min) / (max - min);
    }
    
    /**
     * Denormalize a 0-1 value to this range.
     * 
     * @param normalized a value between 0.0 and 1.0
     * @return the denormalized value within this range
     */
    public float denormalize(float normalized) {
        float clampedNorm = Math.max(0f, Math.min(1f, normalized));
        return min + (max - min) * clampedNorm;
    }
    
    // ========== UTILITY ==========
    
    /**
     * @return a human-readable description of this range
     */
    public String describe() {
        if (this == UNBOUNDED) return "any value";
        if (max == Float.MAX_VALUE) return min + "+";
        return min + " to " + max;
    }
}

