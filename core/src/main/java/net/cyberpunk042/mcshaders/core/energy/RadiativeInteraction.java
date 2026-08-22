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
 * Radiative energy interaction modes.
 * 
 * <p>Defines how radiative energy interacts with the shape based on phase (0-1).
 * This replaces the old {@code LengthMode} with physics-based naming.</p>
 * 
 * <h2>Modes</h2>
 * <ul>
 *   <li><b>NONE</b>: No radiative interaction (full shape visible)</li>
 *   <li><b>EMISSION</b>: Energy radiates outward from center (was RADIATE)</li>
 *   <li><b>ABSORPTION</b>: Energy flows inward toward center (was ABSORB)</li>
 *   <li><b>REFLECTION</b>: Energy bounces back from surface</li>
 *   <li><b>TRANSMISSION</b>: Energy passes through (was SEGMENT)</li>
 *   <li><b>SCATTERING</b>: Energy disperses in multiple directions</li>
 *   <li><b>OSCILLATION</b>: Energy oscillates/pulses (was PULSE)</li>
 *   <li><b>RESONANCE</b>: Energy grows then decays (was GROW_SHRINK)</li>
 * </ul>
 * 
 * <h2>Phase Interpretation</h2>
 * <p>Phase (0-1) drives the animation:
 * <ul>
 *   <li>EMISSION: phase 0 = at inner edge, phase 1 = at outer edge</li>
 *   <li>ABSORPTION: phase 0 = at outer edge, phase 1 = at inner edge</li>
 *   <li>TRANSMISSION: phase controls position of visible segment</li>
 *   <li>OSCILLATION: phase 0→0.5→1 = shrink→expand→shrink</li>
 *   <li>RESONANCE: phase 0→0.5 = grow, phase 0.5→1 = shrink</li>
 * </ul>
 * </p>
 * 
 * @see EnergyInteractionType
 */
public enum RadiativeInteraction {
    /** No radiative interaction - full shape visible. */
    NONE("None"),
    
    /** Energy radiates outward from center. Visible segment moves inner→outer. */
    EMISSION("Emission"),
    
    /** Energy flows inward toward center. Visible segment moves outer→inner. */
    ABSORPTION("Absorption"),
    
    /** Energy bounces back from surface. Creates reflection patterns. */
    REFLECTION("Reflection"),
    
    /** Energy passes through. Fixed-length segment moves along shape. */
    TRANSMISSION("Transmission"),
    
    /** Energy disperses in multiple directions. Creates scattered visibility. */
    SCATTERING("Scattering"),
    
    /** Energy oscillates/pulses. Whole shape breathes in and out. */
    OSCILLATION("Oscillation"),
    
    /** Energy grows then decays. One complete cycle of expansion/contraction. */
    RESONANCE("Resonance");
    
    private final String displayName;
    
    RadiativeInteraction(String displayName) {
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
     * Whether this mode moves energy outward (away from center).
     */
    public boolean isOutward() {
        return this == EMISSION;
    }
    
    /**
     * Whether this mode moves energy inward (toward center).
     */
    public boolean isInward() {
        return this == ABSORPTION;
    }
    
    /**
     * Parses from string, case-insensitive.
     * Also supports legacy LengthMode names for backward compatibility.
     */
    public static RadiativeInteraction fromString(String value) {
        if (value == null) return NONE;
        
        // Support legacy LengthMode names
        String normalized = value.toUpperCase().replace(" ", "_").replace("-", "_");
        switch (normalized) {
            case "RADIATE" -> { return EMISSION; }
            case "ABSORB" -> { return ABSORPTION; }
            case "SEGMENT" -> { return TRANSMISSION; }
            case "PULSE" -> { return OSCILLATION; }
            case "GROW_SHRINK" -> { return RESONANCE; }
        }
        
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
    
    /**
     * Convert to legacy LengthMode name for backward compatibility.
     */
    public String toLegacyName() {
        return switch (this) {
            case NONE -> "NONE";
            case EMISSION -> "RADIATE";
            case ABSORPTION -> "ABSORB";
            case TRANSMISSION -> "SEGMENT";
            case OSCILLATION -> "PULSE";
            case RESONANCE -> "GROW_SHRINK";
            case REFLECTION, SCATTERING -> "NONE"; // New modes have no legacy equivalent
        };
    }
}
