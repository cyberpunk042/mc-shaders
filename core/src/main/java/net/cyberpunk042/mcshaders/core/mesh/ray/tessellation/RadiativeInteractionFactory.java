/*
 * Ported from the-virus-block-mc (net.cyberpunk042.client.visual.mesh), where it
 * is tessellation code with no Minecraft or graphics-API dependency: the whole
 * subtree imported nothing from net.minecraft or com.mojang.
 * Relicensed to MIT here by the author, per the engine/content split recorded in
 * docs/PORTING.md.
 *
 * The mod's logging system was replaced with core's Diag, which routes to
 * java.lang.System.Logger. The call sites are unchanged apart from the name.
 */
package net.cyberpunk042.mcshaders.core.mesh.ray.tessellation;

import net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction;

/**
 * Factory for computing clip ranges based on RadiativeInteraction and phase.
 * 
 * <p>This factory interprets the phase value (0-1) according to the 
 * RadiativeInteraction mode defined in the shape. It is the bridge between
 * the phase-driven animation system and the actual visible segment computation.</p>
 * 
 * <h2>How It Works</h2>
 * <ul>
 *   <li>Phase comes from animation (FlowPhaseStage)</li>
 *   <li>RadiativeInteraction comes from shape (RaysShape)</li>
 *   <li>This factory combines them to compute what segment is visible</li>
 * </ul>
 * 
 * <h2>Output</h2>
 * <p>Returns a {@link ClipRange} with:</p>
 * <ul>
 *   <li>start: 0-1 parametric start of visible segment</li>
 *   <li>end: 0-1 parametric end of visible segment</li>
 *   <li>scale: width/alpha multiplier</li>
 * </ul>
 * 
 * @see RadiativeInteraction
 * @see TessEdgeModeFactory
 */
public final class RadiativeInteractionFactory {
    
    private RadiativeInteractionFactory() {} // Utility class
    
    /**
     * Compute the clip range for a given RadiativeInteraction and phase.
     * 
     * @param mode The radiative interaction mode from RaysShape
     * @param phase The current animation phase (0-1)
     * @param segmentLength The visible segment length (0-1) from RaysShape
     * @return The computed clip range
     */
    public static ClipRange compute(RadiativeInteraction mode, float phase, float segmentLength) {
        return compute(mode, phase, segmentLength, false);
    }
    
    /**
     * Compute the clip range for a given RadiativeInteraction and phase.
     * 
     * @param mode The radiative interaction mode from RaysShape
     * @param phase The current animation phase (0-1)
     * @param segmentLength The visible segment length (0-1) from RaysShape
     * @param startFullLength If true: full ray at phase=0, slides out. If false: grows from 0.
     * @return The computed clip range
     */
    public static ClipRange compute(RadiativeInteraction mode, float phase, float segmentLength, boolean startFullLength) {
        if (mode == null) mode = RadiativeInteraction.NONE;
        
        return switch (mode) {
            case NONE -> ClipRange.FULL;
            case EMISSION -> computeEmission(phase, segmentLength, startFullLength);
            case ABSORPTION -> computeAbsorption(phase, segmentLength, startFullLength);
            case TRANSMISSION -> computeTransmission(phase, segmentLength);
            case OSCILLATION -> computeOscillation(phase);
            case RESONANCE -> computeResonance(phase);
            case REFLECTION -> computeReflection(phase, segmentLength);
            case SCATTERING -> computeScattering(phase, segmentLength);
        };
    }
    
    /**
     * EMISSION: Segment moves from inner (phase=0) to outer (phase=1).
     * 
     * If startFullLength=false: Ray grows from 0 at phase 0 to full at phase 1.
     * If startFullLength=true: Full ray visible at phase 0, slides out and disappears at phase 1.
     */
    private static ClipRange computeEmission(float phase, float segmentLength, boolean startFullLength) {
        if (startFullLength) {
            // Full ray at phase 0, slides out at phase 1
            // At phase 0: visible [0, 1]
            // At phase 1: visible [-1, 0] (i.e., nothing visible since clamped to [0,1])
            float start = phase;
            float end = phase + 1.0f;
            return new ClipRange(Math.max(0, start), Math.min(1, end), 1f);
        } else {
            // Segment travels from -segmentLength to 1.0
            float travelRange = 1.0f + segmentLength;
            float center = phase * travelRange - segmentLength / 2;
            float start = center - segmentLength / 2;
            float end = center + segmentLength / 2;
            return new ClipRange(Math.max(0, start), Math.min(1, end), 1f);
        }
    }
    
