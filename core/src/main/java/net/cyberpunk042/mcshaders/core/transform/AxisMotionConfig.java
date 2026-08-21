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
package net.cyberpunk042.mcshaders.core.transform;

import net.cyberpunk042.mcshaders.core.animation.Waveform;
import net.cyberpunk042.mcshaders.core.validation.Range;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;
import net.cyberpunk042.mcshaders.core.serial.JsonField;

/**
 * Configuration for motion along a single axis (X, Y, or Z).
 * 
 * <p>Part of the 3D orbital motion system. Each axis can have independent
 * motion with its own mode, amplitude, frequency, phase, and easing.</p>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "x": {
 *   "mode": "CIRCULAR",
 *   "amplitude": 2.0,
 *   "frequency": 0.5,
 *   "phase": 0.0,
 *   "easing": "SINE",
 *   "amplitude2": 0.5,    // For complex modes (EPICYCLIC, HELIX, etc.)
 *   "frequency2": 2.0,    // Secondary frequency (WOBBLE, FLOWER, etc.)
 *   "swingAngle": 90.0    // For PENDULUM mode (degrees)
 * }
 * </pre>
 * 
 * <h2>Primary Parameters</h2>
 * <ul>
 *   <li><b>mode</b>: Type of motion (NONE, WAVE, CIRCULAR, HELIX, etc.)</li>
 *   <li><b>amplitude</b>: Maximum displacement from center (radius for orbits)</li>
 *   <li><b>frequency</b>: Speed of motion (cycles per second)</li>
 *   <li><b>phase</b>: Starting phase offset (0-1 = 0-360°)</li>
 *   <li><b>easing</b>: Waveform shape for single-axis modes</li>
 * </ul>
 * 
 * <h2>Secondary Parameters (for complex modes)</h2>
 * <ul>
 *   <li><b>amplitude2</b>: Secondary amplitude (HELIX wave height, EPICYCLIC small circle, etc.)</li>
 *   <li><b>frequency2</b>: Secondary frequency (WOBBLE speed, FLOWER petal count, etc.)</li>
 *   <li><b>swingAngle</b>: Arc angle for PENDULUM mode (degrees, e.g., 90 = quarter arc)</li>
 * </ul>
 * 
 * @see MotionMode
 * @see OrbitConfig3D
 * @see Waveform
 */
