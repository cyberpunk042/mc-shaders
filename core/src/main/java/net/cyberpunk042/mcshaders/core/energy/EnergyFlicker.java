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
 * Energy flicker modes - random alpha variations.
 * 
 * <p>Renamed from {@code FlickerMode} for consistency with energy naming.</p>
 * 
 * <h2>Modes</h2>
 * <ul>
 *   <li><b>NONE</b>: No flicker</li>
 *   <li><b>SCINTILLATION</b>: Random per-ray flickering (star twinkling)</li>
 *   <li><b>STROBE</b>: Rhythmic on/off blinking</li>
 *   <li><b>FADE_PULSE</b>: Smooth fade in/out (breathing)</li>
 *   <li><b>FLICKER</b>: Candlelight-style unstable flickering</li>
 *   <li><b>LIGHTNING</b>: Flash bright then fade</li>
 *   <li><b>HEARTBEAT</b>: Double-pulse rhythm pattern</li>
 * </ul>
 * 
 * @see EnergyInteractionType
 */
public enum EnergyFlicker {
    /** No flicker. */
    NONE("None"),
    
    /** Random per-ray flickering (star twinkling). */
    SCINTILLATION("Scintillation"),
    
    /** Rhythmic synchronized strobing. */
    STROBE("Strobe"),
    
    /** Smooth fade in/out (breathing alpha). */
    FADE_PULSE("Fade Pulse"),
    
    /** Candlelight-style unstable flickering. */
    FLICKER("Flicker"),
    
    /** Flash bright then fade. */
    LIGHTNING("Lightning"),
    
    /** Double-pulse rhythm pattern. */
    HEARTBEAT("Heartbeat");
    
    private final String displayName;
    
    EnergyFlicker(String displayName) {
        this.displayName = displayName;
    }
    
    public String displayName() {
        return displayName;
    }
    
    /**
     * Whether this mode is active (not NONE).
     */
    public boolean isActive() {
        return this != NONE;
    }
    
    /**
     * Whether this mode is random/per-ray or synchronized.
     */
    public boolean isPerRay() {
        return this == SCINTILLATION || this == FLICKER;
    }
    
    /**
     * Parses from string, case-insensitive.
     */
    public static EnergyFlicker fromString(String value) {
        if (value == null) return NONE;
        try {
            return valueOf(value.toUpperCase().replace(" ", "_").replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