    /**
     * ABSORPTION: Segment moves from outer (phase=0) to inner (phase=1).
     * 
     * If startFullLength=false: Segment grows inward.
     * If startFullLength=true: Full ray visible at phase 0, slides in and disappears at phase 1.
     */
    private static ClipRange computeAbsorption(float phase, float segmentLength, boolean startFullLength) {
        if (startFullLength) {
            // Full ray at phase 0, slides inward at phase 1
            // Reverse: at phase 0 visible [0, 1], at phase 1 visible inward
            float end = 1.0f - phase;
            float start = end - 1.0f;
            return new ClipRange(Math.max(0, start), Math.min(1, end), 1f);
        } else {
            // Same as emission but reversed
            float travelRange = 1.0f + segmentLength;
            float center = 1.0f - (phase * travelRange - segmentLength / 2);
            float start = center - segmentLength / 2;
            float end = center + segmentLength / 2;
            return new ClipRange(Math.max(0, start), Math.min(1, end), 1f);
        }
    }
    
    /**
     * TRANSMISSION: Segment slides along the ray based on phase.
     * Energy passes through.
     */
    private static ClipRange computeTransmission(float phase, float segmentLength) {
        // Center of segment follows phase
        float start = phase - segmentLength / 2;
        float end = phase + segmentLength / 2;
        return new ClipRange(Math.max(0, start), Math.min(1, end), 1f);
    }
    
    /**
     * OSCILLATION: Whole ray pulses in/out.
     * Phase 0 = contracted, phase 0.5 = expanded, phase 1 = contracted.
     */
    private static ClipRange computeOscillation(float phase) {
        // Sine wave oscillation
        float scale = 0.5f + 0.5f * (float) Math.sin(phase * Math.PI * 2);
        return new ClipRange(0f, 1f, scale);
    }
    
    /**
     * RESONANCE: Ray grows then shrinks.
     * Phase 0 = nothing, phase 0.5 = full, phase 1 = nothing.
     */
    private static ClipRange computeResonance(float phase) {
        // Triangle wave: grows 0→0.5, shrinks 0.5→1
        float clipEnd = phase < 0.5f ? phase * 2 : 2 - phase * 2;
        return new ClipRange(0f, clipEnd, 1f);
    }
    
    /**
     * REFLECTION: Energy bounces back from the outer edge.
     * Phase 0-0.5 = outward, phase 0.5-1 = inward.
     */
    private static ClipRange computeReflection(float phase, float segmentLength) {
        // First half: emission, second half: absorption
        if (phase < 0.5f) {
            return computeEmission(phase * 2, segmentLength, false);
        } else {
            return computeAbsorption((phase - 0.5f) * 2, segmentLength, false);
        }
    }
    
    /**
     * SCATTERING: Energy disperses in multiple scattered segments.
     * Creates a fragmented/scattered appearance.
     */
    private static ClipRange computeScattering(float phase, float segmentLength) {
        // Multiple small scattered segments based on phase
        // For now, simple implementation - can be enhanced later
        float scatter = 0.5f + 0.5f * (float) Math.sin(phase * Math.PI * 6);
        return new ClipRange(0f, 1f, scatter);
    }
    
    /**
     * Represents the computed clip range for a ray segment.
     */
    public record ClipRange(float start, float end, float scale) {
        /** Full visibility - no clipping. */
        public static final ClipRange FULL = new ClipRange(0f, 1f, 1f);
        
        /** Nothing visible. */
        public static final ClipRange EMPTY = new ClipRange(0f, 0f, 0f);
        
        /**
         * Whether a given parametric position t is within the visible range.
         */
        public boolean contains(float t) {
            return t >= start && t <= end;
        }
        
        /**
         * Compute the alpha for a given parametric position.
         * Returns scale if within range, 0 otherwise.
         */
        public float alphaAt(float t) {
            return contains(t) ? scale : 0f;
        }
    }
}
