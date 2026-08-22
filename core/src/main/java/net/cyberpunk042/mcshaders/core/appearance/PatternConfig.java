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
package net.cyberpunk042.mcshaders.core.appearance;

import net.cyberpunk042.mcshaders.core.util.MathSupport;

import net.cyberpunk042.mcshaders.core.pattern.VertexPattern;
import net.cyberpunk042.mcshaders.core.pattern.QuadPattern;
import net.cyberpunk042.mcshaders.core.pattern.SegmentPattern;
import net.cyberpunk042.mcshaders.core.pattern.SectorPattern;
import net.cyberpunk042.mcshaders.core.pattern.EdgePattern;
import net.cyberpunk042.mcshaders.core.serial.JsonField;

/**
 * Configuration for surface patterns (bands, checker, etc.) and vertex patterns.
 * 
 * <h2>Surface Pattern Types</h2>
 * <ul>
 *   <li>{@link PatternType#NONE} - No pattern, full surface</li>
 *   <li>{@link PatternType#BANDS} - Horizontal stripes/bands</li>
 *   <li>{@link PatternType#CHECKER} - Checkerboard pattern</li>
 * </ul>
 * 
 * <h2>Vertex Patterns</h2>
 * <p>Control how vertices are arranged in tessellation:
 * <ul>
 *   <li>{@link QuadPattern} - For quad-based (spheres, prism sides)</li>
 *   <li>{@link SegmentPattern} - For segment-based (rings)</li>
 *   <li>{@link SectorPattern} - For radial/fan (discs)</li>
 *   <li>{@link EdgePattern} - For wireframes (cages)</li>
 * </ul>
 * 
 * <h2>Usage in Tessellation</h2>
 * <pre>
 * PatternConfig pattern = PatternConfig.bands(4, 0.3f);
 * if (pattern.shouldRender(latFraction, lonFraction)) {
 *     // Emit this cell
 * }
 * 
 * // Get vertex pattern for quad-based tessellation
 * QuadPattern quadPattern = pattern.quadPattern();
 * </pre>
 * 
 * @see Appearance
 * @see VertexPattern
 */
public record PatternConfig(
    PatternType type,
    int count,
    float thickness,
    @JsonField(skipIfEqualsConstant = "QuadPattern.DEFAULT") VertexPattern vertexPattern
){
    
    /**
     * Pattern type enumeration.
     */
    public enum PatternType {
        /** No pattern - render entire surface. */
        NONE("none"),
        
        /** Horizontal bands/stripes. */
        BANDS("bands"),
        
        /** Checkerboard pattern. */
        CHECKER("checker");
        
        private final String id;
        
        PatternType(String id) {
            this.id = id;
        }
        
        public String id() {
            return id;
        }
        
        public static PatternType fromId(String id) {
            if (id == null) return NONE;
            String lower = id.toLowerCase();
            for (PatternType type : values()) {
                if (type.id.equals(lower)) {
                    return type;
                }
            }
            return NONE;
        }
    }
    
    // =========================================================================
    // Presets
    // =========================================================================
    
    /** No pattern - render full surface. */
    public static final PatternConfig NONE = new PatternConfig(PatternType.NONE, 0, 0.0f, QuadPattern.DEFAULT);
    
    // =========================================================================
    // Compact Constructor (Validation)
    // =========================================================================
    
    public PatternConfig {
        count = Math.max(0, count);
        thickness = MathSupport.clamp(thickness, 0.0f, 1.0f);
        vertexPattern = vertexPattern != null ? vertexPattern : QuadPattern.DEFAULT;
    }
    
    // =========================================================================
    // Factory Methods
    // =========================================================================
    
    /**
     * Creates a bands pattern.
     * @param count Number of bands
     * @param thickness Band thickness (0.0-1.0, relative to band spacing)
     */
    public static PatternConfig bands(int count, float thickness) {
        return new PatternConfig(PatternType.BANDS, count, thickness, QuadPattern.DEFAULT);
    }
    
    /**
     * Creates a checker pattern.
     * @param count Grid divisions per axis
     */
    public static PatternConfig checker(int count) {
        return new PatternConfig(PatternType.CHECKER, count, 0.5f, QuadPattern.DEFAULT);
    }
    
    /**
     * Creates a pattern with specific vertex pattern.
     */
    public static PatternConfig withVertexPattern(VertexPattern pattern) {
        return new PatternConfig(PatternType.NONE, 0, 0.0f, pattern);
    }
    
    /**
     * Creates a bands pattern with specific vertex pattern.
     */
    public static PatternConfig bands(int count, float thickness, VertexPattern pattern) {
        return new PatternConfig(PatternType.BANDS, count, thickness, pattern);
    }
    
    // =========================================================================
    // Vertex Pattern Accessors (Type-Safe)
    // =========================================================================
    
    /**
     * Gets the quad pattern for quad-based tessellation (spheres, prisms).
     * Returns DEFAULT if the pattern is not a QuadPattern.
     */
    public QuadPattern quadPattern() {
        return vertexPattern instanceof QuadPattern qp ? qp : QuadPattern.DEFAULT;
    }
    
    /**
     * Gets the segment pattern for ring tessellation.
     * Returns DEFAULT if the pattern is not a SegmentPattern.
     */
    public SegmentPattern segmentPattern() {
        return vertexPattern instanceof SegmentPattern sp ? sp : SegmentPattern.DEFAULT;
    }
    
    /**
     * Gets the sector pattern for disc tessellation.
     * Returns DEFAULT if the pattern is not a SectorPattern.
     */
    public SectorPattern sectorPattern() {
        return vertexPattern instanceof SectorPattern sp ? sp : SectorPattern.DEFAULT;
    }
    
    /**
     * Gets the edge pattern for wireframe/cage rendering.
     * Returns DEFAULT if the pattern is not an EdgePattern.
     */
    public EdgePattern edgePattern() {
        return vertexPattern instanceof EdgePattern ep ? ep : EdgePattern.DEFAULT;
    }
    
    // trianglePatternType() removed - use quadPattern() or vertexPattern() directly
    
    // =========================================================================
    // Pattern Logic
    // =========================================================================
    
    /**
     * Determines if a cell should be rendered based on surface pattern.
     * 
     * @param latFraction Latitude position (0.0 = north pole, 1.0 = south pole)
     * @param lonFraction Longitude position (0.0 = start, 1.0 = full circle)
     * @return true if this cell should be rendered
     */
    public boolean shouldRender(float latFraction, float lonFraction) {
        return switch (type) {
            case NONE -> true;
            
            case BANDS -> {
                if (count <= 0) yield true;
                float scaled = latFraction * count;
                float frac = scaled - (int) Math.floor(scaled);
                yield frac <= thickness;
            }
            
            case CHECKER -> {
                int band = Math.max(1, count);
                int latCell = Math.min(band - 1, (int) Math.floor(latFraction * band));
                int lonCell = Math.min(band - 1, (int) Math.floor(lonFraction * band));
                yield ((latCell + lonCell) & 1) == 0;
            }
        };
    }
    
    /**
     * Checks if this config has any surface pattern effect.
     */
    public boolean hasPattern() {
        return type != PatternType.NONE && count > 0;
    }
    
    /**
     * Checks if this config has a non-default vertex pattern.
     */
    public boolean hasVertexPattern() {
        return vertexPattern != QuadPattern.DEFAULT;
    }
    
    // =========================================================================
    // JSON Serialization
    // =========================================================================
    
    
    
    
}
