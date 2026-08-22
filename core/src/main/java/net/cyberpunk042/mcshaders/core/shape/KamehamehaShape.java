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
package net.cyberpunk042.mcshaders.core.shape;

import net.cyberpunk042.mcshaders.core.serial.JsonField;
import net.cyberpunk042.mcshaders.core.pattern.CellType;
import net.cyberpunk042.mcshaders.core.validation.Range;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;
import org.joml.Vector3f;

import java.util.Map;

/**
 * Kamehameha shape - energy beam attack with charging orb and extending beam.
 * 
 * <h2>Components</h2>
 * <p>A Kamehameha consists of two main components:</p>
 * 
 * <h3>ORB (Charging Sphere)</h3>
 * <ul>
 *   <li>Transition style (FADE, SCALE, FADE_AND_SCALE)</li>
 *   <li>Size and resolution settings</li>
 * </ul>
 * 
 * <h3>BEAM (Energy Stream)</h3>
 * <p>Beam is ALWAYS proportional to the orb. Use {@code beamRatio} to control width relative to orb.</p>
 * <ul>
 *   <li>{@code beamRatio} (0-1): Base radius = orbRadius × beamRatio</li>
 *   <li>{@code beamTaper} (0-1.5): Tip radius = baseRadius × beamTaper (jet, cylinder, or flared)</li>
 *   <li>{@code beamTwist}: Spiral twist in degrees</li>
 *   <li>{@code beamLengthIntensity}: How fast length scales (0=no scaling, higher=faster)</li>
 *   <li>{@code beamWidthIntensity}: How fast width scales (0=no scaling, higher=faster)</li>
 * </ul>
 * 
 * <h2>Scale Intensity</h2>
 * <p>The intensity values control BOTH whether an axis scales AND how fast:</p>
 * <ul>
 *   <li>intensity = 0: No scaling (stays at full size)</li>
 *   <li>intensity = 1: Linear scaling</li>
 *   <li>intensity &gt; 1: Faster start, levels off (ease-out)</li>
 *   <li>intensity &lt; 1: Slower start, catches up (ease-in)</li>
 * </ul>
 * 
 * <h2>Geometry</h2>
 * <pre>
 *                    ┌─────────────────────────────────
 *                   ╱                                   ╲  tipRadius (derived)
 *     orbRadius    ╱                                     ╲
 *       ╭───╮     ╱ ────────────────────────────────────  ╲
 *      (  ●  )═══&lt;        BEAM (beamLength)                )  → direction
 *       ╰───╯     ╲ ────────────────────────────────────  ╱
 *                  ╲                                     ╱
 *                   ╲                                   ╱  tipRadius (derived)
 *                    └─────────────────────────────────
 *        ORB                    BEAM
 * </pre>
 * 
 * <h2>Lifecycle Integration</h2>
 * <p>The orb and beam progress fields (0-1) are controlled by LifecycleAnimator:</p>
 * <ul>
 *   <li><b>CHARGE</b> - Orb progress 0→1, beam progress 0</li>
 *   <li><b>FIRE</b> - Orb progress 1, beam progress 0→1</li>
 *   <li><b>SUSTAIN</b> - Both at 1</li>
 *   <li><b>RETRACT</b> - Beam 1→0, then orb 1→0</li>
 * </ul>
 * 
 * <h2>Parts</h2>
 * <ul>
 *   <li><b>orb</b> (QUAD) - Main charging sphere</li>
 *   <li><b>beam</b> (QUAD) - Main beam cylinder</li>
 *   <li><b>beamTip</b> (QUAD) - Beam end cap (dome or flat)</li>
 * </ul>
 * 
 * @see TransitionStyle
 * @see net.cyberpunk042.mcshaders.core.animation.LifecycleAnimator
 */
