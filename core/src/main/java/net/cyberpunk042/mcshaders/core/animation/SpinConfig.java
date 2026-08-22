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

import net.cyberpunk042.mcshaders.core.serial.JsonField;
import net.cyberpunk042.mcshaders.core.validation.Range;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;

/**
 * Configuration for per-axis rotation animation.
 * 
 * <p>Supports independent rotation on X, Y, Z axes with optional oscillation per axis.</p>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "spin": {
 *   "speedX": 0, "speedY": 45, "speedZ": 0,
 *   "oscillateX": false, "oscillateY": false, "oscillateZ": false,
 *   "rangeX": 360, "rangeY": 360, "rangeZ": 360
 * }
 * </pre>
 * 
 * <p>Legacy format is also supported:</p>
 * <pre>
 * "spin": { "axis": "Y", "speed": 45 }
 * </pre>
 */
public record SpinConfig(
    @Range(ValueRange.UNBOUNDED) float speedX,
    @Range(ValueRange.UNBOUNDED) float speedY,
    @Range(ValueRange.UNBOUNDED) float speedZ,
    @JsonField(skipIfDefault = true) boolean oscillateX,
    @JsonField(skipIfDefault = true) boolean oscillateY,
    @JsonField(skipIfDefault = true) boolean oscillateZ,
    @JsonField(skipIfDefault = true, defaultValue = "360") @Range(ValueRange.DEGREES_FULL) float rangeX,
    @JsonField(skipIfDefault = true, defaultValue = "360") @Range(ValueRange.DEGREES_FULL) float rangeY,
    @JsonField(skipIfDefault = true, defaultValue = "360") @Range(ValueRange.DEGREES_FULL) float rangeZ
) {
    // =========================================================================
    // Static Constants
    // =========================================================================
    
    /** No spin (static). */
    public static final SpinConfig NONE = new SpinConfig(0, 0, 0, false, false, false, 360, 360, 360);
    
    /** Default spin (slow Y-axis rotation). */
    public static final SpinConfig DEFAULT = new SpinConfig(0, 45, 0, false, false, false, 360, 360, 360);
    
    // =========================================================================
    // Factory Methods
    // =========================================================================
    
    /** Creates a simple Y-axis spin at the given speed (degrees/sec). */
    public static SpinConfig aroundY(float speed) {
        return new SpinConfig(0, speed, 0, false, false, false, 360, 360, 360);
    }
    
    /** Creates a spin on all three axes at the given speeds. */
    public static SpinConfig combined(float speedX, float speedY, float speedZ) {
        return new SpinConfig(speedX, speedY, speedZ, false, false, false, 360, 360, 360);
    }
    
    // =========================================================================
    // Queries
    // =========================================================================
    
    /** Whether any rotation is active. */
    public boolean isActive() {
        return speedX != 0 || speedY != 0 || speedZ != 0;
    }
    
    /** Whether X-axis rotation is active. */
    public boolean isActiveX() { return speedX != 0; }
    
    /** Whether Y-axis rotation is active. */
    public boolean isActiveY() { return speedY != 0; }
    
    /** Whether Z-axis rotation is active. */
    public boolean isActiveZ() { return speedZ != 0; }
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    
    public Builder toBuilder() {
        return new Builder()
            .speedX(speedX).speedY(speedY).speedZ(speedZ)
            .oscillateX(oscillateX).oscillateY(oscillateY).oscillateZ(oscillateZ)
            .rangeX(rangeX).rangeY(rangeY).rangeZ(rangeZ);
    }
    
    public static class Builder {
        private float speedX = 0, speedY = 0, speedZ = 0;
        private boolean oscillateX = false, oscillateY = false, oscillateZ = false;
        private float rangeX = 360, rangeY = 360, rangeZ = 360;
        
        public Builder speedX(float v) { this.speedX = v; return this; }
        public Builder speedY(float v) { this.speedY = v; return this; }
        public Builder speedZ(float v) { this.speedZ = v; return this; }
        public Builder oscillateX(boolean v) { this.oscillateX = v; return this; }
        public Builder oscillateY(boolean v) { this.oscillateY = v; return this; }
        public Builder oscillateZ(boolean v) { this.oscillateZ = v; return this; }
        public Builder rangeX(float v) { this.rangeX = v; return this; }
        public Builder rangeY(float v) { this.rangeY = v; return this; }
        public Builder rangeZ(float v) { this.rangeZ = v; return this; }
        
        public SpinConfig build() {
            return new SpinConfig(speedX, speedY, speedZ, oscillateX, oscillateY, oscillateZ, rangeX, rangeY, rangeZ);
        }
    }
    
    // =========================================================================
    // JSON Parsing (with legacy support)
    // =========================================================================
    
    
    
    
    
    
}
