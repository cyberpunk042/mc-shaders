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
package net.cyberpunk042.mcshaders.core.effect;

/**
 * Configuration for Horizon (rim lighting / Fresnel) effect.
 * 
 * <h2>What is Horizon Effect?</h2>
 * <p>The Horizon effect creates a glow at the edges of objects, like an
 * atmosphere seen from space. It's based on the Fresnel principle that
 * surfaces reflect more light at grazing angles. Uses include:</p>
 * <ul>
 *   <li>Energy shields and force fields</li>
 *   <li>Corona/atmosphere effects on celestial bodies</li>
 *   <li>Holographic outlines</li>
 *   <li>Ice, glass, and translucent materials</li>
 * </ul>
 * 
 * <h2>Parameters</h2>
 * <ul>
 *   <li><b>power</b> - Controls edge sharpness. Low values (1-2) give a soft
 *       diffuse glow; high values (5-10) give a sharp edge highlight.</li>
 *   <li><b>intensity</b> - Brightness of the rim. 0 = off, 1 = subtle, 2+ = bright.</li>
 *   <li><b>red/green/blue</b> - Color of the rim glow (0-1 range).</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>
 * // Default white rim
 * HorizonEffect rim = HorizonEffect.DEFAULT;
 * 
 * // Custom orange corona
 * HorizonEffect corona = new HorizonEffect(true, 2f, 3f, 1f, 0.5f, 0f);
 * 
 * // Preset effects
 * HorizonEffect ice = HorizonEffect.ice();
 * HorizonEffect fire = HorizonEffect.fire();
 * </pre>
 * 
 * @param enabled Whether the Horizon effect is active
 * @param power Edge sharpness (1.0 = soft, 10.0 = sharp)
 * @param intensity Brightness multiplier (0.0 = off, 5.0 = very bright)
 * @param red Red component of rim color (0.0 - 1.0)
 * @param green Green component of rim color (0.0 - 1.0)
 * @param blue Blue component of rim color (0.0 - 1.0)
 */
public record HorizonEffect(
    boolean enabled,
    float power,
    float intensity,
    float red,
    float green,
    float blue
) {
    // ═══════════════════════════════════════════════════════════════════════════
    // Constants
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Power range: minimum (soft glow) */
    public static final float MIN_POWER = 1.0f;
    
    /** Power range: maximum (sharp edge) */
    public static final float MAX_POWER = 10.0f;
    
    /** Intensity range: minimum */
    public static final float MIN_INTENSITY = 0.0f;
    
    /** Intensity range: maximum */
    public static final float MAX_INTENSITY = 5.0f;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Standard Presets
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** No Horizon effect (disabled). */
    public static final HorizonEffect NONE = new HorizonEffect(false, 3f, 0f, 1f, 1f, 1f);
    
    /** Default subtle white rim. */
    public static final HorizonEffect DEFAULT = new HorizonEffect(true, 3f, 1.5f, 1f, 1f, 1f);
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Themed Presets
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Orange corona glow - for suns and stars.
     * Low power for wide, soft glow.
     */
    public static HorizonEffect corona() {
        return new HorizonEffect(true, 2f, 3f, 1f, 0.5f, 0f);
    }
    
    /**
     * Blue ice glow - cold, crystalline appearance.
     * Higher power for sharper edges.
     */
    public static HorizonEffect ice() {
        return new HorizonEffect(true, 4f, 2f, 0.5f, 0.8f, 1f);
    }
    
    /**
     * Red-orange fire glow - for fiery objects.
     */
    public static HorizonEffect fire() {
        return new HorizonEffect(true, 3f, 2.5f, 1f, 0.3f, 0f);
    }
    
    /**
     * Cyan energy shield - sci-fi force field look.
     */
    public static HorizonEffect shield() {
        return new HorizonEffect(true, 4f, 2f, 0.2f, 0.8f, 1f);
    }
    
    /**
     * Purple void/dark energy.
     */
    public static HorizonEffect void_() {
        return new HorizonEffect(true, 3f, 2f, 0.5f, 0f, 0.8f);
    }
    
    /**
     * Green toxic/radiation glow.
     */
    public static HorizonEffect toxic() {
        return new HorizonEffect(true, 3f, 2.5f, 0.3f, 1f, 0.2f);
    }
    
    /**
     * White-blue eclipse corona.
     * Very wide, soft glow for celestial events.
     */
    public static HorizonEffect eclipse() {
        return new HorizonEffect(true, 1.5f, 4f, 0.9f, 0.95f, 1f);
    }
    
    /**
     * Pale moonlight glow.
     */
    public static HorizonEffect moonlight() {
        return new HorizonEffect(true, 2.5f, 1.5f, 0.8f, 0.85f, 0.95f);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Utility Methods
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a new effect with different color.
     */
    public HorizonEffect withColor(float r, float g, float b) {
        return new HorizonEffect(enabled, power, intensity, r, g, b);
    }
    
    /**
     * Creates a new effect with different power (edge sharpness).
     */
    public HorizonEffect withPower(float newPower) {
        return new HorizonEffect(enabled, newPower, intensity, red, green, blue);
    }
    
    /**
     * Creates a new effect with different intensity.
     */
    public HorizonEffect withIntensity(float newIntensity) {
        return new HorizonEffect(enabled, power, newIntensity, red, green, blue);
    }
    
    /**
     * Creates an enabled/disabled copy of this effect.
     */
    public HorizonEffect withEnabled(boolean newEnabled) {
        return new HorizonEffect(newEnabled, power, intensity, red, green, blue);
    }
    
    /**
     * Returns the color as a packed ARGB int (for GUI compatibility).
     */
    public int colorAsArgb() {
        int r = (int)(red * 255) & 0xFF;
        int g = (int)(green * 255) & 0xFF;
        int b = (int)(blue * 255) & 0xFF;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    
    /**
     * Creates a HorizonEffect from an ARGB color int.
     */
    public static HorizonEffect fromArgb(int argb, float power, float intensity) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        return new HorizonEffect(true, power, intensity, r, g, b);
    }
}

