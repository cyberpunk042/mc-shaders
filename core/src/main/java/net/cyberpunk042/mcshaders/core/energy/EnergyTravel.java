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
 * Energy travel modes - how energy/particles move along the shape.
 * 
 * <h2>Base Modes (Unidirectional)</h2>
 * <ul>
 *   <li><b>NONE</b>: No travel animation</li>
 *   <li><b>CHASE</b>: Discrete particles travel outward</li>
 *   <li><b>SCROLL</b>: Continuous gradient scrolls</li>
 *   <li><b>COMET</b>: Bright head with fading tail</li>
 *   <li><b>SPARK</b>: Random sparks shoot outward</li>
 *   <li><b>PULSE_WAVE</b>: Brightness pulses travel outward</li>
 * </ul>
 * 
 * <h2>Reverse Modes</h2>
 * <ul>
 *   <li><b>REVERSE_CHASE</b>: Chase particles moving inward</li>
 *   <li><b>REVERSE_SCROLL</b>: Gradient scrolls inward</li>
 *   <li><b>REVERSE_COMET</b>: Comet traveling inward</li>
 *   <li><b>REVERSE_PULSE</b>: Pulse wave traveling inward</li>
 * </ul>
 * 
 * <h2>Bipolar Modes (Dual Opposing Jets)</h2>
 * Two jets from center, traveling in opposite directions.
 * <ul>
 *   <li><b>*_SYNC</b>: Both sides peak together (mirrored phase)</li>
 *   <li><b>*_ALTERNATE</b>: One side leads while other trails (ping-pong)</li>
 * </ul>
 * 
 * @see EnergyInteractionType
 */
public enum EnergyTravel {
    // =========================================================================
    // No Animation
    // =========================================================================
    NONE("None", Category.NONE, false),
    
    // =========================================================================
    // Unidirectional Outward
    // =========================================================================
    /** Discrete particles/packets travel outward from center. */
    CHASE("Chase", Category.CHASE, false),
    
    /** Continuous gradient scrolls outward. */
    SCROLL("Scroll", Category.SCROLL, false),
    
    /** Bright head with fading tail travels outward. */
    COMET("Comet", Category.COMET, false),
    
    /** Random sparks shoot outward. */
    SPARK("Spark", Category.SPARK, false),
    
    /** Brightness pulse waves travel outward. */
    PULSE_WAVE("Pulse Wave", Category.PULSE, false),
    
    // =========================================================================
    // Unidirectional Inward (Reverse)
    // =========================================================================
    /** Chase particles moving inward toward center. */
    REVERSE_CHASE("Reverse Chase", Category.CHASE, false),
    
    /** Gradient scrolls inward toward center. */
    REVERSE_SCROLL("Reverse Scroll", Category.SCROLL, false),
    
    /** Comet traveling inward toward center. */
    REVERSE_COMET("Reverse Comet", Category.COMET, false),
    
    /** Pulse wave traveling inward toward center. */
    REVERSE_PULSE("Reverse Pulse", Category.PULSE, false),
    
    // =========================================================================
    // Bipolar Chase
    // =========================================================================
    /** Bipolar chase - both jets peak together (synchronized phase). */
    BIPOLAR_CHASE_SYNC("Bipolar Chase Sync", Category.CHASE, true),
    
    /** Bipolar chase - one jet leads, the other trails (alternating). */
    BIPOLAR_CHASE_ALT("Bipolar Chase Alt", Category.CHASE, true),
    
    // =========================================================================
    // Bipolar Scroll
    // =========================================================================
    /** Bipolar scroll - both gradients scroll outward together. */
    BIPOLAR_SCROLL_SYNC("Bipolar Scroll Sync", Category.SCROLL, true),
    
    /** Bipolar scroll - alternating gradient flow. */
    BIPOLAR_SCROLL_ALT("Bipolar Scroll Alt", Category.SCROLL, true),
    
    // =========================================================================
    // Bipolar Comet
    // =========================================================================
    /** Bipolar comet - two comets launch from center simultaneously. */
    BIPOLAR_COMET_SYNC("Bipolar Comet Sync", Category.COMET, true),
    
    /** Bipolar comet - comets alternate, one launches as other returns. */
    BIPOLAR_COMET_ALT("Bipolar Comet Alt", Category.COMET, true),
    
    // =========================================================================
    // Bipolar Spark
    // =========================================================================
    /** Bipolar spark - sparks shoot from center in both directions. */
    BIPOLAR_SPARK_SYNC("Bipolar Spark Sync", Category.SPARK, true),
    
    /** Bipolar spark - alternating spark bursts. */
    BIPOLAR_SPARK_ALT("Bipolar Spark Alt", Category.SPARK, true),
    
