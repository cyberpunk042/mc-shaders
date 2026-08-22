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
 * Lifecycle stages specific to Ray shapes.
 * 
 * <h2>Stage vs Phase</h2>
 * <ul>
 *   <li><b>Stage:</b> User controlled - set in UI or config</li>
 *   <li><b>Phase:</b> Animation controlled - computed from time (0-1)</li>
 * </ul>
 * 
 * <h2>How Phase Interprets in Each Stage</h2>
 * <table>
 *   <tr><th>Stage</th><th>Phase 0</th><th>Phase 1</th></tr>
 *   <tr><td>DORMANT</td><td colspan="2">No visual - phase ignored</td></tr>
 *   <tr><td>SPAWNING</td><td>Just appearing</td><td>Fully visible</td></tr>
 *   <tr><td>ACTIVE</td><td>At inner radius</td><td>At outer radius</td></tr>
 *   <tr><td>DESPAWNING</td><td>Fully visible</td><td>Gone</td></tr>
 * </table>
 */
public enum RayFlowStage implements ShapeStage {
    
    /**
     * Ray is not visible. Phase has no effect.
     */
    DORMANT("Dormant", false, false),
    
    /**
     * Ray is appearing/spawning.
     * Phase 0 = just starting to appear
     * Phase 1 = fully visible
     */
    SPAWNING("Spawning", true, true),
    
    /**
     * Ray is fully visible and active.
     * Phase controls position along the ray path (0 = inner, 1 = outer).
     */
    ACTIVE("Active", true, false),
    
    /**
     * Ray is disappearing/despawning.
     * Phase 0 = fully visible
     * Phase 1 = gone
     */
    DESPAWNING("Despawning", true, true);
    
    private final String displayName;
    private final boolean visible;
    private final boolean transitional;
    
    RayFlowStage(String displayName, boolean visible, boolean transitional) {
        this.displayName = displayName;
        this.visible = visible;
        this.transitional = transitional;
    }
    
    @Override
    public boolean isVisible() {
        return visible;
    }
    
    @Override
    public boolean isTransitional() {
        return transitional;
    }
    
    @Override
    public String displayName() {
        return displayName;
    }
    
    /**
     * Parse from string, case-insensitive.
     */
    public static RayFlowStage fromString(String value) {
        if (value == null) return ACTIVE;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ACTIVE;
        }
    }
}
