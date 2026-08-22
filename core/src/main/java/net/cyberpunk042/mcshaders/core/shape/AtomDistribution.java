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
 * Atom distribution patterns for MoleculeShape.
 * 
 * <p>Controls how atoms are positioned in 3D space around the center.</p>
 */
public enum AtomDistribution {
    
    /**
     * Fibonacci spiral distribution.
     * <p>Produces evenly-spaced points on a sphere using the golden angle.
     * Best for arbitrary atom counts.</p>
     */
    FIBONACCI("Fibonacci", "Even spherical distribution using golden angle"),
    
    /**
     * Random distribution with seed control.
     * <p>Produces pseudo-random positions. Same seed = same positions.</p>
     */
    RANDOM("Random", "Random positions controlled by seed"),
    
    /**
     * Tetrahedral vertices (4 atoms).
     * <p>Atoms at the 4 vertices of a regular tetrahedron.
     * Optimal for 4-atom molecules like CH4.</p>
     */
    TETRAHEDRAL("Tetrahedral", "4 atoms at tetrahedral vertices"),
    
    /**
     * Octahedral vertices (6 atoms).
     * <p>Atoms at the 6 vertices of a regular octahedron (±X, ±Y, ±Z).
     * Optimal for 6-atom molecules like SF6.</p>
     */
    OCTAHEDRAL("Octahedral", "6 atoms at octahedral vertices"),
    
    /**
     * Icosahedral vertices (12 atoms).
     * <p>Atoms at the 12 vertices of a regular icosahedron.
     * Produces buckminsterfullerene-like arrangements.</p>
     */
    ICOSAHEDRAL("Icosahedral", "12 atoms at icosahedral vertices"),
    
    /**
     * Linear arrangement (2 atoms).
     * <p>Two atoms on opposite ends of a line through center.
     * Optimal for dumbbell/diatomic molecules like H2 or O2.</p>
     */
    LINEAR("Linear", "2 atoms on opposite ends");
    
    private final String displayName;
    private final String description;
    
    AtomDistribution(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String displayName() { return displayName; }
    public String description() { return description; }
    
    @Override
    public String toString() { return displayName; }
}
