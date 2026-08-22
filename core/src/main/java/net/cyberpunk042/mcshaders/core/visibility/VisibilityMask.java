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
package net.cyberpunk042.mcshaders.core.visibility;

import net.cyberpunk042.mcshaders.core.validation.Range;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;
import net.cyberpunk042.mcshaders.core.serial.JsonField;

/**
 * Controls which parts of a shape are visible.
 * 
 * <h2>Mask Types</h2>
 * <ul>
 *   <li>{@link MaskType#FULL} - Everything visible (default)</li>
 *   <li>{@link MaskType#BANDS} - Horizontal bands</li>
 *   <li>{@link MaskType#STRIPES} - Vertical stripes</li>
 *   <li>{@link MaskType#CHECKER} - Checkerboard pattern</li>
 *   <li>{@link MaskType#RADIAL} - Radial segments</li>
 *   <li>{@link MaskType#GRADIENT} - Alpha gradient</li>
 *   <li>{@link MaskType#CUSTOM} - Custom mask function</li>
 * </ul>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "visibility": {
 *   "mask": "BANDS",
 *   "count": 4,
 *   "thickness": 0.5
 * }
 * </pre>
 * 
 * <p>Shorthand: {@code "visibility": "bands"} uses defaults.</p>
 * 
 * @see MaskType
 */
