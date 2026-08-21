/*
 * Ported from the-virus-block-mc (net.cyberpunk042.visual), where it is
 * geometry model code with no Minecraft or graphics-API dependency.
 * Relicensed to MIT here by the author, per the engine/content split
 * recorded in docs/PORTING.md.
 */
package net.cyberpunk042.mcshaders.core.shape;

/**
 * Controls how shapes appear at edges during transitional stages.
 * 
 * <p>This is a user-controlled setting that determines the visual effect
 * when a shape is in entering the field of view or leaving the field of view.</p>
 * 
 * <h2>Modes</h2>
 * <ul>
 *   <li><b>SCALE</b>: Shape scales up/down uniformly</li>
 *   <li><b>CLIP</b>: Shape is progressively clipped from edge</li>
 *   <li><b>FADE</b>: Shape fades in/out via alpha</li>
 * </ul>
 * 
 * <h2>How CLIP Works</h2>
 * <p>Each vertex has an implicit t-value (0 at base, 1 at tip).
 * Vertices outside the visible t-range have their alpha set to 0,
 * creating the illusion of the shape being consumed from one end.</p>
 */
public enum EdgeTransitionMode {
    /** Shape scales uniformly at edges. */
    SCALE("Scale"),
    
    /** Shape is progressively clipped from edge. */
    CLIP("Clip"),
    
    /** Shape fades in/out via alpha. */
    FADE("Fade");
    
    private final String displayName;
    
    EdgeTransitionMode(String displayName) {
        this.displayName = displayName;
    }
    
    public String displayName() {
        return displayName;
    }
    
    /**
     * Parses from string, case-insensitive.
     * @param value String to parse
     * @return Matching mode or CLIP if not found
     */
    public static EdgeTransitionMode fromString(String value) {
        if (value == null) return CLIP;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CLIP;
        }
    }
}
