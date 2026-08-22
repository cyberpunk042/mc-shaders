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

import net.cyberpunk042.mcshaders.core.pattern.CellType;
import org.joml.Vector3f;

import java.util.Map;
import net.cyberpunk042.mcshaders.core.validation.Range;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;
import net.cyberpunk042.mcshaders.core.serial.JsonField;

/**
 * Procedural molecule shape - multiple spheres connected by tubes.
 * 
 * <h2>Overview</h2>
 * <p>Creates metaball-style molecule visualization by combining:</p>
 * <ul>
 *   <li><b>Atom spheres</b> - positioned using Fibonacci spiral or geometric patterns</li>
 *   <li><b>Connector tubes</b> - rings/cylinders that connect nearby atoms</li>
 * </ul>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "shape": {
 *   "type": "molecule",
 *   "atomCount": 4,
 *   "atomRadius": 0.3,
 *   "atomDistance": 0.8,
 *   "neckRadius": 0.15,
 *   "neckPinch": 0.5,
 *   "connectionDistance": 1.0,
 *   "seed": 42
 * }
 * </pre>
 * 
 * <h2>Atom Distributions</h2>
 * <ul>
 *   <li><b>FIBONACCI</b> - Even spherical distribution (default)</li>
 *   <li><b>RANDOM</b> - Random positions with seed control</li>
 *   <li><b>TETRAHEDRAL</b> - 4 atoms at tetrahedral vertices</li>
 *   <li><b>OCTAHEDRAL</b> - 6 atoms at octahedral vertices</li>
 *   <li><b>ICOSAHEDRAL</b> - 12 atoms at icosahedral vertices</li>
 * </ul>
 * 
 * @see Shape
 * @see AtomDistribution
 */
