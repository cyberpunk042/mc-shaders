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
package net.cyberpunk042.mcshaders.core.energy;

/**
 * Controls how travel effects blend with base vertex alpha.
 * 
 * <p>This determines whether the travel effect completely controls visibility
 * (REPLACE - current behavior) or modulates the existing alpha (OVERLAY, ADDITIVE, etc.).</p>
 * 
 * <h2>Blend Modes</h2>
 * <ul>
 *   <li><b>REPLACE:</b> Travel alpha replaces base alpha (0 where not lit)</li>
 *   <li><b>OVERLAY:</b> Spotlight effect - base stays visible, travel adds emphasis</li>
 *   <li><b>ADDITIVE:</b> Travel adds glow on top of base alpha</li>
 *   <li><b>MODULATE:</b> Base alpha dimmed by inverse of travel value</li>
 * </ul>
 * 
 * <h2>Formula Details</h2>
 * <pre>
 * REPLACE:   finalAlpha = travelAlpha
 * OVERLAY:   finalAlpha = baseAlpha * (0.3 + 0.7 * travelAlpha)
 * ADDITIVE:  finalAlpha = min(1.0, baseAlpha + travelAlpha * intensity)
 * MODULATE:  finalAlpha = baseAlpha * (1.0 - intensity + travelAlpha * intensity)
 * </pre>
 * 
 * @see EnergyTravel
 */
public enum TravelBlendMode {
    /** 
     * Travel alpha replaces base alpha completely.
     * 
     * <p>Current/default behavior - vertices are invisible (alpha=0)
     * except where the travel effect is active.</p>
     */
    REPLACE("Replace", "Travel replaces visibility"),
    
    /**
     * Spotlight/emphasis effect.
     * 
     * <p>Base shape stays visible at reduced alpha (30%), 
     * travel effect brings it to full visibility (100%).
     * Formula: baseAlpha * (0.3 + 0.7 * travelAlpha)</p>
     */
    OVERLAY("Overlay", "Spotlight emphasis"),
    
    /**
     * Travel adds glow on top of base.
     * 
     * <p>Base shape is fully visible, travel effect adds 
     * brightness. Good for energy bursts.
     * Formula: min(1.0, baseAlpha + travelAlpha * intensity)</p>
     */
    ADDITIVE("Additive", "Glow adds to base"),
    
    /**
     * Base alpha is modulated by travel.
     * 
     * <p>Dimmer where travel is low, brighter where high.
     * Formula: baseAlpha * (1 - intensity + travelAlpha * intensity)</p>
     */
    MODULATE("Modulate", "Dims base by travel");
    
    private final String displayName;
    private final String description;
    
    TravelBlendMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String displayName() {
        return displayName;
    }
    
    public String description() {
        return description;
    }
    
    /**
     * Computes final alpha by blending base alpha with travel alpha.
     * 
     * @param baseAlpha The base vertex alpha (0-1)
     * @param travelAlpha The travel effect alpha (0-1) from EnergyTravel computation
     * @param intensity How much the blend mode affects the result (0-1)
     * @return Final blended alpha (0-1)
     */
    public float blend(float baseAlpha, float travelAlpha, float intensity) {
        return switch (this) {
            case REPLACE -> travelAlpha;
            case OVERLAY -> baseAlpha * (0.3f + 0.7f * travelAlpha);
            case ADDITIVE -> Math.min(1.0f, baseAlpha + travelAlpha * intensity);
            case MODULATE -> baseAlpha * (1.0f - intensity + travelAlpha * intensity);
        };
    }
    
    /**
     * Parses from string, case-insensitive.
     */
    public static TravelBlendMode fromString(String value) {
        if (value == null) return REPLACE;
        try {
            return valueOf(value.toUpperCase().replace(" ", "_").replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return REPLACE;
        }
    }
}
