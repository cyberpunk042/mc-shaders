/*
 * Ported from the-virus-block-mc (net.cyberpunk042.visual), where it is
 * geometry model code with no Minecraft or graphics-API dependency.
 * Relicensed to MIT here by the author, per the engine/content split
 * recorded in docs/PORTING.md.
 */
package net.cyberpunk042.mcshaders.core.shape;

/**
 * Interface for shape lifecycle stages.
 * 
 * Each shape type defines its own stages by implementing this interface
 * with an enum. This allows different shapes to have different stage semantics.
 * 
 * <h2>User Controls</h2>
 * User sets both stage AND phase in UI. Animation modifies phase over time
 * while respecting the user's stage and edgeMode settings.
 * 
 * <h2>Preview Capability</h2>
 * User can set stage + phase manually to preview exactly what the shape 
 * looks like at any point - without running animation.
 *
 
 * <h2>Example: Rays</h2>
 * <pre>
 * public enum RayFlowStage implements ShapeStage {
 *     DORMANT,    // Ray not visible
 *     SPAWNING,   // Ray appearing
 *     ACTIVE,     // Ray fully visible
 *     DESPAWNING  // Ray disappearing
 * }
 * </pre>
 * 
 * <h2>Example: Sphere</h2>
 * <pre>
 * public enum SphereStage implements ShapeStage {
 *     COLLAPSED,  // Sphere at zero radius
 *     EXPANDING,  // Sphere growing
 *     FULL,       // Sphere at full size
 *     CONTRACTING // Sphere shrinking
 * }
 * </pre>
 */
public interface ShapeStage {
    
    /**
     * Returns true if the shape is visible in this stage.
     */
    boolean isVisible();
    
    /**
     * Returns true if this is a transitional stage.
     */
    boolean isTransitional();
    
    /**
     * Display name for UI.
     */
    String displayName();
}