public record MoleculeShape(
    /** Number of atom spheres (2-12) */
    @Range(ValueRange.STEPS) int atomCount,
    
    /** Radius of each atom sphere relative to overall radius */
    @Range(ValueRange.RADIUS) float atomRadius,
    
    /** Distance of atoms from center (0.5-2.0) */
    @Range(ValueRange.POSITIVE) float atomDistance,
    
    /** Radius of connector tubes */
    @Range(ValueRange.RADIUS) float neckRadius,
    
    /** How much the neck pinches in the middle (0=cylinder, 1=very thin waist) */
    @Range(ValueRange.NORMALIZED) float neckPinch,
    
    /** Maximum distance for atoms to be connected (relative to atomDistance) */
    @Range(ValueRange.POSITIVE) float connectionDistance,
    
    /** Seed for reproducible atom positioning */
    @JsonField(skipIfDefault = true, defaultValue = "42") int seed,
    
    /** Tessellation quality for atoms (latitude steps) */
    @JsonField(skipIfDefault = true, defaultValue = "16") int atomLatSteps,
    
    /** Tessellation quality for atoms (longitude steps) */
    @JsonField(skipIfDefault = true, defaultValue = "24") int atomLonSteps,
    
    /** Tessellation quality for connectors (segments around tube) */
    @JsonField(skipIfDefault = true, defaultValue = "12") int neckSegments,
    
    /** Tessellation quality for connectors (rings along tube) */
    @JsonField(skipIfDefault = true, defaultValue = "8") int neckRings,
    
    /** Atom position distribution algorithm */
    @JsonField(skipIfDefault = true, defaultValue = "FIBONACCI") AtomDistribution distribution,
    
    /** Size variation between atoms (0=uniform, 1=varied) */
    @JsonField(skipIfDefault = true) float sizeVariation,
    
    /** Overall scale of the molecule */
    @Range(ValueRange.RADIUS) float scale
) implements Shape {
    
    // =========================================================================
    // Defaults and Factory Methods
    // =========================================================================
    
    /** Default molecule shape */
    public static final MoleculeShape DEFAULT = new MoleculeShape(
        4, 0.3f, 0.8f, 0.12f, 0.5f, 1.2f, 42, 16, 24, 12, 8, 
        AtomDistribution.FIBONACCI, 0.2f, 1.0f);
    
    /** Simple 2-atom dumbbell */
    public static final MoleculeShape DUMBBELL = new MoleculeShape(
        2, 0.35f, 0.6f, 0.1f, 0.6f, 2.0f, 42, 16, 24, 12, 8,
        AtomDistribution.FIBONACCI, 0.0f, 1.0f);
    
    /** Tetrahedral molecule (4 atoms) */
    public static final MoleculeShape TETRAHEDRAL = new MoleculeShape(
        4, 0.28f, 0.7f, 0.1f, 0.5f, 1.5f, 42, 16, 24, 12, 8,
        AtomDistribution.TETRAHEDRAL, 0.0f, 1.0f);
    
    /** Octahedral molecule (6 atoms) */
    public static final MoleculeShape OCTAHEDRAL = new MoleculeShape(
        6, 0.25f, 0.7f, 0.08f, 0.5f, 1.5f, 42, 16, 24, 12, 8,
        AtomDistribution.OCTAHEDRAL, 0.0f, 1.0f);
    
    /** Clustered organic-looking molecule */
    public static final MoleculeShape CLUSTER = new MoleculeShape(
        8, 0.22f, 0.65f, 0.08f, 0.4f, 1.3f, 42, 12, 18, 10, 6,
        AtomDistribution.FIBONACCI, 0.4f, 1.0f);
    
    /**
     * Creates a molecule with the given atom count.
     */
    public static MoleculeShape of(int atomCount) {
        return DEFAULT.toBuilder().atomCount(atomCount).build();
    }
    
    /**
     * Creates a molecule with count and radius.
     */
    public static MoleculeShape of(int atomCount, float atomRadius) {
        return DEFAULT.toBuilder()
            .atomCount(atomCount)
            .atomRadius(atomRadius)
            .build();
    }
    
    // =========================================================================
    // Shape Interface
    // =========================================================================
    
    @Override
    public String getType() {
        return "molecule";
    }
    
    @Override
    public Vector3f getBounds() {
        float extent = scale * (atomDistance + atomRadius) * 2;
        return new Vector3f(extent, extent, extent);
    }
    
    @Override
    public CellType primaryCellType() {
        return CellType.QUAD;  // Uses quadAsTrianglesFromPattern for sphere tessellation
    }
    
    @Override
    public Map<String, CellType> getParts() {
        return Map.of(
            "atoms", CellType.TRIANGLE,
            "connectors", CellType.TRIANGLE
        );
    }
    
    @Override
    public float getRadius() {
        return scale * (atomDistance + atomRadius);
    }
    
    
    
    // =========================================================================
    // Validation
    // =========================================================================
    
    /**
     * Returns a validated copy with clamped parameters.
     * 
     * <p>Note: neckRadius is clamped to a fraction of atomRadius to ensure
     * connectors don't visually break from the spheres.</p>
     */
    public MoleculeShape validated() {
        float validAtomRadius = Math.max(0.1f, Math.min(1.0f, atomRadius));
        // Neck radius must not exceed 80% of atom radius to maintain visual connection
        float maxNeckRadius = validAtomRadius * 0.8f;
        float validNeckRadius = Math.max(0.02f, Math.min(maxNeckRadius, neckRadius));
        
        return new MoleculeShape(
            Math.max(2, Math.min(12, atomCount)),
            validAtomRadius,
            Math.max(0.3f, Math.min(2.0f, atomDistance)),
            validNeckRadius,
            Math.max(0.0f, Math.min(1.0f, neckPinch)),
            Math.max(0.5f, Math.min(3.0f, connectionDistance)),
            Math.max(0, Math.min(999, seed)),
            Math.max(8, Math.min(32, atomLatSteps)),
            Math.max(12, Math.min(64, atomLonSteps)),
            Math.max(6, Math.min(24, neckSegments)),
            Math.max(4, Math.min(16, neckRings)),
            distribution != null ? distribution : AtomDistribution.FIBONACCI,
            Math.max(0.0f, Math.min(1.0f, sizeVariation)),
            Math.max(0.1f, Math.min(10.0f, scale))
        );
    }
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    
    public Builder toBuilder() {
        return new Builder()
            .atomCount(atomCount)
            .atomRadius(atomRadius)
            .atomDistance(atomDistance)
            .neckRadius(neckRadius)
            .neckPinch(neckPinch)
            .connectionDistance(connectionDistance)
            .seed(seed)
            .atomLatSteps(atomLatSteps)
            .atomLonSteps(atomLonSteps)
            .neckSegments(neckSegments)
            .neckRings(neckRings)
            .distribution(distribution)
            .sizeVariation(sizeVariation)
            .scale(scale);
    }
    
    public static class Builder {
        private int atomCount = 4;
        private float atomRadius = 0.3f;
        private float atomDistance = 0.8f;
        private float neckRadius = 0.12f;
        private float neckPinch = 0.5f;
        private float connectionDistance = 1.2f;
        private int seed = 42;
        private int atomLatSteps = 16;
        private int atomLonSteps = 24;
        private int neckSegments = 12;
        private int neckRings = 8;
        private AtomDistribution distribution = AtomDistribution.FIBONACCI;
        private float sizeVariation = 0.2f;
        private float scale = 1.0f;
        
        public Builder atomCount(int v) { this.atomCount = Math.max(2, Math.min(12, v)); return this; }
        public Builder atomRadius(float v) { this.atomRadius = Math.max(0.1f, Math.min(1.0f, v)); return this; }
        public Builder atomDistance(float v) { this.atomDistance = Math.max(0.3f, Math.min(2.0f, v)); return this; }
        public Builder neckRadius(float v) { this.neckRadius = Math.max(0.02f, Math.min(0.5f, v)); return this; }
        public Builder neckPinch(float v) { this.neckPinch = Math.max(0.0f, Math.min(1.0f, v)); return this; }
        public Builder connectionDistance(float v) { this.connectionDistance = Math.max(0.5f, Math.min(3.0f, v)); return this; }
        public Builder seed(int v) { this.seed = Math.max(0, Math.min(999, v)); return this; }
        public Builder atomLatSteps(int v) { this.atomLatSteps = Math.max(8, Math.min(32, v)); return this; }
        public Builder atomLonSteps(int v) { this.atomLonSteps = Math.max(12, Math.min(64, v)); return this; }
        public Builder neckSegments(int v) { this.neckSegments = Math.max(6, Math.min(24, v)); return this; }
        public Builder neckRings(int v) { this.neckRings = Math.max(4, Math.min(16, v)); return this; }
        public Builder distribution(AtomDistribution v) { this.distribution = v != null ? v : AtomDistribution.FIBONACCI; return this; }
        public Builder sizeVariation(float v) { this.sizeVariation = Math.max(0.0f, Math.min(1.0f, v)); return this; }
        public Builder scale(float v) { this.scale = Math.max(0.1f, Math.min(10.0f, v)); return this; }
        
        public MoleculeShape build() {
            return new MoleculeShape(atomCount, atomRadius, atomDistance, neckRadius, neckPinch,
                connectionDistance, seed, atomLatSteps, atomLonSteps, neckSegments, neckRings,
                distribution, sizeVariation, scale);
        }
    }
}
