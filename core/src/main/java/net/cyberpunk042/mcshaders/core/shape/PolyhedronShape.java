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
 * Platonic solid shapes (regular polyhedra).
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "shape": {
 *   "type": "polyhedron",
 *   "polyType": "ICOSAHEDRON",
 *   "radius": 1.0,
 *   "subdivisions": 0
 * }
 * </pre>
 * 
 * <h2>Parts</h2>
 * <ul>
 *   <li><b>faces</b> (TRIANGLE or QUAD depending on type) - Face surfaces</li>
 *   <li><b>edges</b> (EDGE) - Edge lines</li>
 *   <li><b>vertices</b> (POINT) - Vertex markers (future)</li>
 * </ul>
 * 
 * @see PolyType
 */
public record PolyhedronShape(
    PolyType polyType,
    @Range(ValueRange.RADIUS) float radius,
    @Range(ValueRange.STEPS) @JsonField(skipIfDefault = true) int subdivisions
)implements Shape {
    
    /** Default icosahedron. */
    public static PolyhedronShape cube(float radius) { 
        return new PolyhedronShape(PolyType.CUBE, radius, 0); 
    }
    public static PolyhedronShape octahedron(float radius) { 
        return new PolyhedronShape(PolyType.OCTAHEDRON, radius, 0); 
    }
    public static PolyhedronShape dodecahedron(float radius) { 
        return new PolyhedronShape(PolyType.DODECAHEDRON, radius, 0); 
    }
    public static PolyhedronShape tetrahedron(float radius) { 
        return new PolyhedronShape(PolyType.TETRAHEDRON, radius, 0); 
    }
    public static PolyhedronShape icosahedron(float radius) { 
        return new PolyhedronShape(PolyType.ICOSAHEDRON, radius, 0); 
    }
    
    public static final PolyhedronShape DEFAULT = new PolyhedronShape(
        PolyType.ICOSAHEDRON, 1.0f, 0);
    
    /** Cube polyhedron. */
    public static final PolyhedronShape CUBE = new PolyhedronShape(
        PolyType.CUBE, 1.0f, 0);
    
    /** Octahedron. */
    public static final PolyhedronShape OCTAHEDRON = new PolyhedronShape(
        PolyType.OCTAHEDRON, 1.0f, 0);
    
    /** Dodecahedron. */
    public static final PolyhedronShape DODECAHEDRON = new PolyhedronShape(
        PolyType.DODECAHEDRON, 1.0f, 0);
    
    /** Tetrahedron. */
    public static final PolyhedronShape TETRAHEDRON = new PolyhedronShape(
        PolyType.TETRAHEDRON, 1.0f, 0);
    
    /**
     * Creates a polyhedron of the specified type.
     * @param type Polyhedron type
     * @param radius Circumscribed radius
     */
    public static PolyhedronShape of(PolyType type, @Range(ValueRange.RADIUS) float radius) {
        return new PolyhedronShape(type, radius, 0);
    }
    
    /**
     * Creates a subdivided polyhedron (geodesic sphere effect).
     * @param type Base type
     * @param radius Radius
     * @param subdivisions Subdivision level
     */
    public static PolyhedronShape subdivided(PolyType type, @Range(ValueRange.RADIUS) float radius, @Range(ValueRange.STEPS) int subdivisions) {
        return new PolyhedronShape(type, radius, subdivisions);
    }
    
    @Override
    public String getType() {
        return "polyhedron";
    }
    
    @Override
    public Vector3f getBounds() {
        float d = radius * 2;
        return new Vector3f(d, d, d);
    }
    
    @Override
    public CellType primaryCellType() {
        // Cube and Dodecahedron have quad faces, others have triangles
        return switch (polyType) {
            case CUBE -> CellType.QUAD;
            case DODECAHEDRON -> CellType.QUAD; // Pentagons, but rendered as quads
            default -> CellType.TRIANGLE;
        };
    }
    
    @Override
    public Map<String, CellType> getParts() {
        CellType faceType = primaryCellType();
        return Map.of(
            "faces", faceType,
            "edges", CellType.EDGE
        );
    }
    
    

    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    /** Create a builder pre-populated with this shape's values. */
    public Builder toBuilder() {
        return new Builder()
            .polyType(polyType)
            .radius(radius)
            .subdivisions(subdivisions);
    }
    
    public static class Builder {
        private PolyType polyType = PolyType.ICOSAHEDRON;
        private @Range(ValueRange.RADIUS) float radius = 1.0f;
        private @Range(ValueRange.STEPS) int subdivisions = 0;
        
        public Builder polyType(PolyType t) { this.polyType = t; return this; }
        public Builder radius(float r) { this.radius = r; return this; }
        public Builder subdivisions(int s) { this.subdivisions = s; return this; }
        
        public PolyhedronShape build() {
            return new PolyhedronShape(polyType, radius, subdivisions);
        }
    }
}
