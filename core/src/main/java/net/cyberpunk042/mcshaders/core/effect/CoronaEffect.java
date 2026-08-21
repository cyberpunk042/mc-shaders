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

import net.cyberpunk042.mcshaders.core.serial.JsonField;

/**
 * Corona overlay effect for additive rim glow.
 * 
 * <p>Unlike {@link HorizonEffect} which modifies the material's fragment shader,
 * CoronaEffect renders a SECOND pass with additive blending, creating a glowing
 * halo around the edges of the geometry. This is ideal for:</p>
 * 
 * <ul>
 *   <li>Solar corona effects</li>
 *   <li>Energy shields with visible auras</li>
 *   <li>Atmospheric glow on planets</li>
 *   <li>Force field edges</li>
 * </ul>
 * 
 * <h2>Rendering Pipeline</h2>
 * <pre>
 * Pass 1: Base sphere with normal/horizon shader
 * Pass 2: Corona overlay (additive blend, rim-only output)
 * </pre>
 * 
 * <h2>Parameters</h2>
 * <ul>
 *   <li><b>power</b>: Edge sharpness (1=soft glow, 10=tight edge)</li>
 *   <li><b>intensity</b>: Glow brightness (0=off, 1=normal, 5=very bright)</li>
 *   <li><b>falloff</b>: How quickly glow fades from edge (0.1=slow, 1=fast)</li>
 *   <li><b>RGB</b>: Color of the corona glow</li>
 * </ul>
 * 
 * @param enabled Whether corona effect is active
 * @param power Edge sharpness (1.0 - 10.0)
 * @param intensity Glow brightness multiplier (0.0 - 5.0)
 * @param falloff Glow falloff rate (0.1 - 2.0)
 * @param red Red component (0.0 - 1.0)
 * @param green Green component (0.0 - 1.0)
 * @param blue Blue component (0.0 - 1.0)
 * 
 * @see HorizonEffect for single-pass rim lighting
 */
