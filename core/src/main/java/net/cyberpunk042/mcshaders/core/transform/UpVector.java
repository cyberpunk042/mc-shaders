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
package net.cyberpunk042.mcshaders.core.transform;

/**
 * Defines what "up" means for a primitive's orientation.
 * 
 * <p>The up vector is used when calculating facing and billboard
 * rotations to maintain proper orientation.</p>
 * 
 * @see Transform
 * @see Facing
 */
public enum UpVector {
    /** World Y-axis is always up (DEFAULT) */
    WORLD_UP,
    
    /** Matches the player's current orientation (for player-relative effects) */
    PLAYER_UP,
    
    /** Up vector follows movement direction (for trails/motion effects) */
    VELOCITY,
    
    /** Uses custom rotation values from Transform */
    CUSTOM;
    
    /**
     * Parse from string (case-insensitive).
     * @param id The string identifier
     * @return Matching UpVector, or WORLD_UP if not found
     */
    public static UpVector fromId(String id) {
        if (id == null || id.isEmpty()) return WORLD_UP;
        try {
            return valueOf(id.toUpperCase().replace("-", "_").replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return WORLD_UP;
        }
    }
}
