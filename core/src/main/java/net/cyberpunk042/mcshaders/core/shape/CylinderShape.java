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
 * Cylinder/tube shape (also used for beams).
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "shape": {
 *   "type": "cylinder",
 *   "radius": 0.5,
 *   "height": 10.0,
 *   "segments": 16,
 *   "topRadius": 0.5,
 *   "heightSegments": 1,
 *   "capTop": true,
 *   "capBottom": false,
 *   "arc": 360
 * }
 * </pre>
 * 
 * <h2>Parts</h2>
 * <ul>
 *   <li><b>sides</b> (QUAD) - Cylinder wall</li>
 *   <li><b>capTop</b> (SECTOR) - Top cap</li>
 *   <li><b>capBottom</b> (SECTOR) - Bottom cap</li>
 *   <li><b>edges</b> (EDGE) - Edge lines</li>
 * </ul>
 * 
 * @see Shape
 * @see CellType
 */
public record CylinderShape(
    @Range(ValueRange.RADIUS) float radius,
    @Range(ValueRange.POSITIVE_NONZERO) float height,
    @Range(ValueRange.STEPS) int segments,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfEqualsField = "radius") float topRadius,
    @Range(ValueRange.STEPS) @JsonField(skipIfDefault = true, defaultValue = "1") int heightSegments,
    @JsonField(skipIfDefault = true, defaultValue = "true") boolean capTop,
    @JsonField(skipIfDefault = true) boolean capBottom,
    @Range(ValueRange.DEGREES) @JsonField(skipIfDefault = true, defaultValue = "360") float arc
)implements Shape {
    
    /** Default cylinder (both caps). */
    public static CylinderShape defaults() { return DEFAULT; }
    public static CylinderShape thin(float height) { 
        return new CylinderShape(0.1f, height, 16, 0.1f, 1, true, false, 360f); 
    }
    
    public static final CylinderShape DEFAULT = new CylinderShape(
        0.5f, 2.0f, 32, 0.5f, 1, true, true, 360);
    
    /** Beam (tall, thin, top cap only - per docs default). */
    public static final CylinderShape BEAM = new CylinderShape(
        0.1f, 10.0f, 16, 0.1f, 1, true, false, 360);
    
    /** Tube (no caps). */
    public static final CylinderShape TUBE = new CylinderShape(
        0.5f, 2.0f, 32, 0.5f, 1, false, false, 360);
    
    /** Cone (tapered, both caps). */
    public static final CylinderShape CONE = new CylinderShape(
        1.0f, 2.0f, 32, 0.0f, 1, true, true, 360);
    
    /**
     * Creates a simple cylinder with both caps.
     * @param radius Radius
     * @param height Height
     */
    public static CylinderShape of(@Range(ValueRange.RADIUS) float radius, @Range(ValueRange.POSITIVE_NONZERO) float height) {
        return new CylinderShape(radius, height, 32, radius, 1, true, true, 360);
    }
    
    /**
     * Creates a tapered cylinder (cone-like).
     * @param bottomRadius Bottom radius
     * @param topRadius Top radius
     * @param height Height
     */
    public static CylinderShape tapered(float bottomRadius, @Range(ValueRange.POSITIVE) float topRadius, @Range(ValueRange.POSITIVE_NONZERO) float height) {
        return new CylinderShape(bottomRadius, height, 32, topRadius, 1, true, true, 360);
    }
    
    /**
     * Creates a tube (no caps).
     * @param radius Radius
     * @param height Height
     */
    public static CylinderShape tube(@Range(ValueRange.RADIUS) float radius, @Range(ValueRange.POSITIVE_NONZERO) float height) {
        return new CylinderShape(radius, height, 32, radius, 1, false, false, 360);
    }
    
    @Override
    public String getType() {
        return "cylinder";
    }
    
    @Override
    public Vector3f getBounds() {
        float maxR = Math.max(radius, topRadius);
        return new Vector3f(maxR * 2, height, maxR * 2);
    }
    
    @Override
    public CellType primaryCellType() {
        return CellType.QUAD;
    }
    
    @Override
    public Map<String, CellType> getParts() {
        return Map.of(
            "sides", CellType.QUAD,
            "capTop", CellType.SECTOR,
            "capBottom", CellType.SECTOR,
            "edges", CellType.EDGE
        );
    }
    
    /** Whether this is a regular cylinder (same top/bottom radius). */
    public boolean isRegular() {
        return radius == topRadius;
    }
    
    /** Whether this is a cone (top radius = 0). */
    public boolean isCone() {
        return topRadius == 0;
    }
    
    /** Whether this is a tube (no caps). */
    public boolean isTube() {
        return !capTop && !capBottom;
    }
    
    

    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    /** Create a builder pre-populated with this shape's values. */
    public Builder toBuilder() {
        return new Builder()
            .radius(radius)
            .height(height)
            .segments(segments)
            .topRadius(topRadius)
            .heightSegments(heightSegments)
            .capTop(capTop)
            .capBottom(capBottom)
            .arc(arc);
    }
    
    public static class Builder {
        private @Range(ValueRange.RADIUS) float radius = 0.5f;
        private @Range(ValueRange.POSITIVE_NONZERO) float height = 2.0f;
        private @Range(ValueRange.STEPS) int segments = 32;
        private @Range(ValueRange.POSITIVE) float topRadius = 0.5f;
        private @Range(ValueRange.STEPS) int heightSegments = 1;
        private boolean capTop = true;
        private boolean capBottom = false;  // Per docs default
        private @Range(ValueRange.DEGREES) float arc = 360;
        
        public Builder radius(float r) { this.radius = r; return this; }
        public Builder height(float h) { this.height = h; return this; }
        public Builder segments(int s) { this.segments = s; return this; }
        public Builder topRadius(float r) { this.topRadius = r; return this; }
        public Builder heightSegments(int s) { this.heightSegments = s; return this; }
        public Builder capTop(boolean c) { this.capTop = c; return this; }
        public Builder capBottom(boolean c) { this.capBottom = c; return this; }
        public Builder arc(float a) { this.arc = a; return this; }
        
        public CylinderShape build() {
            return new CylinderShape(radius, height, segments, topRadius, heightSegments, capTop, capBottom, arc);
        }
    }
}