    // =========================================================================
    // Bipolar Pulse
    // =========================================================================
    /** Bipolar pulse - waves expand from center in both directions. */
    BIPOLAR_PULSE_SYNC("Bipolar Pulse Sync", Category.PULSE, true),
    
    /** Bipolar pulse - alternating pulse expansion. */
    BIPOLAR_PULSE_ALT("Bipolar Pulse Alt", Category.PULSE, true),
    
    // =========================================================================
    // Legacy Aliases (for backwards compatibility)
    // =========================================================================
    /** @deprecated Use BIPOLAR_CHASE_SYNC */
    @Deprecated BIPOLAR("Bipolar", Category.CHASE, true),
    
    /** @deprecated Use BIPOLAR_CHASE_ALT */
    @Deprecated BIPOLAR_ALTERNATE("Bipolar Alt", Category.CHASE, true);
    
    // =========================================================================
    // Category (for grouping related modes)
    // =========================================================================
    public enum Category {
        NONE, CHASE, SCROLL, COMET, SPARK, PULSE
    }
    
    private final String displayName;
    private final Category category;
    private final boolean bipolar;
    
    EnergyTravel(String displayName, Category category, boolean bipolar) {
        this.displayName = displayName;
        this.category = category;
        this.bipolar = bipolar;
    }
    
    public String displayName() {
        return displayName;
    }
    
    public Category category() {
        return category;
    }
    
    /**
     * Whether this mode is active (not NONE).
     */
    public boolean isActive() {
        return this != NONE;
    }
    
    /**
     * Whether this mode travels outward (away from center).
     */
    public boolean isOutward() {
        return !isReverse() && this != NONE;
    }
    
    /**
     * Whether this is a reverse (inward) mode.
     */
    public boolean isReverse() {
        return name().startsWith("REVERSE_");
    }
    
    /**
     * Whether this is a bipolar mode (two opposing jets from center).
     */
    public boolean isBipolar() {
        return bipolar;
    }
    
    /**
     * Whether this is an alternating bipolar mode (ping-pong effect).
     */
    public boolean isAlternating() {
        return name().endsWith("_ALT") || this == BIPOLAR_ALTERNATE;
    }
    
    /**
     * Whether this is a synchronized bipolar mode (both jets peak together).
     */
    public boolean isSynchronized() {
        return bipolar && !isAlternating();
    }
    
    /**
     * Gets the base travel style (CHASE, SCROLL, COMET, SPARK, PULSE).
     */
    public Category getBaseStyle() {
        return category;
    }
    
    /**
     * Parses from string, case-insensitive.
     */
    public static EnergyTravel fromString(String value) {
        if (value == null) return NONE;
        try {
            return valueOf(value.toUpperCase().replace(" ", "_").replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
    
    /**
     * Returns all active modes (excludes NONE and deprecated).
     */
    public static EnergyTravel[] activeModes() {
        return new EnergyTravel[] {
            // Outward
            CHASE, SCROLL, COMET, SPARK, PULSE_WAVE,
            // Inward
            REVERSE_CHASE, REVERSE_SCROLL, REVERSE_COMET, REVERSE_PULSE,
            // Bipolar Sync
            BIPOLAR_CHASE_SYNC, BIPOLAR_SCROLL_SYNC, BIPOLAR_COMET_SYNC, BIPOLAR_SPARK_SYNC, BIPOLAR_PULSE_SYNC,
            // Bipolar Alt
            BIPOLAR_CHASE_ALT, BIPOLAR_SCROLL_ALT, BIPOLAR_COMET_ALT, BIPOLAR_SPARK_ALT, BIPOLAR_PULSE_ALT
        };
    }
    
    /**
     * Returns only unidirectional modes (outward + inward).
     */
    public static EnergyTravel[] unidirectionalModes() {
        return new EnergyTravel[] {
            CHASE, SCROLL, COMET, SPARK, PULSE_WAVE,
            REVERSE_CHASE, REVERSE_SCROLL, REVERSE_COMET, REVERSE_PULSE
        };
    }
    
    /**
     * Returns only bipolar modes.
     */
    public static EnergyTravel[] bipolarModes() {
        return new EnergyTravel[] {
            BIPOLAR_CHASE_SYNC, BIPOLAR_CHASE_ALT,
            BIPOLAR_SCROLL_SYNC, BIPOLAR_SCROLL_ALT,
            BIPOLAR_COMET_SYNC, BIPOLAR_COMET_ALT,
            BIPOLAR_SPARK_SYNC, BIPOLAR_SPARK_ALT,
            BIPOLAR_PULSE_SYNC, BIPOLAR_PULSE_ALT
        };
    }
}
