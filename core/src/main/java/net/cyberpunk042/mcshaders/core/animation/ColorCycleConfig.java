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

import java.util.List;
import net.cyberpunk042.mcshaders.core.serial.JsonField;
import net.cyberpunk042.mcshaders.core.validation.Range;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;

/**
 * Configuration for color cycling animation (FUTURE).
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "colorCycle": {
 *   "colors": ["#FF0000", "#00FF00", "#0000FF"],
 *   "speed": 1.0,
 *   "blend": true
 * }
 * </pre>
 * 
 * @see Animation
 */
public record ColorCycleConfig(
    @JsonField(skipIfEmpty = true) List<String> colors,
    @Range(ValueRange.POSITIVE) float speed,
    @JsonField(skipIfDefault = true, defaultValue = "true") boolean blend
) {
    
    
    public static final ColorCycleConfig NONE = new ColorCycleConfig(null, 0, false);
    
    /** Default RGB cycle. */
    public static final ColorCycleConfig RGB = new ColorCycleConfig(
        List.of("#FF0000", "#00FF00", "#0000FF"), 1.0f, true);
    
    /** Rainbow cycle. */
    public static final ColorCycleConfig RAINBOW = new ColorCycleConfig(
        List.of("#FF0000", "#FF7F00", "#FFFF00", "#00FF00", "#0000FF", "#4B0082", "#9400D3"),
        0.5f, true);
    
    /** Fire colors. */
    public static final ColorCycleConfig FIRE = new ColorCycleConfig(
        List.of("#FF0000", "#FF4500", "#FFA500", "#FFD700"), 2.0f, true);
    
    /**
     * Creates a color cycle between two colors.
     * @param color1 First color
     * @param color2 Second color
     * @param speed Cycle speed
     */
    public static ColorCycleConfig between(String color1, String color2, @Range(ValueRange.POSITIVE) float speed) {
        return new ColorCycleConfig(List.of(color1, color2), speed, true);
    }
    
    /** Whether color cycling is active. */
    public boolean isActive() {
        return colors != null && !colors.isEmpty() && speed > 0;
    }
    
    
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    /** Create a builder pre-populated with this record's values. */
    public Builder toBuilder() {
        return new Builder()
            .colors(colors)
            .colors(colors)
            .speed(speed)
            .blend(blend);
    }
    
    public static class Builder {
        private List<String> colors = null;
        private @Range(ValueRange.POSITIVE) float speed = 1.0f;
        private boolean blend = true;
        
        public Builder colors(List<String> c) { this.colors = c; return this; }
        public Builder colors(String... c) { this.colors = List.of(c); return this; }
        public Builder speed(float s) { this.speed = s; return this; }
        public Builder blend(boolean b) { this.blend = b; return this; }
        
        public ColorCycleConfig build() {
            return new ColorCycleConfig(colors, speed, blend);
        }
    }
}