public record AxisMotionConfig(
    @JsonField(skipIfDefault = true) MotionMode mode,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true) float amplitude,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true, defaultValue = "1.0") float frequency,
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true) float phase,
    @JsonField(skipIfDefault = true) Waveform easing,
    // Secondary parameters for complex modes (EPICYCLIC, HELIX, WOBBLE, etc.)
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true) float amplitude2,
    @JsonField(skipIfDefault = true) float frequency2,  // Can be negative for CW/CCW
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true) float swingAngle,
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true) float phase2,
    // Secondary orbit orientation (for EPICYCLIC - tilt of the "moon's orbit" plane)
    @JsonField(skipIfDefault = true) float orbit2TiltX,  // Pitch of secondary orbit (degrees)
    @JsonField(skipIfDefault = true) float orbit2TiltY,  // Yaw of secondary orbit (degrees)
    @JsonField(skipIfDefault = true) float orbit2TiltZ   // Roll of secondary orbit (degrees)
) {
    /** No motion on this axis. */
    public static final AxisMotionConfig NONE = new AxisMotionConfig(
        MotionMode.NONE, 0f, 1f, 0f, Waveform.SINE, 0.5f, 2f, 90f, 0f, 0f, 0f, 0f);
    
    /** Default circular motion (1.0 amplitude, 1.0 frequency). */
    public static final AxisMotionConfig CIRCULAR_DEFAULT = new AxisMotionConfig(
        MotionMode.CIRCULAR, 1.0f, 1.0f, 0f, Waveform.SINE, 0.5f, 2f, 90f, 0f, 0f, 0f, 0f);
    
    /** Default wave/oscillation motion. */
    public static final AxisMotionConfig WAVE_DEFAULT = new AxisMotionConfig(
        MotionMode.WAVE, 0.5f, 1.0f, 0f, Waveform.SINE, 0.5f, 2f, 90f, 0f, 0f, 0f, 0f);
    
    // =========================================================================
    // Factory Methods
    // =========================================================================
    
    /**
     * Creates a circular motion config.
     * @param amplitude Distance from center
     * @param frequency Speed (cycles per second)
     */
    public static AxisMotionConfig circular(float amplitude, float frequency) {
        return new AxisMotionConfig(MotionMode.CIRCULAR, amplitude, frequency, 0f, Waveform.SINE, 
            0.5f, 2f, 90f, 0f, 0f, 0f, 0f);
    }
    
    /**
     * Creates a circular motion with phase offset.
     */
    public static AxisMotionConfig circular(float amplitude, float frequency, float phase) {
        return new AxisMotionConfig(MotionMode.CIRCULAR, amplitude, frequency, phase, Waveform.SINE, 
            0.5f, 2f, 90f, 0f, 0f, 0f, 0f);
    }
    
    /**
     * Creates a wave/oscillation motion.
     */
    public static AxisMotionConfig wave(float amplitude, float frequency) {
        return new AxisMotionConfig(MotionMode.WAVE, amplitude, frequency, 0f, Waveform.SINE, 
            0.5f, 2f, 90f, 0f, 0f, 0f, 0f);
    }
    
    /**
     * Creates bounce motion.
     */
    public static AxisMotionConfig bounce(float amplitude, float frequency) {
        return new AxisMotionConfig(MotionMode.BOUNCE, amplitude, frequency, 0f, Waveform.SINE, 
            0.5f, 2f, 90f, 0f, 0f, 0f, 0f);
    }
    
    // =========================================================================
    // Query Methods
    // =========================================================================
    
    /** Whether this axis has any motion. */
    public boolean isActive() {
        return mode != MotionMode.NONE && amplitude > 0;
    }
    
    /** Whether this axis uses 2D orbital motion (creates motion in perpendicular axis). */
    public boolean is2DOrbit() {
        return mode.is2DOrbit();
    }
    
    /** Whether this axis uses 3D orbital motion (WOBBLE, HELIX, ORBIT_BOUNCE). */
    public boolean is3DOrbit() {
        return mode.is3DOrbit();
    }
    
    /** Factory for disabled axis. */
    public static AxisMotionConfig none() {
        return NONE;
    }
    
    // Immutable with* methods for single-property updates
    public AxisMotionConfig withMode(MotionMode m) { return toBuilder().mode(m).build(); }
    public AxisMotionConfig withAmplitude(float a) { return toBuilder().amplitude(a).build(); }
    public AxisMotionConfig withFrequency(float f) { return toBuilder().frequency(f).build(); }
    public AxisMotionConfig withPhase(float p) { return toBuilder().phase(p).build(); }
    public AxisMotionConfig withEasing(Waveform e) { return toBuilder().easing(e).build(); }
    
    // =========================================================================
    // Motion Calculation
    // =========================================================================
    
    /**
     * Calculates displacement at a given time (sin component for 2D modes).
     * 
     * <p>Frequency is in cycles per second (at 20 ticks/sec game speed).
     * So frequency = 1.0 means 1 full orbit per second.</p>
     * 
     * @param time Time in ticks
     * @return Displacement value scaled by amplitude
     */
    public float getDisplacement(float time) {
        if (!isActive()) return 0f;
        
        float t = (time / 20f) * frequency + phase;
        
        // Special handling for complex modes
        return switch (mode) {
            case PENDULUM -> {
                // PENDULUM: Sweeps through swingAngle degrees of arc
                // Frequency scaled by 0.5 for natural pendulum speed (freq=1 -> 2 sec period)
                float pendulumT = (time / 20f) * (frequency * 0.5f) + phase;
                float oscillation = (float) Math.sin(pendulumT * Math.PI * 2); // -1 to 1
                float maxAngleRad = (float) Math.toRadians(swingAngle / 2f);
                float currentAngleRad = oscillation * maxAngleRad;
                // Primary arc position (sin component)
                float primary = (float) Math.sin(currentAngleRad) * amplitude;
                
                // Secondary oscillation: perpendicular wobble during swing
                // frequency2 = oscillations per swing cycle, amplitude2 = wobble amount
                if (amplitude2 > 0.001f && frequency2 > 0.001f) {
                    float secondaryT = pendulumT * frequency2;
                    float secondary = (float) Math.sin(secondaryT * Math.PI * 2) * amplitude2;
                    yield primary + secondary;
                }
                yield primary;
            }
            case FLOWER -> {
                // Sin with radius modulation for petal pattern
                yield (float) Math.sin(t * Math.PI * 2) * amplitude * getRadiusMultiplier(time);
            }
            case ELLIPTIC -> {
                // Sin component at full amplitude (minor axis)
                yield (float) Math.sin(t * Math.PI * 2) * amplitude;
            }
            case EPICYCLIC -> {
                // Primary orbit: uses amplitude, frequency, phase
                float primary = (float) Math.sin(t * Math.PI * 2) * amplitude;
                
                // Secondary orbit: uses amplitude2, frequency2 (scaled), phase2
                // frequency2 divided by 10 for manageable control
                float t2 = (time / 20f) * (frequency2 / 10f) + phase2;
                
                // Calculate secondary orbit position in local space
                float secX = (float) Math.sin(t2 * Math.PI * 2) * amplitude2;
                float secY = (float) Math.cos(t2 * Math.PI * 2) * amplitude2;
                
                // Apply tilt rotation to secondary orbit (simplified: just use X tilt for this axis)
                // Full 3D rotation is applied in getDisplacementCos and getEpicyclicZ
                float tiltRad = (float) Math.toRadians(orbit2TiltX);
                float rotatedSec = secX * (float) Math.cos(tiltRad) - secY * (float) Math.sin(tiltRad);
                
                yield primary + rotatedSec;
            }
            default -> {
                float raw = mode.evaluate(t);
                // Apply easing for single-axis modes
                if (mode.tier() == MotionMode.Tier.SINGLE_AXIS && easing != Waveform.SINE) {
                    raw = raw * easing.evaluate(Math.abs(raw));
                }
                yield raw * amplitude;
            }
        };
    }
    
    /**
     * Gets cosine displacement (for 2D orbit mode paired axes).
     * 
     * @param time Time in ticks
     * @return Cosine displacement scaled by amplitude
     */
    public float getDisplacementCos(float time) {
        if (!isActive()) return 0f;
        
        float t = (time / 20f) * frequency + phase;
        
        return switch (mode) {
            case PENDULUM -> {
                // PENDULUM cos component: horizontal position on the arc
                // Same scaled frequency as sin component
                float pendulumT = (time / 20f) * (frequency * 0.5f) + phase;
                float oscillation = (float) Math.sin(pendulumT * Math.PI * 2);
                float maxAngleRad = (float) Math.toRadians(swingAngle / 2f);
                float currentAngleRad = oscillation * maxAngleRad;
                // Cos component of arc position (horizontal movement)
                yield (float) Math.cos(currentAngleRad) * amplitude;
            }
            case FLOWER -> {
                // Cos with radius modulation
                yield (float) Math.cos(t * Math.PI * 2) * amplitude * getRadiusMultiplier(time);
            }
            case ELLIPTIC -> {
                // Cos component scaled by amplitude2 (ellipse ratio)
                // amplitude2 = 1.0 -> circle, amplitude2 = 0.5 -> ellipse with half-width
                float ratio = amplitude2 > 0.01f ? amplitude2 : 0.6f;  // default 0.6 if not set
                yield (float) Math.cos(t * Math.PI * 2) * amplitude * ratio;
            }
            case EPICYCLIC -> {
                // Primary orbit cosine component
                float primary = (float) Math.cos(t * Math.PI * 2) * amplitude;
                
                // Secondary orbit with phase2 and Y-tilt (frequency2 scaled)
                float t2 = (time / 20f) * (frequency2 / 10f) + phase2;
                float secX = (float) Math.sin(t2 * Math.PI * 2) * amplitude2;
                float secY = (float) Math.cos(t2 * Math.PI * 2) * amplitude2;
                
                // Apply Y-tilt rotation to secondary orbit
                float tiltRad = (float) Math.toRadians(orbit2TiltY);
                float rotatedSec = secX * (float) Math.sin(tiltRad) + secY * (float) Math.cos(tiltRad);
                
                yield primary + rotatedSec;
            }
            default -> mode.evaluateCos(t) * amplitude;
        };
    }
    
    /**
     * Gets perpendicular displacement for 3D modes (Y axis when orbiting in XZ).
     * Only applicable for WOBBLE, HELIX, ORBIT_BOUNCE modes.
     * 
     * @param time Time in ticks
     * @return Perpendicular displacement
     */
    public float getDisplacementPerp(float time) {
        if (!isActive()) return 0f;
        
        // Base orbit position (without phase, for HELIX coil calculation)
        float baseOrbitT = (time / 20f) * frequency;
        // Full orbit time (with phase, for position-dependent effects)
        float orbitT = baseOrbitT + phase;
        
        // Secondary time - frequency2 scaled down for manageable control
        // Divide by 10 so frequency2=1 means 0.1 cycles/second (slow)
        float secondaryT = (time / 20f) * (frequency2 / 10f) + phase2;
        
        return switch (mode) {
            case WOBBLE -> {
                // WOBBLE: The orbit plane tilts back and forth
                // frequency2 = rock speed (scaled), amplitude2 = tilt amount
                float tiltOscillation = (float) Math.sin(secondaryT * Math.PI * 2);
                // Your position on orbit determines how much tilt affects you
                float orbitPosition = (float) Math.sin(orbitT * Math.PI * 2);
                yield orbitPosition * tiltOscillation * amplitude2;
            }
            case HELIX -> {
                // HELIX: True corkscrew/spring - Y progresses LINEARLY while orbiting
                // Like walking up a spiral staircase - you go around AND up
                // frequency2 = orbits per full up-down cycle
                // amplitude2 = height of the helix (how far up/down it goes)
                
                // Linear sawtooth: goes 0 to 1 repeatedly per orbit
                // Then we scale to -amplitude2 to +amplitude2
                float helixProgress = orbitT / Math.max(frequency2, 0.1f);  // How far through the helix
                float withinCycle = helixProgress - (float) Math.floor(helixProgress);  // 0 to 1
                
                // Triangle wave: 0->1->0 per cycle for smooth up-then-down motion
                float triangleWave = withinCycle < 0.5f 
                    ? withinCycle * 4f - 1f      // -1 to 1 ascending
                    : 3f - withinCycle * 4f;     // 1 to -1 descending
                
                yield triangleWave * amplitude2;
            }
            case ORBIT_BOUNCE -> {
                // ORBIT_BOUNCE: Bouncing on perpendicular axis
                // frequency2 = bounces per second (scaled by /10)
                // Handle negative frequency by taking absolute value of the cycle
                float absSecT = Math.abs(secondaryT);
                float cycle = absSecT - (float) Math.floor(absSecT);  // 0 to 1
                float bx = cycle * 2f - 1f;  // -1 to 1
                yield (1f - bx * bx) * amplitude2;  // parabola: 0 -> 1 -> 0
            }
            case EPICYCLIC -> {
                // Secondary orbit with Z-tilt (scaled frequency)
                float secAngle = (float) (secondaryT * Math.PI * 2);
                float secPos = (float) Math.sin(secAngle) * amplitude2;
                
                // Apply Z-tilt to get perpendicular component
                float tiltRad = (float) Math.toRadians(orbit2TiltZ);
                yield secPos * (float) Math.sin(tiltRad);
            }
            case PENDULUM -> {
                // PENDULUM perpendicular: wobble perpendicular to swing plane
                if (amplitude2 > 0.001f && frequency2 > 0.001f) {
                    float pendulumT = baseOrbitT * frequency2;
                    yield (float) Math.sin(pendulumT * Math.PI * 2) * amplitude2;
                }
                yield 0f;
            }
            default -> 0f;
        };
    }
    
    /**
     * Gets radius multiplier for FLOWER mode.
     * 
     * @param time Time in ticks
     * @return Radius multiplier (1 + oscillation)
     */
    public float getRadiusMultiplier(float time) {
        if (mode != MotionMode.FLOWER) return 1f;
        
        // frequency2 = petal frequency (independent of main orbit)
        // Use time-based calculation to avoid speed multiplication
        float petalT = (time / 20f) * frequency2 + phase2;
        return 1f + (float) Math.sin(petalT * Math.PI * 2) * (amplitude2 / Math.max(amplitude, 0.1f));
    }
    
    // =========================================================================
    // JSON Parsing
    // =========================================================================
    
    
    
    
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    
    /** Creates a builder pre-populated with this config's values. */
    public Builder toBuilder() {
        return new Builder()
            .mode(mode)
            .amplitude(amplitude)
            .frequency(frequency)
            .phase(phase)
            .easing(easing)
            .amplitude2(amplitude2)
            .frequency2(frequency2)
            .swingAngle(swingAngle)
            .phase2(phase2)
            .orbit2TiltX(orbit2TiltX)
            .orbit2TiltY(orbit2TiltY)
            .orbit2TiltZ(orbit2TiltZ);
    }
    
    public static class Builder {
        private MotionMode mode = MotionMode.NONE;
        private float amplitude = 0f;
        private float frequency = 1f;
        private float phase = 0f;
        private Waveform easing = Waveform.SINE;
        private float amplitude2 = 0.5f;  // Sensible default for complex modes
        private float frequency2 = 2f;     // Sensible default
        private float swingAngle = 90f;
        private float phase2 = 0f;
        private float orbit2TiltX = 0f;
        private float orbit2TiltY = 0f;
        private float orbit2TiltZ = 0f;
        
        public Builder mode(MotionMode m) { this.mode = m; return this; }
        public Builder amplitude(float a) { this.amplitude = a; return this; }
        public Builder frequency(float f) { this.frequency = f; return this; }
        public Builder phase(float p) { this.phase = p; return this; }
        public Builder easing(Waveform e) { this.easing = e; return this; }
        public Builder amplitude2(float a) { this.amplitude2 = a; return this; }
        public Builder frequency2(float f) { this.frequency2 = f; return this; }
        public Builder swingAngle(float a) { this.swingAngle = a; return this; }
        public Builder phase2(float p) { this.phase2 = p; return this; }
        public Builder orbit2TiltX(float t) { this.orbit2TiltX = t; return this; }
        public Builder orbit2TiltY(float t) { this.orbit2TiltY = t; return this; }
        public Builder orbit2TiltZ(float t) { this.orbit2TiltZ = t; return this; }
        
        public AxisMotionConfig build() {
            return new AxisMotionConfig(mode, amplitude, frequency, phase, easing, 
                amplitude2, frequency2, swingAngle, phase2, orbit2TiltX, orbit2TiltY, orbit2TiltZ);
        }
    }
}
