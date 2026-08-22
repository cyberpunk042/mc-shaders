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

/**
 * Visual energy type for Kamehameha orb and beam components.
 * 
 * <p>Currently simplified to CLASSIC only. Additional types may be added
 * in the future when visual effects are implemented.</p>
 * 
 * @see KamehamehaShape
 */
public enum EnergyType {
    /** Standard smooth energy beam (classic Kamehameha blue). */
    CLASSIC("Classic", "Smooth flowing energy");
    
    private final String displayName;
    private final String description;
    
    EnergyType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String displayName() { return displayName; }
    public String description() { return description; }
    
    /**
     * Base alpha multiplier for this type.
     */
    public float baseAlpha() {
        return 1.0f;
    }
}
