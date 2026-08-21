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
package net.cyberpunk042.mcshaders.core.animation;

/**
 * Stage transition modes - how a stage ends and transitions to the next.
 * 
 * <h2>Modes</h2>
 * <ul>
 *   <li><b>TIME</b>: Stage ends when duration expires, auto-advances to next</li>
 *   <li><b>MANUAL</b>: Stage waits for explicit transition call (GUI button)</li>
 *   <li><b>EVENT</b>: Stage ends on external event (e.g., key release)</li>
 *   <li><b>HOLD</b>: Stage continues indefinitely until explicitly ended</li>
 *   <li><b>CHAIN</b>: Immediately chains to next stage (0 duration)</li>
 * </ul>
 * 
 * @see StageConfig
 */
public enum StageTransition {
    /** Stage ends when duration expires, automatically advances to next stage. */
    TIME("Time-based"),
    
    /** Stage waits for explicit transition call (user clicks button in GUI). */
    MANUAL("Manual"),
    
    /** Stage ends on external event trigger. */
    EVENT("Event Trigger"),
    
    /** Stage continues indefinitely until manually ended. */
    HOLD("Hold"),
    
    /** Immediately chains to next stage (no waiting). */
    CHAIN("Chain");
    
    private final String displayName;
    
    StageTransition(String displayName) {
        this.displayName = displayName;
    }
    
    public String displayName() {
        return displayName;
    }
    
    /**
     * Whether this transition requires a duration.
     */
    public boolean requiresDuration() {
        return this == TIME;
    }
    
    /**
     * Whether this transition waits for user input.
     */
    public boolean isManual() {
        return this == MANUAL;
    }
    
    /**
     * Whether this transition can run indefinitely.
     */
    public boolean isIndefinite() {
        return this == HOLD || this == MANUAL;
    }
    
    /**
     * Parse from string, case-insensitive.
     */
    public static StageTransition fromString(String value) {
        if (value == null) return TIME;
        try {
            return valueOf(value.toUpperCase().replace("-", "_").replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return TIME;
        }
    }
}