public record VisibilityMask(
    @JsonField(skipIfEqualsConstant = "FULL") // Phase 1 fields
    MaskType mask,
    @Range(ValueRange.STEPS) @JsonField(skipIfDefault = true, defaultValue = "1") int count,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true, defaultValue = "1.0") float thickness,
    // Phase 2 fields
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true) float offset,
    @JsonField(skipIfDefault = true) boolean invert,
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true) float feather,
    @JsonField(skipIfDefault = true) boolean animate,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true) float animSpeed,
    // Gradient options
    @JsonField(skipIfNull = true) String direction,
    @JsonField(skipIfDefault = true) float falloff,
    @JsonField(skipIfDefault = true) float gradientStart,
    @JsonField(skipIfDefault = true, defaultValue = "1") float gradientEnd,
    // Radial options
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "0.5") float centerX,
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "0.5") float centerY
){
    /** Full visibility (no masking). */
    public static final VisibilityMask FULL = new VisibilityMask(
        MaskType.FULL, 1, 1.0f, 0, false, 0, false, 0, null, 0, 0, 1, 0.5f, 0.5f);
    
    /** Default band mask (4 bands, 50% thickness). */
    public static final VisibilityMask BANDS = new VisibilityMask(
        MaskType.BANDS, 4, 0.5f, 0, false, 0, false, 0, null, 0, 0, 1, 0.5f, 0.5f);
    
    /** Default stripe mask (8 stripes, 50% thickness). */
    public static final VisibilityMask STRIPES = new VisibilityMask(
        MaskType.STRIPES, 8, 0.5f, 0, false, 0, false, 0, null, 0, 0, 1, 0.5f, 0.5f);
    
    /** Default checker mask (4x4 grid). */
    public static final VisibilityMask CHECKER = new VisibilityMask(
        MaskType.CHECKER, 4, 0.5f, 0, false, 0, false, 0, null, 0, 0, 1, 0.5f, 0.5f);
    
    // =========================================================================
    // Factory Methods
    // =========================================================================
    
    /**
     * Creates a band mask.
     * @param count Number of bands
     * @param thickness Band thickness (0-1)
     */
    public static VisibilityMask bands(@Range(ValueRange.STEPS) int count, @Range(ValueRange.POSITIVE) float thickness) {
        return builder().mask(MaskType.BANDS).count(count).thickness(thickness).build();
    }
    
    /**
     * Creates a stripe mask.
     * @param count Number of stripes
     * @param thickness Stripe thickness (0-1)
     */
    public static VisibilityMask stripes(@Range(ValueRange.STEPS) int count, @Range(ValueRange.POSITIVE) float thickness) {
        return builder().mask(MaskType.STRIPES).count(count).thickness(thickness).build();
    }
    
    /** Whether any masking is applied. */
    public boolean hasMask() {
        return mask != MaskType.FULL;
    }
    
    /**
     * Checks if a cell at the given UV coordinates should be visible.
     * 
     * <p>UV coordinates are normalized (0-1) representing position on the surface:
     * <ul>
     *   <li>u: horizontal position (longitude fraction for spheres, angle fraction for rings)</li>
     *   <li>v: vertical position (latitude fraction for spheres, radius fraction for rings)</li>
     * </ul>
     * 
     * @param u horizontal coordinate (0-1)
     * @param v vertical coordinate (0-1)
     * @return true if the cell should be rendered
     */
    public boolean isVisible(float u, float v) {
        return isVisible(u, v, 0f);
    }
    
    /**
     * Checks visibility with animation support.
     * 
     * @param u horizontal coordinate (0-1)
     * @param v vertical coordinate (0-1)
     * @param time animation time (for animated masks)
     * @return true if the cell should be rendered
     */
    public boolean isVisible(float u, float v, float time) {
        if (mask == MaskType.FULL) {
            return true;
        }
        
        // Apply offset (static + animated)
        float animOffset = animate ? (time * animSpeed * 0.01f) : 0f;
        float totalOffset = offset + animOffset;
        float adjustedU = (u + totalOffset) % 1.0f;
        float adjustedV = (v + totalOffset) % 1.0f;
        if (adjustedU < 0) adjustedU += 1.0f;
        if (adjustedV < 0) adjustedV += 1.0f;
        
        boolean visible = switch (mask) {
            case FULL -> true;
            
            case BANDS -> {
                // Horizontal bands based on v coordinate
                float bandPos = (adjustedV * count) % 1.0f;
                yield bandPos < thickness;
            }
            
            case STRIPES -> {
                // Vertical stripes based on u coordinate
                float stripePos = (adjustedU * count) % 1.0f;
                yield stripePos < thickness;
            }
            
            case CHECKER -> {
                // Checkerboard pattern
                int uCell = (int) (adjustedU * count);
                int vCell = (int) (adjustedV * count);
                yield (uCell + vCell) % 2 == 0;
            }
            
            case RADIAL -> {
                // Radial pattern from center point
                float dx = adjustedU - centerX;
                float dy = adjustedV - centerY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float angle = (float) Math.atan2(dy, dx);
                float normalizedAngle = (angle + (float) Math.PI) / (2 * (float) Math.PI);
                float segmentPos = (normalizedAngle * count) % 1.0f;
                yield segmentPos < thickness;
            }
            
            case GRADIENT -> {
                // Gradient visibility based on position
                float gradientPos = direction != null && direction.equals("horizontal") ? adjustedU : adjustedV;
                yield gradientPos >= gradientStart && gradientPos <= gradientEnd;
            }
            
            case CUSTOM -> true; // Custom masks need external handling
        };
        
        return invert ? !visible : visible;
    }
    
    /**
     * Gets visibility alpha at UV coordinates (for feathered edges).
     * 
     * <p>When {@link #feather()} &gt; 0, edges of visible regions are faded.
     * 
     * @param u horizontal coordinate (0-1)
     * @param v vertical coordinate (0-1)
     * @param time animation time
     * @return alpha value (0.0 = invisible, 1.0 = fully visible)
     */
    public float getAlpha(float u, float v, float time) {
        if (mask == MaskType.FULL) {
            return 1.0f;
        }
        
        if (feather <= 0) {
            return isVisible(u, v, time) ? 1.0f : 0.0f;
        }
        
        // Apply offset (static + animated)
        float animOffset = animate ? (time * animSpeed * 0.01f) : 0f;
        float totalOffset = offset + animOffset;
        float adjustedU = (u + totalOffset) % 1.0f;
        float adjustedV = (v + totalOffset) % 1.0f;
        if (adjustedU < 0) adjustedU += 1.0f;
        if (adjustedV < 0) adjustedV += 1.0f;
        
        // Calculate distance from edge based on mask type
        float edgeDist = switch (mask) {
            case FULL -> 1.0f;
            
            case BANDS -> {
                float bandPos = (adjustedV * count) % 1.0f;
                float dist = bandPos < thickness ? bandPos : (thickness - bandPos);
                yield Math.min(dist / feather, 1.0f);
            }
            
            case STRIPES -> {
                float stripePos = (adjustedU * count) % 1.0f;
                float dist = stripePos < thickness ? stripePos : (thickness - stripePos);
                yield Math.min(dist / feather, 1.0f);
            }
            
            default -> isVisible(u, v, time) ? 1.0f : 0.0f;
        };
        
        return invert ? (1.0f - edgeDist) : edgeDist;
    }
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    // =========================================================================
    // Serialization
    // =========================================================================
    
    

    public static Builder builder() { return new Builder(); }
    /** Create a builder pre-populated with this record's values. */
    public Builder toBuilder() {
        return new Builder()
            .mask(mask)
            .count(count)
            .thickness(thickness)
            .offset(offset)
            .invert(invert)
            .feather(feather)
            .animate(animate)
            .animSpeed(animSpeed)
            .direction(direction)
            .falloff(falloff)
            .gradientStart(gradientStart)
            .gradientEnd(gradientEnd)
            .centerX(centerX)
            .centerY(centerY);
    }
    

    
    public static class Builder {
        private MaskType mask = MaskType.FULL;
        private @Range(ValueRange.STEPS) int count = 4;
        private @Range(ValueRange.POSITIVE) float thickness = 0.5f;
        private @Range(ValueRange.NORMALIZED) float offset = 0;
        private boolean invert = false;
        private @Range(ValueRange.NORMALIZED) float feather = 0;
        private boolean animate = false;
        private @Range(ValueRange.POSITIVE) float animSpeed = 0;
        private String direction = null;
        private float falloff = 0;
        private float gradientStart = 0;
        private float gradientEnd = 1;
        private @Range(ValueRange.NORMALIZED) float centerX = 0.5f;
        private @Range(ValueRange.NORMALIZED) float centerY = 0.5f;
        
        public Builder mask(MaskType m) { this.mask = m; return this; }
        public Builder count(int c) { this.count = c; return this; }
        public Builder thickness(float t) { this.thickness = t; return this; }
        public Builder offset(float o) { this.offset = o; return this; }
        public Builder invert(boolean i) { this.invert = i; return this; }
        public Builder feather(float f) { this.feather = f; return this; }
        public Builder animate(boolean a) { this.animate = a; return this; }
        public Builder animSpeed(float s) { this.animSpeed = s; return this; }
        public Builder direction(String d) { this.direction = d; return this; }
        public Builder falloff(float f) { this.falloff = f; return this; }
        public Builder gradientStart(float s) { this.gradientStart = s; return this; }
        public Builder gradientEnd(float e) { this.gradientEnd = e; return this; }
        public Builder centerX(float x) { this.centerX = x; return this; }
        public Builder centerY(float y) { this.centerY = y; return this; }
        
        public VisibilityMask build() {
            return new VisibilityMask(mask, count, thickness, offset, invert, 
                feather, animate, animSpeed, direction, falloff, gradientStart, gradientEnd,
                centerX, centerY);
        }
    }
}
