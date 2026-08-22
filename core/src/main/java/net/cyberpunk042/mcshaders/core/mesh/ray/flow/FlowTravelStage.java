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
package net.cyberpunk042.mcshaders.core.mesh.ray.flow;

import net.cyberpunk042.mcshaders.core.animation.RayFlowConfig;
import net.cyberpunk042.mcshaders.core.energy.EnergyTravel;
import net.cyberpunk042.mcshaders.core.energy.TravelBlendMode;

/**
 * Computes travel alpha for per-vertex modulation.
 * 
 * <p>Travel modes create moving effects along the ray by modulating per-vertex alpha.
 * The travel phase (position) is computed here; actual vertex alpha is computed
 * during tessellation using computeTravelAlpha(t, travelPhase).</p>
 * 
 * @see EnergyTravel
 */
public final class FlowTravelStage implements FlowStage {
    
    public static final FlowTravelStage INSTANCE = new FlowTravelStage();
    
    private FlowTravelStage() {}
    
    @Override
    public AnimationState process(AnimationState state, FlowContext ctx) {
        RayFlowConfig config = ctx.config();
        if (config == null || !config.hasTravel()) {
            return state;
        }
        
        EnergyTravel travelMode = config.effectiveTravel();
        if (travelMode == EnergyTravel.NONE) {
            return state;
        }
        
        // Travel phase is stored separately - not combined with base phase
        float travelSpeed = Math.max(0.1f, config.travelSpeed());
        float travelPhase = (ctx.time() * travelSpeed * 0.3f) % 1.0f;
        if (travelPhase < 0) travelPhase += 1.0f;
        
        return state.withTravelPhase(travelPhase);
    }
    