public record CoronaEffect(
    @JsonField(skipIfDefault = true) boolean enabled,
    @JsonField(skipIfDefault = true, defaultValue = "2") float power,
    @JsonField(skipIfDefault = true, defaultValue = "1") float intensity,
    @JsonField(skipIfDefault = true, defaultValue = "0.5") float falloff,
    @JsonField(skipIfDefault = true, defaultValue = "1") float red,
    @JsonField(skipIfDefault = true, defaultValue = "1") float green,
    @JsonField(skipIfDefault = true, defaultValue = "1") float blue,
    /** Offset of corona layer from surface (-1 to 1, default 0). Positive = outward. */
    @JsonField(skipIfDefault = true) float offset,
    /** Width/spread of the corona glow (0.1-3, default 1). Higher = wider glow band. */
    @JsonField(skipIfDefault = true, defaultValue = "1") float width
) {
    
    // =========================================================================
    // Constants
    // =========================================================================
    
    public static final float MIN_POWER = 1.0f;
    public static final float MAX_POWER = 10.0f;
    public static final float MIN_INTENSITY = 0.0f;
    public static final float MAX_INTENSITY = 5.0f;
    public static final float MIN_FALLOFF = 0.1f;
    public static final float MAX_FALLOFF = 2.0f;
    
    // =========================================================================
    // Standard Presets
    // =========================================================================
    
    /** Disabled corona effect. */
    public static final CoronaEffect NONE = new CoronaEffect(false, 2f, 1f, 0.5f, 1f, 1f, 1f, 0f, 1f);
    
    /** Default soft white corona. */
    public static final CoronaEffect DEFAULT = new CoronaEffect(true, 2f, 1f, 0.5f, 1f, 1f, 1f, 0f, 1f);
    
    // =========================================================================
    // Themed Presets - Astrophysical
    // =========================================================================
    
    /** Solar corona - intense orange/yellow glow. */
    public static final CoronaEffect SOLAR = new CoronaEffect(
        true, 1.5f, 2.5f, 0.3f,
        1f, 0.9f, 0.4f, 0f, 1f  // Warm yellow-orange
    );
    
    /** Blue star corona - hot blue glow. */
    public static final CoronaEffect BLUE_STAR = new CoronaEffect(
        true, 2f, 2f, 0.4f,
        0.3f, 0.6f, 1f, 0f, 1f  // Hot blue
    );
    
    /** Red dwarf corona - dim red glow. */
    public static final CoronaEffect RED_DWARF = new CoronaEffect(
        true, 3f, 0.8f, 0.6f,
        1f, 0.3f, 0.2f, 0f, 1f  // Deep red
    );
    
    /** Nebula corona - purple/magenta diffuse glow. */
    public static final CoronaEffect NEBULA = new CoronaEffect(
        true, 1.2f, 1.5f, 0.2f,
        0.8f, 0.3f, 1f, 0f, 1f  // Magenta-purple
    );
    
    // =========================================================================
    // Themed Presets - Energy/Magical
    // =========================================================================
    
    /** Energy shield - cyan electric glow. */
    public static final CoronaEffect ENERGY_SHIELD = new CoronaEffect(
        true, 3f, 1.8f, 0.5f,
        0.2f, 0.9f, 1f, 0f, 1f  // Electric cyan
    );
    
    /** Dark matter - inverted/purple-black glow. */
    public static final CoronaEffect DARK_MATTER = new CoronaEffect(
        true, 4f, 1.2f, 0.7f,
        0.4f, 0.1f, 0.6f, 0f, 1f  // Dark purple
    );
    
    /** Holy aura - soft golden glow. */
    public static final CoronaEffect HOLY = new CoronaEffect(
        true, 1.5f, 1.5f, 0.3f,
        1f, 0.95f, 0.7f, 0f, 1f  // Soft gold
    );
    
    /** Toxic corona - sickly green glow. */
    public static final CoronaEffect TOXIC = new CoronaEffect(
        true, 2.5f, 1.3f, 0.5f,
        0.3f, 1f, 0.2f, 0f, 1f  // Toxic green
    );
    
    /** Fire corona - flickering orange-red. */
    public static final CoronaEffect FIRE = new CoronaEffect(
        true, 2f, 2f, 0.4f,
        1f, 0.4f, 0.1f, 0f, 1f  // Fire orange
    );
    
    /** Ice corona - cold white-blue. */
    public static final CoronaEffect ICE = new CoronaEffect(
        true, 3f, 1f, 0.6f,
        0.8f, 0.95f, 1f, 0f, 1f  // Ice blue-white
    );
    
    // =========================================================================
    // Utility Methods
    // =========================================================================
    
    /**
     * Returns whether this effect should be applied.
     */
    public boolean isActive() {
        return enabled && intensity > 0.001f;
    }
    
    /**
     * Creates a modified copy with different intensity.
     */
    public CoronaEffect withIntensity(float newIntensity) {
        return new CoronaEffect(enabled, power, newIntensity, falloff, red, green, blue, offset, width);
    }
    
    /**
     * Creates a modified copy with different color.
     */
    public CoronaEffect withColor(float r, float g, float b) {
        return new CoronaEffect(enabled, power, intensity, falloff, r, g, b, offset, width);
    }
    
    /**
     * Creates a modified copy with different power/sharpness.
     */
    public CoronaEffect withPower(float newPower) {
        return new CoronaEffect(enabled, newPower, intensity, falloff, red, green, blue, offset, width);
    }
    
    /**
     * Creates a modified copy with different falloff.
     */
    public CoronaEffect withFalloff(float newFalloff) {
        return new CoronaEffect(enabled, power, intensity, newFalloff, red, green, blue, offset, width);
    }
    
    /**
     * Returns the hex color string (e.g., "#FF8800").
     */
    public String toHexColor() {
        int r = Math.clamp((int)(red * 255), 0, 255);
        int g = Math.clamp((int)(green * 255), 0, 255);
        int b = Math.clamp((int)(blue * 255), 0, 255);
        return String.format("#%02X%02X%02X", r, g, b);
    }
    
    @Override
    public String toString() {
        if (!enabled) return "CoronaEffect[disabled]";
        return String.format("CoronaEffect[power=%.1f, intensity=%.1f, falloff=%.1f, color=%s]",
            power, intensity, falloff, toHexColor());
    }
}
