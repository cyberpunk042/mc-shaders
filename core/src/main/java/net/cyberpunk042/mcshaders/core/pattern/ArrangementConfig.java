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
package net.cyberpunk042.mcshaders.core.pattern;

import net.cyberpunk042.mcshaders.core.serial.JsonField;

/**
 * Configuration for vertex arrangement patterns, supporting multi-part shapes.
 * 
 * <p>Each shape can have different patterns for different parts.
 * If a part is not specified, the default pattern is used.</p>
 * 
 * <h2>Shape Parts</h2>
 * <ul>
 *   <li><b>Sphere:</b> main, poles, equator, hemisphereTop, hemisphereBottom</li>
 *   <li><b>Ring:</b> surface, innerEdge, outerEdge</li>
 *   <li><b>Disc:</b> surface, edge</li>
 *   <li><b>Prism:</b> sides, capTop, capBottom, edges</li>
 *   <li><b>Polyhedron:</b> faces, edges, vertices</li>
 * </ul>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * // Simple (all parts use same pattern)
 * "arrangement": "wave_1"
 * 
 * // Multi-part
 * "arrangement": {
 *   "default": "filled_1",
 *   "poles": "sparse",
 *   "equator": "alternating"
 * }
 * </pre>
 */
public record ArrangementConfig(
    // Default for all parts
    String defaultPattern,
    // Sphere parts
    @JsonField(skipIfNull = true) String main,
    @JsonField(skipIfNull = true) String poles,
    @JsonField(skipIfNull = true) String equator,
    @JsonField(skipIfNull = true) String hemisphereTop,
    @JsonField(skipIfNull = true) String hemisphereBottom,
    // Ring parts
    @JsonField(skipIfNull = true) String surface,
    @JsonField(skipIfNull = true) String innerEdge,
    @JsonField(skipIfNull = true) String outerEdge,
    @JsonField(skipIfNull = true) // Disc parts
    String discEdge,
    // Prism/Cylinder parts
    @JsonField(skipIfNull = true) String sides,
    @JsonField(skipIfNull = true) String capTop,
    @JsonField(skipIfNull = true) String capBottom,
    @JsonField(skipIfNull = true) String prismEdges,
    // Polyhedron parts
    @JsonField(skipIfNull = true) String faces,
    @JsonField(skipIfNull = true) String polyEdges,
    @JsonField(skipIfNull = true) String vertices
){
    /** Default arrangement (filled_1 for all parts). */
    public static final ArrangementConfig DEFAULT = of("filled_1");
    
    /**
     * Creates a simple arrangement where all parts use the same pattern.
     * @param pattern Pattern name (e.g., "filled_1", "wave_1")
     */
    public static ArrangementConfig of(String pattern) {
        return new ArrangementConfig(
            pattern,
            null, null, null, null, null,  // sphere
            null, null, null,               // ring
            null,                           // disc
            null, null, null, null,         // prism
            null, null, null                // polyhedron
        );
    }
    
    /**
     * Gets the pattern for a named part, or default if not specified.
     * @param partName The part name (e.g., "poles", "surface")
     * @return The pattern name for that part
     */
    public String getPattern(String partName) {
        String specific = switch (partName) {
            case "main" -> main;
            case "poles" -> poles;
            case "equator" -> equator;
            case "hemisphereTop" -> hemisphereTop;
            case "hemisphereBottom" -> hemisphereBottom;
            case "surface" -> surface;
            case "innerEdge" -> innerEdge;
            case "outerEdge" -> outerEdge;
            case "edge", "discEdge" -> discEdge;
            case "sides" -> sides;
            case "capTop" -> capTop;
            case "capBottom" -> capBottom;
            case "edges", "prismEdges" -> prismEdges;
            case "faces" -> faces;
            case "polyEdges" -> polyEdges;
            case "vertices" -> vertices;
            default -> null;
        };
        return specific != null ? specific : defaultPattern;
    }
    
    /**
     * Resolves the pattern for a part to an actual VertexPattern.
     * <p><b>Warning:</b> Does not validate CellType compatibility.
     * Use {@link #resolvePattern(String, CellType)} for validated resolution.</p>
     * 
     * @param partName The part name
     * @return The VertexPattern for that part
     */
    public VertexPattern resolvePattern(String partName) {
        String patternName = getPattern(partName);
        return VertexPattern.fromString(patternName);
    }
    
    /**
     * Resolves the pattern for a part with CellType validation.
     * <p>If the pattern's CellType doesn't match the expected type,
     * logs an error to chat and returns null (caller should skip rendering).</p>
     * 
     * @param partName The part name
     * @param expectedCellType The CellType the shape expects
     * @return The VertexPattern, or null if incompatible
     * @see VertexPattern#resolveForCellType(String, CellType)
     */
    public VertexPattern resolvePattern(String partName, CellType expectedCellType) {
        String patternName = getPattern(partName);
        return VertexPattern.resolveForCellType(patternName, expectedCellType);
    }
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    // =========================================================================
    // Serialization
    // =========================================================================
    
    

    public static Builder builder() { return new Builder(); }
    /** Create a builder pre-populated with this record's values. */
    public Builder toBuilder() {
        return new Builder()
            .defaultPattern(defaultPattern)
            .main(main)
            .poles(poles)
            .equator(equator)
            .hemisphereTop(hemisphereTop)
            .hemisphereBottom(hemisphereBottom)
            .surface(surface)
            .innerEdge(innerEdge)
            .outerEdge(outerEdge)
            .discEdge(discEdge)
            .sides(sides)
            .capTop(capTop)
            .capBottom(capBottom)
            .prismEdges(prismEdges)
            .faces(faces)
            .polyEdges(polyEdges)
            .vertices(vertices);
    }
    

    
    public static class Builder {
        private String defaultPattern = "filled_1";
        private String main, poles, equator, hemisphereTop, hemisphereBottom;
        private String surface, innerEdge, outerEdge;
        private String discEdge;
        private String sides, capTop, capBottom, prismEdges;
        private String faces, polyEdges, vertices;
        
        public Builder defaultPattern(String p) { this.defaultPattern = p; return this; }
        public Builder main(String p) { this.main = p; return this; }
        public Builder poles(String p) { this.poles = p; return this; }
        public Builder equator(String p) { this.equator = p; return this; }
        public Builder hemisphereTop(String p) { this.hemisphereTop = p; return this; }
        public Builder hemisphereBottom(String p) { this.hemisphereBottom = p; return this; }
        public Builder surface(String p) { this.surface = p; return this; }
        public Builder innerEdge(String p) { this.innerEdge = p; return this; }
        public Builder outerEdge(String p) { this.outerEdge = p; return this; }
        public Builder discEdge(String p) { this.discEdge = p; return this; }
        public Builder sides(String p) { this.sides = p; return this; }
        public Builder capTop(String p) { this.capTop = p; return this; }
        public Builder capBottom(String p) { this.capBottom = p; return this; }
        public Builder prismEdges(String p) { this.prismEdges = p; return this; }
        public Builder faces(String p) { this.faces = p; return this; }
        public Builder polyEdges(String p) { this.polyEdges = p; return this; }
        public Builder vertices(String p) { this.vertices = p; return this; }
        
        public ArrangementConfig build() {
            return new ArrangementConfig(
                defaultPattern,
                main, poles, equator, hemisphereTop, hemisphereBottom,
                surface, innerEdge, outerEdge,
                discEdge,
                sides, capTop, capBottom, prismEdges,
                faces, polyEdges, vertices
            );
        }
    }
}
