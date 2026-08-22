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
import net.cyberpunk042.mcshaders.core.energy.EnergyTravel;
import net.cyberpunk042.mcshaders.core.energy.TravelBlendMode;
import net.cyberpunk042.mcshaders.core.validation.Range;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;

/**
 * General travel effect configuration for ANY shape.
 * 
 * <p>Unlike {@link RayFlowConfig} which is specific to rays, this config
 * can be applied to any shape (sphere, cube, etc.) to create "relativistic jet"
 * style effects where an alpha gradient travels across the surface.</p>
 * 
 * <h2>Direction Control</h2>
 * <p>For shapes without a natural axis (unlike rays), the direction of travel
 * is specified using {@code direction} (axis enum) or {@code dirX/Y/Z} for
 * custom direction vectors.</p>
 * 
 * <h2>Blend Modes</h2>
 * <ul>
 *   <li><b>REPLACE</b> - Travel fully controls visibility</li>
 *   <li><b>OVERLAY</b> - Spotlight effect, base stays visible</li>
 *   <li><b>ADDITIVE</b> - Travel adds glow</li>
 *   <li><b>MODULATE</b> - Travel dims base</li>
 * </ul>
 * 
 * @see RayFlowConfig for ray-specific flow animation
 * @see EnergyTravel for travel mode types
 * @see TravelBlendMode for blend formulas
 */