public record KamehamehaShape(
    // =========================================================================
    // ORB CONFIGURATION
    // =========================================================================
    
    /** How the orb transitions in/out during lifecycle. */
    @JsonField(skipIfDefault = true)
    TransitionStyle orbTransition,
    
    /** Radius of the charging orb at full charge. */
    @Range(ValueRange.POSITIVE) 
    @JsonField(skipIfDefault = true, defaultValue = "0.3")
    float orbRadius,
    
    /** Resolution of the orb (segments). */
    @Range(ValueRange.STEPS) 
    @JsonField(skipIfDefault = true, defaultValue = "16")
    int orbSegments,
    
    /** Orb animation progress (0 = invisible, 1 = full). */
    @Range(ValueRange.NORMALIZED)
    @JsonField(skipIfDefault = true, defaultValue = "1.0")
    float orbProgress,
    
    // =========================================================================
    // BEAM CONFIGURATION
    // =========================================================================
    
    /** How the beam transitions in/out during lifecycle. */
    @JsonField(skipIfDefault = true)
    TransitionStyle beamTransition,
    
    /** Length of the beam at full extension. */
    @Range(ValueRange.POSITIVE) 
    @JsonField(skipIfDefault = true, defaultValue = "5.0")
    float beamLength,
    
    /** Ratio of beam base radius to orb radius (0.0-1.0). Base radius = orbRadius × beamRatio. */
    @Range(ValueRange.NORMALIZED)
    @JsonField(skipIfDefault = true, defaultValue = "0.8")
    float beamRatio,
    
    /** Taper of beam tip relative to base (0.0-1.5). Tip radius = baseRadius × beamTaper. 
     *  <1 = jet/cone, =1 = cylinder, >1 = flared */
    @Range(ValueRange.POSITIVE)
    @JsonField(skipIfDefault = true, defaultValue = "1.0")
    float beamTaper,
    
    /** Twist of beam in degrees (-360 to 360). Creates spiral effect. */
    @Range(ValueRange.DEGREES_FULL)
    @JsonField(skipIfDefault = true, defaultValue = "0.0")
    float beamTwist,
    
    /** How fast length scales with progress. 0=no scaling, 1=linear, >1=faster start. */
    @Range(ValueRange.POSITIVE)
    @JsonField(skipIfDefault = true, defaultValue = "1.0")
    float beamLengthIntensity,
    
    /** How fast width scales with progress. 0=no scaling, 1=linear, >1=faster start. */
    @Range(ValueRange.POSITIVE)
    @JsonField(skipIfDefault = true, defaultValue = "1.0")
    float beamWidthIntensity,
    
    /** Number of radial segments around the beam. */
    @Range(ValueRange.STEPS) 
    @JsonField(skipIfDefault = true, defaultValue = "16")
    int beamSegments,
    
    /** Number of segments along the beam length. */
    @Range(ValueRange.STEPS) 
    @JsonField(skipIfDefault = true, defaultValue = "8")
    int beamLengthSegments,
    
    /** Beam animation progress (0 = invisible, 1 = full). */
    @Range(ValueRange.NORMALIZED)
    @JsonField(skipIfDefault = true, defaultValue = "1.0")
    float beamProgress,
    
    // =========================================================================
    // BEAM TIP STYLE
    // =========================================================================
    
    /** Whether the beam tip has a dome (hemisphere) cap. If false, the tip is flat. */
    @JsonField(skipIfDefault = true, defaultValue = "true")
    boolean hasDomeTip,
    
    // =========================================================================
    // ALPHA GRADIENT CONFIGURATION
    // =========================================================================
    
    /** Orb alpha (standard opacity). */
    @Range(ValueRange.NORMALIZED)
    @JsonField(skipIfDefault = true, defaultValue = "1.0")
    float orbAlpha,
    
    /** Orb minimum alpha (floor for travel effects). */
    @Range(ValueRange.NORMALIZED)
    @JsonField(skipIfDefault = true, defaultValue = "0.0")
    float orbMinAlpha,
    
    /** Beam base alpha at orb connection. */
    @Range(ValueRange.NORMALIZED)
    @JsonField(skipIfDefault = true, defaultValue = "1.0")
    float beamBaseAlpha,
    
    /** Beam base minimum alpha (floor for travel effects). */
    @Range(ValueRange.NORMALIZED)
    @JsonField(skipIfDefault = true, defaultValue = "0.0")
    float beamBaseMinAlpha,
    
    /** Beam tip alpha at far end. */
    @Range(ValueRange.NORMALIZED)
    @JsonField(skipIfDefault = true, defaultValue = "0.8")
    float beamTipAlpha,
    
    /** Beam tip minimum alpha (floor for travel effects). */
    @Range(ValueRange.NORMALIZED)
    @JsonField(skipIfDefault = true, defaultValue = "0.0")
    float beamTipMinAlpha,
    
    // =========================================================================
    // ORIENTATION CONFIGURATION
    // =========================================================================
    
    /** Axis along which the beam extends. Default is +Z (forward/horizontal). */
    @JsonField(skipIfDefault = true)
    OrientationAxis orientationAxis,
    
    /** Offset distance from origin along the beam axis. Positive = further from player. */
    @JsonField(skipIfDefault = true, defaultValue = "0.0")
    float originOffset
    
) implements Shape {
    
    // =========================================================================
    // Constants & Presets
    // =========================================================================
    
    /** Default Kamehameha (classic blue energy beam). */
    public static final KamehamehaShape DEFAULT = new KamehamehaShape(
        // Orb: transition, radius, segments, progress
        TransitionStyle.FADE_AND_SCALE, 0.3f, 16, 1.0f,
        // Beam: transition, length, ratio, taper, twist, lengthIntensity, widthIntensity, segments, lengthSegments, progress
        TransitionStyle.SCALE, 5.0f, 0.8f, 0.8f, 0f, 1.0f, 1.0f, 16, 8, 1.0f,
        // Tip (true = dome/hemisphere, false = flat)
        true,
        // Alpha gradient
        1.0f, 0.2f,   // orbAlpha, orbMinAlpha
        1.0f, 0.1f,   // beamBaseAlpha, beamBaseMinAlpha
        0.6f, 0.0f,   // beamTipAlpha, beamTipMinAlpha
        // Orientation
        OrientationAxis.POS_Z, 0f
    );
    
    /** Thin jet beam (length extends fast, width stays narrow). */
    public static final KamehamehaShape JET_BEAM = new KamehamehaShape(
        TransitionStyle.SCALE, 0.15f, 12, 1.0f,
        TransitionStyle.SCALE, 8.0f, 0.5f, 0.3f, 0f, 2.0f, 0f, 8, 12, 1.0f,  // High length intensity, no width scaling
        false,  // No dome tip
        0.8f, 0.1f, 1.0f, 0.2f, 0.3f, 0.0f,
        OrientationAxis.POS_Z, 0f
    );
    
    /** Flared beam (width expands faster than length). */
    public static final KamehamehaShape FLARED_BEAM = new KamehamehaShape(
        TransitionStyle.FADE_AND_SCALE, 0.4f, 16, 1.0f,
        TransitionStyle.SCALE, 4.0f, 0.7f, 1.3f, 0f, 0.5f, 2.0f, 16, 6, 1.0f,  // Low length, high width intensity
        true,
        1.0f, 0.3f, 0.9f, 0.2f, 0.7f, 0.1f,
        OrientationAxis.POS_Z, 0f
    );
    
    /** Twisted beam (spiral effect). */
    public static final KamehamehaShape TWISTED_BEAM = new KamehamehaShape(
        TransitionStyle.FADE_AND_SCALE, 0.5f, 20, 1.0f,
        TransitionStyle.FADE_AND_SCALE, 3.0f, 0.9f, 1.0f, 180f, 1.0f, 1.0f, 16, 4, 1.0f,  // 180° twist
        true,
        1.0f, 0.4f, 1.0f, 0.3f, 0.8f, 0.2f,
        OrientationAxis.POS_Z, 0f
    );
    
    /** Charging state (orb only, no beam). */
    public static final KamehamehaShape CHARGING = DEFAULT.toBuilder()
        .orbProgress(0.5f)
        .beamProgress(0.0f)
        .build();
    
    public static KamehamehaShape defaults() { return DEFAULT; }
    
    // =========================================================================
    // Shape Interface
    // =========================================================================
    
    @Override
    public String getType() {
        return "kamehameha";
    }
    
    @Override
    public Vector3f getBounds() {
        float effectiveBeamLength = effectiveBeamLength();
        float maxRadius = Math.max(effectiveOrbRadius(), Math.max(effectiveBeamBaseRadius(), effectiveBeamTipRadius()));
        return new Vector3f(maxRadius * 2, effectiveBeamLength + effectiveOrbRadius() * 2, maxRadius * 2);
    }
    
    @Override
    public CellType primaryCellType() {
        return CellType.QUAD;
    }
    
    @Override
    public Map<String, CellType> getParts() {
        var parts = new java.util.LinkedHashMap<String, CellType>();
        
        // Orb parts
        parts.put("orb", CellType.QUAD);
        
        // Beam parts (only if beam has length)
        if (beamLength > 0) {
            parts.put("beam", CellType.QUAD);
            parts.put("beamTip", CellType.QUAD);
        }
        
        return parts;
    }
    
    @Override
    public float getRadius() {
        return Math.max(orbRadius, effectiveBeamBaseRadius());
    }
    
    // =========================================================================
    // Computed Properties (for rendering)
    // =========================================================================
    
    /** Effective orb radius after applying progress and transition. */
    public float effectiveOrbRadius() {
        if (orbTransition == null || !orbTransition.affectsScale()) return orbRadius;
        return orbRadius * orbProgress;
    }
    
    /** 
     * Effective orb alpha after applying progress and transition.
     * Interpolates from orbMinAlpha to orbAlpha based on progress.
     */
    public float effectiveOrbAlpha() {
        if (orbTransition == null || !orbTransition.affectsAlpha()) return orbAlpha;
        // Interpolate from min to max based on progress
        return orbMinAlpha + (orbAlpha - orbMinAlpha) * orbProgress;
    }
    
    /**
     * Applies intensity curve to progress.
     * @param progress Raw 0-1 progress
     * @param intensity Curve intensity (0=no effect, 1=linear, >1=faster start)
     * @return Curved progress value
     */
    private float applyIntensity(float progress, float intensity) {
        if (intensity <= 0) return 1.0f;  // No scaling - always full
        if (intensity == 1.0f) return progress;  // Linear
        return (float) Math.pow(progress, 1.0 / intensity);
    }
    
    /** 
     * Effective beam length after applying progress and intensity.
     * Note: beamLength=0 is treated as infinity (renders to view distance).
     */
    public float effectiveBeamLength() {
        // Length 0 = infinity (large value for "infinite" beam)
        float baseLength = beamLength <= 0 ? 1000f : beamLength;
        if (beamTransition == null || !beamTransition.affectsScale()) return baseLength;
        float lengthProgress = applyIntensity(beamProgress, beamLengthIntensity);
        return baseLength * lengthProgress;
    }
    
    /** 
     * Effective beam base radius - ALWAYS proportional to orb.
     * Base radius = effectiveOrbRadius × beamRatio × widthScale
     */
    public float effectiveBeamBaseRadius() {
        float baseR = effectiveOrbRadius() * beamRatio;
        if (beamTransition == null || !beamTransition.affectsScale()) return baseR;
        float widthProgress = applyIntensity(beamProgress, beamWidthIntensity);
        return baseR * widthProgress;
    }
    
    /** 
     * Effective beam tip radius - derived from base radius and taper.
     * Tip radius = effectiveBeamBaseRadius × beamTaper
     */
    public float effectiveBeamTipRadius() {
        return effectiveBeamBaseRadius() * beamTaper;
    }
    
    /** 
     * Effective beam base alpha after applying progress and both intensity modes.
     * - widthIntensity: UNIFORM fade (how fast whole beam fades)
     * - lengthIntensity: PROGRESSIVE wipe (base leads, tip follows)
     */
    public float effectiveBeamBaseAlpha() {
        if (beamTransition == null || !beamTransition.affectsAlpha()) return beamBaseAlpha;
        
        // Width intensity = UNIFORM fade (affects whole beam equally)
        float uniformProgress = applyIntensity(beamProgress, beamWidthIntensity);
        
        // Length intensity = PROGRESSIVE wipe (base leads tip)
        // Calculate wipe position - how far along the beam the "fade front" has traveled
        float wipePosition = applyIntensity(beamProgress, beamLengthIntensity);
        
        // Base is at position 0, so it becomes visible first
        // baseT = how much of the base is "revealed" by the wipe
        float baseT = Math.min(1f, wipePosition * 2f);  // Reaches 1 at 50% wipe position
        
        // Combine: uniform overall fade * progressive wipe
        float combinedT = uniformProgress * baseT;
        
        return beamBaseMinAlpha + (beamBaseAlpha - beamBaseMinAlpha) * combinedT;
    }
    
    /** 
     * Effective beam tip alpha after applying progress and both intensity modes.
     * - widthIntensity: UNIFORM fade (how fast whole beam fades)
     * - lengthIntensity: PROGRESSIVE wipe (base leads, tip follows)
     */
    public float effectiveBeamTipAlpha() {
        if (beamTransition == null || !beamTransition.affectsAlpha()) return beamTipAlpha;
        
        // Width intensity = UNIFORM fade (affects whole beam equally)
        float uniformProgress = applyIntensity(beamProgress, beamWidthIntensity);
        
        // Length intensity = PROGRESSIVE wipe (tip follows base)
        // Calculate wipe position
        float wipePosition = applyIntensity(beamProgress, beamLengthIntensity);
        
        // Tip is at position 1, so it becomes visible last
        // tipT = how much of the tip is "revealed" by the wipe
        float tipT = Math.max(0f, (wipePosition - 0.5f) * 2f);  // Starts at 50%, reaches 1 at 100%
        
        // Combine: uniform overall fade * progressive wipe
        float combinedT = uniformProgress * tipT;
        
        return beamTipMinAlpha + (beamTipAlpha - beamTipMinAlpha) * combinedT;
    }
    
    /** @deprecated Use effectiveBeamBaseAlpha() instead */
    @Deprecated
    public float effectiveBeamAlpha() {
        return effectiveBeamBaseAlpha();
    }
    
    /** Whether the orb is currently visible. */
    public boolean isOrbVisible() {
        return orbProgress > 0.001f && orbRadius > 0;
    }
    
    /** Whether the beam is currently visible. beamLength=0 means infinity, so still visible. */
    public boolean isBeamVisible() {
        return beamProgress > 0.001f;  // beamLength=0 means infinity, not invisible
    }
    
    // =========================================================================
    // Combined Progress (UX Helper)
    // =========================================================================
    
    /**
     * Gets the combined progress value [0, 2].
     * <ul>
     *   <li>0.0 - 1.0: Orb charging (beam invisible)</li>
     *   <li>1.0 - 2.0: Beam extending (orb at full)</li>
     * </ul>
     */
    public float combinedProgress() {
        // If orb isn't full yet, progress is just orbProgress
        if (orbProgress < 1.0f) {
            return orbProgress;
        }
        // Orb is full, add beam progress
        return 1.0f + beamProgress;
    }
    
    /**
     * Creates a new shape with the combined progress value applied.
     * <ul>
     *   <li>0.0 - 1.0: Sets orbProgress, beamProgress = 0</li>
     *   <li>1.0 - 2.0: Sets orbProgress = 1, beamProgress = value - 1</li>
     * </ul>
     * 
     * @param combined Progress value from 0 to 2
     * @return New shape with appropriate orb/beam progress
     */
    public KamehamehaShape withCombinedProgress(float combined) {
        combined = Math.max(0f, Math.min(2f, combined));
        if (combined <= 1.0f) {
            return toBuilder().orbProgress(combined).beamProgress(0f).build();
        } else {
            return toBuilder().orbProgress(1f).beamProgress(combined - 1f).build();
        }
    }
    

    
    // =========================================================================
    // JSON
    // =========================================================================
    
    
    
    
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    
    public Builder toBuilder() {
        return new Builder()
            // Orb
            .orbTransition(orbTransition).orbRadius(orbRadius)
            .orbSegments(orbSegments).orbProgress(orbProgress)
            // Beam
            .beamTransition(beamTransition)
            .beamLength(beamLength).beamRatio(beamRatio).beamTaper(beamTaper).beamTwist(beamTwist)
            .beamLengthIntensity(beamLengthIntensity).beamWidthIntensity(beamWidthIntensity)
            .beamSegments(beamSegments).beamLengthSegments(beamLengthSegments).beamProgress(beamProgress)
            // Tip
            .hasDomeTip(hasDomeTip)
            // Alpha gradient
            .orbAlpha(orbAlpha).orbMinAlpha(orbMinAlpha)
            .beamBaseAlpha(beamBaseAlpha).beamBaseMinAlpha(beamBaseMinAlpha)
            .beamTipAlpha(beamTipAlpha).beamTipMinAlpha(beamTipMinAlpha)
            // Orientation
            .orientationAxis(orientationAxis).originOffset(originOffset);
    }
    
    public static class Builder {
        // Orb
        private TransitionStyle orbTransition = TransitionStyle.FADE_AND_SCALE;
        private float orbRadius = 0.3f;
        private int orbSegments = 16;
        private float orbProgress = 1.0f;
        // Beam
        private TransitionStyle beamTransition = TransitionStyle.SCALE;
        private float beamLength = 5.0f;
        private float beamRatio = 0.8f;
        private float beamTaper = 1.0f;
        private float beamTwist = 0.0f;
        private float beamLengthIntensity = 1.0f;
        private float beamWidthIntensity = 1.0f;
        private int beamSegments = 16;
        private int beamLengthSegments = 8;
        private float beamProgress = 1.0f;
        // Tip
        private boolean hasDomeTip = true;
        // Alpha gradient
        private float orbAlpha = 1.0f;
        private float orbMinAlpha = 0.0f;
        private float beamBaseAlpha = 1.0f;
        private float beamBaseMinAlpha = 0.0f;
        private float beamTipAlpha = 1.0f;
        private float beamTipMinAlpha = 0.0f;
        // Orientation
        private OrientationAxis orientationAxis = OrientationAxis.POS_Z;
        private float originOffset = 0.0f;
        
        // Orb setters
        public Builder orbTransition(TransitionStyle v) { this.orbTransition = v != null ? v : TransitionStyle.FADE_AND_SCALE; return this; }
        public Builder orbRadius(float v) { this.orbRadius = Math.max(0.01f, v); return this; }
        public Builder orbSegments(int v) { this.orbSegments = Math.max(4, v); return this; }
        public Builder orbProgress(float v) { this.orbProgress = Math.max(0, Math.min(1, v)); return this; }
        
        // Beam setters
        public Builder beamTransition(TransitionStyle v) { this.beamTransition = v != null ? v : TransitionStyle.SCALE; return this; }
        public Builder beamLength(float v) { this.beamLength = Math.max(0, v); return this; }
        public Builder beamRatio(float v) { this.beamRatio = Math.max(0.05f, Math.min(1.0f, v)); return this; }
        public Builder beamTaper(float v) { this.beamTaper = Math.max(0.0f, Math.min(1.5f, v)); return this; }
        public Builder beamTwist(float v) { this.beamTwist = Math.max(-360f, Math.min(360f, v)); return this; }
        public Builder beamLengthIntensity(float v) { this.beamLengthIntensity = Math.max(0f, Math.min(3f, v)); return this; }
        public Builder beamWidthIntensity(float v) { this.beamWidthIntensity = Math.max(0f, Math.min(3f, v)); return this; }
        public Builder beamSegments(int v) { this.beamSegments = Math.max(4, v); return this; }
        public Builder beamLengthSegments(int v) { this.beamLengthSegments = Math.max(1, v); return this; }
        public Builder beamProgress(float v) { this.beamProgress = Math.max(0, Math.min(1, v)); return this; }
        
        // Tip setter
        public Builder hasDomeTip(boolean v) { this.hasDomeTip = v; return this; }
        
        // Alpha gradient setters
        public Builder orbAlpha(float v) { this.orbAlpha = Math.max(0, Math.min(1, v)); return this; }
        public Builder orbMinAlpha(float v) { this.orbMinAlpha = Math.max(0, Math.min(1, v)); return this; }
        public Builder beamBaseAlpha(float v) { this.beamBaseAlpha = Math.max(0, Math.min(1, v)); return this; }
        public Builder beamBaseMinAlpha(float v) { this.beamBaseMinAlpha = Math.max(0, Math.min(1, v)); return this; }
        public Builder beamTipAlpha(float v) { this.beamTipAlpha = Math.max(0, Math.min(1, v)); return this; }
        public Builder beamTipMinAlpha(float v) { this.beamTipMinAlpha = Math.max(0, Math.min(1, v)); return this; }
        
        // Orientation setters
        public Builder orientationAxis(OrientationAxis v) { this.orientationAxis = v != null ? v : OrientationAxis.POS_Z; return this; }
        public Builder originOffset(float v) { this.originOffset = v; return this; }
        
        public KamehamehaShape build() {
            return new KamehamehaShape(
                orbTransition, orbRadius, orbSegments, orbProgress,
                beamTransition, beamLength, beamRatio, beamTaper, beamTwist,
                beamLengthIntensity, beamWidthIntensity,
                beamSegments, beamLengthSegments, beamProgress,
                hasDomeTip,
                orbAlpha, orbMinAlpha, beamBaseAlpha, beamBaseMinAlpha, beamTipAlpha, beamTipMinAlpha,
                orientationAxis, originOffset
            );
        }
    }
}
