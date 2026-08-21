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

import net.cyberpunk042.mcshaders.core.validation.Range;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;
import net.cyberpunk042.mcshaders.core.serial.JsonField;

/**
 * Configuration for surface wave/ripple deformation animation.
 * 
 * <h2>Wave Modes</h2>
 * <ul>
 *   <li><b>GPU</b>: Shader-based deformation (best performance)</li>
 *   <li><b>CPU</b>: Tessellator-level deformation (compatible with all render modes)</li>
 * </ul>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "wave": {
 *   "amplitude": 0.1,
 *   "frequency": 2.0,
 *   "speed": 1.0,
 *   "direction": "Y",
 *   "mode": "GPU"
 * }
 * </pre>
 * 
 * @see Animation
 * @see Axis
 */
public record WaveConfig(
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true) float amplitude,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true, defaultValue = "2.0") float frequency,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true, defaultValue = "1.0") float speed,
    @JsonField(skipIfDefault = true) Axis direction,
    @JsonField(skipIfDefault = true) WaveMode mode
){
    /**
     * Wave rendering mode.
     */
    public enum WaveMode {
        /** GPU shader-based deformation (best performance) */
        GPU("GPU (Fast)"),
        /** CPU tessellator-level deformation (compatible) */
        CPU("CPU (Compatible)");
        
        private final String displayName;
        WaveMode(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }
    }
    
    
    
    /** No wave animation. */
    public static final WaveConfig NONE = new WaveConfig(0, 0, 0, Axis.Y, WaveMode.CPU);
    
    /** Default gentle wave. */
    public static final WaveConfig DEFAULT = new WaveConfig(0.1f, 2.0f, 1.0f, Axis.Y, WaveMode.CPU);
    
    /** Strong wave. */
    public static final WaveConfig STRONG = new WaveConfig(0.3f, 3.0f, 1.5f, Axis.Y, WaveMode.CPU);
    
    /** Horizontal ripple. */
    public static final WaveConfig RIPPLE = new WaveConfig(0.05f, 4.0f, 2.0f, Axis.Y, WaveMode.CPU);
    
    /**
     * Creates a vertical wave using GPU mode.
     * @param amplitude Wave height
     * @param frequency Wave frequency
     */
    public static WaveConfig vertical(
            @Range(ValueRange.POSITIVE) float amplitude, 
            @Range(ValueRange.POSITIVE) float frequency) {
        return new WaveConfig(amplitude, frequency, 1.0f, Axis.Y, WaveMode.CPU);
    }
    
    /** Whether wave is active. */
    public boolean isActive() {
        return amplitude > 0 && frequency > 0;
    }
    
    /** Whether to use GPU shader mode. */
    public boolean isGpuMode() {
        return mode == WaveMode.GPU;
    }
    
    /** Whether to use CPU tessellator mode. */
    public boolean isCpuMode() {
        return mode == WaveMode.CPU;
    }
    
    
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    
    /** Create a builder pre-populated with this record's values. */
    public Builder toBuilder() {
        return new Builder()
            .amplitude(amplitude)
            .frequency(frequency)
            .speed(speed)
            .direction(direction)
            .mode(mode);
    }
    
    public static class Builder {
        private @Range(ValueRange.POSITIVE) float amplitude = 0.1f;
        private @Range(ValueRange.POSITIVE) float frequency = 2.0f;
        private @Range(ValueRange.POSITIVE) float speed = 1.0f;
        private Axis direction = Axis.Y;
        private WaveMode mode = WaveMode.CPU;
        
        public Builder amplitude(float a) { this.amplitude = a; return this; }
        public Builder frequency(float f) { this.frequency = f; return this; }
        public Builder speed(float s) { this.speed = s; return this; }
        public Builder direction(Axis d) { this.direction = d; return this; }
        public Builder mode(WaveMode m) { this.mode = m; return this; }
        
        public WaveConfig build() {
            return new WaveConfig(amplitude, frequency, speed, direction, mode);
        }
    }
}
