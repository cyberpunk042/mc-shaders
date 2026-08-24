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
 * Animation configuration for a primitive.
 * 
 * <p>Handles all time-based effects: spin, pulse, wobble, etc.</p>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "animation": {
 *   "spin": { "axis": "Y", "speed": 0.02 },
 *   "pulse": { "scale": 0.1, "speed": 1.0 },
 *   "phase": 0.5,
 *   "alphaPulse": { "min": 0.5, "max": 1.0, "speed": 1.0 },
 *   "colorCycle": { "colors": ["#FF0000", "#00FF00"], "speed": 1.0 },
 *   "wobble": { "amplitude": [0.1, 0.05, 0.1], "speed": 1.0 },
 *   "wave": { "amplitude": 0.1, "frequency": 2.0 }
 * }
 * </pre>
 * 
 * @see SpinConfig
 * @see PulseConfig
 * @see AlphaPulseConfig
 * @see ColorCycleConfig
 * @see WobbleConfig
 * @see WaveConfig
 * @see RayFlowConfig
 * @see RayMotionConfig
 * @see RayWiggleConfig
 * @see RayTwistConfig
 */
public record Animation(
    @JsonField(skipUnless = "isActive") SpinConfig spin,
    @JsonField(skipUnless = "isActive") PulseConfig pulse,
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "0.0") float phase,
    @JsonField(skipUnless = "isActive") AlphaPulseConfig alphaPulse,
    @JsonField(skipUnless = "isActive") ColorCycleConfig colorCycle,
    @JsonField(skipUnless = "isActive") WobbleConfig wobble,
    @JsonField(skipUnless = "isActive") WaveConfig wave,
    @JsonField(skipUnless = "isActive") PrecessionConfig precession,
    @JsonField(skipUnless = "isActive") TravelEffectConfig travelEffect,
    @JsonField(skipUnless = "isActive") RayFlowConfig rayFlow,
    @JsonField(skipUnless = "isActive") RayMotionConfig rayMotion,
    @JsonField(skipUnless = "isActive") RayWiggleConfig rayWiggle,
    @JsonField(skipUnless = "isActive") RayTwistConfig rayTwist
){
    /** No animation (static). */
    public static Animation none() { return NONE; }
    
    public static final Animation NONE = new Animation(
        SpinConfig.NONE, PulseConfig.NONE, 0, AlphaPulseConfig.NONE, null, null, null, null, null, null, null, null, null);
    
    /** Default animation (slow spin). */
    public static final Animation DEFAULT = new Animation(
        SpinConfig.DEFAULT, PulseConfig.NONE, 0, AlphaPulseConfig.NONE, null, null, null, null, null, null, null, null, null);

    
    /** Spinning animation. */
    public static final Animation SPINNING = new Animation(
        SpinConfig.DEFAULT, PulseConfig.NONE, 0, AlphaPulseConfig.NONE, null, null, null, null, null, null, null, null, null);
    
    /** Pulsing animation. */
    public static final Animation PULSING = new Animation(
        SpinConfig.NONE, PulseConfig.DEFAULT, 0, AlphaPulseConfig.NONE, null, null, null, null, null, null, null, null, null);
    
    /**
     * Creates spin animation.
     * @param speed Rotation speed
     */
    public static Animation spin(float speed) {
        return new Animation(
            SpinConfig.aroundY(speed), PulseConfig.NONE, 0, 
            AlphaPulseConfig.NONE, null, null, null, null, null, null, null, null, null);
    }
    
    /**
     * Creates pulse animation.
     * @param amplitude Pulse amplitude
     * @param speed Pulse speed
     */
    public static Animation pulse(float amplitude, float speed) {
        return new Animation(
            SpinConfig.NONE, PulseConfig.sine(amplitude, speed), 0,
            AlphaPulseConfig.NONE, null, null, null, null, null, null, null, null, null);
    }
    
    /**
     * Creates spin + pulse animation.
     * @param spinSpeed Spin speed
     * @param pulseAmplitude Pulse amplitude
     */
    public static Animation spinAndPulse(float spinSpeed, float pulseAmplitude) {
        return new Animation(
            SpinConfig.aroundY(spinSpeed),
            PulseConfig.sine(pulseAmplitude, 1.0f),
            0, AlphaPulseConfig.NONE, null, null, null, null, null, null, null, null, null);
    }
    
    /** Whether any animation is active. */
    public boolean isActive() {
        return (spin != null && spin.isActive()) ||
               (pulse != null && pulse.isActive()) ||
               (alphaPulse != null && alphaPulse.isActive()) ||
               (colorCycle != null && colorCycle.isActive()) ||
               (wobble != null && wobble.isActive()) ||
               (wave != null && wave.isActive()) ||
               (precession != null && precession.isActive()) ||
               (travelEffect != null && travelEffect.isActive()) ||
               (rayFlow != null && rayFlow.isActive()) ||
               (rayMotion != null && rayMotion.isActive()) ||
               (rayWiggle != null && rayWiggle.isActive()) ||
               (rayTwist != null && rayTwist.isActive());
    }
    
    /** Whether spin is active. */
    public boolean hasSpin() { return spin != null && spin.isActive(); }
    
    /** Whether pulse is active. */
    public boolean hasPulse() { return pulse != null && pulse.isActive(); }
    
    /** Whether alpha pulse is active. */
    public boolean hasAlphaPulse() { return alphaPulse != null && alphaPulse.isActive(); }
    
    /** Whether color cycling is active. */
    public boolean hasColorCycle() { return colorCycle != null && colorCycle.isActive(); }
    
    /** Whether wobble is active. */
    public boolean hasWobble() { return wobble != null && wobble.isActive(); }
    
    /** Whether wave is active. */
    public boolean hasWave() { return wave != null && wave.isActive(); }
    
    /** Whether precession is active. */
    public boolean hasPrecession() { return precession != null && precession.isActive(); }
    
    /** Whether general travel effect is active. */
    public boolean hasTravelEffect() { return travelEffect != null && travelEffect.isActive(); }
    
    /** Whether ray flow animation is active. */
    public boolean hasRayFlow() { return rayFlow != null && rayFlow.isActive(); }
    
    /** Whether ray motion animation is active. */
    public boolean hasRayMotion() { return rayMotion != null && rayMotion.isActive(); }
    
    /** Whether ray wiggle animation is active. */
    public boolean hasRayWiggle() { return rayWiggle != null && rayWiggle.isActive(); }
    
    /** Whether ray twist animation is active. */
    public boolean hasRayTwist() { return rayTwist != null && rayTwist.isActive(); }
    

    // =========================================================================
    // JSON Parsing
    // =========================================================================
    
    
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    /** Create a builder pre-populated with this record's values. */
    public Builder toBuilder() {
        return new Builder()
            .spin(spin)
            .pulse(pulse)
            .phase(phase)
            .alphaPulse(alphaPulse)
            .colorCycle(colorCycle)
            .wobble(wobble)
            .wave(wave)
            .precession(precession)
            .travelEffect(travelEffect)
            .rayFlow(rayFlow)
            .rayMotion(rayMotion)
            .rayWiggle(rayWiggle)
            .rayTwist(rayTwist);
    }
    
    public static class Builder {
        private SpinConfig spin = SpinConfig.NONE;
        private PulseConfig pulse = PulseConfig.NONE;
        private @Range(ValueRange.NORMALIZED) float phase = 0;
        private AlphaPulseConfig alphaPulse = AlphaPulseConfig.NONE;
        private ColorCycleConfig colorCycle = null;
        private WobbleConfig wobble = null;
        private WaveConfig wave = null;
        private PrecessionConfig precession = null;
        private TravelEffectConfig travelEffect = null;
        private RayFlowConfig rayFlow = null;
        private RayMotionConfig rayMotion = null;
        private RayWiggleConfig rayWiggle = null;
        private RayTwistConfig rayTwist = null;
        
        public Builder spin(SpinConfig s) { this.spin = s; return this; }
        public Builder spin(float speed) { this.spin = SpinConfig.aroundY(speed); return this; }
        public Builder pulse(PulseConfig p) { this.pulse = p; return this; }
        public Builder phase(float p) { this.phase = p; return this; }
        public Builder alphaPulse(AlphaPulseConfig a) { this.alphaPulse = a; return this; }
        public Builder colorCycle(ColorCycleConfig c) { this.colorCycle = c; return this; }
        public Builder wobble(WobbleConfig w) { this.wobble = w; return this; }
        public Builder wave(WaveConfig w) { this.wave = w; return this; }
        public Builder precession(PrecessionConfig p) { this.precession = p; return this; }
        public Builder travelEffect(TravelEffectConfig t) { this.travelEffect = t; return this; }
        public Builder rayFlow(RayFlowConfig r) { this.rayFlow = r; return this; }
        public Builder rayMotion(RayMotionConfig r) { this.rayMotion = r; return this; }
        public Builder rayWiggle(RayWiggleConfig r) { this.rayWiggle = r; return this; }
        public Builder rayTwist(RayTwistConfig r) { this.rayTwist = r; return this; }
        
        public Animation build() {
            return new Animation(spin, pulse, phase, alphaPulse, colorCycle, wobble, wave, precession, travelEffect, rayFlow, rayMotion, rayWiggle, rayTwist);
        }
    }

    // =========================================================================
    // Immutable Modifiers
    // =========================================================================
    
    /**
     * Returns a copy with the specified spin config.
     */
    public Animation withSpin(SpinConfig newSpin) {
        return new Animation(newSpin, pulse, phase, alphaPulse, colorCycle, wobble, wave, precession, travelEffect, rayFlow, rayMotion, rayWiggle, rayTwist);
    }
    
    /**
     * Returns a copy with the specified pulse config.
     */
    public Animation withPulse(PulseConfig newPulse) {
        return new Animation(spin, newPulse, phase, alphaPulse, colorCycle, wobble, wave, precession, travelEffect, rayFlow, rayMotion, rayWiggle, rayTwist);
    }
    
    /**
     * Returns a copy with the specified phase.
     */
    public Animation withPhase(float newPhase) {
        return new Animation(spin, pulse, newPhase, alphaPulse, colorCycle, wobble, wave, precession, travelEffect, rayFlow, rayMotion, rayWiggle, rayTwist);
    }
    
    /**
     * Returns a copy with the specified precession config.
     */
    public Animation withPrecession(PrecessionConfig newPrecession) {
        return new Animation(spin, pulse, phase, alphaPulse, colorCycle, wobble, wave, newPrecession, travelEffect, rayFlow, rayMotion, rayWiggle, rayTwist);
    }
    
    /**
     * Returns a copy with the specified travel effect config.
     */
    public Animation withTravelEffect(TravelEffectConfig newTravelEffect) {
        return new Animation(spin, pulse, phase, alphaPulse, colorCycle, wobble, wave, precession, newTravelEffect, rayFlow, rayMotion, rayWiggle, rayTwist);
    }
    
    /**
     * Returns a copy with the specified ray flow config.
     */
    public Animation withRayFlow(RayFlowConfig newRayFlow) {
        return new Animation(spin, pulse, phase, alphaPulse, colorCycle, wobble, wave, precession, travelEffect, newRayFlow, rayMotion, rayWiggle, rayTwist);
    }
    
    /**
     * Returns a copy with the specified ray motion config.
     */
    public Animation withRayMotion(RayMotionConfig newRayMotion) {
        return new Animation(spin, pulse, phase, alphaPulse, colorCycle, wobble, wave, precession, travelEffect, rayFlow, newRayMotion, rayWiggle, rayTwist);
    }
    
    /**
     * Returns a copy with the specified ray wiggle config.
     */
    public Animation withRayWiggle(RayWiggleConfig newRayWiggle) {
        return new Animation(spin, pulse, phase, alphaPulse, colorCycle, wobble, wave, precession, travelEffect, rayFlow, rayMotion, newRayWiggle, rayTwist);
    }
    
    /**
     * Returns a copy with the specified ray twist config.
     */
    public Animation withRayTwist(RayTwistConfig newRayTwist) {
        return new Animation(spin, pulse, phase, alphaPulse, colorCycle, wobble, wave, precession, travelEffect, rayFlow, rayMotion, rayWiggle, newRayTwist);
    }
    
    

}