public record TravelEffectConfig(
    @JsonField(skipIfDefault = true) boolean enabled,
    @JsonField(skipIfDefault = true) EnergyTravel mode,
    @Range(ValueRange.UNBOUNDED) @JsonField(skipIfDefault = true, defaultValue = "1.0") float speed,
    @JsonField(skipIfDefault = true) TravelBlendMode blendMode,
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "0.0") float minAlpha,
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "1.0") float intensity,
    @JsonField(skipIfDefault = true) Axis direction,
    @JsonField(skipIfDefault = true) TravelDirection travelDirection,
    @Range(ValueRange.UNBOUNDED) @JsonField(skipIfDefault = true, defaultValue = "0.0") float dirX,
    @Range(ValueRange.UNBOUNDED) @JsonField(skipIfDefault = true, defaultValue = "1.0") float dirY,
    @Range(ValueRange.UNBOUNDED) @JsonField(skipIfDefault = true, defaultValue = "0.0") float dirZ,
    @Range(ValueRange.STEPS) @JsonField(skipIfDefault = true, defaultValue = "1") int count,
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "0.3") float width
) {
    // =========================================================================
    // Static Constants
    // =========================================================================
    
    /** No travel effect. */
    public static final TravelEffectConfig NONE = new TravelEffectConfig(
        false, EnergyTravel.NONE, 1f, TravelBlendMode.REPLACE, 0f, 1f,
        Axis.Y, TravelDirection.LINEAR, 0f, 1f, 0f, 1, 0.3f
    );
    
    /** Default relativistic jet - vertical chase with overlay blend. */
    public static final TravelEffectConfig RELATIVISTIC_JET = new TravelEffectConfig(
        true, EnergyTravel.COMET, 1.5f, TravelBlendMode.OVERLAY, 0.2f, 0.8f,
        Axis.Y, TravelDirection.LINEAR, 0f, 1f, 0f, 1, 0.3f
    );
    
    /** Chase particles with replace blend. */
    public static final TravelEffectConfig CHASE = new TravelEffectConfig(
        true, EnergyTravel.CHASE, 1f, TravelBlendMode.REPLACE, 0f, 1f,
        Axis.Y, TravelDirection.LINEAR, 0f, 1f, 0f, 3, 0.2f
    );
    
    /** Spotlight sweep effect. */
    public static final TravelEffectConfig SPOTLIGHT = new TravelEffectConfig(
        true, EnergyTravel.SCROLL, 0.5f, TravelBlendMode.OVERLAY, 0.3f, 0.7f,
        Axis.Y, TravelDirection.LINEAR, 0f, 1f, 0f, 1, 0.4f
    );
    
    // =========================================================================
    // Queries
    // =========================================================================
    
    /** Whether this effect is active. */
    public boolean isActive() {
        return enabled && mode != null && mode != EnergyTravel.NONE;
    }
    
    /** Gets effective travel mode, defaulting to NONE if not set. */
    public EnergyTravel effectiveMode() {
        return mode != null ? mode : EnergyTravel.NONE;
    }
    
    /** Gets effective blend mode, defaulting to REPLACE if not set. */
    public TravelBlendMode effectiveBlendMode() {
        return blendMode != null ? blendMode : TravelBlendMode.REPLACE;
    }
    
    /** Gets effective direction axis, defaulting to Y if not set. */
    public Axis effectiveDirection() {
        return direction != null ? direction : Axis.Y;
    }
    
    /** Gets effective travel direction mode, defaulting to LINEAR if not set. */
    public TravelDirection effectiveTravelDirection() {
        return travelDirection != null ? travelDirection : TravelDirection.LINEAR;
    }
    
    /**
     * Gets the direction vector components.
     * Uses custom dirX/Y/Z if direction is CUSTOM, otherwise derives from axis.
     */
    public float[] getDirectionVector() {
        if (direction == Axis.CUSTOM) {
            // Normalize the custom direction
            float len = (float) Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
            if (len < 0.001f) return new float[] { 0f, 1f, 0f };  // Default to Y
            return new float[] { dirX / len, dirY / len, dirZ / len };
        }
        return switch (effectiveDirection()) {
            case X -> new float[] { 1f, 0f, 0f };
            case Y -> new float[] { 0f, 1f, 0f };
            case Z -> new float[] { 0f, 0f, 1f };
            case CUSTOM -> new float[] { 0f, 1f, 0f };  // Fallback
            default -> new float[] { 0f, 1f, 0f };
        };
    }
    
    // =========================================================================
    // JSON Parsing
    // =========================================================================
    
    
    
    
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    
    public Builder toBuilder() {
        return new Builder()
            .enabled(enabled).mode(mode).speed(speed)
            .blendMode(blendMode).minAlpha(minAlpha).intensity(intensity)
            .direction(direction).travelDirection(travelDirection)
            .dirX(dirX).dirY(dirY).dirZ(dirZ)
            .count(count).width(width);
    }
    
    public static class Builder {
        private boolean enabled = false;
        private EnergyTravel mode = EnergyTravel.NONE;
        private float speed = 1f;
        private TravelBlendMode blendMode = TravelBlendMode.REPLACE;
        private float minAlpha = 0f;
        private float intensity = 1f;
        private Axis direction = Axis.Y;
        private TravelDirection travelDirection = TravelDirection.LINEAR;
        private float dirX = 0f;
        private float dirY = 1f;
        private float dirZ = 0f;
        private int count = 1;
        private float width = 0.3f;
        
        public Builder enabled(boolean b) { this.enabled = b; return this; }
        public Builder mode(EnergyTravel m) { this.mode = m != null ? m : EnergyTravel.NONE; return this; }
        public Builder speed(float s) { this.speed = s; return this; }
        public Builder blendMode(TravelBlendMode m) { this.blendMode = m != null ? m : TravelBlendMode.REPLACE; return this; }
        public Builder minAlpha(float a) { this.minAlpha = a; return this; }
        public Builder intensity(float i) { this.intensity = i; return this; }
        public Builder direction(Axis d) { this.direction = d != null ? d : Axis.Y; return this; }
        public Builder travelDirection(TravelDirection td) { this.travelDirection = td != null ? td : TravelDirection.LINEAR; return this; }
        public Builder dirX(float x) { this.dirX = x; return this; }
        public Builder dirY(float y) { this.dirY = y; return this; }
        public Builder dirZ(float z) { this.dirZ = z; return this; }
        public Builder customDirection(float x, float y, float z) {
            this.direction = Axis.CUSTOM;
            this.dirX = x; this.dirY = y; this.dirZ = z;
            return this;
        }
        public Builder count(int c) { this.count = c; return this; }
        public Builder width(float w) { this.width = w; return this; }
        
        public TravelEffectConfig build() {
            return new TravelEffectConfig(
                enabled, mode, speed, blendMode, minAlpha, intensity,
                direction, travelDirection, dirX, dirY, dirZ, count, width
            );
        }
    }
}
