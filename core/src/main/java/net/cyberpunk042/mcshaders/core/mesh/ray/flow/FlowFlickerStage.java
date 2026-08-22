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
import net.cyberpunk042.mcshaders.core.energy.EnergyFlicker;

/**
 * Applies flicker effects to the animation.
 * 
 * <p>Implements all EnergyFlicker types:
 * SCINTILLATION, STROBE, FADE_PULSE, FLICKER, LIGHTNING, HEARTBEAT.</p>
 * 
 * @see EnergyFlicker
 */
public final class FlowFlickerStage implements FlowStage {
    
    public static final FlowFlickerStage INSTANCE = new FlowFlickerStage();
    
    private FlowFlickerStage() {}
    
    @Override
    public AnimationState process(AnimationState state, FlowContext ctx) {
        RayFlowConfig config = ctx.config();
        if (config == null || !config.hasFlicker()) {
            return state;
        }
        
        EnergyFlicker mode = config.effectiveFlicker();
        float intensity = config.flickerIntensity();
        float freq = config.flickerFrequency();
        
        if (intensity <= 0.001f) {
            return state;
        }
        
        // Compute flicker alpha based on mode
        float flickerAlpha = computeFlickerAlpha(
            mode, ctx.time(), ctx.rayIndex(), intensity, freq
        );
        
        return state.withFlickerAlpha(flickerAlpha);
    }
    
    /**
     * Compute flicker alpha based on EnergyFlicker mode.
     * 
     * <p>This static method can be called directly during rendering.</p>
     * 
     * @param mode EnergyFlicker type
     * @param time Current time
     * @param rayIndex Index of the ray
     * @param intensity Flicker intensity (0-1)
     * @param freq Flicker frequency
     * @return Alpha multiplier (0-1)
     */
    public static float computeFlickerAlpha(EnergyFlicker mode, float time, int rayIndex, 
                                       float intensity, float freq) {
        if (mode == null || mode == EnergyFlicker.NONE) {
            return 1f;
        }
        
        return switch (mode) {
            case NONE -> 1f;
            case SCINTILLATION -> {
                // Per-ray random flicker using hash (star-like twinkling)
                float hash = hash(rayIndex, (int)(time * freq));
                float flicker = 0.5f + 0.5f * hash;
                yield 1f - intensity * (1f - flicker);
            }
            case STROBE -> {
                // Synchronized on/off (hard edges)
                float wave = (float) Math.sin(time * freq * Math.PI * 2);
                yield wave > 0 ? 1f : (1f - intensity);
            }
            case FADE_PULSE -> {
                // Smooth sine wave fading (breathing effect)
                float wave = (float) Math.sin(time * freq * Math.PI * 2);
                float alpha = 0.5f + 0.5f * wave;
                yield (1f - intensity) + intensity * alpha;
            }
            case FLICKER -> {
                // Irregular noise-based flicker (like a dying light bulb)
                float h1 = hash(rayIndex, (int)(time * freq));
                float h2 = hash(rayIndex + 1000, (int)(time * freq * 0.7f));
                float h3 = hash(rayIndex + 2000, (int)(time * freq * 1.3f));
                float noise = (h1 + h2 * 0.5f + h3 * 0.25f) / 1.75f;
                yield (1f - intensity) + intensity * noise;
            }
            case LIGHTNING -> {
                // Spike with exponential decay (electric flash)
                float cycleTime = (time * freq) % 1.0f;
                float spikePoint = hash(rayIndex, (int)(time * freq / 2)) * 0.3f;
                
                if (cycleTime < spikePoint + 0.05f && cycleTime >= spikePoint) {
                    yield 1f;
                } else if (cycleTime > spikePoint + 0.05f && cycleTime < spikePoint + 0.3f) {
                    float elapsed = cycleTime - spikePoint - 0.05f;
                    float decay = (float) Math.exp(-elapsed * 15);
                    yield (1f - intensity) + intensity * decay;
                } else {
                    yield 1f - intensity * 0.8f;
                }
            }
            case HEARTBEAT -> {
                // Double-pulse pattern (like a heartbeat: lub-dub)
                float cycleTime = (time * freq * 0.5f) % 1.0f;
                float pulse = 0f;
                
                if (cycleTime < 0.15f) {
                    float t = cycleTime / 0.15f;
                    pulse = (float) Math.sin(t * Math.PI);
                } else if (cycleTime >= 0.2f && cycleTime < 0.35f) {
                    float t = (cycleTime - 0.2f) / 0.15f;
                    pulse = 0.7f * (float) Math.sin(t * Math.PI);
                }
                
                yield (1f - intensity) + intensity * pulse;
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
    
    @Override
    public boolean shouldRun(FlowContext ctx) {
        if (ctx.config() == null) return false;
        return ctx.config().hasFlicker();
    }
    
    @Override
    public String name() {
        return "FlickerStage";
    }
}
