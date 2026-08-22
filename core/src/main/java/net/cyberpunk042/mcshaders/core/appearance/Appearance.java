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

import net.cyberpunk042.mcshaders.core.validation.Range;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;
import net.cyberpunk042.mcshaders.core.serial.JsonField;

/**
 * Visual appearance configuration for a primitive.
 * 
 * <p>Appearance controls colors, transparency, and effects.
 * Animation is handled separately by Animation record.</p>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "appearance": {
 *   "color": "@primary",
 *   "alpha": { "min": 0.6, "max": 0.8 },
 *   "glow": 0.5,
 *   "emissive": 0.0,
 *   "saturation": 1.0,
 *   "brightness": 1.0,
 *   "hueShift": 0.0,
 *   "secondaryColor": "@secondary",
 *   "colorBlend": 0.0
 * }
 * </pre>
 * 
 * @see AlphaRange
 * @see net.cyberpunk042.mcshaders.core.animation.Animation
 */
public record Appearance(
    @JsonField(skipIfNull = true) String color,
    @JsonField(skipIfNull = true) AlphaRange alpha,
    @Range(ValueRange.ALPHA) @JsonField(skipIfDefault = true) float glow,
    @Range(ValueRange.ALPHA) float emissive,
    @Range(ValueRange.ALPHA) float saturation,
    @Range(ValueRange.ALPHA) float brightness,
    @Range(ValueRange.DEGREES) float hueShift,
    @JsonField(skipIfNull = true) String secondaryColor,
    @Range(ValueRange.ALPHA) float colorBlend,
    // Color mode system
    @JsonField(skipIfDefault = true) ColorMode colorMode,
    @JsonField(skipIfDefault = true) ColorDistribution colorDistribution,
    @JsonField(skipIfDefault = true) ColorSet colorSet,
    @JsonField(skipIfDefault = true) GradientDirection gradientDirection,
    @Range(ValueRange.ALPHA) @JsonField(skipIfDefault = true) float timePhase
){
    /** Default appearance. */
    public static Appearance defaults() { return DEFAULT; }
    
    public static Appearance wireframe(String color) { 
        return builder().color(color).build();
    }
    
    public static Appearance translucent(String color, float alpha) { 
        return builder().color(color).alpha(AlphaRange.of(alpha)).build();
    }
    
    public static final Appearance DEFAULT = new Appearance(
        "@primary", AlphaRange.DEFAULT, 0, 0, 1, 1, 0, null, 0, 
        ColorMode.GRADIENT, ColorDistribution.UNIFORM, ColorSet.RAINBOW, GradientDirection.Y_AXIS, 0f);
    
    /** Glowing appearance. */
    public static final Appearance GLOWING = new Appearance(
        "@primary", AlphaRange.DEFAULT, 0.8f, 0.5f, 1, 1, 0, null, 0, 
        ColorMode.GRADIENT, ColorDistribution.UNIFORM, ColorSet.RAINBOW, GradientDirection.Y_AXIS, 0f);
    
    /** Translucent appearance. */
    public static final Appearance TRANSLUCENT = new Appearance(
        "@primary", AlphaRange.of(0.3f), 0, 0, 1, 1, 0, null, 0, 
        ColorMode.GRADIENT, ColorDistribution.UNIFORM, ColorSet.RAINBOW, GradientDirection.Y_AXIS, 0f);
    
    /**
     * Creates a simple solid-color appearance.
     * @param color Color reference (e.g., "@primary", "#FF0000")
     */
    public static Appearance color(String color) {
        return new Appearance(color, AlphaRange.OPAQUE, 0, 0, 1, 1, 0, null, 0, 
            ColorMode.GRADIENT, ColorDistribution.UNIFORM, ColorSet.RAINBOW, GradientDirection.Y_AXIS, 0f);
    }
    
    /**
     * Creates an appearance with glow.
     * @param color Color reference
     * @param glow Glow intensity (0-1)
     */
    public static Appearance glowing(String color, @Range(ValueRange.ALPHA) float glow) {
        return new Appearance(color, AlphaRange.DEFAULT, glow, glow * 0.5f, 1, 1, 0, null, 0, 
            ColorMode.GRADIENT, ColorDistribution.UNIFORM, ColorSet.RAINBOW, GradientDirection.Y_AXIS, 0f);
    }
    
    /** Whether glow is active. */
    public boolean hasGlow() { return glow > 0; }
    
    /** Whether emissive is active. */
    public boolean hasEmissive() { return emissive > 0; }
    
    /** Whether secondary color is used. */
    public boolean hasSecondaryColor() { return secondaryColor != null && colorBlend > 0; }
    
    /** Whether gradient color mode is active. */
    public boolean isGradientMode() { return colorMode == ColorMode.GRADIENT || colorMode == ColorMode.MESH_GRADIENT; }
    
    /** Whether mesh rainbow color mode is active. */
    public boolean isMeshRainbow() { return colorMode == ColorMode.MESH_RAINBOW; }
    
    /** Whether cycling color mode is active. */
    public boolean isCycling() { return colorMode == ColorMode.CYCLING; }
    
    /** Whether random color mode is active. */
    public boolean isRandom() { return colorMode == ColorMode.RANDOM; }
    
    /** Whether per-vertex coloring is needed. */
    public boolean isPerVertex() { return colorMode != null && colorMode.isPerVertex(); }
    
    /** Get effective color mode (defaults to SOLID if null). */
    public ColorMode effectiveColorMode() { return colorMode != null ? colorMode : ColorMode.GRADIENT; }
    
    /** Get effective color distribution (defaults to UNIFORM if null). */
    public ColorDistribution effectiveDistribution() { return colorDistribution != null ? colorDistribution : ColorDistribution.UNIFORM; }
    
    /** Get effective color set (defaults to RAINBOW if null). */
    public ColorSet effectiveColorSet() { return colorSet != null ? colorSet : ColorSet.RAINBOW; }
    
    /** Get effective gradient direction (defaults to Y_AXIS if null). */
    public GradientDirection effectiveDirection() { return gradientDirection != null ? gradientDirection : GradientDirection.Y_AXIS; }
    
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
            .color(color)
            .alpha(alpha)
            .glow(glow)
            .emissive(emissive)
            .saturation(saturation)
            .brightness(brightness)
            .hueShift(hueShift)
            .secondaryColor(secondaryColor)
            .colorBlend(colorBlend)
            .colorMode(colorMode)
            .colorDistribution(colorDistribution)
            .colorSet(colorSet)
            .gradientDirection(gradientDirection)
            .timePhase(timePhase);
    }
    

    
    public static class Builder {
        private String color = "@primary";
        private AlphaRange alpha = AlphaRange.DEFAULT;
        private @Range(ValueRange.ALPHA) float glow = 0;
        private @Range(ValueRange.ALPHA) float emissive = 0;
        private @Range(ValueRange.ALPHA) float saturation = 1;
        private @Range(ValueRange.ALPHA) float brightness = 1;
        private @Range(ValueRange.DEGREES) float hueShift = 0;
        private String secondaryColor = null;
        private @Range(ValueRange.ALPHA) float colorBlend = 0;
        private ColorMode colorMode = ColorMode.GRADIENT;
        private ColorDistribution colorDistribution = ColorDistribution.UNIFORM;
        private ColorSet colorSet = ColorSet.RAINBOW;
        private GradientDirection gradientDirection = GradientDirection.Y_AXIS;
        private float timePhase = 0f;
        
        public Builder color(String c) { this.color = c; return this; }
        public Builder alpha(AlphaRange a) { this.alpha = a; return this; }
        public Builder alpha(float a) { this.alpha = AlphaRange.of(a); return this; }
        public Builder glow(float g) { this.glow = g; return this; }
        public Builder emissive(float e) { this.emissive = e; return this; }
        public Builder saturation(float s) { this.saturation = s; return this; }
        public Builder brightness(float b) { this.brightness = b; return this; }
        public Builder hueShift(float h) { this.hueShift = h; return this; }
        public Builder secondaryColor(String c) { this.secondaryColor = c; return this; }
        public Builder colorBlend(float b) { this.colorBlend = b; return this; }
        public Builder colorMode(ColorMode m) { this.colorMode = m; return this; }
        public Builder colorDistribution(ColorDistribution d) { this.colorDistribution = d; return this; }
        public Builder colorSet(ColorSet s) { this.colorSet = s; return this; }
        public Builder gradientDirection(GradientDirection d) { this.gradientDirection = d; return this; }
        public Builder timePhase(float p) { this.timePhase = p; return this; }
        /** @deprecated Use timePhase instead */
        @Deprecated public Builder rainbowSpeed(float s) { this.timePhase = s; return this; }
        
        public Appearance build() {
            return new Appearance(color, alpha, glow, emissive, saturation, brightness, 
                hueShift, secondaryColor, colorBlend, colorMode, colorDistribution, 
                colorSet, gradientDirection, timePhase);
        }
    }

    // =========================================================================
    // Immutable Modifiers
    // =========================================================================
    
    /**
     * Returns a copy with the specified alpha range.
     */
    public Appearance withAlpha(AlphaRange newAlpha) {
        return new Appearance(color, newAlpha, glow, emissive, saturation, brightness, hueShift, 
            secondaryColor, colorBlend, colorMode, colorDistribution, colorSet, gradientDirection, timePhase);
    }
    
    /**
     * Returns a copy with the specified color.
     */
    public Appearance withColor(String newColor) {
        return new Appearance(newColor, alpha, glow, emissive, saturation, brightness, hueShift, 
            secondaryColor, colorBlend, colorMode, colorDistribution, colorSet, gradientDirection, timePhase);
    }

}
