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
package net.cyberpunk042.mcshaders.core.appearance;

import java.util.Random;

/**
 * Context for per-vertex color calculation in tessellators.
 * 
 * <p>Passed to tessellators when ColorMode requires per-vertex coloring
 * (MESH_GRADIENT, MESH_RAINBOW, RANDOM).</p>
 */
public record ColorContext(
    ColorMode mode,
    int primaryColor,
    int secondaryColor,
    ColorSet colorSet,
    GradientDirection direction,
    ColorDistribution distribution,
    float colorBlend,
    float alpha,        // Alpha value 0-1 to apply to all calculated colors
    float timePhase,
    float time,
    float shapeRadius,
    float shapeHeight
) {
    // Random generator seeded per-frame for consistent random colors within a frame
    private static final ThreadLocal<Random> RANDOM = ThreadLocal.withInitial(() -> new Random());
    
    /**
     * Creates a ColorContext from an Appearance and render parameters.
     */
    public static ColorContext from(Appearance appearance, int resolvedPrimary, int resolvedSecondary, 
                                    float time, float shapeRadius, float shapeHeight) {
        // Get alpha from appearance (use max of AlphaRange)
        float alpha = 1.0f;
        if (appearance.alpha() != null) {
            alpha = appearance.alpha().max();
        }
        
        return new ColorContext(
            appearance.effectiveColorMode(),
            resolvedPrimary,
            resolvedSecondary,
            appearance.effectiveColorSet(),
            appearance.effectiveDirection(),
            appearance.effectiveDistribution(),
            appearance.colorBlend(),
            alpha,
            appearance.timePhase(),
            time,
            shapeRadius,
            shapeHeight
        );
    }
    
    /**
     * Creates a simple context for solid color (GRADIENT with blend=0).
     */
    public static ColorContext solid(int color) {
        return new ColorContext(
            ColorMode.GRADIENT, color, color, 
            ColorSet.RAINBOW, GradientDirection.Y_AXIS, ColorDistribution.GRADIENT,
            0f, 1f, 0f, 0f, 1f, 1f
        );
    }
    
    /**
     * Whether this context requires per-vertex color calculation.
     */
    public boolean isPerVertex() {
        return mode.isPerVertex();
    }
    
    /**
     * Calculate color for a vertex at the given position.
     * 
     * @param x Vertex X position (local coordinates)
     * @param y Vertex Y position
     * @param z Vertex Z position
     * @param cellIndex Index of the cell (quad/triangle) this vertex belongs to
     * @return ARGB color for this vertex (with alpha applied)
     */
    public int calculateColor(float x, float y, float z, int cellIndex) {
        int color = switch (mode) {
            case GRADIENT -> blendColors(primaryColor, secondaryColor, colorBlend);
            case CYCLING -> primaryColor; // Already handled in renderer
            case MESH_GRADIENT -> calculateMeshGradient(x, y, z, cellIndex);
            case MESH_RAINBOW -> calculateMeshRainbow(x, y, z, cellIndex);
            case RANDOM -> calculateRandom(cellIndex);
            case HEAT_MAP -> calculateHeatMap(x, y, z);
            case RANDOM_PULSE -> calculateRandomPulse(cellIndex);
            case BREATHE -> calculateBreathe();
            case REACTIVE -> calculateReactive(x, y, z);
        };
        
        // Apply alpha to the calculated color
        return applyAlpha(color, alpha);
    }
    
    /**
     * Apply alpha multiplier to a color.
     */
    private static int applyAlpha(int color, float alpha) {
        int a = (int)(((color >> 24) & 0xFF) * alpha);
        return (color & 0x00FFFFFF) | (a << 24);
    }
    
    /**
     * Calculate color for MESH_GRADIENT mode.
     * Interpolates from primary to (primary→secondary blended by colorBlend) based on position.
     */
    private int calculateMeshGradient(float x, float y, float z, int cellIndex) {
        float t;
        
        switch (distribution) {
            case INDEXED -> {
                // Each cell gets a color based on its index (stepped pattern)
                t = (cellIndex * 0.618033988749895f) % 1f;
            }
            case RANDOM -> {
                // Each cell gets a random t value (animated by time)
                long seed = cellIndex * 31L + (long)(timePhase * 1000) + (long)(time / 40f);
                RANDOM.get().setSeed(seed);
                t = RANDOM.get().nextFloat();
            }
            default -> {
                // GRADIENT: Smooth gradient based on vertex position
                t = direction.calculateT(x, y, z, shapeRadius, shapeHeight);
            }
        }
        
        // Add time-based animation
        // time/20f = animation progress (~1 cycle per second), timePhase = starting offset
        // Use modulo 2 to keep in ping-pong range (0-2)
        float animOffset = (time / 20f + timePhase) % 2f;
        if (animOffset < 0) animOffset += 2f;  // Handle negative time
        
        t = t + animOffset;
        // Ping-pong: fold values outside [0,1] back into range smoothly
        // Use modulo first to handle large values
        t = t % 2f;
        if (t < 0) t += 2f;
        if (t > 1f) {
            t = 2f - t; // Fold back: 1.5 -> 0.5, 2.0 -> 0.0
        }
        t = Math.max(0f, Math.min(1f, t));
        
        // Blend from primary to secondary based on t
        // colorBlend controls the maximum extent (1.0 = full secondary at t=1)
        float effectiveBlend = t * colorBlend;
        return blendColors(primaryColor, secondaryColor, effectiveBlend);
    }
    
    /**
     * Calculate color for MESH_RAINBOW mode.
     * Uses ColorSet's spectrum - full HSB for RAINBOW, palette interpolation for others.
     */
    private int calculateMeshRainbow(float x, float y, float z, int cellIndex) {
        switch (distribution) {
            case INDEXED -> {
                // Each cell gets a color based on its index (stepped pattern)
                float t = (cellIndex * 0.618033988749895f) % 1f;
                t = (t + timePhase * time / 20f) % 1f;
                if (t < 0) t += 1f;
                return colorSet.interpolateSpectrum(t);
            }
            case RANDOM -> {
                // Each cell gets a random color, with smooth interpolation during phase changes
                // Use timePhase to determine which "generation" of colors we're in
                float animatedPhase = (timePhase * time / 20f) % 1f;
                if (animatedPhase < 0) animatedPhase += 1f;
                
                // Current color generation (floor of phase * some factor for visible changes)
                int generation = (int)(animatedPhase * 4); // 4 color changes per full cycle
                float blendFactor = (animatedPhase * 4) % 1f; // How far into transition
                
                // Get current color (stable seed based on cell + generation)
                long currentSeed = cellIndex * 31L + generation * 17L;
                RANDOM.get().setSeed(currentSeed);
                int currentColor = colorSet.random(RANDOM.get());
                
                // Get next color for smooth interpolation
                long nextSeed = cellIndex * 31L + (generation + 1) * 17L;
                RANDOM.get().setSeed(nextSeed);
                int nextColor = colorSet.random(RANDOM.get());
                
                // Smooth interpolation between current and next
                return blendColors(currentColor, nextColor, blendFactor);
            }
            default -> {
                // GRADIENT: Smooth spectrum based on vertex position
                float t = direction.calculateT(x, y, z, shapeRadius, shapeHeight);
                t = (t + timePhase * time / 20f) % 1f;
                if (t < 0) t += 1f;
                return colorSet.interpolateSpectrum(t);
            }
        }
    }
    
    /**
     * Calculate color for RANDOM mode.
     * Uses the selected ColorSet.
     */
    private int calculateRandom(int cellIndex) {
        switch (distribution) {
            case INDEXED -> {
                // Each cell gets a color based on index from the palette
                return colorSet.color(cellIndex);
            }
            case RANDOM -> {
                // Each cell gets a random color with smooth interpolation
                float animatedPhase = (timePhase * time / 20f) % 1f;
                if (animatedPhase < 0) animatedPhase += 1f;
                
                int generation = (int)(animatedPhase * 4);
                float blendFactor = (animatedPhase * 4) % 1f;
                
                long currentSeed = cellIndex * 31L + generation * 17L;
                RANDOM.get().setSeed(currentSeed);
                int currentColor = colorSet.random(RANDOM.get());
                
                long nextSeed = cellIndex * 31L + (generation + 1) * 17L;
                RANDOM.get().setSeed(nextSeed);
                int nextColor = colorSet.random(RANDOM.get());
                
                return blendColors(currentColor, nextColor, blendFactor);
            }
            default -> {
                // GRADIENT: Same random color for entire shape (smoothly animated)
                float animatedPhase = (timePhase * time / 20f) % 1f;
                if (animatedPhase < 0) animatedPhase += 1f;
                
                int generation = (int)(animatedPhase * 4);
                float blendFactor = (animatedPhase * 4) % 1f;
                
                RANDOM.get().setSeed(generation * 17L);
                int currentColor = colorSet.random(RANDOM.get());
                
                RANDOM.get().setSeed((generation + 1) * 17L);
                int nextColor = colorSet.random(RANDOM.get());
                
                return blendColors(currentColor, nextColor, blendFactor);
            }
        }
    }
    
    /**
     * Blend two ARGB colors.
     */
    private static int blendColors(int color1, int color2, float t) {
        t = Math.max(0, Math.min(1, t));
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        
        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        
        int a = (int)(a1 + (a2 - a1) * t);
        int r = (int)(r1 + (r2 - r1) * t);
        int g = (int)(g1 + (g2 - g1) * t);
        int b = (int)(b1 + (b2 - b1) * t);
        
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    
    /**
     * Convert HSB to ARGB.
     */
    private static int hsbToArgb(float hue, float saturation, float brightness) {
        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness);
        return 0xFF000000 | (rgb & 0x00FFFFFF); // Ensure full alpha
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // NEW COLOR MODES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Calculate color for HEAT_MAP mode.
     * Inner positions = hot colors (red/orange), outer = cold (blue/purple).
     */
    private int calculateHeatMap(float x, float y, float z) {
        // Distance from center (use y for rays since it's the t value 0-1)
        float t = direction.calculateT(x, y, z, shapeRadius, shapeHeight);
        
        // Add time-based animation
        t = (t + timePhase * time / 30f) % 1f;
        if (t < 0) t += 1f;
        
        // Heat map: blend from primary (hot/inner) to secondary (cold/outer)
        // Reverse so center (t=0) is hot = secondary, edge (t=1) is cold = primary
        return blendColors(secondaryColor, primaryColor, t);
    }
    
    /**
     * Calculate color for RANDOM_PULSE mode.
     * Occasional bursts of random colors from the color set.
     */
    private int calculateRandomPulse(int cellIndex) {
        // Animated phase - faster animation for visible effect
        float animPhase = (time / 10f + timePhase + cellIndex * 0.15f) % 1f;
        
        // Pulse happens in a wider window for more visibility
        float pulseWindow = 0.25f;  // 25% of cycle is "pulsing"
        float pulseFraction = animPhase % 0.5f;  // 2 pulses per cycle
        
        if (pulseFraction < pulseWindow) {
            // In pulse - show random color from set
            float blendFactor = pulseFraction / pulseWindow;  // Fade in/out
            blendFactor = 1f - Math.abs(2f * blendFactor - 1f);  // Triangle wave 0→1→0
            
            int generation = (int)(time / 3f) + cellIndex;
            RANDOM.get().setSeed(generation * 31L);
            int pulseColor = colorSet.random(RANDOM.get());
            
            return blendColors(primaryColor, pulseColor, blendFactor);
        }
        
        return primaryColor;  // Base color when not pulsing
    }
    
    /**
     * Calculate color for BREATHE mode.
     * Smooth brightness/saturation pulsing (breathing effect).
     */
    private int calculateBreathe() {
        // Smooth sine wave oscillation - faster for visible effect
        float breathePhase = (time / 20f + timePhase) % 1f;
        float intensity = 0.5f + 0.5f * (float)Math.sin(breathePhase * 2 * Math.PI);
        
        // Extract HSB from primary color
        int r = (primaryColor >> 16) & 0xFF;
        int g = (primaryColor >> 8) & 0xFF;
        int b = primaryColor & 0xFF;
        
        float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
        
        // Modulate brightness (keep hue and saturation)
        float minBrightness = 0.2f;  // Darker minimum for more visible pulse
        float maxBrightness = 1.0f;
        float newBrightness = minBrightness + (maxBrightness - minBrightness) * intensity;
        
        return hsbToArgb(hsb[0], hsb[1], newBrightness);
    }
    
    /**
     * Calculate color for REACTIVE mode.
     * Color responds to position dynamically (wave-like effect).
     */
    private int calculateReactive(float x, float y, float z) {
        // Position-based wave pattern
        float t = direction.calculateT(x, y, z, shapeRadius, shapeHeight);
        
        // Traveling wave: position + time creates moving pattern
        float wavePhase = (t + time / 20f + timePhase) % 1f;
        if (wavePhase < 0) wavePhase += 1f;
        
        // Sine wave creates smooth oscillation
        float wave = 0.5f + 0.5f * (float)Math.sin(wavePhase * 4 * Math.PI);
        
        // Blend between primary and secondary based on wave
        return blendColors(primaryColor, secondaryColor, wave);
    }
}