    /**
     * Compute per-vertex travel alpha based on vertex position.
     * 
     * <p>This static method is called during tessellation to get the alpha
     * modifier for a specific vertex based on its position along the ray.</p>
     * 
     * @param t Vertex position along axis (0 = base, 1 = tip)
     * @param mode EnergyTravel type
     * @param phase Current animation phase (0-1)
     * @param chaseCount Number of chase particles
     * @param chaseWidth Width of each particle
     * @return Alpha multiplier (0-1)
     */
    public static float computeTravelAlpha(float t, EnergyTravel mode, 
            float phase, int chaseCount, float chaseWidth) {
        
        if (mode == null || mode == EnergyTravel.NONE) {
            return 1.0f;
        }
        
        final int count = Math.max(1, chaseCount);
        final float width = Math.max(0.05f, chaseWidth);
        
        return switch (mode) {
            case NONE -> 1.0f;
            
            // Unidirectional Chase - animates from base to tip
            case CHASE -> {
                float spacing = 1f / count;
                float maxAlpha = 0f;
                for (int i = 0; i < count; i++) {
                    float center = (phase + i * spacing) % 1f;
                    float dist = Math.min(Math.abs(t - center), Math.min(
                        Math.abs(t - center - 1f), Math.abs(t - center + 1f)));
                    if (dist <= width / 2f) {
                        float falloff = 1f - (dist / (width / 2f));
                        maxAlpha = Math.max(maxAlpha, falloff * falloff);
                    }
                }
                yield maxAlpha;
            }
            
            case REVERSE_CHASE -> {
                float reverseOffset = 1f - phase;
                float spacing = 1f / count;
                for (int i = 0; i < count; i++) {
                    float center = (reverseOffset + i * spacing) % 1f;
                    float dist = Math.min(Math.abs(t - center), Math.min(
                        Math.abs(t - center - 1f), Math.abs(t - center + 1f)));
                    if (dist <= width / 2f) {
                        float falloff = 1f - (dist / (width / 2f));
                        yield falloff * falloff;
                    }
                }
                yield 0f;
            }
            
            // BIPOLAR CHASE - animates from CENTER OUTWARD toward both extremities
            case BIPOLAR, BIPOLAR_CHASE_SYNC -> {
                // Remap t: center (0.5) becomes 0, extremities (0 or 1) become 1
                float normalizedDist = Math.abs(t - 0.5f) * 2f;  // 0 at center, 1 at edges
                
                float spacing = 1f / count;
                float maxAlpha = 0f;
                for (int i = 0; i < count; i++) {
                    float center = (phase + i * spacing) % 1f;
                    float dist = Math.min(Math.abs(normalizedDist - center), Math.min(
                        Math.abs(normalizedDist - center - 1f), Math.abs(normalizedDist - center + 1f)));
                    if (dist <= width / 2f) {
                        float falloff = 1f - (dist / (width / 2f));
                        maxAlpha = Math.max(maxAlpha, falloff * falloff);
                    }
                }
                yield maxAlpha;
            }
            
            case BIPOLAR_CHASE_ALT, BIPOLAR_ALTERNATE -> {
                // Alternating bipolar - one side leads while other trails (ping-pong)
                float signedDist = t - 0.5f;
                
                // Determine phase offset based on which side
                float effectivePhase = signedDist >= 0 ? phase : (phase + 0.5f) % 1f;
                
                // Distance from center normalized to 0-1
                float normalizedDist = Math.abs(signedDist) * 2f;
                
                float spacing = 1f / count;
                float maxAlpha = 0f;
                for (int i = 0; i < count; i++) {
                    float center = (effectivePhase + i * spacing) % 1f;
                    float dist = Math.min(Math.abs(normalizedDist - center), 
                        Math.min(Math.abs(normalizedDist - center - 1f), 
                                 Math.abs(normalizedDist - center + 1f)));
                    if (dist <= width / 2f) {
                        float falloff = 1f - (dist / (width / 2f));
                        maxAlpha = Math.max(maxAlpha, falloff * falloff);
                    }
                }
                yield maxAlpha;
            }
            
            // Unidirectional Scroll
            case SCROLL -> {
                float scrolledT = (t + phase) % 1f;
                yield 1f - Math.abs(scrolledT - 0.5f) * 2f;
            }
            case REVERSE_SCROLL -> {
                float scrolledT = (t - phase + 1f) % 1f;
                yield 1f - Math.abs(scrolledT - 0.5f) * 2f;
            }
            
            // BIPOLAR SCROLL - scrolls from center outward
            case BIPOLAR_SCROLL_SYNC -> {
                float normalizedDist = Math.abs(t - 0.5f) * 2f;
                float scrolledDist = (normalizedDist + phase) % 1f;
                yield 1f - Math.abs(scrolledDist - 0.5f) * 2f;
            }
            case BIPOLAR_SCROLL_ALT -> {
                float signedDist = t - 0.5f;
                float effectivePhase = signedDist >= 0 ? phase : (phase + 0.5f) % 1f;
                float normalizedDist = Math.abs(signedDist) * 2f;
                float scrolledDist = (normalizedDist + effectivePhase) % 1f;
                yield 1f - Math.abs(scrolledDist - 0.5f) * 2f;
            }
            
            // Unidirectional Comet
            case COMET -> {
                float headPos = phase;
                float tailLength = Math.max(0.1f, width);
                float tailStart = headPos - tailLength;
                
                float distBehind;
                if (t > headPos) {
                    if (tailStart < 0) {
                        float wrappedTailStart = tailStart + 1f;
                        if (t >= wrappedTailStart) {
                            distBehind = (1f - t) + headPos;
                        } else {
                            yield 0f;
                        }
                    } else {
                        yield 0f;
                    }
                } else if (t >= tailStart || tailStart < 0) {
                    distBehind = headPos - t;
                } else {
                    yield 0f;
                }
                
                if (distBehind >= 0 && distBehind <= tailLength) {
                    float tailAlpha = 1f - (distBehind / tailLength);
                    yield tailAlpha * tailAlpha;
                } else {
                    yield 0f;
                }
            }
            case REVERSE_COMET -> {
                float headPos = 1f - phase;
                float tailLength = Math.max(0.1f, width);
                float tailEnd = headPos + tailLength;
                
                float distBehind;
                if (t < headPos) {
                    yield 0f;
                } else if (t <= tailEnd || tailEnd > 1f) {
                    distBehind = t - headPos;
                    if (distBehind > tailLength && tailEnd > 1f) {
                        if (t < tailEnd - 1f) {
                            distBehind = t + (1f - headPos);
                        } else {
                            yield 0f;
                        }
                    }
                } else {
                    yield 0f;
                }
                
                if (distBehind >= 0 && distBehind <= tailLength) {
                    float tailAlpha = 1f - (distBehind / tailLength);
                    yield tailAlpha * tailAlpha;
                } else {
                    yield 0f;
                }
            }
            
            // BIPOLAR COMET - two comets launch from center simultaneously
            case BIPOLAR_COMET_SYNC -> {
                float normalizedDist = Math.abs(t - 0.5f) * 2f;  // 0 at center, 1 at edges
                float headPos = phase;
                float tailLength = Math.max(0.1f, width);
                
                // Comet traveling outward from center
                float distBehind = headPos - normalizedDist;
                if (distBehind >= 0 && distBehind <= tailLength) {
                    float tailAlpha = 1f - (distBehind / tailLength);
                    yield tailAlpha * tailAlpha;
                }
                yield 0f;
            }
            case BIPOLAR_COMET_ALT -> {
                float signedDist = t - 0.5f;
                float effectivePhase = signedDist >= 0 ? phase : (phase + 0.5f) % 1f;
                float normalizedDist = Math.abs(signedDist) * 2f;
                
                float headPos = effectivePhase;
                float tailLength = Math.max(0.1f, width);
                
                float distBehind = headPos - normalizedDist;
                if (distBehind >= 0 && distBehind <= tailLength) {
                    float tailAlpha = 1f - (distBehind / tailLength);
                    yield tailAlpha * tailAlpha;
                }
                yield 0f;
            }
            
            // Unidirectional Spark
            case SPARK -> {
                float maxAlpha = 0f;
                for (int i = 0; i < count; i++) {
                    float sparkPhase = hash(i, (int)(phase * 10));
                    float sparkPos = hash(i + 1000, (int)(phase * 3));
                    if (sparkPhase > 0.5f) {
                        float dist = Math.abs(t - sparkPos);
                        if (dist < width) {
                            float spark = 1f - (dist / width);
                            maxAlpha = Math.max(maxAlpha, spark * spark);
                        }
                    }
                }
                yield maxAlpha;
            }
            
            // BIPOLAR SPARK - sparks emanate from center region
            case BIPOLAR_SPARK_SYNC -> {
                float normalizedDist = Math.abs(t - 0.5f) * 2f;
                float maxAlpha = 0f;
                for (int i = 0; i < count; i++) {
                    float sparkPhase = hash(i, (int)(phase * 10));
                    float sparkPos = hash(i + 1000, (int)(phase * 3));
                    if (sparkPhase > 0.5f) {
                        float dist = Math.abs(normalizedDist - sparkPos);
                        if (dist < width) {
                            float spark = 1f - (dist / width);
                            maxAlpha = Math.max(maxAlpha, spark * spark);
                        }
                    }
                }
                yield maxAlpha;
            }
            case BIPOLAR_SPARK_ALT -> {
                float signedDist = t - 0.5f;
                float effectivePhase = signedDist >= 0 ? phase : (phase + 0.5f) % 1f;
                float normalizedDist = Math.abs(signedDist) * 2f;
                
                float maxAlpha = 0f;
                for (int i = 0; i < count; i++) {
                    float sparkPhase = hash(i, (int)(effectivePhase * 10));
                    float sparkPos = hash(i + 1000, (int)(effectivePhase * 3));
                    if (sparkPhase > 0.5f) {
                        float dist = Math.abs(normalizedDist - sparkPos);
                        if (dist < width) {
                            float spark = 1f - (dist / width);
                            maxAlpha = Math.max(maxAlpha, spark * spark);
                        }
                    }
                }
                yield maxAlpha;
            }
            
            // Unidirectional Pulse
            case PULSE_WAVE -> {
                float waveWidth = Math.max(0.1f, width);
                float maxAlpha = 0f;
                for (int i = 0; i < count; i++) {
                    float waveCenter = (phase + (float)i / count) % 1f;
                    float dist = Math.min(Math.abs(t - waveCenter),
                                 Math.min(Math.abs(t - waveCenter - 1f),
                                          Math.abs(t - waveCenter + 1f)));
                    if (dist < waveWidth) {
                        float normalized = dist / waveWidth;
                        float pulse = (float) Math.exp(-normalized * normalized * 4);
                        maxAlpha = Math.max(maxAlpha, pulse);
                    }
                }
                yield maxAlpha;
            }
            case REVERSE_PULSE -> {
                float waveWidth = Math.max(0.1f, width);
                float maxAlpha = 0f;
                float reversePhase = 1f - phase;
                for (int i = 0; i < count; i++) {
                    float waveCenter = (reversePhase + (float)i / count) % 1f;
                    float dist = Math.min(Math.abs(t - waveCenter),
                                 Math.min(Math.abs(t - waveCenter - 1f),
                                          Math.abs(t - waveCenter + 1f)));
                    if (dist < waveWidth) {
                        float normalized = dist / waveWidth;
                        float pulse = (float) Math.exp(-normalized * normalized * 4);
                        maxAlpha = Math.max(maxAlpha, pulse);
                    }
                }
                yield maxAlpha;
            }
            
            // BIPOLAR PULSE - pulses emanate from center
            case BIPOLAR_PULSE_SYNC -> {
                float normalizedDist = Math.abs(t - 0.5f) * 2f;
                float waveWidth = Math.max(0.1f, width);
                float maxAlpha = 0f;
                for (int i = 0; i < count; i++) {
                    float waveCenter = (phase + (float)i / count) % 1f;
                    float dist = Math.min(Math.abs(normalizedDist - waveCenter),
                                 Math.min(Math.abs(normalizedDist - waveCenter - 1f),
                                          Math.abs(normalizedDist - waveCenter + 1f)));
                    if (dist < waveWidth) {
                        float normalized = dist / waveWidth;
                        float pulse = (float) Math.exp(-normalized * normalized * 4);
                        maxAlpha = Math.max(maxAlpha, pulse);
                    }
                }
                yield maxAlpha;
            }
            case BIPOLAR_PULSE_ALT -> {
                float signedDist = t - 0.5f;
                float effectivePhase = signedDist >= 0 ? phase : (phase + 0.5f) % 1f;
                float normalizedDist = Math.abs(signedDist) * 2f;
                
                float waveWidth = Math.max(0.1f, width);
                float maxAlpha = 0f;
                for (int i = 0; i < count; i++) {
                    float waveCenter = (effectivePhase + (float)i / count) % 1f;
                    float dist = Math.min(Math.abs(normalizedDist - waveCenter),
                                 Math.min(Math.abs(normalizedDist - waveCenter - 1f),
                                          Math.abs(normalizedDist - waveCenter + 1f)));
                    if (dist < waveWidth) {
                        float normalized = dist / waveWidth;
                        float pulse = (float) Math.exp(-normalized * normalized * 4);
                        maxAlpha = Math.max(maxAlpha, pulse);
                    }
                }
                yield maxAlpha;
            }
        };
    }
    
    /**
     * Simple hash function for deterministic pseudo-random values.
     */
    public static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        return (h & 0x7FFFFFFF) / (float)0x7FFFFFFF;
    }
    
    /**
     * Compute blended alpha using TravelBlendMode.
     * 
     * <p>This combines the raw travel alpha with base alpha according to the blend mode,
     * allowing for OVERLAY, ADDITIVE, and MODULATE effects instead of just REPLACE.</p>
     * 
     * @param baseAlpha The base vertex alpha before travel effect
     * @param t Vertex position along axis (0 = base, 1 = tip)
     * @param mode EnergyTravel type
     * @param phase Current animation phase (0-1)
     * @param chaseCount Number of chase particles
     * @param chaseWidth Width of each particle
     * @param blendMode How to blend travel with base alpha
     * @param intensity Blend intensity (for ADDITIVE and MODULATE modes)
     * @param minAlpha Minimum alpha floor (0-1), prevents going fully invisible
     * @return Final blended alpha, guaranteed to be at least minAlpha
     */
    public static float computeBlendedAlpha(float baseAlpha, float t, EnergyTravel mode,
            float phase, int chaseCount, float chaseWidth, 
            TravelBlendMode blendMode, float intensity, float minAlpha) {
        
        // Get raw travel alpha (0-1)
        float travelAlpha = computeTravelAlpha(t, mode, phase, chaseCount, chaseWidth);
        
        // Apply blend mode
        if (blendMode == null) {
            blendMode = TravelBlendMode.REPLACE;
        }
        
        float blendedAlpha = blendMode.blend(baseAlpha, travelAlpha, intensity);
        
        // Enforce minimum alpha floor
        return Math.max(minAlpha, blendedAlpha);
    }
    
    @Override
    public boolean shouldRun(FlowContext ctx) {
        if (ctx.config() == null) return false;
        return ctx.config().hasTravel();
    }
    
    @Override
    public String name() {
        return "TravelStage";
    }
}
